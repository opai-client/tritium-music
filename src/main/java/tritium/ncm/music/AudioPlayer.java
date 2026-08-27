package tritium.ncm.music;

import lombok.Getter;
import org.lwjgl.BufferUtils;
import repackage.processing.sound.FFT;
import repackage.processing.sound.JSynFFT;
import tritium.TritiumMusicExtension;
import tritium.widget.impl.MusicSpectrumWidget;
import tritium.widget.impl.SpectrumVisualizer;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class AudioPlayer {
    private StreamingSoundPlayer player;
    public Runnable afterPlayed;

    private static final int BAR_COUNT = 128;
    private static final int FFT_HOP_SAMPLES = BAR_COUNT * 5;
    private static final float[] FFT_WINDOW = createFftWindow();

    public static volatile float[] bandValues = new float[0];
    public static final SpectrumVisualizer visualizer = new SpectrumVisualizer(JSynFFT.FFT_SIZE, BAR_COUNT);

    private final float[] fftWindow = new float[JSynFFT.FFT_SIZE];
    private int fftWindowOffset;
    private int fftSamplesSinceAnalysis;
    private final Object captureLock = new Object();
    private float[] captureLeft = new float[0];
    private float[] captureRight = new float[0];
    private int captureOffset;
    private int captureCount;
    private int captureSampleRate = 44100;
    private int captureWindowMillis = -1;

    @Getter
    public float volume = 0.25f;

    public AudioPlayer(File file) {
        this(file, 0);
    }

    public AudioPlayer(File file, long durationMillis) {
        finished = false;
        this.player = new StreamingSoundPlayer(file, durationMillis, this::onPcm);
        this.setListeners();
    }

    public AudioPlayer(String url, String type, long durationMillis) {
        finished = false;
        this.player = new StreamingSoundPlayer(url, type, durationMillis, this::onPcm);
        this.setListeners();
    }

    public void setAudio(File file, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(file, durationMillis, this::onPcm);
        this.setListeners();
        finished = false;
    }

    public void setAudio(String url, String type, long durationMillis) {
        this.close();
        this.player = new StreamingSoundPlayer(url, type, durationMillis, this::onPcm);
        this.setListeners();
        finished = false;
    }

    public float[] wave, waveRight;

    public float[] waveVertexes, waveRightVertexes;
    public float[] osc, oscRight;
    public ByteBuffer waveVertexesBufferBackend, waveRightVertexesBufferBackend;
    public FloatBuffer waveVertexesBuffer, waveRightVertexesBuffer;

    public final ReentrantLock lockL = new ReentrantLock(), lockR = new ReentrantLock();
    public volatile boolean spectrumDataLFilled = false, spectrumDataRFilled = false;

    private OscilloscopeState oscStateL;
    private OscilloscopeState oscStateR;

    private static final int OSC_TARGET_TAPS = 384;
    private static final float OSC_EDGE_STRENGTH = 0.8f;
    private static final float OSC_BUFFER_STRENGTH = 1.0f;
    private static final float OSC_RESPONSIVENESS = 0.4f;

    public static class OscilloscopeState {
        final int stride;
        final int wd;
        final int nd;
        final float[] corrected;
        final float[] ds;
        final float[] corrBuffer;
        final float[] kernel;
        final float[] slopeFinder;
        final float[] bufferWindow;

        OscilloscopeState(int captureSamples, int displaySamples) {
            int s = Math.max(1, Math.round(displaySamples / (float) OSC_TARGET_TAPS));
            this.stride = s;
            this.wd = Math.max(8, displaySamples / s);
            this.nd = captureSamples / s;

            this.corrected = new float[captureSamples];
            this.ds = new float[nd];
            this.corrBuffer = new float[wd];
            this.kernel = new float[wd];
            this.slopeFinder = new float[wd];
            this.bufferWindow = new float[wd];

            float center = (wd - 1) * 0.5f;
            float slopeStd = Math.max(1f, wd * 0.10f);
            float winStd = Math.max(1f, wd * 0.35f);
            int half = wd / 2;

            for (int k = 0; k < wd; k++) {
                float ds = (k - center) / slopeStd;
                float g = (float) Math.exp(-0.5f * ds * ds);
                slopeFinder[k] = (k < half ? -OSC_EDGE_STRENGTH : OSC_EDGE_STRENGTH) * g;

                float dw = (k - center) / winStd;
                bufferWindow[k] = (float) Math.exp(-0.5f * dw * dw);
            }
        }
    }

    public void setListeners() {
        fftWindowOffset = 0;
        fftSamplesSinceAnalysis = 0;
        Arrays.fill(fftWindow, 0);
        synchronized (captureLock) {
            captureOffset = 0;
            captureCount = 0;
        }
        player.setOnFinished(() -> finished = true);
        player.setOnFailed(() -> {
            failed = true;
            finished = true;
        });
    }

    public void doDetections() {
        boolean pausing = this.isPausing();
        if (!TritiumMusicExtension.getInstance().musicSpectrum.isEnabled() || pausing) {
            return;
        }

        MusicSpectrumWidget.Style style = MusicSpectrumWidget.Style.valueOf(TritiumMusicExtension.getInstance().musicSpectrum.style.getValue());

        boolean rect = style == MusicSpectrumWidget.Style.Rect;
        boolean line = style == MusicSpectrumWidget.Style.Line;
        if (rect || line) {
            return;
        }

        if (style == MusicSpectrumWidget.Style.Waveform || style == MusicSpectrumWidget.Style.Oscilloscope) {
            updateCaptureGeometry();
            synchronized (captureLock) {
                int length = captureLeft.length;
                if (length == 0 || captureCount < length) {
                    return;
                }
                wave = orderedCapture(captureLeft);
                waveRight = orderedCapture(captureRight);
            }

            if (style == MusicSpectrumWidget.Style.Waveform) {
                this.computeVertexes(wave, waveVertexes);
                this.computeVertexes(waveRight, waveRightVertexes);
            } else {
                computeOscilloscopeVertexes(wave, osc, waveVertexes, oscStateL);
                computeOscilloscopeVertexes(waveRight, oscRight, waveRightVertexes, oscStateR);
            }

            if (waveVertexesBuffer == null || waveVertexesBuffer.capacity() != this.waveVertexes.length) {
                lockL.lock();
                waveVertexesBufferBackend = BufferUtils.createByteBuffer(this.waveVertexes.length << 2);
                waveVertexesBuffer = waveVertexesBufferBackend.asFloatBuffer();
                waveVertexesBuffer.put(this.waveVertexes);
                lockL.unlock();
            } else {
                waveVertexesBuffer.clear();
                waveVertexesBuffer.put(this.waveVertexes);
            }

            waveVertexesBuffer.flip();
            spectrumDataLFilled = true;

            if (waveRightVertexesBuffer == null || waveRightVertexesBuffer.capacity() != this.waveRightVertexes.length) {
                lockR.lock();
                waveRightVertexesBufferBackend = BufferUtils.createByteBuffer(this.waveRightVertexes.length << 2);
                waveRightVertexesBuffer = waveRightVertexesBufferBackend.asFloatBuffer();
                waveRightVertexesBuffer.put(this.waveRightVertexes);
                lockR.unlock();
            } else {
                waveRightVertexesBuffer.clear();
                waveRightVertexesBuffer.put(this.waveRightVertexes);
            }

            waveRightVertexesBuffer.flip();
            spectrumDataRFilled = true;
        }
    }

    private void onPcm(byte[] data, int offset, int length, AudioFormat format) {
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int bytesPerSample = frameSize / channels;
        int frameCount = length / frameSize;
        float playbackVolume = volume;
        int sampleRate = Math.round(format.getSampleRate());
        captureSampleRate = sampleRate;
        updateCaptureGeometry();

        synchronized (captureLock) {
            for (int frame = 0; frame < frameCount; frame++) {
                int base = offset + frame * frameSize;
                float left = readSample(data, base, bytesPerSample, format.isBigEndian()) * playbackVolume;
                float right = channels > 1
                        ? readSample(data, base + bytesPerSample, bytesPerSample, format.isBigEndian()) * playbackVolume
                        : left;
                captureLeft[captureOffset] = left;
                captureRight[captureOffset] = right;
                captureOffset = (captureOffset + 1) % captureLeft.length;
                captureCount = Math.min(captureCount + 1, captureLeft.length);

                fftWindow[fftWindowOffset++] = (left + right) * 0.5f;
                if (fftWindowOffset == fftWindow.length) {
                    fftWindowOffset = 0;
                }
                fftSamplesSinceAnalysis++;
                if (fftSamplesSinceAnalysis >= FFT_HOP_SAMPLES) {
                    publishSpectrum(sampleRate);
                    fftSamplesSinceAnalysis -= FFT_HOP_SAMPLES;
                }
            }
        }
    }

    private void updateCaptureGeometry() {
        int windowMillis = TritiumMusicExtension.getInstance().musicSpectrum.windowTime.getValue().intValue();
        int displaySamples = Math.max(2, captureSampleRate * windowMillis / 1000);
        int captureSamples = displaySamples * 2;
        synchronized (captureLock) {
            if (captureLeft.length == captureSamples && captureWindowMillis == windowMillis) {
                return;
            }
            captureWindowMillis = windowMillis;
            captureLeft = new float[captureSamples];
            captureRight = new float[captureSamples];
            captureOffset = 0;
            captureCount = 0;
            wave = new float[captureSamples];
            waveRight = new float[captureSamples];
            waveVertexes = new float[displaySamples * 2];
            waveRightVertexes = new float[displaySamples * 2];
            osc = new float[displaySamples];
            oscRight = new float[displaySamples];
            oscStateL = new OscilloscopeState(captureSamples, displaySamples);
            oscStateR = new OscilloscopeState(captureSamples, displaySamples);
            waveVertexesBuffer = null;
            waveRightVertexesBuffer = null;
            spectrumDataLFilled = false;
            spectrumDataRFilled = false;
        }
    }

    private float[] orderedCapture(float[] source) {
        float[] ordered = new float[source.length];
        int tail = source.length - captureOffset;
        System.arraycopy(source, captureOffset, ordered, 0, tail);
        System.arraycopy(source, 0, ordered, tail, captureOffset);
        return ordered;
    }

    private void publishSpectrum(int sampleRate) {
        float[] ordered = new float[fftWindow.length];
        int tail = fftWindow.length - fftWindowOffset;
        System.arraycopy(fftWindow, fftWindowOffset, ordered, 0, tail);
        System.arraycopy(fftWindow, 0, ordered, tail, fftWindowOffset);
        for (int i = 0; i < ordered.length; i++) {
            ordered[i] *= FFT_WINDOW[i];
        }
        float[] magnitudes = FFT.analyzeSample(ordered, fftWindow.length);
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] *= 2.0f;
        }
        bandValues = visualizer.processFFT(magnitudes, sampleRate);
    }

    private static float[] createFftWindow() {
        float[] window = new float[JSynFFT.FFT_SIZE];
        for (int i = 0; i < window.length; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (window.length - 1)));
        }
        return window;
    }

    private static float readSample(byte[] data, int offset, int bytes, boolean bigEndian) {
        int value = 0;
        if (bigEndian) {
            for (int i = 0; i < bytes; i++) {
                value = (value << 8) | (data[offset + i] & 0xff);
            }
        } else {
            for (int i = bytes - 1; i >= 0; i--) {
                value = (value << 8) | (data[offset + i] & 0xff);
            }
        }
        int shift = 32 - bytes * 8;
        return (value << shift >> shift) / (float) (1L << (bytes * 8 - 1));
    }

    public void computeVertexes(float[] input, float[] output) {
        MusicSpectrumWidget ms = TritiumMusicExtension.getInstance().musicSpectrum;

        int display = output.length / 2;
        int offset = Math.max(0, input.length - display);

        double spacing = (ms.getWidgetWidth() - 8) / (double) display;
        double height = (ms.stereo.getValue() ? (ms.getWidgetHeight() - 17) * 0.5 : (ms.getWidgetHeight() - 17)) - 4;
        double volumeScale = ms.absVol.getValue() ? (TritiumMusicExtension.getInstance().musicInfo.volume.getValue() * 2) : .5 + (TritiumMusicExtension.getInstance().musicInfo.volume.getValue() * 1.75);

        for (int i = 0; i < display; i++) {
            float v = input[offset + i];
            int outputIdx = i * 2;
            output[outputIdx] = (float) (spacing * i);
            output[outputIdx + 1] = (float) (height * v / volumeScale);
        }
    }

    public void computeOscilloscopeVertexes(float[] input, float[] output, float[] vertexes, OscilloscopeState state) {
        MusicSpectrumWidget ms = TritiumMusicExtension.getInstance().musicSpectrum;

        int n = input.length;
        int display = output.length;

        int stride = state.stride;
        int wd = state.wd;
        int nd = Math.min(state.nd, n / stride);
        int searchD = Math.max(1, nd - wd);

        float[] corrected = state.corrected;
        float[] ds = state.ds;
        float[] buf = state.corrBuffer;
        float[] kernel = state.kernel;
        float[] slope = state.slopeFinder;
        float[] window = state.bufferWindow;

        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += input[i];
        }
        float mean = (float) (sum / n);
        for (int i = 0; i < n; i++) {
            corrected[i] = input[i] - mean;
        }

        double energy = 0;
        for (int j = 0; j < nd; j++) {
            int base = j * stride;
            float s = 0;
            int cnt = 0;
            for (int k = 0; k < stride; k++) {
                int idx = base + k;
                if (idx < n) {
                    s += corrected[idx];
                    cnt++;
                }
            }
            float v = cnt > 0 ? s / cnt : 0f;
            ds[j] = v;
            energy += (double) v * v;
        }
        float dsRms = (float) Math.sqrt(energy / nd);

        int triggerFull;

        if (dsRms < 1.0e-6f) {
            triggerFull = 0;
        } else {
            for (int k = 0; k < wd; k++) {
                kernel[k] = slope[k] + OSC_BUFFER_STRENGTH * buf[k];
            }

            int bestOff = 0;
            double bestScore = -Double.MAX_VALUE;
            for (int off = 0; off <= searchD; off++) {
                double score = 0;
                for (int k = 0; k < wd; k++) {
                    score += ds[off + k] * kernel[k];
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestOff = off;
                }
            }

            triggerFull = bestOff * stride;

            double a2 = 0;
            for (int k = 0; k < wd; k++) {
                float v = ds[bestOff + k];
                a2 += (double) v * v;
            }
            float aRms = (float) Math.sqrt(a2 / wd);
            if (aRms > 1.0e-6f) {
                float ainv = 1f / aRms;
                for (int k = 0; k < wd; k++) {
                    float val = ds[bestOff + k] * ainv * window[k];
                    buf[k] += OSC_RESPONSIVENESS * (val - buf[k]);
                }

                double b2 = 0;
                for (int k = 0; k < wd; k++) {
                    b2 += (double) buf[k] * buf[k];
                }
                if (b2 > 1.0e-9) {
                    float bn = (float) (1.0 / Math.sqrt(b2 / wd));
                    for (int k = 0; k < wd; k++) {
                        buf[k] *= bn;
                    }
                }
            }
        }

        if (triggerFull > n - display) {
            triggerFull = n - display;
        }
        if (triggerFull < 0) {
            triggerFull = 0;
        }

        for (int j = 0; j < display; j++) {
            output[j] = corrected[triggerFull + j];
        }

        double spacing = (ms.getWidgetWidth() - 8) / (double) display;

        double height =
                (ms.stereo.getValue()
                        ? (ms.getWidgetHeight() - 17) * 0.5
                        : (ms.getWidgetHeight() - 17)) - 4;

        float volumeScale =
                (float) (ms.absVol.getValue()
                        ? (TritiumMusicExtension.getInstance().musicInfo.volume.getValue() * 2)
                        : (.5f + (TritiumMusicExtension.getInstance().musicInfo.volume.getValue() * 1.75f)));

        for (int j = 0; j < display; j++) {
            int vi = j * 2;
            vertexes[vi] = (float) (spacing * j);
            vertexes[vi + 1] = (float) (height * output[j] / volumeScale);
        }
    }

    public void play() {
        finished = false;
        failed = false;
        this.player.play();
        this.player.setVolume(volume);
    }

    public void setPlaybackTime(float millis) {
        this.player.seek((long) millis);
        this.player.setVolume(volume);
    }

    public void close() {
        this.player.close();
    }

    @Getter
    private boolean finished;

    @Getter
    private boolean failed;

    public void setAfterPlayed(Runnable runnable) {
        this.afterPlayed = runnable;
        this.player.setOnFinished(() -> {
            finished = true;
            runnable.run();
        });
        this.player.setOnFailed(() -> {
            failed = true;
            finished = true;
            runnable.run();
        });
    }

    public float getTotalTimeSeconds() {
        return this.player.durationMillis() / 1000f;
    }

    public float getCurrentTimeSeconds() {
        return (int) (getCurrentTimeMillis() / 1000);
    }

    public float getTotalTimeMillis() {
        return getTotalTimeSeconds() * 1000;
    }

    public float getCurrentTimeMillis() {
        return this.player.positionMillis();
    }

    public boolean isPausing() {
        return !this.player.isPlaying();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        this.player.setVolume(this.getVolume());
    }

    public void pause() {
        this.player.pause();
    }

    public void unpause() {
        this.play();
    }

    public boolean isPlaying() {
        return this.player.isPlaying();
    }
}
