package tritium;

import lombok.Getter;
import today.opai.api.OpenAPI;

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

    public TritiumMusicExtension() {

    }

    public void init(OpenAPI openAPI) {
        System.out.println("Hello, Opai!");
    }

    public void unload() {

    }

}
