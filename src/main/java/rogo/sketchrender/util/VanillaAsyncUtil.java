package rogo.sketchrender.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.NotNull;
import rogo.sketchrender.api.Config;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.api.EntitiesForRender;
import rogo.sketchrender.api.RenderChunkInfo;
import rogo.sketchrender.api.RenderSectionVisibility;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import static net.minecraft.client.renderer.LevelRenderer.DIRECTIONS;

public class VanillaAsyncUtil {
    private static int chunkLength = 0;
    private static LevelRenderer.RenderChunkStorage storage;
    private static LevelRenderer levelRenderer;
    private static final Semaphore shouldUpdate = new Semaphore(0);
    public static boolean injectedAsyncMixin;

    public static void asyncSearchRebuildSection() {
        shouldUpdate.acquireUninterruptibly();
        if (levelRenderer == null) return;
        LevelRenderer.RenderChunkStorage renderChunkStorage = new LevelRenderer.RenderChunkStorage(chunkLength);
        Queue<LevelRenderer.RenderChunkInfo> queue = new ArrayDeque<>();
        HashSet<BlockPos> posHashSet = new HashSet<>();
        BlockPos origin = getOriginPos();
        LevelRenderer.RenderChunkInfo originChunk = new LevelRenderer.RenderChunkInfo(((EntitiesForRender) levelRenderer).invokeGetRenderChunkAt(origin), null, 0);
        queue.add(originChunk);

        while (!queue.isEmpty()) {
            LevelRenderer.RenderChunkInfo last = queue.poll();
            ChunkRenderDispatcher.RenderChunk lastRenderChunk = ((RenderChunkInfo) last).getRenderChunk();
            if (originChunk != last && (!CullingStateManager.FRUSTUM.isVisible(lastRenderChunk.getBoundingBox()) || !CullingStateManager.shouldRenderChunk((RenderSectionVisibility) lastRenderChunk, true))) {
                continue;
            }

            ChunkRenderDispatcher.CompiledChunk compiledChunk = lastRenderChunk.getCompiledChunk();
            boolean build = lastRenderChunk.isDirty() && Minecraft.getInstance().level.getLightEngine().lightOnInSection(SectionPos.of(lastRenderChunk.getOrigin()));
            boolean render = !compiledChunk.getRenderableBlockEntities().isEmpty() || !compiledChunk.hasNoRenderableLayers();

            if (build || render) {
                renderChunkStorage.renderChunks.add(last);
            }

            for (Direction direction : DIRECTIONS) {
                ChunkRenderDispatcher.RenderChunk sideRenderChunk = ((EntitiesForRender) levelRenderer).invokeGetRelativeFrom(origin, lastRenderChunk, direction);
                if (sideRenderChunk != null && !posHashSet.contains(sideRenderChunk.getOrigin())) {
                    posHashSet.add(sideRenderChunk.getOrigin());
                    LevelRenderer.RenderChunkInfo newRenderChunk = new LevelRenderer.RenderChunkInfo(sideRenderChunk, direction, ((RenderChunkInfo) last).getStep() + 1);
                    queue.add(newRenderChunk);
                }
            }
        }
        if(CullingStateManager.CHUNK_CULLING_MAP != null)
            CullingStateManager.CHUNK_CULLING_MAP.queueUpdateCount++;
        storage = renderChunkStorage;
    }

    public static void update(LevelRenderer renderer, int length) {
        levelRenderer = renderer;
        chunkLength = length;
    }

    public static LevelRenderer.RenderChunkStorage getChunkStorage() {
        return storage;
    }

    public static boolean shouldReplaceStorage() {
        return Config.getAsyncChunkRebuild() && getChunkStorage() != null;
    }

    public static void shouldUpdate() {
        if (shouldUpdate.availablePermits() < 1) {
            shouldUpdate.release();
        }
    }

    @NotNull
    private static BlockPos getOriginPos() {
        BlockPos origin = Minecraft.getInstance().gameRenderer.getMainCamera().getBlockPosition();
        if (origin.getY() < Minecraft.getInstance().level.getMinBuildHeight()) {
            origin = new BlockPos(origin.getX(), Minecraft.getInstance().level.getMinBuildHeight(), origin.getZ());
        } else if (origin.getY() > Minecraft.getInstance().level.getMaxBuildHeight()) {
            origin = new BlockPos(origin.getX(), Minecraft.getInstance().level.getMaxBuildHeight(), origin.getZ());
        }
        return origin;
    }
}