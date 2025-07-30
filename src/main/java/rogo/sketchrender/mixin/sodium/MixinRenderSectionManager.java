package rogo.sketchrender.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.*;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rogo.sketchrender.SketchRender;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.compat.sodium.ExtraChunkRenderer;
import rogo.sketchrender.compat.sodium.IndirectDrawChunkRenderer;
import rogo.sketchrender.compat.sodium.MeshUniform;
import rogo.sketchrender.compat.sodium.SodiumSectionAsyncUtil;
import rogo.sketchrender.culling.ChunkRenderMixinHook;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.shader.IndirectCommandBuffer;

import java.util.ArrayDeque;
import java.util.Map;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {

    @Shadow(remap = false)
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow(remap = false)
    private @NotNull SortedRenderLists renderLists;

    @Shadow
    @Final
    private ChunkVertexType vertexType;

    @Shadow
    private @NotNull Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;
    @Shadow
    @Final
    private ChunkRenderer chunkRenderer;
    @Unique
    private ChunkRenderer sketchlib$computeChunkRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(ClientLevel world, int renderDistance, CommandList commandList, CallbackInfo ci) {
        SodiumSectionAsyncUtil.fromSectionManager(this.sectionByPosition, world);
        sketchlib$computeChunkRenderer = new IndirectDrawChunkRenderer(RenderDevice.INSTANCE, this.chunkRenderer.getVertexType(), (ExtraChunkRenderer) this.chunkRenderer);
    }

    @Inject(method = "renderLayer", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onRenderStart(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        ChunkRenderMixinHook.onRenderStart(matrices, pass, x, y, z, ci);
        if (Config.getCullChunk() && !CullingStateManager.SHADER_LOADER.renderingShaderPass()) {
            RenderDevice device = RenderDevice.INSTANCE;
            CommandList commandList = device.createCommandList();
            sketchlib$computeChunkRenderer.render(matrices, commandList, this.renderLists, pass, new CameraTransform(x, y, z));
            commandList.flush();
            ci.cancel();
            SketchRender.COMMAND_TIMER.end("renderLayer");
            //SketchRender.RENDER_TIMER.end("renderLayer_R");
        }
    }

    @Inject(method = "renderLayer", at = @At(value = "RETURN"), remap = false)
    private void onRenderEnd(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, CallbackInfo ci) {
        IndirectCommandBuffer.INSTANCE.unBind();
        SketchRender.COMMAND_TIMER.end("renderLayer");
        //SketchRender.RENDER_TIMER.end("renderLayer_R");
    }

    @ModifyArg(method = "destroy", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderer;delete(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)V"), remap = false)
    private CommandList onDestroy(CommandList commandList) {
        sketchlib$computeChunkRenderer.delete(commandList);
        return commandList;
    }

    @Inject(method = "update", at = @At(value = "HEAD"), remap = false)
    private void onUpdate(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        CullingStateManager.updating();
        MeshUniform.currentFrame = frame;
    }

    @Inject(method = "isSectionVisible", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onIsSectionVisible(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (Config.getAsyncChunkRebuild()) {
            cir.setReturnValue(SodiumSectionAsyncUtil.isSectionVisible(x, y, z));
        }
    }

    @Inject(method = "createTerrainRenderList", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onCreateTerrainRenderList(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        if (Config.getAsyncChunkRebuild()) {
            VisibleChunkCollector collector = CullingStateManager.renderingIris() ? SodiumSectionAsyncUtil.getShadowCollector() : SodiumSectionAsyncUtil.getChunkCollector();

            if (collector != null) {
                this.renderLists = collector.createRenderLists();
                this.rebuildLists = collector.getRebuildLists();
            }

            ci.cancel();
        }
    }
}