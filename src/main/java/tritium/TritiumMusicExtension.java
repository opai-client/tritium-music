package tritium;

import lombok.Getter;
import today.opai.api.OpenAPI;
import tritium.management.AbstractManager;
import tritium.management.FontManager;
import tritium.module.impl.OpenNCMScreen;
import tritium.ncm.music.CloudMusic;
import tritium.reflection.Reflection;
import tritium.rendering.Framebuffer;
import tritium.rendering.OpenGlHelper;
import tritium.utils.other.multithreading.MultiThreadingUtil;
import tritium.widget.impl.MusicInfoWidget;
import tritium.widget.impl.MusicLyricsWidget;
import tritium.widget.impl.MusicSpectrumWidget;

import java.util.Arrays;
import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2026/3/31 22:28
 */
public class TritiumMusicExtension {

    public static final String NAME = "Tritium Music";
    public static final String AUTHOR = "IzumiiKonata";
    public static final String VERSION = "1.0.2";

    @Getter
    private static final TritiumMusicExtension instance = new TritiumMusicExtension();

    @Getter
    private FontManager fontManager;

    public OpenNCMScreen tritiumMusic =  new OpenNCMScreen();
    public MusicInfoWidget musicInfo = new MusicInfoWidget();
    public MusicLyricsWidget musicLyrics = new MusicLyricsWidget();
    public MusicSpectrumWidget musicSpectrum = new MusicSpectrumWidget();

    public TritiumMusicExtension() {

    }

    public void init(OpenAPI api) {
        api.registerEvent(TritiumEventHandler.getInstance());

        MultiThreadingUtil.runAsync(CloudMusic::initNCM);
        Reflection.init(api);
//        Framebuffer.updateMcFramebuffer();

        this.fontManager = new FontManager();

        List<AbstractManager> managers = Arrays.asList(this.fontManager);

        for (AbstractManager manager : managers) {
//            logger.debug("calling init() on {}...", manager.getName());
            manager.init();
        }

        api.registerFeature(this.tritiumMusic);
        api.registerFeature(this.musicInfo);
        api.registerFeature(this.musicInfo.widget);
        api.registerFeature(this.musicLyrics);
        api.registerFeature(this.musicLyrics.widget);
        api.registerFeature(this.musicSpectrum);
        api.registerFeature(this.musicSpectrum.widget);
    }

    public void unload() {

    }

    public static boolean isCallingFromMainThread() {
        return Thread.currentThread().getName().equals("Client thread");
    }

}
