package rogo.sketch.compat.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import rogo.sketch.compat.sodium.api.CollectorAccessor;
import rogo.sketch.compat.sodium.api.ResourceChecker;
import rogo.sketch.feature.culling.CullingStateManager;

import java.util.*;
import java.util.concurrent.Semaphore;

public class SodiumSectionAsyncUtil {
    private static int ASYNC_FRAME = 0;
    private static OcclusionCuller OCCLUSION_CULLER;
    private static Viewport VIEWPORT;
    private static float SEARCH_DISTANCE;
    private static boolean USE_OCCLUSION_CULLING;
    private static VisibleChunkCollector COLLECTOR;
    private static float SHADOW_SEARCH_DISTANCE;
    private static Viewport SHADOW_VIEWPORT;
    private static boolean SHADOW_USE_OCCLUSION_CULLING;
    private static VisibleChunkCollector SHADOW_COLLECTOR;
    public static boolean RENDERING_ENTITIES;
    private static final Semaphore SHOULD_UPDATE = new Semaphore(0);
    public static boolean NEED_ASYNC_BUILD;
    private static Set<Integer> VISIBLE_SECTIONS = new HashSet<>();
    private static Set<Integer> SWAP_VISIBLE_SECTIONS = new HashSet<>();

    public static void fromSectionManager(Long2ReferenceMap<RenderSection> sections, Level world) {
        SodiumSectionAsyncUtil.OCCLUSION_CULLER = new OcclusionCuller(sections, world);
    }

    public static void asyncSearchRebuildSection() {
        SHOULD_UPDATE.acquireUninterruptibly();
        if (CullingStateManager.enabledShader() && SHADOW_VIEWPORT != null) {
            ASYNC_FRAME++;
            CullingStateManager.USE_OCCLUSION_CULLING = false;
            VisibleChunkCollector shadowCollector = new AsynchronousChunkCollector(ASYNC_FRAME);
            OCCLUSION_CULLER.findVisible(shadowCollector, SHADOW_VIEWPORT, SHADOW_SEARCH_DISTANCE, SHADOW_USE_OCCLUSION_CULLING, ASYNC_FRAME);
            SodiumSectionAsyncUtil.SHADOW_COLLECTOR = shadowCollector;
            CullingStateManager.USE_OCCLUSION_CULLING = true;
        }

        if (VIEWPORT != null) {
            ASYNC_FRAME++;
            SodiumSectionAsyncUtil.SWAP_VISIBLE_SECTIONS = new HashSet<>();
            VisibleChunkCollector collector = new AsynchronousChunkCollector(ASYNC_FRAME);
            OCCLUSION_CULLER.findVisible(collector, VIEWPORT, SEARCH_DISTANCE, USE_OCCLUSION_CULLING, ASYNC_FRAME);
            SodiumSectionAsyncUtil.COLLECTOR = collector;
            SodiumSectionAsyncUtil.VISIBLE_SECTIONS = SWAP_VISIBLE_SECTIONS;

            MeshResource.QUEUE_UPDATE_COUNT++;
            Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildList = SodiumSectionAsyncUtil.COLLECTOR.getRebuildLists();
            for (ArrayDeque<RenderSection> arrayDeque : rebuildList.values()) {
                if (!arrayDeque.isEmpty()) {
                    NEED_ASYNC_BUILD = true;
                    break;
                }
            }
        }
    }

    public static void pauseAsync() {
        SodiumSectionAsyncUtil.COLLECTOR = null;
        SodiumSectionAsyncUtil.SHADOW_COLLECTOR = null;
    }

    public static void update(Viewport viewport, float searchDistance, boolean useOcclusionCulling) {
        if (CullingStateManager.renderingShadowPass()) {
            SodiumSectionAsyncUtil.SHADOW_VIEWPORT = viewport;
            SodiumSectionAsyncUtil.SHADOW_SEARCH_DISTANCE = searchDistance;
            SodiumSectionAsyncUtil.SHADOW_USE_OCCLUSION_CULLING = useOcclusionCulling;
        } else {
            SodiumSectionAsyncUtil.VIEWPORT = viewport;
            SodiumSectionAsyncUtil.SEARCH_DISTANCE = searchDistance;
            SodiumSectionAsyncUtil.USE_OCCLUSION_CULLING = useOcclusionCulling;
        }
    }

    public static boolean isSectionVisible(int x, int y, int z) {
        return VISIBLE_SECTIONS.contains(Objects.hash(x, y, z));
    }

    public static VisibleChunkCollector getChunkCollector() {
        return SodiumSectionAsyncUtil.COLLECTOR;
    }

    public static VisibleChunkCollector getShadowCollector() {
        return SodiumSectionAsyncUtil.SHADOW_COLLECTOR;
    }

    public static void notifyUpdate() {
        if (SHOULD_UPDATE.availablePermits() < 1) {
            SHOULD_UPDATE.release();
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
                SectionPos sectionPos = section.getPosition();

                SWAP_VISIBLE_SECTIONS.add(Objects.hash(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ()));
            }

            ((CollectorAccessor) this).addAsyncToRebuildLists(section);
        }

        @Override
        public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
            if (!RenderSystem.isOnRenderThread()) {
                return super.getRebuildLists();
            }
            if (!sent) {
                sent = true;
            } else {
                return EMPTY_LIST;
            }

            super.getRebuildLists().forEach(((chunkUpdateType, renderSections) -> {
                for (RenderSection section : renderSections) {
                    if (!section.isDisposed() && !((ResourceChecker) section.getRegion()).disposed() && section.getBuildCancellationToken() == null) {
                        try {
                            syncRebuildLists.get(chunkUpdateType).add(section);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }));

            return syncRebuildLists;
        }
    }
}