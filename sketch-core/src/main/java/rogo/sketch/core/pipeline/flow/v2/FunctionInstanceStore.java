package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FunctionInstanceStore {
    private final Map<FunctionalGraphics, Entry> entries = new LinkedHashMap<>();

    public Entry register(
            FunctionalGraphics graphics,
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

    public void remove(FunctionalGraphics graphics) {
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
        private final FunctionalGraphics graphics;
        private final RenderParameter renderParameter;
        private final KeyId containerType;
        private final long orderHint;
        private RenderSetting renderSetting;
        private CompiledRenderSetting compiledRenderSetting;

        private Entry(
                FunctionalGraphics graphics,
                RenderParameter renderParameter,
                KeyId containerType,
                long orderHint) {
            this.graphics = graphics;
            this.renderParameter = renderParameter;
            this.containerType = containerType;
            this.orderHint = orderHint;
        }

        public FunctionalGraphics graphics() {
            return graphics;
        }

        public KeyId containerType() {
            return containerType;
        }

        public long orderHint() {
            return orderHint;
        }

        public RenderSetting renderSetting() {
            return renderSetting;
        }

        public CompiledRenderSetting compiledRenderSetting() {
            return compiledRenderSetting;
        }

        public void refreshCompiledRenderSetting() {
            this.renderSetting = RenderSetting.fromPartial(
                    renderParameter,
                    rogo.sketch.core.pipeline.PartialRenderSetting.EMPTY);
            this.compiledRenderSetting = RenderSettingCompiler.compile(renderSetting);
        }
    }
}

