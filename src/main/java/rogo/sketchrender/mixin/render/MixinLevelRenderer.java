package rogo.sketchrender.mixin.render;

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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.minecraft.GraphStages;
import rogo.sketchrender.minecraft.McGraphicsPipeline;
import rogo.sketchrender.minecraft.McRenderContext;

import javax.annotation.Nullable;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow
    private int ticks;
    @Shadow
    @Nullable
    private Frustum capturedFrustum;
    private McGraphicsPipeline graphPipeline;

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void onInit(Minecraft p_234245_, EntityRenderDispatcher p_234246_, BlockEntityRenderDispatcher p_234247_, RenderBuffers p_234248_, CallbackInfo ci) {
        graphPipeline = new McGraphicsPipeline(true);
        GraphStages.registerVanillaStages(graphPipeline);
    }

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void onRenderStart(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        McRenderContext context = new McRenderContext((LevelRenderer) (Object) this, modelViewMatrix, projectionMatrix, camera, this.capturedFrustum, this.ticks, partialTicks);
        graphPipeline.resetRenderContext(context);
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
        graphPipeline.renderStagesBefore(GraphStages.SKY.getIdentifier());
    }

    @Inject(method = "renderChunkLayer", at = @At(value = "HEAD"))
    private void beforeSkyStage(RenderType renderType, PoseStack p_172995_, double p_172996_, double p_172997_, double p_172998_, Matrix4f p_254039_, CallbackInfo ci) {
        if (renderType == RenderType.solid()) {
            graphPipeline.renderStagesBetween(GraphStages.SKY.getIdentifier(), GraphStages.TERRAIN_SOLID.getIdentifier());
        } else if (renderType == RenderType.cutoutMipped()) {
            graphPipeline.renderStagesBetween(GraphStages.TERRAIN_SOLID.getIdentifier(), GraphStages.TERRAIN_CUTOUT_MIPPED.getIdentifier());
        } else if (renderType == RenderType.cutout()) {
            graphPipeline.renderStagesBetween(GraphStages.TERRAIN_CUTOUT_MIPPED.getIdentifier(), GraphStages.TERRAIN_CUTOUT.getIdentifier());
        } else if (renderType == RenderType.translucent()) {
            graphPipeline.renderStagesBetween(GraphStages.BLOCK_OUTLINE.getIdentifier(), GraphStages.TERRAIN_TRANSLUCENT.getIdentifier());
        } else if (renderType == RenderType.tripwire()) {
            graphPipeline.renderStagesBetween(GraphStages.TERRAIN_TRANSLUCENT.getIdentifier(), GraphStages.TERRAIN_TRIPWIRE.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.TERRAIN_CUTOUT.getIdentifier(), GraphStages.ENTITIES.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.ENTITIES.getIdentifier(), GraphStages.BLOCK_ENTITIES.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.BLOCK_ENTITIES.getIdentifier(), GraphStages.DESTROY_PROGRESS.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.DESTROY_PROGRESS.getIdentifier(), GraphStages.BLOCK_OUTLINE.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.TERRAIN_TRIPWIRE.getIdentifier(), GraphStages.PARTICLE.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.PARTICLE.getIdentifier(), GraphStages.CLOUDS.getIdentifier());
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
        graphPipeline.renderStagesBetween(GraphStages.CLOUDS.getIdentifier(), GraphStages.WEATHER.getIdentifier());
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void onRenderEnd(PoseStack modelViewMatrix, float partialTicks, long nanoTime, boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        graphPipeline.renderStagesAfter(GraphStages.WEATHER.getIdentifier());
    }
}