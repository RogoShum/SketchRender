package rogo.sketch.feature.culling.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL13;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.MeshResource;
import rogo.sketch.core.instance.ComputeGraphics;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceReference;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.util.KeyId;

import static org.lwjgl.opengl.GL42.GL_ATOMIC_COUNTER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class ComputeChunkCullingGraphics extends ComputeGraphics {
    private final ResourceReference<PartialRenderSetting> cullingChunkSetting = GraphicsResourceManager.getInstance()
            .getReference(ResourceTypes.PARTIAL_RENDER_SETTING, KeyId.of(SketchRender.MOD_ID, "cull_chunk"));

    public ComputeChunkCullingGraphics(KeyId keyId) {
        super(keyId, null, (c, shader) -> {
            shader.dispatch(MeshResource.ORDERED_REGION_SIZE, 3, 1);
            shader.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT |
                    GL_ATOMIC_COUNTER_BARRIER_BIT |
                    GL_COMMAND_BARRIER_BIT);

            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        });
    }

    @Override
    public PartialRenderSetting getPartialRenderSetting() {
        if (cullingChunkSetting.isAvailable()) {
            return cullingChunkSetting.get();
        }

        return null;
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}