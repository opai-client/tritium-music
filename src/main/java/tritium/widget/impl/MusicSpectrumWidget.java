package tritium.widget.impl;

import org.lwjgl.opengl.GL11;
import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.features.ExtensionWidget;
import today.opai.api.interfaces.EventHandler;
import today.opai.api.interfaces.modules.values.BooleanValue;
import today.opai.api.interfaces.modules.values.ColorValue;
import today.opai.api.interfaces.modules.values.ModeValue;
import today.opai.api.interfaces.modules.values.NumberValue;
import tritium.interfaces.SharedConstants;
import tritium.interfaces.SharedRenderingConstants;
import tritium.ncm.music.AudioPlayer;
import tritium.ncm.music.CloudMusic;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.rendersystem.RenderSystem;
import tritium.utils.Tuple;
import tritium.utils.WidgetWrapper;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Objects;
import static tritium.widget.impl.MusicSpectrumWidget.Style.*;

/**
 * @author IzumiiKonata
 * Date: 2025/3/8 16:17
 */
public class MusicSpectrumWidget extends ExtensionModule implements SharedConstants, SharedRenderingConstants, EventHandler {

    float[] renderSpectrum = new float[1];
    float[] renderSpectrumIndicator = new float[1];

    long[] indicatorTimeStamp = new long[1];

    public final ModeValue style = api.getValueManager().createModes("Style", "Rect", new String[]{"Rect", "Waveform", "Oscilloscope", "Line"});

    public enum Style {
        Rect,
        Waveform,
        Oscilloscope,
        Line
    }

    public final BooleanValue compatMode = api.getValueManager().createBoolean("Compact Mode", false);
    public final BooleanValue indicator = api.getValueManager().createBoolean("Indicator", true);
    public final ColorValue rectColor = api.getValueManager().createColor("Rect Color", new Color(125, 125, 125, 200));
    public final NumberValue multiplier = api.getValueManager().createDouble("Multiplier", 1.0, 0.1, 3.0, 0.1);
    public final NumberValue spectrumTilt = api.getValueManager().createDouble("Spectrum Tilt", 3.0, 0.0, 6.0, 0.5);
    public final NumberValue smoothing = api.getValueManager().createDouble("Smoothing", 0.55, 0.0, 0.95, 0.05);
    public final BooleanValue absVol = api.getValueManager().createBoolean("Absolute Volume", true);
    public final BooleanValue stereo = api.getValueManager().createBoolean("Waveform Stereo", false);
    public final NumberValue windowTime = api.getValueManager().createDouble("Window Time (ms)", 16.0, 4.0, 256.0, 0.1);
    private float lastNSamples = windowTime.getValue().floatValue();

    public ExtensionWidget widget;
    public WidgetWrapper.WidgetPosSizeInterface wpsInterface;
    public MusicSpectrumWidget() {
        super("Music Spectrum", "Shows spectrum.", EnumModuleCategory.VISUAL);

        indicator.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect"));
        rectColor.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect"));
        stereo.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Waveform") && !Objects.equals(style.getValue(), "Oscilloscope"));
        windowTime.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Waveform") && !Objects.equals(style.getValue(), "Oscilloscope"));
        multiplier.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect") && !Objects.equals(style.getValue(), "Line"));
        spectrumTilt.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect") && !Objects.equals(style.getValue(), "Line"));
        smoothing.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect") && !Objects.equals(style.getValue(), "Line"));
        rectColor.setAlphaAllowed(true);

        this.addValues(style, compatMode, indicator, rectColor, multiplier, spectrumTilt, smoothing, absVol, stereo, windowTime);

        Tuple<ExtensionWidget, WidgetWrapper.WidgetPosSizeInterface> wrapped = WidgetWrapper.createWrapper(this, this::onRender);
        this.widget = wrapped.getA();
        this.wpsInterface = wrapped.getB();
        this.setEventHandler(this);
    }

    public double getWidgetWidth() {
        return wpsInterface.getWidth();
    }

    public double getWidgetHeight() {
        return wpsInterface.getHeight();
    }

    public void onRender() {
        float currentWindowTime = windowTime.getValue().floatValue();
        if (CloudMusic.player != null && lastNSamples != currentWindowTime) {
            CloudMusic.player.setListeners();
            CloudMusic.player.spectrumDataLFilled = CloudMusic.player.spectrumDataRFilled = false;
            lastNSamples = currentWindowTime;
        }

        Style style = Style.valueOf(this.style.getValue());
        boolean compatMode = this.compatMode.getValue();



        if (CloudMusic.player != null) {

            boolean rect = style == Rect;
            boolean line = style == Line;
            boolean waveform = style == Waveform;
            boolean oscilloscope = style == Oscilloscope;

            if (compatMode || waveform || oscilloscope) {
                this.roundedRect(wpsInterface.getX(), wpsInterface.getY(), wpsInterface.getWidth(), wpsInterface.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            if (rect || line) {
                this.updateSpectrum();
            }

            api.getGLStateManager().pushMatrix();

            if (rect) {
                this.drawBars(compatMode);
            }

            if (waveform || oscilloscope) {
                boolean stereo = this.stereo.getValue();

                double pWidgetHeight = stereo ? wpsInterface.getHeight() * 0.5 : wpsInterface.getHeight();
                if (CloudMusic.player.spectrumDataLFilled && CloudMusic.player.lockL.tryLock()) {
                    api.getGLStateManager().color(1, 1, 1, 1);
                    this.drawWaveSub(pWidgetHeight, false, CloudMusic.player.waveVertexesBufferBackend, CloudMusic.player.waveVertexes.length / 2);
                    CloudMusic.player.lockL.unlock();
                }

                if (stereo && CloudMusic.player.spectrumDataRFilled && CloudMusic.player.lockR.tryLock()) {
                    double lineHeight = 0.5;
                    tritium.rendering.Rect.draw(wpsInterface.getX() + 4, (float) (wpsInterface.getY() + pWidgetHeight - lineHeight * 0.5), wpsInterface.getWidth() - 8, (float) lineHeight, hexColor(255, 255, 255, 160));

                    api.getGLStateManager().color(1, 1, 1, 1);
                    this.drawWaveSub(pWidgetHeight, true, CloudMusic.player.waveRightVertexesBufferBackend, CloudMusic.player.waveRightVertexes.length / 2);
                    CloudMusic.player.lockR.unlock();
                }

            }

            if (line) {
                this.drawLine(compatMode);
            }

            if (rect) {
                wpsInterface.setWidth(-1);
            }

            if (waveform || oscilloscope || compatMode){
                wpsInterface.setWidth(200);
                wpsInterface.setHeight(80);
            }

            api.getGLStateManager().popMatrix();
        }

    }

    public void drawWaveSub(double pWidgetHeight, boolean secondHalf, ByteBuffer bb, int vertCount) {
        if (bb == null) {
            return;
        }

        double startX = wpsInterface.getX() + 4;
        double startY = wpsInterface.getY() + pWidgetHeight * 0.5 + (secondHalf ? pWidgetHeight : 0);

        api.getGLStateManager().disableAlpha();
        api.getGLStateManager().enableBlend();
        api.getGLStateManager().disableTexture2D();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        api.getGLStateManager().pushMatrix();
        api.getGLStateManager().translate(startX, startY, 0);

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(2, GL11.GL_FLOAT, 0, bb);

        api.getGLStateManager().tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GL11.glLineWidth(1.4f);
        api.getGLStateManager().color(0.92f, 0.98f, 1.0f, 0.95f);
        GL11.glDrawArrays(GL11.GL_LINE_STRIP, 0, vertCount);

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        api.getGLStateManager().popMatrix();

        GL11.glLineWidth(1f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        api.getGLStateManager().enableTexture2D();
        api.getGLStateManager().enableAlpha();
    }


    private void updateSpectrum() {
        int n = AudioPlayer.bandValues.length;

        if (renderSpectrum.length != n) {
            renderSpectrum = new float[n];
            renderSpectrumIndicator = new float[n];
            indicatorTimeStamp = new long[n];
        }

        boolean playing = CloudMusic.player.isPlaying();
        float smooth = this.smoothing.getValue().floatValue();
        float attackFraction = 1.0f + (1.0f - smooth) * 1.4f;
        float decayFraction = 0.07f + (1.0f - smooth) * 1.6f;

        long now = System.currentTimeMillis();
        boolean indicator = this.indicator.getValue();

        for (int i = 0; i < n; i++) {
            float target = AudioPlayer.bandValues[i];

            if (!Float.isFinite(target) || !playing) {
                target = 0.0f;
            }

            float previous = renderSpectrum[i];
            float current = Interpolations.interpolate(previous, target, target > previous ? attackFraction : decayFraction);
            renderSpectrum[i] = current;

            if (indicator) {
                if (current >= renderSpectrumIndicator[i]) {
                    renderSpectrumIndicator[i] = current;
                    indicatorTimeStamp[i] = now;
                } else if (now - indicatorTimeStamp[i] > 450) {
                    float fallen = Interpolations.interpolate(renderSpectrumIndicator[i], 0.0f, 0.12f);
                    renderSpectrumIndicator[i] = Math.max(fallen, current);
                }
            }
        }
    }

    private void drawBars(boolean compact) {
        int n = renderSpectrum.length;
        if (n == 0) {
            return;
        }

        double pad = 4;
        double regionX, regionW, baseY, maxH;

        if (compact) {
            regionX = wpsInterface.getX() + pad;
            regionW = wpsInterface.getWidth() - pad * 2;
            baseY = wpsInterface.getY() + wpsInterface.getHeight() - pad;
            maxH = wpsInterface.getHeight() - pad * 2;
        } else {
            regionX = 0;
            regionW = RenderSystem.getWidth();
            baseY = RenderSystem.getHeight();
            maxH = RenderSystem.getHeight() * 0.33;
        }

        double mult = this.multiplier.getValue();
        double pitch = regionW / n;
        double barW = compact ? Math.max(1.0, pitch * 0.82) : pitch;

        api.getGLStateManager().enableBlend();
        api.getGLStateManager().disableTexture2D();
        api.getGLStateManager().tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        api.getGLStateManager().shadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            if (h <= 0) {
                continue;
            }

            double x0 = regionX + i * pitch + (pitch - barW) * 0.5;
            double x1 = x0 + barW;
            double top = baseY - h;

            int rgb = this.rectColor.getValue().getRGB();
            float a = (rgb >> 24 & 255) * RenderSystem.DIVIDE_BY_255;
            float r = (rgb >> 16 & 255) * RenderSystem.DIVIDE_BY_255;
            float g = (rgb >> 8 & 255) * RenderSystem.DIVIDE_BY_255;
            float b = (rgb & 255) * RenderSystem.DIVIDE_BY_255;

            float topAlpha = a * (1.0f - 0.8f * (float) (h / maxH));

            api.getGLStateManager().color(r, g, b, a);
            GL11.glVertex2d(x0, baseY);
            GL11.glVertex2d(x1, baseY);

            api.getGLStateManager().color(r, g, b, topAlpha);
            GL11.glVertex2d(x1, top);
            GL11.glVertex2d(x0, top);
        }
        GL11.glEnd();

        if (this.indicator.getValue()) {
            double capH = compact ? 1.0 : 1.5;

            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < n; i++) {
                double ph = Math.min(maxH, renderSpectrumIndicator[i] * maxH * mult);
                if (ph <= capH) {
                    continue;
                }

                double x0 = regionX + i * pitch + (pitch - barW) * 0.5;
                double x1 = x0 + barW;
                double capY = baseY - ph;

                int rgb = this.rectColor.getValue().getRGB();
                float a = (rgb >> 24 & 255) * RenderSystem.DIVIDE_BY_255;
                float r = (rgb >> 16 & 255) * RenderSystem.DIVIDE_BY_255;
                float g = (rgb >> 8 & 255) * RenderSystem.DIVIDE_BY_255;
                float b = (rgb & 255) * RenderSystem.DIVIDE_BY_255;

                api.getGLStateManager().color(r, g, b, Math.min(1.0f, a + 0.25f));
                GL11.glVertex2d(x0, capY - capH);
                GL11.glVertex2d(x1, capY - capH);
                GL11.glVertex2d(x1, capY);
                GL11.glVertex2d(x0, capY);
            }
            GL11.glEnd();
        }

        api.getGLStateManager().shadeModel(GL11.GL_FLAT);
        api.getGLStateManager().enableTexture2D();
    }

    private void drawLine(boolean compact) {
        int n = renderSpectrum.length;
        if (n < 2) {
            return;
        }

        double pad = 4;
        double regionX, regionW, baseY, maxH;

        if (compact) {
            regionX = wpsInterface.getX() + pad;
            regionW = wpsInterface.getWidth() - pad * 2;
            baseY = wpsInterface.getY() + wpsInterface.getHeight() - pad;
            maxH = wpsInterface.getHeight() - pad * 2;
        } else {
            regionX = 0;
            regionW = RenderSystem.getWidth();
            baseY = RenderSystem.getHeight();
            maxH = RenderSystem.getHeight() * 0.33;
        }

        double mult = this.multiplier.getValue();
        double pitch = regionW / (n - 1);

        api.getGLStateManager().enableBlend();
        api.getGLStateManager().disableTexture2D();
        api.getGLStateManager().tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        api.getGLStateManager().shadeModel(GL11.GL_SMOOTH);

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            double x = regionX + i * pitch;

            api.getGLStateManager().color(1f, 1f, 1f, 0.16f);
            GL11.glVertex2d(x, baseY);
            api.getGLStateManager().color(1f, 1f, 1f, 0.34f);
            GL11.glVertex2d(x, baseY - h);
        }
        GL11.glEnd();

        api.getGLStateManager().color(1f, 1f, 1f, 0.9f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(compact ? 1.0f : 1.5f);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i < n; i++) {
            double h = Math.min(maxH, renderSpectrum[i] * maxH * mult);
            double x = regionX + i * pitch;
            GL11.glVertex2d(x, baseY - h);
        }
        GL11.glEnd();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        api.getGLStateManager().shadeModel(GL11.GL_FLAT);
        api.getGLStateManager().enableTexture2D();
    }

}
