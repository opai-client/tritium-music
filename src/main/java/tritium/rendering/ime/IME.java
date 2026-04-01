package tritium.rendering.ime;

import ingameime.*;
import org.lwjgl.input.Keyboard;
import tritium.interfaces.SharedConstants;
import tritium.interfaces.SharedRenderingConstants;
import tritium.management.FontManager;
import tritium.rendering.Rect;
import tritium.rendering.font.CFontRenderer;
import tritium.rendering.rendersystem.RenderSystem;
import tritium.settings.ClientSettings;
import tritium.utils.cursor.CursorUtils;
import tritium.utils.logging.LogManager;
import tritium.utils.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * @author IzumiiKonata
 * @since 2024/8/26 08:33
 */
public class IME implements SharedRenderingConstants, SharedConstants {

    private static final Logger LOG = LogManager.getLogger("IngameIME");

    public static InputContext InputCtx = null;

    private static long getWindowHandle() {
        return CursorUtils.getHwnd();
    }

    public static void createInputCtx() {
        long hWnd = getWindowHandle();
        if (hWnd != 0) {
            API api = API.Imm32;
            InputCtx = IngameIME.CreateInputContextWin32(hWnd, api, false);
        } else {
            LOG.error("InputContext could not init as the hWnd is NULL!");
            return;
        }

        // Free unused native object
        System.gc();
    }

    public static void destroyInputCtx() {
        if (InputCtx != null) {
            InputCtx.delete();
            InputCtx = null;
        }
    }

    public static boolean getActivated() {
        if (InputCtx != null) return InputCtx.getActivated();
        else return false;
    }

    public static void setActivated(boolean activated) {
        if (InputCtx != null) {
            InputCtx.setActivated(activated);
//            LOG.info("IM active state: {}", activated);
        }
    }

}
