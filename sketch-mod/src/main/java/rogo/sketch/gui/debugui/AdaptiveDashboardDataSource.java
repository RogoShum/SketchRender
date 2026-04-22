package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import rogo.sketch.Config;
import rogo.sketch.core.backend.GpuHandle;
import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.debugger.DashboardControlAccessor;
import rogo.sketch.core.debugger.DashboardDataSource;
import rogo.sketch.core.debugger.DashboardMetricCard;
import rogo.sketch.core.debugger.DashboardTexturePreview;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.kernel.FrameCaptureSnapshot;
import rogo.sketch.core.ui.texture.UiTextureRef;
import rogo.sketch.core.ui.texture.UiTextureUv;
import rogo.sketch.feature.culling.MinecraftCullingDebugState;
import rogo.sketch.feature.culling.MinecraftHiZState;
import rogo.sketch.vanilla.PipelineUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AdaptiveDashboardDataSource implements DashboardDataSource {
    private final ModuleRuntimeHost runtimeHost;
    private final AdaptiveDashboardControlAccessor controlAccessor = new AdaptiveDashboardControlAccessor();
    private final Supplier<List<Double>> frameHistorySupplier;

    public AdaptiveDashboardDataSource(ModuleRuntimeHost runtimeHost, Supplier<List<Double>> frameHistorySupplier) {
        this.runtimeHost = runtimeHost;
        this.frameHistorySupplier = frameHistorySupplier;
    }

    @Override
    public ModuleRuntimeHost runtimeHost() {
        return runtimeHost;
    }

    @Override
    public DashboardControlAccessor controlAccessor() {
        return controlAccessor;
    }

    @Override
    public List<DashboardMetricCard> extraMetricCards() {
        List<DashboardMetricCard> cards = new ArrayList<>();
        MinecraftCullingDebugState cullingDebugState = MinecraftCullingDebugState.getInstance();
        MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
        cards.add(new DashboardMetricCard("fps", "debug.dashboard.metric.fps", String.valueOf(Minecraft.getInstance().getFps()), "fps", 0xFF10B981));
        cards.add(new DashboardMetricCard("cull-entity", "sketch_render.cull_entity", Config.getCullEntity() ? "On" : "Off", "", 0xFF10B981));
        cards.add(new DashboardMetricCard("cull-block", "sketch_render.cull_block_entity", Config.getCullBlockEntity() ? "On" : "Off", "", 0xFF3B82F6));
        cards.add(new DashboardMetricCard("cull-chunk", "sketch_render.cull_chunk", Config.getCullChunk() ? "On" : "Off", "", 0xFFF59E0B));
        cards.add(new DashboardMetricCard("hiz-preview", "debug.dashboard.hiz.preview",
                cullingDebugState.checkingTexture() ? "On" : "Off",
                hiZState.depthBufferTargetWidth() + "x" + hiZState.depthBufferTargetHeight(),
                cullingDebugState.checkingTexture() ? 0xFF34D399 : 0xFF64748B));
        cards.add(new DashboardMetricCard("hiz-epoch", "debug.dashboard.hiz.epoch",
                String.valueOf(hiZState.completedEpoch()), "", 0xFF60A5FA));
        return cards;
    }

    @Override
    public List<DashboardTexturePreview> texturePreviews() {
        MinecraftCullingDebugState cullingDebugState = MinecraftCullingDebugState.getInstance();
        MinecraftHiZState hiZState = MinecraftHiZState.getInstance();
        int handle = hiZState.depthBufferTargetHandle();
        if (!cullingDebugState.checkingTexture() || handle == 0) {
            return List.of();
        }
        int width = hiZState.depthBufferTargetWidth();
        int height = hiZState.depthBufferTargetHeight();
        return List.of(new DashboardTexturePreview(
                "hiz-depth",
                "debug.dashboard.hiz.texture",
                UiTextureRef.gpuHandle(GpuHandle.ofGl(handle)),
                UiTextureUv.FLIP_Y,
                width,
                height,
                "epoch=" + hiZState.completedEpoch() + ", size=" + width + "x" + height,
                0xFF34D399));
    }

    @Override
    public List<Double> frameTimeHistory() {
        return frameHistorySupplier.get();
    }

    @Override
    public MemoryDebugSnapshot memorySnapshot() {
        return PipelineUtil.pipeline() != null ? PipelineUtil.pipeline().memoryDebugSnapshot() : MemoryDebugSnapshot.empty();
    }

    @Override
    public void requestFrameCapture() {
        if (PipelineUtil.pipeline() != null) {
            PipelineUtil.pipeline().requestFrameCapture();
        }
    }

    @Override
    public FrameCaptureSnapshot latestFrameCaptureSnapshot() {
        return PipelineUtil.pipeline() != null ? PipelineUtil.pipeline().latestFrameCaptureSnapshot() : FrameCaptureSnapshot.empty();
    }
}
