package rogo.sketch.gui.debugui;

import net.minecraft.client.Minecraft;
import rogo.sketch.Config;
import rogo.sketch.core.debugger.DashboardControlAccessor;
import rogo.sketch.core.debugger.DashboardDataSource;
import rogo.sketch.core.debugger.DashboardMetricCard;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;

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
        cards.add(new DashboardMetricCard("fps", "debug.dashboard.metric.fps", String.valueOf(Minecraft.getInstance().getFps()), "fps", 0xFF10B981));
        cards.add(new DashboardMetricCard("cull-entity", "sketch_render.cull_entity", Config.getCullEntity() ? "On" : "Off", "", 0xFF10B981));
        cards.add(new DashboardMetricCard("cull-block", "sketch_render.cull_block_entity", Config.getCullBlockEntity() ? "On" : "Off", "", 0xFF3B82F6));
        cards.add(new DashboardMetricCard("cull-chunk", "sketch_render.cull_chunk", Config.getCullChunk() ? "On" : "Off", "", 0xFFF59E0B));
        return cards;
    }

    @Override
    public List<Double> frameTimeHistory() {
        return frameHistorySupplier.get();
    }
}
