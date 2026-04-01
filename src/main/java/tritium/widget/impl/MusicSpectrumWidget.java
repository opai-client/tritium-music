package tritium.widget.impl;

import org.lwjgl.opengl.GL11;
import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.features.ExtensionWidget;
import today.opai.api.interfaces.modules.values.BooleanValue;
import today.opai.api.interfaces.modules.values.ColorValue;
import today.opai.api.interfaces.modules.values.ModeValue;
import today.opai.api.interfaces.modules.values.NumberValue;
import tritium.TritiumMusicExtension;
import tritium.interfaces.SharedConstants;
import tritium.interfaces.SharedRenderingConstants;
import tritium.ncm.music.AudioPlayer;
import tritium.ncm.music.CloudMusic;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.rendersystem.RenderSystem;
import tritium.utils.Tuple;
import tritium.utils.WidgetWrapper;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author IzumiiKonata
 * Date: 2025/3/8 16:17
 */
public class MusicSpectrumWidget extends ExtensionModule implements SharedConstants, SharedRenderingConstants {

    float[] renderSpectrum = new float[1];
    float[] renderSpectrumIndicator = new float[1];

    Map<Integer, Long> indicatorTimeStamp = new HashMap<>();

    public final ModeValue style = api.getValueManager().createModes("Style", "Rect", new String[] { "Rect", "Line" });

    public final BooleanValue compatMode = api.getValueManager().createBoolean("Compact Mode", false);
    public final BooleanValue indicator = api.getValueManager().createBoolean("Indicator", true);
    public final BooleanValue absVol = api.getValueManager().createBoolean("Absolute Volume", true);
    public final ColorValue rectColor = api.getValueManager().createColor("Rect Color", new Color(125, 125, 125, 200));

    public final NumberValue multiplier = api.getValueManager().createDouble("Multiplier", 1.0, 0.1, 3.0, 0.1);

    public ExtensionWidget widget;
    WidgetWrapper.WidgetPosSizeInterface wpsInterface;
    
    public MusicSpectrumWidget() {
        super("Music Spectrum", "Shows spectrum.", EnumModuleCategory.VISUAL);

        indicator.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect"));
        rectColor.setHiddenPredicate(() -> !Objects.equals(style.getValue(), "Rect"));
        
        this.addValues(style, compatMode, indicator, rectColor, multiplier);

        Tuple<ExtensionWidget, WidgetWrapper.WidgetPosSizeInterface> wrapped = WidgetWrapper.createWrapper(this, this::onRender);
        this.widget = wrapped.getA();
        this.wpsInterface = wrapped.getB();
    }

    public void onRender() {
        float offset = 170;

        AtomicReference<Double> spectrumWidth = new AtomicReference<>(RenderSystem.getWidth() / (double) renderSpectrum.length);

        double maximumSpectrum = 1;

        String style = this.style.getValue();

        boolean compatMode = this.compatMode.getValue();

        if (CloudMusic.player != null) {

            boolean rect = style.equals("Rect");
            boolean line = style.equals("Line");

            if (compatMode) {
                this.roundedRect(wpsInterface.getX(), wpsInterface.getY(), this.getWidth(), this.getHeight(), 6, 0, 0, 0, 0.4f);
            }

            if (rect || line) {

                int leng = (int) (AudioPlayer.bandValues.length * .5);
                if (renderSpectrum.length != leng) {
                    renderSpectrum = Arrays.copyOf(renderSpectrum, leng);
                    renderSpectrumIndicator = new float[leng];
                }

                for (int i = 0; i < leng; i++) {

                    float target = AudioPlayer.bandValues[i] * (compatMode ? 8 : 16);

                    if (!Float.isFinite(target)) {
                        target = 0;
                    }

                    if (!CloudMusic.player.player.isPlaying()) {
                        target = 0;
                    }

                    float factor = 1f;
                    renderSpectrum[i] = Interpolations.interpolate(renderSpectrum[i], target, (float) (2f - (this.absVol.getValue() ? 0 : .5f * (1 - TritiumMusicExtension.getInstance().musicInfo.volume.getValue()))) * factor);
                    maximumSpectrum = (Math.max(maximumSpectrum, target));
                }

            }

            api.getGLStateManager().pushMatrix();

            if (rect) {

                api.getGLStateManager().enableBlend();
                api.getGLStateManager().disableTexture2D();
                api.getGLStateManager().tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

//                int zLayerFixer = 100;
//                api.getGLStateManager().translate(0, 0, -zLayerFixer);


                GL11.glBegin(GL11.GL_TRIANGLES);
                int step = compatMode ? 8 : 3;
                spectrumWidth.set((compatMode ? this.getWidth() : RenderSystem.getWidth()) / ((double) renderSpectrum.length / step));
                this.drawRect(spectrumWidth.get(), renderSpectrum.length, step);
                GL11.glEnd();
//                api.getGLStateManager().translate(0, 0, zLayerFixer);
            }

            if (line) {
                api.getGLStateManager().enableBlend();
                api.getGLStateManager().tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

                api.getGLStateManager().color(1, 1, 1, 1);
                api.getGLStateManager().disableTexture2D();
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(compatMode ? .75f : 1.0f);

                GL11.glBegin(GL11.GL_LINE_STRIP);

                double shrink = 4;
                int step = compatMode ? 2 : 1;

                if (compatMode) {
                    GL11.glVertex2d(wpsInterface.getX() + 4, wpsInterface.getY() + this.getHeight() - shrink);
                    spectrumWidth.set((this.getWidth() - shrink * 2) / ((double) renderSpectrum.length / step));
                } else {
                    GL11.glVertex2d(0, RenderSystem.getHeight());
                }

                for (int i = 0; i < renderSpectrum.length; i += step) {
                    api.getGLStateManager().color(1, 1, 1, 1);
                    double height = -renderSpectrum[i] * this.multiplier.getValue() * 10;

                    if (compatMode)
                        height = Math.max(height, -this.getHeight() + shrink * 2);

                    GL11.glVertex2d((compatMode ? (wpsInterface.getX() + 4) : 0) + spectrumWidth.get() * (i + 1) / step + spectrumWidth.get() * 0.5, (compatMode ? (wpsInterface.getY() + this.getHeight() - shrink) : RenderSystem.getHeight()) + height);
                }

                if (compatMode) {
                    GL11.glVertex2d(wpsInterface.getX() + this.getWidth() - shrink, wpsInterface.getY() + this.getHeight() - shrink);
                } else {
                    GL11.glVertex2d(RenderSystem.getWidth(), RenderSystem.getHeight());
                }

                GL11.glEnd();
            }

            if (rect) {
                wpsInterface.setWidth(-1);
//                this.setHeight(offset);
            }

            if (compatMode){
                wpsInterface.setWidth(200);
                wpsInterface.setHeight(80);
            }

            api.getGLStateManager().popMatrix();
        }

    }

    private void drawRect(double spectrumWidth, int j, int step) {

        boolean compatMode = this.compatMode.getValue();

        double shrink = 4;
        spectrumWidth -= (compatMode ? shrink * 2 : 0) / ((double) j / step);

        for (int i = 0; i < j; i += step) {

            double height = -renderSpectrum[i] * this.multiplier.getValue() * 10;

            if (compatMode)
                height = Math.max(height, -this.getHeight() + shrink * 2);

            if (this.indicator.getValue()) {

                if ((float) height < renderSpectrumIndicator[i]) {
                    renderSpectrumIndicator[i] = (float) height;

                    if (indicatorTimeStamp.containsKey(i)) {
                        indicatorTimeStamp.replace(i, System.currentTimeMillis());
                    } else {
                        indicatorTimeStamp.put(i, System.currentTimeMillis());
                    }

                } else {
                    long timeStamp = indicatorTimeStamp.computeIfAbsent(i, k -> System.currentTimeMillis());

                    if (System.currentTimeMillis() - timeStamp > 200) {
                        renderSpectrumIndicator[i] = Interpolations.interpolateLinear(renderSpectrumIndicator[i], (float) 6, 8);
                    }

                }
            }

            double posX = (compatMode ? wpsInterface.getX() + shrink : 0) + spectrumWidth * i / step;
            double y = compatMode ? (wpsInterface.getY() + this.getHeight() - shrink) : RenderSystem.getHeight();

            double left = posX;
            double top = y;
            double right = posX + spectrumWidth;
            double bottom = y + height;

            if (left > right) {
                double i1 = left;
                left = right;
                right = i1;
            }

            if (top > bottom) {
                double j1 = top;
                top = bottom;
                bottom = j1;
            }

            int rgb = this.rectColor.getValue().getRGB();

            float a = (rgb >> 24 & 255) * RenderSystem.DIVIDE_BY_255;
            float r = (rgb >> 16 & 255) * RenderSystem.DIVIDE_BY_255;
            float g = (rgb >> 8 & 255) * RenderSystem.DIVIDE_BY_255;
            float b = (rgb & 255) * RenderSystem.DIVIDE_BY_255;

            api.getGLStateManager().color(r, g, b, a);

            GL11.glVertex2d(right, bottom);
            GL11.glVertex2d(left, top);
            GL11.glVertex2d(left, bottom);

            GL11.glVertex2d(right, top);
            GL11.glVertex2d(left, top);
            GL11.glVertex2d(right, bottom);

            if (this.indicator.getValue()) {

//                posX = spectrumWidth * i / step;
                height = -1;

                left = posX;
                top = y + renderSpectrumIndicator[i] - 1;
                right = posX + spectrumWidth;
                bottom = y + renderSpectrumIndicator[i] - 1 + height;

                if (left > right) {
                    double i1 = left;
                    left = right;
                    right = i1;
                }

                if (top > bottom) {
                    double j1 = top;
                    top = bottom;
                    bottom = j1;
                }

                GL11.glVertex2d(right, bottom);
                GL11.glVertex2d(left, top);
                GL11.glVertex2d(left, bottom);

                GL11.glVertex2d(right, top);
                GL11.glVertex2d(left, top);
                GL11.glVertex2d(right, bottom);
            }

        }

    }

}
