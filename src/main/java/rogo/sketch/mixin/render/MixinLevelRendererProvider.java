package rogo.sketch.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketch.api.LevelPipelineProvider;
import rogo.sketch.render.GraphicsPipeline;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.McRenderContext;
import rogo.sketch.vanilla.MinecraftRenderStages;

import javax.annotation.Nullable;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererProvider implements LevelPipelineProvider {
    @Shadow
    private int ticks;
    @Shadow
    @Nullable
    private Frustum capturedFrustum;
    @Shadow
    private Frustum cullingFrustum;
    @Unique
    private McGraphicsPipeline sketchlib$graphPipeline;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void onInit(Minecraft p_234245_, EntityRenderDispatcher p_234246_, BlockEntityRenderDispatcher p_234247_, RenderBuffers p_234248_, CallbackInfo ci) {
        sketchlib$graphPipeline = new McGraphicsPipeline(true);
    }

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void onRenderStart(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        Frustum frustum;
        if (capturedFrustum != null) {
            frustum = this.capturedFrustum;
        } else {
            frustum = this.cullingFrustum;
        }

        McRenderContext context = new McRenderContext((LevelRenderer) (Object) this, modelViewMatrix, projectionMatrix, camera, frustum, this.ticks, partialTicks);
        sketchlib$graphPipeline.resetRenderContext(context);
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=captureFrustum"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void beforeFrustumStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.RENDER_START.getIdentifier(), MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=sky"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void beforeSkyStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier(), MinecraftRenderStages.SKY.getIdentifier());
    }

    @Inject(method = "renderChunkLayer", at = @At(value = "HEAD"))
    private void beforeSkyStage(RenderType renderType, PoseStack p_172995_, double p_172996_, double p_172997_, double p_172998_, Matrix4f p_254039_, CallbackInfo ci) {
        if (renderType == RenderType.solid()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.SKY.getIdentifier(), MinecraftRenderStages.TERRAIN_SOLID.getIdentifier());
        } else if (renderType == RenderType.cutoutMipped()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_SOLID.getIdentifier(), MinecraftRenderStages.TERRAIN_CUTOUT_MIPPED.getIdentifier());
        } else if (renderType == RenderType.cutout()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_CUTOUT_MIPPED.getIdentifier(), MinecraftRenderStages.TERRAIN_CUTOUT.getIdentifier());
        } else if (renderType == RenderType.translucent()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.BLOCK_OUTLINE.getIdentifier(), MinecraftRenderStages.TERRAIN_TRANSLUCENT.getIdentifier());
        } else if (renderType == RenderType.tripwire()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_TRANSLUCENT.getIdentifier(), MinecraftRenderStages.TERRAIN_TRIPWIRE.getIdentifier());
        }
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=entities"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onEntityStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_CUTOUT.getIdentifier(), MinecraftRenderStages.ENTITIES.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=blockentities"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onBlockEntityStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.ENTITIES.getIdentifier(), MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=destroyProgress"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onDestroyProgressStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier(), MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=outline"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onOutlineStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier(), MinecraftRenderStages.BLOCK_OUTLINE.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=particles"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onParticleStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_TRIPWIRE.getIdentifier(), MinecraftRenderStages.PARTICLE.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=clouds"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onCloudsStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.PARTICLE.getIdentifier(), MinecraftRenderStages.CLOUDS.getIdentifier());
    }

    @Inject(
            method = {"renderLevel"},
            at = {@At(
                    value = "CONSTANT",
                    args = {"stringValue=weather"},
                    shift = At.Shift.BEFORE,
                    by = 1
            )}
    )
    private void onWeatherStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.CLOUDS.getIdentifier(), MinecraftRenderStages.WEATHER.getIdentifier());
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void onRenderEnd(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.WEATHER.getIdentifier(), MinecraftRenderStages.LEVEL_END.getIdentifier());
    }

    @Override
    public GraphicsPipeline<?> getGraphicsPipeline() {
        return sketchlib$graphPipeline;
    }
}