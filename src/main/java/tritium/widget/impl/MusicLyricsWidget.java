package tritium.widget.impl;

import today.opai.api.enums.EnumChatColor;
import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.features.ExtensionWidget;
import today.opai.api.interfaces.EventHandler;
import today.opai.api.interfaces.modules.values.BooleanValue;
import today.opai.api.interfaces.modules.values.ColorValue;
import today.opai.api.interfaces.modules.values.ModeValue;
import today.opai.api.interfaces.modules.values.NumberValue;
import tritium.TritiumMusicExtension;
import tritium.interfaces.SharedConstants;
import tritium.interfaces.SharedRenderingConstants;
import tritium.management.FontManager;
import tritium.ncm.music.AudioPlayer;
import tritium.ncm.music.CloudMusic;
import tritium.reflection.Reflection;
import tritium.rendering.RGBA;
import tritium.rendering.Rect;
import tritium.rendering.StencilClipManager;
import tritium.rendering.animation.Easing;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.font.CFontRenderer;
import tritium.screens.ncm.LyricLine;
import tritium.settings.ClientSettings;
import tritium.utils.Tuple;
import tritium.utils.I18n;
import tritium.utils.WidgetWrapper;
import tritium.utils.math.Mth;

import java.awt.Color;
import java.util.Objects;
/**
 * @author IzumiiKonata
 * Date: 2025/2/14 20:34
 */
public class MusicLyricsWidget extends ExtensionModule implements SharedConstants, SharedRenderingConstants, EventHandler {

    static double scrollOffset = 0;

    private double fontH, lyricH;

    public ModeValue scrollEffects = api.getValueManager().createModes("Scroll Effects", "Scroll", new String[]{"Scroll", "FadeIn", "SlideIn", "Aurora"});
    public ModeValue alignMode = api.getValueManager().createModes("Align Mode", "Center", new String[]{"Left", "Center", "Right", "Karaoke"});

    public enum ScrollEffects {
        Scroll,
        FadeIn,
        SlideIn,
        Aurora
    }

    public enum AlignMode {
        Left,
        Center,
        Right,
        Karaoke
    }

    public NumberValue width = api.getValueManager().createDouble("Width", 450, 225, 900, 5);
    public NumberValue height = api.getValueManager().createDouble("Height", 120, 60, 480, 5);
    public NumberValue lyricHeight = api.getValueManager().createDouble("Lyric Height", 20.0, 14.0, 50.0, 0.5);
    public BooleanValue shadow = api.getValueManager().createBoolean("Shadow", false);
    public BooleanValue singleLine = api.getValueManager().createBoolean("Single Line Mode", false);
    public BooleanValue showTranslation = api.getValueManager().createBoolean("Show Translation", true);
    public BooleanValue graceScroll = api.getValueManager().createBoolean("Elegant Scrolling", true);
    public BooleanValue showRoman = api.getValueManager().createBoolean("Show Romanization in Japanese songs", false);
    public BooleanValue dynIsland = api.getValueManager().createBoolean("Dynamic Island Lyrics", false);
    public ColorValue glowColor = api.getValueManager().createColor("Glow Color", new Color(140, 215, 255, 255));
    public BooleanValue auroraBloom = api.getValueManager().createBoolean("Aurora Glow", true);
    public BooleanValue auroraSpark = api.getValueManager().createBoolean("Aurora Spark", true);
    public BooleanValue audioReactive = api.getValueManager().createBoolean("Audio Reactive", true);
    public NumberValue auroraUnsungOpacity = api.getValueManager().createDouble("Unsung Opacity", 0.35, 0.0, 1.0, 0.05);

    private double auroraEnergy = 0;
    private boolean karaokeRightAligned;

    public ExtensionWidget widget;
    public WidgetWrapper.WidgetPosSizeInterface wpsInterface;
    public MusicLyricsWidget() {
        super("Music Lyrics", "Show lyrics.", EnumModuleCategory.VISUAL);

        graceScroll.setHiddenPredicate(() -> singleLine.getValue());
        showRoman.setHiddenPredicate(() -> !showTranslation.getValue());
        dynIsland.setHiddenPredicate(() -> !Reflection.DYNAMIC_ISLAND_SUPPORTED);
        glowColor.setHiddenPredicate(() -> !Objects.equals(scrollEffects.getValue(), "Aurora"));
        auroraBloom.setHiddenPredicate(() -> !Objects.equals(scrollEffects.getValue(), "Aurora"));
        auroraSpark.setHiddenPredicate(() -> !Objects.equals(scrollEffects.getValue(), "Aurora"));
        audioReactive.setHiddenPredicate(() -> !Objects.equals(scrollEffects.getValue(), "Aurora"));
        auroraUnsungOpacity.setHiddenPredicate(() -> !Objects.equals(scrollEffects.getValue(), "Aurora"));
        glowColor.setAlphaAllowed(true);

        this.addValues(scrollEffects, alignMode, width, height, lyricHeight, shadow, singleLine, showTranslation, graceScroll, showRoman, dynIsland, glowColor, auroraBloom, auroraSpark, audioReactive, auroraUnsungOpacity);

        Tuple<ExtensionWidget, WidgetWrapper.WidgetPosSizeInterface> wrapped = WidgetWrapper.createWrapper(this, this::onRender);
        this.widget = wrapped.getA();
        this.wpsInterface = wrapped.getB();
        this.setEventHandler(this);
    }
    public static void resetProgress(float progress) {
        if (CloudMusic.lyrics.isEmpty()) return;

        try {
            CloudMusic.setLyricsProgress(progress);
            scrollOffset = (CloudMusic.lyrics.indexOf(CloudMusic.currentLyric)) * getLyricHeight();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getLyricHeight() {
        double baseHeight = getFontRenderer().getHeight();
        double adjustment = CloudMusic.hasSecondaryLyrics() ? 0 : -getSmallFontRenderer().getHeight() - 4;
        return baseHeight + adjustment + TritiumMusicExtension.getInstance().musicLyrics.lyricHeight.getValue();
    }

    public static boolean hasSecondaryLyrics() {
        return CloudMusic.hasSecondaryLyrics();
    }

    public static String getSecondaryLyrics(LyricLine bean) {
        return CloudMusic.getSecondaryLyrics(bean);
    }

    public void onRender() {

        if (!shouldRender()) {
            System.setProperty("ncm.dynIslandLyrics", "");
            return;
        }

        wpsInterface.setWidth(this.width.getValue().floatValue());
        wpsInterface.setHeight(this.height.getValue().floatValue());

        this.fontH = getFontRenderer().getHeight();
        this.lyricH = getLyricHeight();

        float songProgress = CloudMusic.player.getCurrentTimeMillis();

        boolean shouldNotDisplayOtherLyrics = this.singleLine.getValue();

        handleSingleLineMode(shouldNotDisplayOtherLyrics);

        updateScrollOffset(shouldNotDisplayOtherLyrics);

        if (Reflection.DYNAMIC_ISLAND_SUPPORTED && dynIsland.getValue()) {
            renderDynamicIsland(songProgress);
        } else {
            System.setProperty("ncm.dynIslandLyrics", "");
            api.getGLStateManager().pushMatrix();

            StencilClipManager.beginClip(() -> Rect.draw(wpsInterface.getX() - 2, wpsInterface.getY(), wpsInterface.getWidth() + 4, wpsInterface.getHeight(), -1));

            renderAllLyrics(shouldNotDisplayOtherLyrics, songProgress);

            api.getGLStateManager().popMatrix();
            StencilClipManager.endClip();
        }

        if (ClientSettings.DEBUG_MODE) {
            LyricLine currentLine = CloudMusic.currentLyric;
            if (currentLine != null && !CloudMusic.haveNoWords) {
                WordInfo wordInfo = calculateCurrentWordInfo(currentLine, songProgress);

                LyricLine.Word current = currentLine.words.get(wordInfo.currentIndex);
                FontManager.pf28bold.drawStringWithShadow("Current word: " + current.word, 100, 100, -1);
                double value = (songProgress - current.timestamp) / (double) (current.duration);
                FontManager.pf28bold.drawStringWithShadow("Perc: " + value, 100, 120, -1);
                FontManager.pf28bold.drawStringWithShadow("Dur: " + current.duration, 100, 140, -1);
                FontManager.pf28bold.drawStringWithShadow("Pos: " + (songProgress - current.timestamp), 100, 160, -1);
            }
        }
    }

    private void renderDynamicIsland(float songProgress) {
        LyricLine currentLine = CloudMusic.currentLyric;
        if (currentLine == null) {
            System.setProperty("ncm.dynIslandLyrics", "");
            return;
        }

        if (currentLine.isBreakLine) {
            int index = CloudMusic.lyrics.indexOf(currentLine);
            if (index > 0) {
                currentLine = CloudMusic.lyrics.get(index - 1);
            } else if (index + 1 < CloudMusic.lyrics.size()) {
                currentLine = CloudMusic.lyrics.get(index + 1);
            }
        }

        if (CloudMusic.haveNoWords) {
            System.setProperty("ncm.dynIslandLyrics", EnumChatColor.WHITE + currentLine.getLyric());
            return;
        }

        WordInfo wordInfo = calculateCurrentWordInfo(currentLine, songProgress);
        StringBuilder left = new StringBuilder(EnumChatColor.WHITE.toString());
        StringBuilder right = new StringBuilder(EnumChatColor.GRAY.toString());
        for (int i = 0; i < wordInfo.currentIndex; i++) {
            left.append(currentLine.words.get(i).word);
        }

        LyricLine.Word current = currentLine.words.get(wordInfo.currentIndex);
        double progress = Mth.limit((songProgress - current.timestamp) / current.duration, 0, 1);
        int split = progress > 0 ? Math.min(current.word.length(), Math.max(1, (int) (current.word.length() * progress))) : 0;
        left.append(current.word, 0, split);
        right.append(current.word.substring(split));

        for (int i = wordInfo.currentIndex + 1; i < currentLine.words.size(); i++) {
            right.append(currentLine.words.get(i).word);
        }
        System.setProperty("ncm.dynIslandLyrics", left.append(right).toString());
    }
    private boolean shouldRender() {
        return CloudMusic.player != null && !CloudMusic.player.isFinished() && !CloudMusic.lyrics.isEmpty();
    }

    private void handleSingleLineMode(boolean shouldNotDisplayOtherLyrics) {
        if (shouldNotDisplayOtherLyrics && CloudMusic.currentLyric == null) {
            if (!CloudMusic.lyrics.isEmpty()) {
                CloudMusic.currentLyric = CloudMusic.lyrics.getFirst();
            }
        }
    }

    private void updateScrollOffset(boolean shouldNotDisplayOtherLyrics) {
        int indexOf = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);

        if (!shouldNotDisplayOtherLyrics) {
            if (CloudMusic.currentLyric == null) {
                scrollOffset = 0;
            } else {
                scrollOffset = Interpolations.interpolate(scrollOffset, indexOf * lyricH, 0.2f);
            }
        }
    }

    private void renderAllLyrics(boolean shouldNotDisplayOtherLyrics, float songProgress) {
        if (AlignMode.valueOf(this.alignMode.getValue()) == AlignMode.Karaoke) {
            renderKaraokeLyrics(shouldNotDisplayOtherLyrics, songProgress);
            return;
        }

        double offsetY = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - fontH / 2.0 - scrollOffset;
        int indexOf = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);

        AlignMode alignMode = AlignMode.valueOf(this.alignMode.getValue());
        double pivotX = alignPivotX(alignMode);

        synchronized (CloudMusic.lyrics) {
            for (int i = 0; i < CloudMusic.lyrics.size(); i++) {
                LyricLine line = CloudMusic.lyrics.get(i);

                if (shouldNotDisplayOtherLyrics) {
                    if (i < indexOf) continue;
                    if (i > indexOf) break;
                }

                LyricRenderInfo renderInfo = calculateLyricPosition(
                        line, i, indexOf, offsetY, shouldNotDisplayOtherLyrics
                );

                if (renderInfo.shouldSkip) {
                    offsetY += lyricH;
                    continue;
                }

                if (renderInfo.shouldBreak) {
                    break;
                }

                updateLyricAnimation(line, i == indexOf);

                double focus = Math.max(0f, line.lineAlpha - 0.25f) / 0.75;
                double scale = 1.0 + focus * 0.05;

                api.getGLStateManager().pushMatrix();
                scaleAtPos(pivotX, renderInfo.yPosition + fontH * 0.5, scale);

                if (Objects.equals(this.scrollEffects.getValue(), "Aurora") && !line.words.isEmpty()) {
                    updateAuroraLinger(line, renderInfo, line == CloudMusic.currentLyric);
                }

                renderLyricText(line, renderInfo, i, indexOf);

                if (line == CloudMusic.currentLyric && !line.words.isEmpty()) {
                    handleScrollEffects(line, renderInfo, songProgress);
                }

                api.getGLStateManager().popMatrix();

                offsetY += lyricH;
            }
        }
    }

    private double alignPivotX(AlignMode alignMode) {
        return switch (alignMode) {
            case Left, Karaoke -> wpsInterface.getX();
            case Center -> wpsInterface.getX() + wpsInterface.getWidth() / 2.0;
            case Right -> wpsInterface.getX() + wpsInterface.getWidth();
        };
    }

    private void renderKaraokeLyrics(boolean singleLineMode, float songProgress) {
        int indexOf = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);
        LyricLine current;
        LyricLine preview;
        synchronized (CloudMusic.lyrics) {
            current = indexOf >= 0 ? CloudMusic.lyrics.get(indexOf) : null;
            if (current != null) {
                preview = indexOf + 1 < CloudMusic.lyrics.size() ? CloudMusic.lyrics.get(indexOf + 1) : null;
            } else {
                preview = CloudMusic.lyrics.isEmpty() ? null : CloudMusic.lyrics.getFirst();
            }
        }

        boolean currentOnLeft = indexOf >= 0 && indexOf % 2 == 0;
        boolean hasSecondary = hasSecondaryLyrics();
        double margin = karaokeMargin(hasSecondary);
        double smallFontHeight = getSmallFontRenderer().getHeight();

        if (current != null) {
            updateLyricAnimation(current, true);
            karaokeRightAligned = !currentOnLeft;
            LyricRenderInfo info = new LyricRenderInfo();
            info.yPosition = karaokeY(currentOnLeft, hasSecondary, smallFontHeight, margin);
            info.fade = computeEdgeFade(info.yPosition);
            renderKaraokeCurrentLine(current, info, songProgress, currentOnLeft, hasSecondary ? getSecondaryLyrics(current) : "");
        }

        if (preview != null && !singleLineMode) {
            updateLyricAnimation(preview, false);
            preview.auroraGlow = Interpolations.interpolate(preview.auroraGlow, 0f, 0.06f);
            boolean previewOnLeft = !currentOnLeft;
            LyricRenderInfo info = new LyricRenderInfo();
            info.yPosition = karaokeY(previewOnLeft, hasSecondary, smallFontHeight, margin);
            info.fade = computeEdgeFade(info.yPosition);
            renderKaraokeLine(preview, info, previewOnLeft, false, hasSecondary ? getSecondaryLyrics(preview) : "");
        }
    }

    private void renderKaraokeCurrentLine(LyricLine line, LyricRenderInfo info, float songProgress, boolean onLeft, String secondaryLyric) {
        double focus = Math.max(0f, line.lineAlpha - 0.25f) / 0.75;
        double pivotX = onLeft ? wpsInterface.getX() : wpsInterface.getX() + wpsInterface.getWidth();
        api.getGLStateManager().pushMatrix();
        scaleAtPos(pivotX, info.yPosition + fontH * 0.5, 1.0 + focus * 0.05);
        updateAuroraLinger(line, info, true);
        renderKaraokeLine(line, info, onLeft, true, secondaryLyric);
        if (!line.words.isEmpty()) handleScrollEffects(line, info, songProgress);
        api.getGLStateManager().popMatrix();
    }

    private void renderKaraokeLine(LyricLine line, LyricRenderInfo info, boolean onLeft, boolean isCurrent, String secondaryLyric) {
        boolean hasWords = !line.words.isEmpty();
        ScrollEffects effect = ScrollEffects.valueOf(this.scrollEffects.getValue());
        boolean selfRendered = isCurrent && hasWords && (effect == ScrollEffects.SlideIn || effect == ScrollEffects.Aurora);
        int primaryAlpha = isCurrent && hasWords ? 80 : (int) (line.lineAlpha * 255);
        int secondaryAlpha = isCurrent ? (int) (line.lineAlpha * 255) : 100;
        int primaryColor = withFade(RGBA.color(255, 255, 255, primaryAlpha), info.fade);
        int secondaryColor = withFade(RGBA.color(255, 255, 255, secondaryAlpha), info.fade);
        double secondaryY = info.yPosition - getSmallFontRenderer().getHeight() - 2;

        if (!selfRendered) {
            double x = onLeft ? wpsInterface.getX() : wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(line.getLyric());
            bigFrString(line.getLyric(), x, info.yPosition, primaryColor);
        }
        if (!secondaryLyric.isEmpty()) {
            double x = onLeft ? wpsInterface.getX() : wpsInterface.getX() + wpsInterface.getWidth() - getSmallFontRenderer().getStringWidthD(secondaryLyric);
            smallFrString(secondaryLyric, x, secondaryY, secondaryColor);
        }
    }

    private double karaokeY(boolean onLeft, boolean hasSecondary, double smallFontHeight, double margin) {
        return onLeft
                ? wpsInterface.getY() + margin + (hasSecondary ? smallFontHeight + 2 : 0)
                : wpsInterface.getY() + wpsInterface.getHeight() - margin - fontH;
    }

    private double karaokeMargin(boolean hasSecondary) {
        double blockHeight = fontH + (hasSecondary ? getSmallFontRenderer().getHeight() + 2 : 0);
        return Math.min(16, Math.max(8, (wpsInterface.getHeight() - blockHeight * 2) * 0.5));
    }

    private double computeEdgeFade(double yPosition) {
        double height = wpsInterface.getHeight();
        if (height <= 0) return 1.0;

        double band = Math.min(height * 0.5, lyricH * 1.4);
        if (band <= 0) return 1.0;

        double cy = yPosition + fontH * 0.5;
        double distance = Math.min(cy - wpsInterface.getY(), wpsInterface.getY() + height - cy);

        return Easing.EASE_OUT_CUBIC.getFunction().apply(Mth.limit(distance / band, 0, 1));
    }

    private static int withFade(int color, double fade) {
        if (fade >= 1.0) return color;
        int alpha = (int) (((color >>> 24) & 0xFF) * Mth.limit(fade, 0, 1));
        return RGBA.color(color & 0xFFFFFF, alpha);
    }

    private void renderEditingPlaceholder() {
        wpsInterface.setWidth(this.width.getValue().floatValue());
        wpsInterface.setHeight(this.height.getValue().floatValue());
        this.fontH = getFontRenderer().getHeight();
        this.lyricH = getLyricHeight();

        AlignMode alignMode = AlignMode.valueOf(this.alignMode.getValue());
        double pivotX = alignPivotX(alignMode);
        double centerY = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - fontH / 2.0;
        String[] samples = {"Tritium Music", I18n.get("tritium-music.ui.editor.lyric.current"), "Now Playing Preview"};

        api.getGLStateManager().pushMatrix();
        StencilClipManager.beginClip(() -> Rect.draw(wpsInterface.getX() - 2, wpsInterface.getY(), wpsInterface.getWidth() + 4, wpsInterface.getHeight(), -1));

        for (int i = -1; i <= 1; i++) {
            double y = centerY + i * lyricH;
            double fade = computeEdgeFade(y);
            int alpha = (int) ((i == 0 ? 1.0 : 0.25) * 255 * fade);
            double scale = i == 0 ? 1.05 : 1.0;

            api.getGLStateManager().pushMatrix();
            scaleAtPos(pivotX, y + fontH * 0.5, scale);
            renderAlignedText(samples[i + 1], y, RGBA.color(255, 255, 255, alpha), alignMode);
            api.getGLStateManager().popMatrix();
        }

        StencilClipManager.endClip();
        api.getGLStateManager().popMatrix();
    }

    private LyricRenderInfo calculateLyricPosition(LyricLine line, int index, int currentIndex,
                                                   double offsetY, boolean singleLineMode) {
        LyricRenderInfo info = new LyricRenderInfo();

        if (!singleLineMode) {
            double dest = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - fontH / 2.0 +
                    index * lyricH - (currentIndex * lyricH);

            if (line.offsetY == Double.MIN_VALUE || Math.abs(line.offsetY - dest) > 100) {
                line.offsetY = dest;
            }

            if (line.offsetY + lyricH < wpsInterface.getY()) {
                info.shouldSkip = true;
                line.offsetY = dest;
                return info;
            }

            if (offsetY > wpsInterface.getY() + wpsInterface.getHeight()) {
                info.shouldBreak = true;
                return info;
            }

            applyGraceScroll(line, index, currentIndex, dest);

            info.yPosition = this.graceScroll.getValue() ? line.offsetY : offsetY;
        } else {
            info.yPosition = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - fontH / 2.0;
            line.offsetY = info.yPosition;
        }

        info.fade = computeEdgeFade(info.yPosition);
        return info;
    }

    private void applyGraceScroll(LyricLine line, int index, int currentIndex, double dest) {
        float speed = 0.15f;
        LyricLine prevLrc = null;

        try {
            if (index > 0) {
                prevLrc = CloudMusic.lyrics.get(index - 1);
            }
        } catch (Exception ignored) {}

        if (prevLrc != null) {
            double prevDest = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - fontH / 2.0 +
                    (index - 1) * lyricH - (currentIndex * lyricH);
            double v = prevLrc.offsetY - prevDest;

            if (v < lyricH * 0.55f) {
                line.offsetY = Interpolations.interpolate(line.offsetY, dest, speed);
            }
        } else {
            line.offsetY = Interpolations.interpolate(line.offsetY, dest, speed);
        }
    }

    private void updateLyricAnimation(LyricLine line, boolean isCurrent) {
        line.lineAlpha = Interpolations.interpolate(
                line.lineAlpha,
                isCurrent ? 1f : .25f,
                0.1f
        );
    }

    private void renderLyricText(LyricLine line, LyricRenderInfo renderInfo,
                                 int index, int currentIndex) {
        boolean hasWords = !line.words.isEmpty();
        ScrollEffects effect = ScrollEffects.valueOf(this.scrollEffects.getValue());
        boolean isCurrent = index == currentIndex;
        boolean slideInSelf = hasWords && effect == ScrollEffects.SlideIn && isCurrent && AlignMode.valueOf(this.alignMode.getValue()) != AlignMode.Left;
        boolean auroraSelf = hasWords && effect == ScrollEffects.Aurora && isCurrent;
        boolean shouldRender = !(slideInSelf || auroraSelf);

        boolean isActive = index <= currentIndex;
        int primaryColor = withFade(RGBA.color(255, 255, 255, calculateAlpha(line, index, currentIndex, hasWords)), renderInfo.fade);
        int secondaryColor = withFade(RGBA.color(255, 255, 255, isActive ? (int) (line.lineAlpha * 255) : 100), renderInfo.fade);

        String secondaryLyric = hasSecondaryLyrics() ? getSecondaryLyrics(line) : "";
        boolean secondaryLyricEmpty = secondaryLyric.isEmpty();

        renderByAlignment(line, renderInfo, secondaryLyric, secondaryLyricEmpty,
                shouldRender, primaryColor, secondaryColor);
    }

    private int calculateAlpha(LyricLine line, int index, int currentIndex, boolean hasWords) {
        if (hasWords) {
            return index != currentIndex ? (int) (line.lineAlpha * 255) : 80;
        } else {
            return (int) (line.lineAlpha * 255);
        }
    }

    private void renderByAlignment(LyricLine line, LyricRenderInfo renderInfo,
                                   String secondaryLyric, boolean secondaryLyricEmpty,
                                   boolean shouldRender, int hexColor, int rgb) {
        AlignMode alignMode = AlignMode.valueOf(this.alignMode.getValue());
        double y = renderInfo.yPosition;
        double secondaryY = y + fontH + 2;

        switch (alignMode) {
            case Left:
                if (shouldRender) {
                    bigFrString(line.getLyric(), wpsInterface.getX(), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric, wpsInterface.getX(), secondaryY, rgb);
                }
                break;
            case Center:
                double centerX = wpsInterface.getX() + wpsInterface.getWidth() / 2.0;
                if (shouldRender) {
                    bigFrStringCentered(line.getLyric(), centerX, y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrStringCentered(secondaryLyric, centerX, secondaryY, rgb);
                }
                break;
            case Right:
                if (shouldRender) {
                    bigFrString(line.getLyric(),
                            wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(line.getLyric()), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric,
                            wpsInterface.getX() + wpsInterface.getWidth() - getSmallFontRenderer().getStringWidthD(secondaryLyric),
                            secondaryY, rgb);
                }
                break;
            case Karaoke:
                break;
        }
    }

    private void handleScrollEffects(LyricLine line, LyricRenderInfo renderInfo, float songProgress) {
        WordInfo wordInfo = calculateCurrentWordInfo(line, songProgress);

        updateScrollWidth(line, wordInfo, songProgress);

        renderScrollEffect(line, renderInfo, wordInfo, songProgress);
    }

    private WordInfo calculateCurrentWordInfo(LyricLine line, float songProgress) {
        WordInfo info = new WordInfo();
        for (int k = 0; k < line.words.size(); k++) {
            LyricLine.Word word = line.words.get(k);

            if (word.timestamp > songProgress) {
                info.currentIndex = Math.max(0, k - 1);
                break;
            } else if (k == line.words.size() - 1) {
                info.currentIndex = k;
            }
        }
        for (int m = 0; m < info.currentIndex; m++) {
            info.textBefore.append(line.words.get(m).word);
        }

        return info;
    }

    private void updateScrollWidth(LyricLine line, WordInfo wordInfo, float songProgress) {
        LyricLine.Word current = line.words.get(wordInfo.currentIndex);

        double value = (songProgress - current.timestamp) / (double) (current.duration);

        double progress = Mth.limit(value, 0, 1);

        double offsetX = progress * getFontRenderer().getStringWidthD(current.word);

        line.scrollWidth = getFontRenderer().getStringWidthD(wordInfo.textBefore.toString()) + offsetX;
    }

    private void renderScrollEffect(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        switch (ScrollEffects.valueOf(this.scrollEffects.getValue())) {
            case Scroll -> renderScrollMode(line, renderInfo);
            case FadeIn -> renderFadeInMode(line, renderInfo, wordInfo, songProgress);
            case SlideIn -> renderSlideInMode(line, renderInfo, wordInfo, songProgress);
            case Aurora -> renderAuroraMode(line, renderInfo);
        }
    }

    private void renderAuroraMode(LyricLine line, LyricRenderInfo renderInfo) {
        CFontRenderer fr = getFontRenderer();
        String text = line.getLyric();
        if (text.isEmpty()) return;

        double leftX = calculateAlignmentX(text, AlignMode.valueOf(this.alignMode.getValue()));
        double baseY = renderInfo.yPosition;
        double fade = renderInfo.fade;

        double lineWidth = fr.getStringWidthD(text);
        double sungTotal = computeSungTotal(fr, line);
        double sweep = sungTotal > 0 ? Mth.limit(line.scrollWidth / sungTotal, 0.0, 1.0) : 0.0;
        double headX = leftX + sweep * lineWidth;

        double rawEnergy = this.audioReactive.getValue() ? lowFrequencyEnergy() : 0.0;
        auroraEnergy = Interpolations.interpolate(auroraEnergy, rawEnergy, 0.25);
        double beat = auroraEnergy;

        double liftAmount = fontH * (0.10 + beat * 0.05);
        double waveSigma = Math.max(10.0, fontH * 1.1);
        double maxCharScale = 0.11 + beat * 0.05;

        if (this.auroraBloom.getValue() && true) {
            renderAuroraGlow(fr, text, leftX, baseY, headX, fade, beat, line.auroraGlow);
        }

        double unsung = this.auroraUnsungOpacity.getValue();

        char[] chars = text.toCharArray();
        double x = leftX;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            double cw = fr.getCharWidth(c, next);

            double reveal = Mth.limit((headX - x) / Math.max(1.0E-3, cw), 0.0, 1.0);
            double charAlpha = (unsung + (1.0 - unsung) * reveal) * fade;

            if (charAlpha > 0.003) {
                double cx = x + cw * 0.5;
                double d = headX - cx;
                double wave = Math.exp(-(d * d) / (2.0 * waveSigma * waveSigma));
                double lift = wave * liftAmount;
                double charScale = 1.0 + wave * maxCharScale;

                int accent = RGBA.opaque(this.glowColor.getValue().getRGB());
                int tinted = RGBA.srgbLerp((float) (wave * 0.55), 0xFFFFFFFF, accent);
                int color = withFade(tinted, charAlpha);

                api.getGLStateManager().pushMatrix();
                scaleAtPos(cx, baseY + fontH * 0.5 - lift, charScale);
                fr.drawString(String.valueOf(c), x, baseY - lift, color);
                api.getGLStateManager().popMatrix();
            }

            x += cw;
        }

        if (this.auroraSpark.getValue() && sweep > 0.001 && sweep < 0.999) {
            renderAuroraSpark(headX, baseY, fade, beat);
        }
    }

    private double computeSungTotal(CFontRenderer fr, LyricLine line) {
        int n = line.words.size();
        if (n == 0) return fr.getStringWidthD(line.getLyric());

        StringBuilder before = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            before.append(line.words.get(i).word);
        }

        return fr.getStringWidthD(before.toString()) + fr.getStringWidthD(line.words.get(n - 1).word);
    }

    private void renderAuroraGlow(CFontRenderer fr, String text, double leftX, double baseY, double headX, double fade, double beat, double glowAlpha) {
        double intensity = Mth.limit(0.45 + beat * 0.5, 0.0, 1.0) * fade * glowAlpha;
        if (intensity <= 0.01) return;

        double centerY = baseY + fontH * 0.5;

        double[][] layers = {
                {1.60, 0.07}, {1.46, 0.11}, {1.33, 0.17}, {1.20, 0.25}, {1.10, 0.34}
        };

        char[] chars = text.toCharArray();
        double x = leftX;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            double cw = fr.getCharWidth(c, next);

            double reveal = Mth.limit((headX - x) / Math.max(1.0E-3, cw), 0.0, 1.0);

            if (reveal > 0.01 && c != ' ') {
                double cx = x + cw * 0.5;
                int rgb = this.glowColor.getValue().getRGB() & 0xFFFFFF;
                String s = String.valueOf(c);

                for (double[] layer : layers) {
                    int alpha = (int) (intensity * reveal * layer[1] * 255.0);
                    if (alpha <= 0) continue;

                    api.getGLStateManager().pushMatrix();
                    scaleAtPos(cx, centerY, layer[0]);
                    fr.drawString(s, x, baseY, RGBA.color(rgb, alpha));
                    api.getGLStateManager().popMatrix();
                }
            }

            x += cw;
        }
    }

    private void updateAuroraLinger(LyricLine line, LyricRenderInfo renderInfo, boolean isCurrent) {
        line.auroraGlow = Interpolations.interpolate(line.auroraGlow, isCurrent ? 1f : 0f, isCurrent ? 0.35f : 0.06f);

        if (isCurrent) return;
        if (!this.auroraBloom.getValue() || !true) return;
        if (line.auroraGlow <= 0.01f) return;

        CFontRenderer fr = getFontRenderer();
        String text = line.getLyric();
        if (text.isEmpty()) return;

        double leftX = calculateAlignmentX(text, AlignMode.valueOf(this.alignMode.getValue()));
        double headX = leftX + fr.getStringWidthD(text);
        renderAuroraGlow(fr, text, leftX, renderInfo.yPosition, headX, renderInfo.fade, auroraEnergy, line.auroraGlow);
    }

    private void renderAuroraSpark(double headX, double baseY, double fade, double beat) {
        double centerY = baseY + fontH * 0.5;
        double pulse = 0.6 + 0.4 * beat;
        int rgb = this.glowColor.getValue().getRGB() & 0xFFFFFF;

        double haloW = Math.max(2.0, fontH * 0.42) * (0.85 + beat * 0.3);
        double haloH = fontH * 1.05;
        roundedRect(headX - haloW * 0.5, centerY - haloH * 0.5, haloW, haloH, haloW * 0.5, RGBA.color(rgb, (int) (fade * pulse * 90.0)));

        double coreW = Math.max(1.0, fontH * 0.13);
        double coreH = fontH * 0.92;
        roundedRect(headX - coreW * 0.5, centerY - coreH * 0.5, coreW, coreH, coreW * 0.5, RGBA.white((int) (fade * pulse * 230.0)));

        double dot = Math.max(1.5, fontH * 0.2) * (0.9 + beat * 0.4);
        roundedRect(headX - dot * 0.5, centerY - dot * 0.5, dot, dot, dot * 0.5, RGBA.white((int) (fade * Math.min(1.0, 0.8 + beat) * 255.0)));
    }

    private double lowFrequencyEnergy() {
        float[] bands = AudioPlayer.bandValues;
        if (bands == null || bands.length == 0) return 0.0;

        int count = Math.min(10, bands.length);
        double weightedSum = 0.0, totalWeight = 0.0;

        for (int i = 0; i < count; i++) {
            double weight = Math.exp(-i * 0.25);
            double value = Math.min(bands[i], 2.0);
            weightedSum += value * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) return 0.0;

        double average = weightedSum / totalWeight;
        return Mth.limit(Math.log1p(average * 4.0) * 0.6, 0.0, 1.0);
    }

    private void renderScrollMode(LyricLine line, LyricRenderInfo renderInfo) {
        AlignMode alignMode = AlignMode.valueOf(this.alignMode.getValue());
        double x = calculateAlignmentX(line.getLyric(), alignMode);

        StencilClipManager.beginClip(() -> Rect.draw(x, renderInfo.yPosition, line.scrollWidth + 1, fontH + 4, -1));

        renderAlignedText(line.getLyric(), renderInfo.yPosition, withFade(-1, renderInfo.fade), alignMode);

        StencilClipManager.endClip();
    }

    private void renderFadeInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        double offsetX = calculateAlignmentX(line.getLyric(), AlignMode.valueOf(this.alignMode.getValue()));

        for (int m = 0; m <= wordInfo.currentIndex; m++) {
            LyricLine.Word word = line.words.get(m);

            if (m == wordInfo.currentIndex) {
                updateCurrentWordAnimation(word, songProgress);
            } else {
                word.alpha = 1;
            }

            bigFrString(word.word, offsetX, renderInfo.yPosition,
                    RGBA.color(255, 255, 255, (int) (word.alpha * 255 * renderInfo.fade)));

            offsetX += getFontRenderer().getStringWidthD(word.word);
        }
    }

    private void renderSlideInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        double offsetX = calculateSlideInTargetX(line, AlignMode.valueOf(this.alignMode.getValue()));
        double targetOffsetX = 0;

        for (int m = 0; m <= wordInfo.currentIndex; m++) {
            LyricLine.Word word = line.words.get(m);
            double stWidth = getFontRenderer().getStringWidthD(word.word);

            if (m == wordInfo.currentIndex) {
                updateCurrentWordAnimation(word, songProgress);
                targetOffsetX += stWidth * Easing.EASE_OUT_CUBIC.getFunction().apply(word.progress);
            } else {
                word.alpha = 1;
                targetOffsetX += stWidth;
            }

            bigFrString(word.word, offsetX, renderInfo.yPosition,
                    RGBA.color(255, 255, 255, (int) (word.alpha * 255 * renderInfo.fade)));

            offsetX += stWidth;
        }

        line.targetOffsetX = targetOffsetX;
    }

    private void updateCurrentWordAnimation(LyricLine.Word word, float songProgress) {
        double progress = Mth.limit((songProgress - word.timestamp) / (double) word.duration, 0, 1);
        word.progress = progress;
        word.alpha = (float) Math.min(1, progress * 1.25f);
    }

    private double calculateAlignmentX(String text, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            return wpsInterface.getX();
        } else if (alignMode == AlignMode.Karaoke) {
            return karaokeRightAligned
                    ? wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text)
                    : wpsInterface.getX();
        } else if (alignMode == AlignMode.Center) {
            return wpsInterface.getX() + wpsInterface.getWidth() / 2.0f - getFontRenderer().getStringWidthD(text) / 2.0f;
        } else {
            return wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text);
        }
    }

    private double calculateSlideInTargetX(LyricLine line, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            return wpsInterface.getX();
        } else if (alignMode == AlignMode.Karaoke) {
            return karaokeRightAligned
                    ? wpsInterface.getX() + wpsInterface.getWidth() - line.targetOffsetX
                    : wpsInterface.getX();
        } else if (alignMode == AlignMode.Center) {
            return wpsInterface.getX() + wpsInterface.getWidth() / 2.0 - line.targetOffsetX / 2.0;
        } else {
            return wpsInterface.getX() + wpsInterface.getWidth() - line.targetOffsetX;
        }
    }

    private void renderAlignedText(String text, double y, int color, AlignMode alignMode) {
        if (alignMode == AlignMode.Left) {
            bigFrString(text, wpsInterface.getX(), y, color);
        } else if (alignMode == AlignMode.Karaoke) {
            if (karaokeRightAligned) {
                bigFrString(text, wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text), y, color);
            } else {
                bigFrString(text, wpsInterface.getX(), y, color);
            }
        } else if (alignMode == AlignMode.Center) {
            bigFrStringCentered(text, wpsInterface.getX() + wpsInterface.getWidth() / 2.0, y, color);
        } else {
            bigFrString(text, wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text), y, color);
        }
    }

    private static class LyricRenderInfo {
        double yPosition;
        double fade = 1.0;
        boolean shouldSkip = false;
        boolean shouldBreak = false;
    }

    private static class WordInfo {
        int currentIndex = 0;
        StringBuilder textBefore = new StringBuilder();
    }

    private static CFontRenderer getFontRenderer() {
        return FontManager.pf28bold;
    }

    private static CFontRenderer getSmallFontRenderer() {
        return FontManager.pf18bold;
    }

    private void bigFrString(String text, double x, double y, int color) {
        if (this.shadow.getValue()) {
            getFontRenderer().drawStringWithShadow(text, x, y, color);
        } else {
            getFontRenderer().drawString(text, x, y, color);
        }
    }

    private void bigFrStringCentered(String text, double x, double y, int color) {
        if (this.shadow.getValue()) {
            getFontRenderer().drawCenteredStringWithShadow(text, x, y, color);
        } else {
            getFontRenderer().drawCenteredString(text, x, y, color);
        }
    }

    private void smallFrString(String text, double x, double y, int color) {
        if (this.shadow.getValue()) {
            getSmallFontRenderer().drawStringWithShadow(text, x, y, color);
        } else {
            getSmallFontRenderer().drawString(text, x, y, color);
        }
    }

    private void smallFrStringCentered(String text, double x, double y, int color) {
        if (this.shadow.getValue()) {
            getSmallFontRenderer().drawCenteredStringWithShadow(text, x, y, color);
        } else {
            getSmallFontRenderer().drawCenteredString(text, x, y, color);
        }
    }

}
