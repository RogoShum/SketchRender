package rogo.sketch.vanilla.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import rogo.sketch.core.resource.Texture;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

public class BuildInRTTexture extends Texture {
    private final Supplier<RenderTarget> renderTarget;
    private final boolean depth;

    public BuildInRTTexture(Supplier<RenderTarget> renderTarget, int format, boolean depth) {
        super(-1, KeyId.of("temp_texture"), -1, -1, format, -1, -1, -1, -1);
        this.renderTarget = renderTarget;
        this.depth = depth;
    }

    @Override
    public int getHandle() {
        return depth ? renderTarget.get().getDepthTextureId() : renderTarget.get().getColorTextureId();
    }

    @Override
    public void dispose() {

    }
}