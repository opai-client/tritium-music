package tritium.rendering;

import lombok.Getter;
import tritium.rendering.texture.ITextureObject;
import tritium.utils.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author IzumiiKonata
 * Date: 2026/4/1 09:22
 */
public class TextureManager {

    @Getter
    private static final TextureManager instance = new TextureManager();

    public final Map<Location, ITextureObject> mapTextureObjects = new ConcurrentHashMap<>(512);

    public ITextureObject getTexture(Location textureLocation) {
        return this.mapTextureObjects.get(textureLocation);
    }

    public void deleteTexture(Location textureLocation) {
        ITextureObject itextureobject = this.getTexture(textureLocation);

        if (itextureobject != null) {
            this.mapTextureObjects.remove(textureLocation);
            TextureUtil.deleteTexture(itextureobject.getGlTextureId());
        }
    }

    public void loadTexture(Location img, ITextureObject textureObj) {
        this.mapTextureObjects.put(img, textureObj);
    }
}
