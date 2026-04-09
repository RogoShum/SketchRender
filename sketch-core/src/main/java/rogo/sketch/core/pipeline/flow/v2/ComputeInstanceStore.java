package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComputeInstanceStore {
    private final Map<DispatchableGraphics, Entry> entries = new LinkedHashMap<>();

    public Entry register(
            DispatchableGraphics graphics,
            RenderParameter renderParameter,
            KeyId containerType,
            long orderHint) {
        if (graphics == null) {
            return null;
        }
        Entry entry = new Entry(graphics, renderParameter, containerType, orderHint);
        entry.refreshCompiledRenderSetting();
        entries.put(graphics, entry);
        return entry;
    }

    public Entry get(DispatchableGraphics graphics) {
        return entries.get(graphics);
    }

    public void remove(DispatchableGraphics graphics) {
        entries.remove(graphics);
    }

    public Collection<Entry> records() {
        return entries.values();
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static final class Entry {
        private final DispatchableGraphics graphics;
        private final RenderParameter renderParameter;
        private final KeyId containerType;
        private final long orderHint;
        private final ComputeDispatchCommand dispatchCommand;
        private CompiledRenderSetting compiledRenderSetting;
        private long descriptorVersion = Long.MIN_VALUE;

        private Entry(
                DispatchableGraphics graphics,
                RenderParameter renderParameter,
                KeyId containerType,
                long orderHint) {
            this.graphics = graphics;
            this.renderParameter = renderParameter;
            this.containerType = containerType;
            this.orderHint = orderHint;
            this.dispatchCommand = graphics.getDispatchOperation();
        }

        public DispatchableGraphics graphics() {
            return graphics;
        }

        public KeyId containerType() {
            return containerType;
        }

        public long orderHint() {
            return orderHint;
        }

        public ComputeDispatchCommand dispatchCommand() {
            return dispatchCommand;
        }

        public CompiledRenderSetting compiledRenderSetting() {
            return compiledRenderSetting;
        }

        public void refreshCompiledRenderSetting() {
            refreshCompiledRenderSetting(false);
        }

        public void refreshCompiledRenderSetting(boolean force) {
            if (!force
                    && compiledRenderSetting != null
                    && DescriptorStability.STABLE.equals(graphics.descriptorStability())) {
                return;
            }

            long nextVersion = DescriptorStability.DYNAMIC.equals(graphics.descriptorStability())
                    ? graphics.descriptorVersion()
                    : 0L;
            if (!force
                    && compiledRenderSetting != null
                    && descriptorVersion == nextVersion) {
                return;
            }

            this.compiledRenderSetting = graphics.buildRenderDescriptor(renderParameter);
            this.descriptorVersion = nextVersion;
        }
    }
}

