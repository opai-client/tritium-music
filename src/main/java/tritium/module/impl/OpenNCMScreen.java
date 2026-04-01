package tritium.module.impl;

import today.opai.api.enums.EnumModuleCategory;
import today.opai.api.features.ExtensionModule;
import today.opai.api.interfaces.EventHandler;
import tritium.interfaces.SharedConstants;
import tritium.screens.ncm.NCMScreen;

/**
 * @author IzumiiKonata
 * Date: 2026/4/1 11:08
 */
public class OpenNCMScreen extends ExtensionModule implements SharedConstants, EventHandler {

    public OpenNCMScreen() {
        super("Tritium Music", "Open tritium music gui", EnumModuleCategory.MISC);
        this.setEventHandler(this);
    }

    @Override
    public void onTick() {
        this.setEnabled(false);
    }

    @Override
    public void onEnabled() {
        api.displayScreen(NCMScreen.getInstance());
    }
}
