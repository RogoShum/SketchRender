package rogo.sketch.vanilla.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import rogo.sketch.core.resource.Texture;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

public class BuildInRTTexture extends Texture {
    private final Supplier<RenderTarget> renderTarget;
    private final boolean depth;

    public BuildInRTTexture(Supplier<RenderTarget> renderTarget, int format, boolean depth, int minFilter, int magFilter, int wrapS, int wrapT) {
        super(-1, KeyId.of("temp_texture"), -1, -1, format, minFilter, magFilter, wrapS, wrapT);
        this.renderTarget = renderTarget;
        this.depth = depth;
    }

    @Override
    public int getHandle() {
        return depth ? renderTarget.get().getDepthTextureId() : renderTarget.get().getColorTextureId();
    }

    @Override
    public int getCurrentWidth() {
        return renderTarget.get().width;
    }

    @Override
    public int getCurrentHeight() {
        return renderTarget.get().height;
    }

    @Override
    public void dispose() {

    }
}