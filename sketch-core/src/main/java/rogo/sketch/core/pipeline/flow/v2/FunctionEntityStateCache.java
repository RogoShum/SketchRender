package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.RenderSetting;

import java.util.LinkedHashMap;
import java.util.Map;

final class FunctionEntityStateCache {
    private final Map<GraphicsEntityId, Entry> entries = new LinkedHashMap<>();

    Entry upsert(GraphicsEntityId entityId) {
        return entries.computeIfAbsent(entityId, ignored -> new Entry());
    }

    void retainOnly(Iterable<GraphicsEntityId> entityIds) {
        Map<GraphicsEntityId, Entry> retained = new LinkedHashMap<>();
        for (GraphicsEntityId entityId : entityIds) {
            Entry entry = entries.get(entityId);
            if (entry != null) {
                retained.put(entityId, entry);
            }
        }
        entries.clear();
        entries.putAll(retained);
    }

    static final class Entry {
        private RenderSetting renderSetting;
        private CompiledRenderSetting compiledRenderSetting;

        RenderSetting renderSetting() {
            return renderSetting;
        }

        void setRenderSetting(RenderSetting renderSetting) {
            this.renderSetting = renderSetting;
        }

        CompiledRenderSetting compiledRenderSetting() {
            return compiledRenderSetting;
        }

        void setCompiledRenderSetting(CompiledRenderSetting compiledRenderSetting) {
            this.compiledRenderSetting = compiledRenderSetting;
        }
    }
}
