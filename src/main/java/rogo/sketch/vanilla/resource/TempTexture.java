package rogo.sketch.vanilla.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import rogo.sketch.render.resource.Texture;
import rogo.sketch.util.GLFeatureChecker;
import rogo.sketch.util.Identifier;

import java.util.function.Supplier;

import static org.lwjgl.opengl.EXTDirectStateAccess.glGetTextureLevelParameterivEXT;
import static org.lwjgl.opengl.EXTDirectStateAccess.glGetTextureParameterivEXT;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL45.glGetTextureLevelParameteriv;
import static org.lwjgl.opengl.GL45.glGetTextureParameteriv;

public class TempTexture {
    private final Supplier<RenderTarget> renderTarget;
    private final boolean depth;
    private Texture texture;

    public TempTexture(Supplier<RenderTarget> renderTarget, boolean depth) {
        this.renderTarget = renderTarget;
        this.depth = depth;
    }

    public Texture getTexture() {
        int textureId = depth ? renderTarget.get().getDepthTextureId() : renderTarget.get().getColorTextureId();

        if (texture == null || textureId != texture.getHandle()) {
            int[] format = new int[1];
            int[] minFilter = new int[1];
            int[] wrapS = new int[1];

            if (GLFeatureChecker.supportsDSA45() || GLFeatureChecker.supportsDSA_ARB()) {
                glGetTextureLevelParameteriv(textureId, 0, GL_TEXTURE_INTERNAL_FORMAT, format);
                glGetTextureParameteriv(textureId, GL_TEXTURE_MIN_FILTER, minFilter);
                glGetTextureParameteriv(textureId, GL_TEXTURE_WRAP_S, wrapS);
            } else if (GLFeatureChecker.supportsDSA_EXT()) {
                glGetTextureLevelParameterivEXT(textureId, GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT, format);
                glGetTextureParameterivEXT(textureId, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
                glGetTextureParameterivEXT(textureId, GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
            } else {
                glBindTexture(GL_TEXTURE_2D, textureId);
                glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT, format);
                glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
                glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
                glBindTexture(GL_TEXTURE_2D, 0); // 可选解绑
            }

            texture = new Texture(
                    textureId,
                    Identifier.of("render_target_" + renderTarget.get().frameBufferId),
                    format[0],
                    minFilter[0],
                    wrapS[0]
            );
            return texture;
        } else {
            return texture;
        }
    }
}