package tritium.widget.impl;

import today.opai.api.enums.EnumChatColor;
import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.features.ExtensionWidget;
import today.opai.api.interfaces.EventHandler;
import today.opai.api.interfaces.modules.values.BooleanValue;
import today.opai.api.interfaces.modules.values.ModeValue;
import today.opai.api.interfaces.modules.values.NumberValue;
import tritium.TritiumMusicExtension;
import tritium.interfaces.SharedConstants;
import tritium.interfaces.SharedRenderingConstants;
import tritium.management.FontManager;
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
import tritium.utils.WidgetWrapper;
import tritium.utils.math.Mth;

/**
 * @author IzumiiKonata
 * Date: 2025/2/14 20:34
 */
public class MusicLyricsWidget extends ExtensionModule implements SharedConstants, SharedRenderingConstants, EventHandler {

    static double scrollOffset = 0;

    public ModeValue scrollEffects = api.getValueManager().createModes("Scroll Effects", "Scroll", new String[] { "Scroll", "FadeIn", "SlideIn" });
    public ModeValue alignMode = api.getValueManager().createModes("Align Mode", "Center", new String[]{ "Left", "Center", "Right" });

    public enum AlignMode {
        Left,
        Center,
        Right
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

    public ExtensionWidget widget;
    WidgetWrapper.WidgetPosSizeInterface wpsInterface;
    
    public MusicLyricsWidget() {
        super("Music Lyrics", "Show lyrics.", EnumModuleCategory.VISUAL);

        graceScroll.setHiddenPredicate(() -> singleLine.getValue());
        showRoman.setHiddenPredicate(() -> !showTranslation.getValue());
        dynIsland.setHiddenPredicate(() -> !Reflection.DYNAMIC_ISLAND_SUPPORTED);
        
        this.addValues(scrollEffects, alignMode, width, height, lyricHeight, shadow, singleLine, graceScroll, showRoman, dynIsland);

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
            return;
        }

        float songProgress = CloudMusic.player.getCurrentTimeMillis();

        boolean shouldNotDisplayOtherLyrics = this.singleLine.getValue();

        handleSingleLineMode(shouldNotDisplayOtherLyrics);

        updateScrollOffset(shouldNotDisplayOtherLyrics);

        if (Reflection.DYNAMIC_ISLAND_SUPPORTED && dynIsland.getValue()) {
//            String property = System.getProperty("ncm.dynIslandLyrics");
            LyricLine currentLine = CloudMusic.currentLyric;
            if (currentLine != null) {

                if (currentLine.isBreakLine) {
                    int i = CloudMusic.lyrics.indexOf(currentLine);

                    if (i > 0)
                        currentLine = CloudMusic.lyrics.get(i - 1);
                    else if (i + 1 < CloudMusic.lyrics.size())
                        currentLine = CloudMusic.lyrics.get(i + 1);
                }

                if (CloudMusic.haveNoWords) {
                    System.setProperty("ncm.dynIslandLyrics", currentLine.getLyric());
                } else {
                    WordInfo wordInfo = calculateCurrentWordInfo(currentLine, songProgress);

                    String left = EnumChatColor.WHITE + "";
                    String right = EnumChatColor.GRAY + "";

                    int leftEndIdx = wordInfo.currentIndex;
                    int rightStartIdx = wordInfo.currentIndex + 1;

                    if (rightStartIdx == 1) {
                        LyricLine.Word current = currentLine.words.get(wordInfo.currentIndex);
                        double value = (songProgress - current.timestamp) / (double) (current.duration);

                        if (value < 0) {
                            rightStartIdx -= 1;
                            leftEndIdx -= 1;
                        }
                    }

                    for (int i = 0; i <= leftEndIdx; i++) {
                        left += currentLine.words.get(i).word;
                    }

                    for (int i = rightStartIdx; i < currentLine.words.size(); i++) {
                        right += currentLine.words.get(i).word;
                    }

                    System.setProperty("ncm.dynIslandLyrics", left + right);
                }
            } else {
                System.setProperty("ncm.dynIslandLyrics", "");
            }
        } else {
            api.getGLStateManager().pushMatrix();

            StencilClipManager.beginClip(() -> Rect.draw(wpsInterface.getX() - 2, wpsInterface.getY(), wpsInterface.getWidth() + 4, wpsInterface.getHeight(), -1));

            renderAllLyrics(shouldNotDisplayOtherLyrics, songProgress);

            cleanupRender();
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
                scrollOffset = Interpolations.interpolate(scrollOffset, (indexOf * getLyricHeight()), 0.2f);
            }
        }
    }

    private void renderAllLyrics(boolean shouldNotDisplayOtherLyrics, float songProgress) {
        double offsetY = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - getFontRenderer().getHeight() / 2.0 - scrollOffset;
        int indexOf = CloudMusic.lyrics.indexOf(CloudMusic.currentLyric);

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
                    offsetY += getLyricHeight();
                    continue;
                }

                if (renderInfo.shouldBreak) {
                    break;
                }

                updateLyricAnimation(line, i == indexOf);

                renderLyricText(line, renderInfo, i, indexOf);

                if (line == CloudMusic.currentLyric && !line.words.isEmpty()) {
                    handleScrollEffects(line, renderInfo, songProgress);
                }

                offsetY += getLyricHeight();
            }
        }
    }

    private LyricRenderInfo calculateLyricPosition(LyricLine line, int index, int currentIndex,
                                                   double offsetY, boolean singleLineMode) {
        LyricRenderInfo info = new LyricRenderInfo();

        if (!singleLineMode) {
            double dest = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - getFontRenderer().getHeight() / 2.0 +
                    index * getLyricHeight() - (currentIndex * getLyricHeight());

            if (line.offsetY == Double.MIN_VALUE || Math.abs(line.offsetY - dest) > 100) {
                line.offsetY = dest;
            }

            if (line.offsetY + getLyricHeight() < wpsInterface.getY()) {
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
            info.yPosition = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - getFontRenderer().getHeight() / 2.0;
            line.offsetY = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - getFontRenderer().getHeight() / 2.0;
        }

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
            double prevDest = wpsInterface.getY() + wpsInterface.getHeight() / 2.0 - getFontRenderer().getHeight() / 2.0 +
                    (index - 1) * getLyricHeight() - (currentIndex * getLyricHeight());
            double v = prevLrc.offsetY - prevDest;

            if (v < MusicLyricsWidget.getLyricHeight() * 0.55f) {
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
        boolean bSlideIn = this.scrollEffects.getValue().equals("SlideIn");
        boolean shouldRender = !hasWords || !bSlideIn || index != currentIndex ||
                this.alignMode.getValue().equals("Left");

        int alpha = calculateAlpha(line, index, currentIndex, hasWords);

        String secondaryLyric = hasSecondaryLyrics() ? getSecondaryLyrics(line) : "";
        boolean secondaryLyricEmpty = secondaryLyric.isEmpty();

        Runnable renderTask = createRenderTask(
                line, renderInfo, secondaryLyric, secondaryLyricEmpty,
                shouldRender, alpha, index <= currentIndex
        );

        renderTask.run();
    }

    private int calculateAlpha(LyricLine line, int index, int currentIndex, boolean hasWords) {
        if (hasWords) {
            return index != currentIndex ? (int) (line.lineAlpha * 255) : 80;
        } else {
            return (int) (line.lineAlpha * 255);
        }
    }

    private Runnable createRenderTask(LyricLine line, LyricRenderInfo renderInfo,
                                      String secondaryLyric, boolean secondaryLyricEmpty,
                                      boolean shouldRender, int alpha,
                                      boolean isActive) {
        return () -> {
            int hexColor = RGBA.color(255, 255, 255, alpha);
            int rgb = RGBA.color(255, 255, 255, isActive ? (int) (line.lineAlpha * 255) : 100);

            renderByAlignment(line, renderInfo, secondaryLyric, secondaryLyricEmpty,
                    shouldRender, hexColor, rgb);

        };
    }

    private void renderByAlignment(LyricLine line, LyricRenderInfo renderInfo,
                                   String secondaryLyric, boolean secondaryLyricEmpty,
                                   boolean shouldRender, int hexColor, int rgb) {
        String alignMode = this.alignMode.getValue();
        double y = renderInfo.yPosition;

        switch (alignMode) {
            case "Left":
                if (shouldRender) {
                    bigFrString(line.getLyric(), wpsInterface.getX(), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric, wpsInterface.getX(),
                            y + getFontRenderer().getHeight() + 2, rgb);
                }
                break;
            case "Center":
                double centerX = wpsInterface.getX() + wpsInterface.getWidth() / 2.0;
                if (shouldRender) {
                    bigFrStringCentered(line.getLyric(), centerX, y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrStringCentered(secondaryLyric, centerX,
                            y + getFontRenderer().getHeight() + 2, rgb);
                }
                break;
            case "Right":
                if (shouldRender) {
                    bigFrString(line.getLyric(),
                            wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(line.getLyric()), y, hexColor);
                }
                if (!secondaryLyricEmpty) {
                    smallFrString(secondaryLyric,
                            wpsInterface.getX() + wpsInterface.getWidth() - getSmallFontRenderer().getStringWidthD(secondaryLyric),
                            y + getFontRenderer().getHeight() + 2, rgb);
                }
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

        // find current word index
        for (int k = 0; k < line.words.size(); k++) {
            LyricLine.Word word = line.words.get(k);

            if (word.timestamp > songProgress) {
                info.currentIndex = Math.max(0, k - 1);
                break;
            } else if (k == line.words.size() - 1) {
                info.currentIndex = k;
            }
        }

        // calculate text before current word
        for (int m = 0; m < info.currentIndex; m++) {
            info.textBefore.append(line.words.get(m).word);
        }

        // calculate accumulated text
        for (int m = 0; m < info.currentIndex + 1; m++) {
            info.textAccumulated.append(line.words.get(m).word);
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
        String effectMode = this.scrollEffects.getValue();

        switch (effectMode) {
            case "Scroll":
                renderScrollMode(line, renderInfo);
                break;
            case "FadeIn":
                renderFadeInMode(line, renderInfo, wordInfo, songProgress);
                break;
            case "SlideIn":
                renderSlideInMode(line, renderInfo, wordInfo, songProgress);
                break;
        }
    }

    private void renderScrollMode(LyricLine line, LyricRenderInfo renderInfo) {
        String alignMode = this.alignMode.getValue();
        double x = calculateAlignmentX(line.getLyric(), alignMode);

        StencilClipManager.beginClip(() -> Rect.draw(x, renderInfo.yPosition, line.scrollWidth + 1, getFontRenderer().getHeight() + 4, -1));

        renderAlignedText(line.getLyric(), renderInfo.yPosition, -1, alignMode);

        StencilClipManager.endClip();
    }

    private void renderFadeInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        String alignMode = this.alignMode.getValue();

        double offsetX = calculateAlignmentX(line.getLyric(), alignMode);
        for (int m = 0; m < wordInfo.currentIndex + 1; m++) {
            LyricLine.Word word = line.words.get(m);
            String wordText = word.word;

            if (m == wordInfo.currentIndex) {
                updateCurrentWordAnimation(word, line, wordInfo.currentIndex, songProgress);
            } else if (m < wordInfo.currentIndex) {
                word.alpha = 1;
            }

            double stWidth = getFontRenderer().getStringWidthD(wordText);
            bigFrString(wordText, offsetX, renderInfo.yPosition,
                    RGBA.color(255, 255, 255, (int) (word.alpha * 255)));

            offsetX += stWidth;
        }
    }

    private void renderSlideInMode(LyricLine line, LyricRenderInfo renderInfo, WordInfo wordInfo, float songProgress) {
        String alignMode = this.alignMode.getValue();

        double targetX = calculateSlideInTargetX(line, alignMode);

        Runnable renderTask = () -> {
            double offsetX = targetX;
            double targetOffsetX = 0;

            for (int m = 0; m < wordInfo.currentIndex + 1; m++) {
                LyricLine.Word word = line.words.get(m);
                String wordText = word.word;
                double stWidth = getFontRenderer().getStringWidthD(wordText);

                if (m == wordInfo.currentIndex) {
                    updateCurrentWordAnimation(word, line, wordInfo.currentIndex, songProgress);

                    Easing easeInOutQuad = Easing.EASE_OUT_CUBIC;
                    targetOffsetX += stWidth * easeInOutQuad.getFunction().apply(word.progress);
                } else if (m < wordInfo.currentIndex) {
                    word.alpha = 1;
                    targetOffsetX += stWidth;
                }

                bigFrString(wordText, offsetX, renderInfo.yPosition,
                        RGBA.color(255, 255, 255, (int) (word.alpha * 255)));

                offsetX += stWidth;
            }

            line.targetOffsetX = targetOffsetX;
        };

        renderTask.run();
    }

    private void updateCurrentWordAnimation(LyricLine.Word word, LyricLine line,
                                            int currentIndex, float songProgress) {
        double perc = Mth.limit((songProgress - word.timestamp) / (double) (word.duration), 0, 1);
        double clamped = Math.max(0, Math.min(1, perc));

        word.progress = Interpolations.interpolate(word.progress, clamped, 1);
        word.alpha = (float) Math.min(1, clamped * 1.25f);
    }

    private double calculateAlignmentX(String text, String alignMode) {
        return switch (alignMode) {
            case "Left" -> wpsInterface.getX();
            case "Center" ->
                    wpsInterface.getX() + wpsInterface.getWidth() / 2.0f - getFontRenderer().getStringWidthD(text) / 2.0f;
            case "Right" -> wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text);
            default -> throw new IllegalStateException("Unexpected value: " + alignMode);
        };
    }

    private double calculateSlideInTargetX(LyricLine line, String alignMode) {
        return switch (alignMode) {
            case "Left" -> wpsInterface.getX();
            case "Center" -> wpsInterface.getX() + wpsInterface.getWidth() / 2.0 - line.targetOffsetX / 2.0;
            case "Right" -> wpsInterface.getX() + wpsInterface.getWidth() - line.targetOffsetX;
            default -> throw new IllegalStateException("Unexpected value: " + alignMode);
        };
    }

    private void renderAlignedText(String text, double y, int color, String alignMode) {
        switch (alignMode) {
            case "Left" -> bigFrString(text, wpsInterface.getX(), y, color);
            case "Center" -> bigFrStringCentered(text, wpsInterface.getX() + wpsInterface.getWidth() / 2.0, y, color);
            case "Right" ->
                    bigFrString(text, wpsInterface.getX() + wpsInterface.getWidth() - getFontRenderer().getStringWidthD(text), y, color);
        }
    }

    private void cleanupRender() {
        api.getGLStateManager().popMatrix();
        wpsInterface.setWidth(this.width.getValue().floatValue());
        wpsInterface.setHeight(this.height.getValue().floatValue());
    }

    private static class LyricRenderInfo {
        double yPosition;
        boolean shouldSkip = false;
        boolean shouldBreak = false;
    }

    private static class WordInfo {
        int currentIndex = 0;
        StringBuilder textBefore = new StringBuilder();
        StringBuilder textAccumulated = new StringBuilder();
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

    private static LyricLine.Word getPrevWord(int cur, int j, LyricLine line) {
        LyricLine.Word prev;
        if (cur - 1 < 0) {
            if (j - 1 < 0) {
                prev = line.words.getFirst();
            } else {
                prev = CloudMusic.lyrics.get(j - 1).words.getLast();
            }
        } else {
            prev = line.words.get(cur - 1);
        }
        return prev;
    }

}