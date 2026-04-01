package tritium;

import ingameime.IngameIMEJNI;
import lombok.Getter;
import today.opai.api.OpenAPI;
import tritium.management.AbstractManager;
import tritium.management.FontManager;
import tritium.rendering.ime.IME;
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
    public static final String VERSION = "1.0";

    @Getter
    private static final TritiumMusicExtension instance = new TritiumMusicExtension();
    private static Thread mainThread;

    @Getter
    private FontManager fontManager;

    public MusicInfoWidget musicInfo = new MusicInfoWidget();
    public MusicLyricsWidget musicLyrics = new MusicLyricsWidget();
    public MusicSpectrumWidget musicSpectrum = new MusicSpectrumWidget();

    public TritiumMusicExtension() {
        mainThread = Thread.currentThread();
    }

    public void init(OpenAPI api) {
        api.registerEvent(TritiumEventHandler.getInstance());

        this.fontManager = new FontManager();

        List<AbstractManager> managers = Arrays.asList(this.fontManager);

        for (AbstractManager manager : managers) {
//            logger.debug("calling init() on {}...", manager.getName());
            manager.init();
        }

        api.registerFeature(this.musicInfo);
        api.registerFeature(this.musicInfo.widget);
        api.registerFeature(this.musicLyrics);
        api.registerFeature(this.musicLyrics.widget);
        api.registerFeature(this.musicSpectrum);
        api.registerFeature(this.musicSpectrum.widget);

        IngameIMEJNI.loadNative();
        if (IngameIMEJNI.supported)
            IME.createInputCtx();
    }

    public void unload() {

    }

    public static boolean isCallingFromMainThread() {
        return Thread.currentThread() == mainThread;
    }

}
