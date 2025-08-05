package rogo.sketchrender.render.component;

import rogo.sketchrender.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class TextureBinding {
    private final Map<Identifier, Identifier> uniformToTexture = new HashMap<>();

    public void bind(Identifier uniformIdentifier, Identifier textureIdentifier) {
        uniformToTexture.put(uniformIdentifier, textureIdentifier);
    }

    public Identifier getTextureIdentifier(Identifier uniformIdentifier) {
        return uniformToTexture.get(uniformIdentifier);
    }
}