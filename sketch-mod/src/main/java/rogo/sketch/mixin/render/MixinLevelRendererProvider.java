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
import rogo.sketch.core.api.LevelPipelineProvider;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineConfig;
import rogo.sketch.profiler.Profiler;
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
    private void onInit(Minecraft p_234245_, EntityRenderDispatcher p_234246_, BlockEntityRenderDispatcher p_234247_,
                        RenderBuffers p_234248_, CallbackInfo ci) {
        PipelineConfig pipelineConfig = new PipelineConfig();
        pipelineConfig.setThrowOnSortFail(true);
        sketchlib$graphPipeline = new McGraphicsPipeline(pipelineConfig);
    }

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void onRenderStart(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                               boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                               Matrix4f projectionMatrix, CallbackInfo ci) {
        Frustum frustum;
        if (capturedFrustum != null) {
            frustum = this.capturedFrustum;
        } else {
            frustum = this.cullingFrustum;
        }

        McRenderContext context = new McRenderContext((LevelRenderer) (Object) this, modelViewMatrix, projectionMatrix,
                camera, frustum, this.ticks, partialTicks);
        sketchlib$graphPipeline.tickFrame();
        context.setRenderStateManager(sketchlib$graphPipeline.renderStateManager());
        context.setTransformStateManager(sketchlib$graphPipeline.transformStateManager());
        context.setNextTick(sketchlib$graphPipeline.anyNextTick());
        sketchlib$graphPipeline.resetRenderContext(context);
        sketchlib$graphPipeline.computeAllRenderCommand();
        Profiler.get().push("renderLevel");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=clear"}, shift = At.Shift.BEFORE, by = 1)})
    private void beforeClearStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                  boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                  Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.RENDER_START.getIdentifier(),
                MinecraftRenderStages.CLEAR.getIdentifier());
        Profiler.get().popPush("clear");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=captureFrustum"}, shift = At.Shift.BEFORE, by = 1)})
    private void beforeFrustumStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                    boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                    Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.CLEAR.getIdentifier(),
                MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier());
        Profiler.get().popPush("captureFrustum");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=sky"}, shift = At.Shift.BEFORE, by = 1)})
    private void beforeSkyStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier(),
                MinecraftRenderStages.SKY.getIdentifier());
        Profiler.get().popPush("sky");
    }

    @Inject(method = "renderChunkLayer", at = @At(value = "HEAD"))
    private void beforeSkyStage(RenderType renderType, PoseStack p_172995_, double p_172996_, double p_172997_,
                                double p_172998_, Matrix4f p_254039_, CallbackInfo ci) {
        if (renderType == RenderType.solid()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.SKY.getIdentifier(),
                    MinecraftRenderStages.TERRAIN_SOLID.getIdentifier());
        } else if (renderType == RenderType.cutoutMipped()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_SOLID.getIdentifier(),
                    MinecraftRenderStages.TERRAIN_CUTOUT_MIPPED.getIdentifier());
        } else if (renderType == RenderType.cutout()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_CUTOUT_MIPPED.getIdentifier(),
                    MinecraftRenderStages.TERRAIN_CUTOUT.getIdentifier());
        } else if (renderType == RenderType.translucent()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.BLOCK_OUTLINE.getIdentifier(),
                    MinecraftRenderStages.TERRAIN_TRANSLUCENT.getIdentifier());
        } else if (renderType == RenderType.tripwire()) {
            sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_TRANSLUCENT.getIdentifier(),
                    MinecraftRenderStages.TERRAIN_TRIPWIRE.getIdentifier());
            Profiler.get().popPush("terrain_tripwire");
        }
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=entities"}, shift = At.Shift.BEFORE, by = 1)})
    private void onEntityStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                               boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                               Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_CUTOUT.getIdentifier(),
                MinecraftRenderStages.ENTITIES.getIdentifier());
        Profiler.get().popPush("entities");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=blockentities"}, shift = At.Shift.BEFORE, by = 1)})
    private void onBlockEntityStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                    boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                    Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.ENTITIES.getIdentifier(),
                MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier());
        Profiler.get().popPush("block_entities");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=destroyProgress"}, shift = At.Shift.BEFORE, by = 1)})
    private void onDestroyProgressStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                        boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                        Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.BLOCK_ENTITIES.getIdentifier(),
                MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier());
        Profiler.get().popPush("destroy_progress");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=outline"}, shift = At.Shift.BEFORE, by = 1)})
    private void onOutlineStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.DESTROY_PROGRESS.getIdentifier(),
                MinecraftRenderStages.BLOCK_OUTLINE.getIdentifier());
        Profiler.get().popPush("outline");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=particles"}, shift = At.Shift.BEFORE, by = 1)})
    private void onParticleStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                 boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                 Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.TERRAIN_TRIPWIRE.getIdentifier(),
                MinecraftRenderStages.PARTICLE.getIdentifier());
        Profiler.get().popPush("particles");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=clouds"}, shift = At.Shift.BEFORE, by = 1)})
    private void onCloudsStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                               boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                               Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.PARTICLE.getIdentifier(),
                MinecraftRenderStages.CLOUDS.getIdentifier());
        Profiler.get().popPush("clouds");
    }

    @Inject(method = {"renderLevel"}, at = {
            @At(value = "CONSTANT", args = {"stringValue=weather"}, shift = At.Shift.BEFORE, by = 1)})
    private void onWeatherStage(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                                boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.CLOUDS.getIdentifier(),
                MinecraftRenderStages.WEATHER.getIdentifier());
        Profiler.get().popPush("weather");
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void onRenderEnd(PoseStack modelViewMatrix, float partialTicks, long nanoTime,
                             boolean shouldRenderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                             Matrix4f projectionMatrix, CallbackInfo ci) {
        sketchlib$graphPipeline.renderStagesBetween(MinecraftRenderStages.WEATHER.getIdentifier(),
                MinecraftRenderStages.LEVEL_END.getIdentifier());
        Profiler.get().popPush("level_end");
        Profiler.get().pop("renderLevel");
    }

    @Override
    public GraphicsPipeline<?> getGraphicsPipeline() {
        return sketchlib$graphPipeline;
    }
}