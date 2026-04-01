package tritium.module.impl;

import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.interfaces.EventHandler;
import today.opai.api.interfaces.modules.values.BooleanValue;
import tritium.interfaces.SharedConstants;
import tritium.screens.ncm.NCMScreen;
import tritium.settings.ClientSettings;

/**
 * @author IzumiiKonata
 * Date: 2026/4/1 11:08
 */
public class OpenNCMScreen extends ExtensionModule implements SharedConstants, EventHandler {

    public OpenNCMScreen() {
        super("Tritium Music", "Open tritium music gui", EnumModuleCategory.MISC);
        this.setEventHandler(this);

        this.addValues(this.boundaries, this.lyricDebug);

        this.boundaries.setValueCallback(b -> ClientSettings.SHOW_WIDGET_BOUNDARY = b);
        this.boundaries.setValueCallback(b -> ClientSettings.DEBUG_MODE = b);
    }

    public BooleanValue boundaries = api.getValueManager().createBoolean("Show UI Widget Boundary", true);
    public BooleanValue lyricDebug = api.getValueManager().createBoolean("Per-word lyrics debug", true);

    @Override
    public void onTick() {
        this.setEnabled(false);
    }

    @Override
    public void onEnabled() {
        api.displayScreen(NCMScreen.getInstance());
    }
}
