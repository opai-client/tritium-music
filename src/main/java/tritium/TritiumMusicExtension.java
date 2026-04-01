package tritium;

import lombok.Getter;
import today.opai.api.OpenAPI;
import tritium.management.AbstractManager;
import tritium.management.FontManager;

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

    public TritiumMusicExtension() {
        mainThread = Thread.currentThread();
    }

    public void init(OpenAPI openAPI) {
        System.out.println("Hello, Opai!");
        openAPI.registerEvent(TritiumEventHandler.getInstance());

        this.fontManager = new FontManager();

        List<AbstractManager> managers = Arrays.asList(this.fontManager);

        for (AbstractManager manager : managers) {
//            logger.debug("calling init() on {}...", manager.getName());
            manager.init();
        }
    }

    public void unload() {

    }

    public static boolean isCallingFromMainThread() {
        return Thread.currentThread() == mainThread;
    }

}
