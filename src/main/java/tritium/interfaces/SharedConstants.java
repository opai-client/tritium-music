package tritium.interfaces;

import today.opai.api.OpenAPI;
import tritium.ExtensionEntry;

/**
 * Commonly shared constants between the classes.
 *
 * @author IzumiiKonata
 * @since 11/19/2023
 */
public interface SharedConstants {

    OpenAPI api = ExtensionEntry.getAPI();

}
