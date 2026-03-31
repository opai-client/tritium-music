package tritium;

import today.opai.api.Extension;
import today.opai.api.OpenAPI;
import today.opai.api.annotations.ExtensionInfo;

/**
 * @author IzumiiKonata
 * Date: 2026/3/31 22:29
 */
@ExtensionInfo(name = TritiumMusicExtension.NAME, author = TritiumMusicExtension.AUTHOR, version = TritiumMusicExtension.VERSION)
public class ExtensionEntry extends Extension {

    @Override
    public void initialize(OpenAPI openAPI) {
        TritiumMusicExtension.getInstance().init(openAPI);
    }

    @Override
    public void onUnload() {
        TritiumMusicExtension.getInstance().unload();
    }

}
