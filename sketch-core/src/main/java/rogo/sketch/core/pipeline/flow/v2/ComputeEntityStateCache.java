package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.pipeline.CompiledRenderSetting;

import java.util.LinkedHashMap;
import java.util.Map;

final class ComputeEntityStateCache {
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
        private CompiledRenderSetting compiledRenderSetting;
        private long descriptorVersion = Long.MIN_VALUE;

        CompiledRenderSetting compiledRenderSetting() {
            return compiledRenderSetting;
        }

        void setCompiledRenderSetting(CompiledRenderSetting compiledRenderSetting) {
            this.compiledRenderSetting = compiledRenderSetting;
        }

        long descriptorVersion() {
            return descriptorVersion;
        }

        void setDescriptorVersion(long descriptorVersion) {
            this.descriptorVersion = descriptorVersion;
        }
    }
}
