package rogo.sketchrender.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.compat.sodium.ComputeShaderChunkRenderer;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketchrender.culling.ChunkCullingUniform;
import rogo.sketchrender.culling.ChunkRenderMixinHook;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.shader.IndirectCommandBuffer;

@Mixin(RenderSectionManager.class)
public abstract class MixinRenderSectionManager {

    @Shadow(remap = false)
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow(remap = false)
    private @NotNull SortedRenderLists renderLists;

    @Shadow @Final private ChunkVertexType vertexType;

    @Unique
    private ChunkRenderer computeChunkRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(ClientLevel world, int renderDistance, CommandList commandList, CallbackInfo ci) {
        SodiumSectionAsyncUtil.fromSectionManager(this.sectionByPosition, world);
        computeChunkRenderer = new ComputeShaderChunkRenderer(RenderDevice.INSTANCE, this.vertexType);
    }

    @Inject(method = "renderLayer", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        ChunkRenderMixinHook.onRenderStart(matrices, pass, x, y, z, ci);
        if (Config.getCullChunk() && !CullingStateManager.SHADER_LOADER.renderingShaderPass()) {
            RenderDevice device = RenderDevice.INSTANCE;
            CommandList commandList = device.createCommandList();
            computeChunkRenderer.render(matrices, commandList, this.renderLists, pass, new CameraTransform(x, y, z));
            commandList.flush();
            ci.cancel();
            SketchRender.COMMAND_TIMER.end("renderLayer");
        }
    }

    @Inject(method = "renderLayer", at = @At(value = "RETURN"), remap = false)
    private void onRenderEnd(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.unBind();
        SketchRender.COMMAND_TIMER.end("renderLayer");
    }

    @ModifyArg(method = "destroy", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderer;delete(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)V"), remap = false)
    private CommandList onDestroy(CommandList commandList) {
        computeChunkRenderer.delete(commandList);
        return commandList;
    }

    @Inject(method = "update", at = @At(value = "HEAD"), remap = false)
    private void onUpdate(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        CullingStateManager.updating();
        ChunkCullingUniform.currentFrame = frame;
    }

    @ModifyVariable(name = "visitor", method = "createTerrainRenderList", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/occlusion/OcclusionCuller;findVisible(Lme/jellysquid/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$Visitor;Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;FZI)V", shift = At.Shift.BEFORE), remap = false)
    private VisibleChunkCollector onCreateTerrainRenderList(VisibleChunkCollector value) {
        if (Config.getAsyncChunkRebuild()) {
            VisibleChunkCollector collector = CullingStateManager.renderingIris() ? SodiumSectionAsyncUtil.getShadowCollector() : SodiumSectionAsyncUtil.getChunkCollector();
            return collector == null ? value : collector;
        }
        return value;
    }

    @Inject(method = "updateChunks", at = @At(value = "HEAD"), remap = false)
    private void onCreateTerrainRenderList(boolean updateImmediately, CallbackInfo ci) {
        if (Config.getAsyncChunkRebuild()) {
            VisibleChunkCollector collector = CullingStateManager.renderingIris() ? SodiumSectionAsyncUtil.getShadowCollector() : SodiumSectionAsyncUtil.getChunkCollector();
            if (collector != null)
                this.renderLists = collector.createRenderLists();
        }
    }
}