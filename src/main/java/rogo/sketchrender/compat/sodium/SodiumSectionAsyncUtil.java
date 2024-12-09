package rogo.sketchrender.compat.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;
import rogo.sketchrender.culling.CullingStateManager;
import rogo.sketchrender.api.CollectorAccessor;

import java.util.*;
import java.util.concurrent.Semaphore;

public class SodiumSectionAsyncUtil {
    private static int frame = 0;
    private static OcclusionCuller occlusionCuller;
    private static Viewport viewport;
    private static float searchDistance;
    private static boolean useOcclusionCulling;
    private static Viewport shadowViewport;
    private static float shadowSearchDistance;
    private static boolean shadowUseOcclusionCulling;
    private static VisibleChunkCollector collector;
    private static VisibleChunkCollector shadowCollector;
    public static boolean renderingEntities;
    private static final Semaphore shouldUpdate = new Semaphore(0);
    public static boolean needSyncRebuild;

    public static void fromSectionManager(Long2ReferenceMap<RenderSection> sections, Level world) {
        SodiumSectionAsyncUtil.occlusionCuller = new OcclusionCuller(sections, world);
    }

    public static void asyncSearchRebuildSection() {
        shouldUpdate.acquireUninterruptibly();
        if(CullingStateManager.needPauseRebuild()) {
            return;
        }
        if (CullingStateManager.enabledShader() && shadowViewport != null) {
            frame++;
            CullingStateManager.useOcclusionCulling = false;
            VisibleChunkCollector shadowCollector = new AsynchronousChunkCollector(frame);
            occlusionCuller.findVisible(shadowCollector, shadowViewport, shadowSearchDistance, shadowUseOcclusionCulling, frame);
            SodiumSectionAsyncUtil.shadowCollector = shadowCollector;
            CullingStateManager.useOcclusionCulling = true;
        }

        if (viewport != null) {
            frame++;
            VisibleChunkCollector collector = new AsynchronousChunkCollector(frame);
            occlusionCuller.findVisible(collector, viewport, searchDistance, useOcclusionCulling, frame);
            SodiumSectionAsyncUtil.collector = collector;

            CullingStateManager.CHUNK_CULLING_UNIFORM.queueUpdateCount++;
            Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildList = SodiumSectionAsyncUtil.collector.getRebuildLists();
            for(ArrayDeque<RenderSection> arrayDeque : rebuildList.values()) {
                if (!arrayDeque.isEmpty()) {
                    needSyncRebuild = true;
                    break;
                }
            }
        }
    }

    public static void pauseAsync() {
        SodiumSectionAsyncUtil.collector = null;
        SodiumSectionAsyncUtil.shadowCollector = null;
    }

    public static void update(Viewport viewport, float searchDistance, boolean useOcclusionCulling) {
        if (CullingStateManager.renderingShader()) {
            SodiumSectionAsyncUtil.shadowViewport = viewport;
            SodiumSectionAsyncUtil.shadowSearchDistance = searchDistance;
            SodiumSectionAsyncUtil.shadowUseOcclusionCulling = useOcclusionCulling;
        } else {
            SodiumSectionAsyncUtil.viewport = viewport;
            SodiumSectionAsyncUtil.searchDistance = searchDistance;
            SodiumSectionAsyncUtil.useOcclusionCulling = useOcclusionCulling;
        }
    }

    public static VisibleChunkCollector getChunkCollector() {
        return SodiumSectionAsyncUtil.collector;
    }

    public static VisibleChunkCollector getShadowCollector() {
        return SodiumSectionAsyncUtil.shadowCollector;
    }

    public static void shouldUpdate() {
        if (shouldUpdate.availablePermits() < 1) {
            shouldUpdate.release();
        }
    }

    public static class AsynchronousChunkCollector extends VisibleChunkCollector {
        private final HashMap<RenderRegion, ChunkRenderList> renderListMap = new HashMap<>();
        private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> syncRebuildLists;
        private static final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> EMPTY_LIST = new EnumMap<>(ChunkUpdateType.class);
        static {
            for (ChunkUpdateType type : ChunkUpdateType.values()) {
                EMPTY_LIST.put(type, new ArrayDeque<>());
            }

        }
        private boolean sent;

        public AsynchronousChunkCollector(int frame) {
            super(frame);
            this.syncRebuildLists = new EnumMap<>(ChunkUpdateType.class);
            ChunkUpdateType[] var2 = ChunkUpdateType.values();

            for (ChunkUpdateType type : var2) {
                this.syncRebuildLists.put(type, new ArrayDeque<>());
            }
        }

        @Override
        public void visit(RenderSection section, boolean visible) {
            if (visible && section.getFlags() != 0) {
                RenderRegion region = section.getRegion();
                ChunkRenderList renderList;
                if (!renderListMap.containsKey(region)) {
                    renderList = new ChunkRenderList(region);
                    ((CollectorAccessor) this).addRenderList(renderList);
                    renderListMap.put(region, renderList);
                } else {
                    renderList = renderListMap.get(region);
                }
                renderList.add(section);
            }

            ((CollectorAccessor) this).addAsyncToRebuildLists(section);
        }

        @Override
        public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
            if(!RenderSystem.isOnRenderThread()) {
                return super.getRebuildLists();
            }
            if(!sent) {
                sent = true;
            } else {
                return EMPTY_LIST;
            }
            if(CullingStateManager.needPauseRebuild()) {
                return syncRebuildLists;
            }
            super.getRebuildLists().forEach(((chunkUpdateType, renderSections) -> {
                for (RenderSection section : renderSections) {
                    if (!section.isDisposed() && section.getBuildCancellationToken() == null) {
                        try {
                            syncRebuildLists.get(chunkUpdateType).add(section);
                        } catch (Exception ignored) {}
                    }
                }
            }));
            return syncRebuildLists;
        }
    }
}