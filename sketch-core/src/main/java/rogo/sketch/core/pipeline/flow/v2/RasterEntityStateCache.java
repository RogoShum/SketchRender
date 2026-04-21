package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;

import java.util.LinkedHashMap;
import java.util.Map;

final class RasterEntityStateCache {
    private final Map<GraphicsEntityId, Entry> entries = new LinkedHashMap<>();

    Entry get(GraphicsEntityId entityId) {
        return entries.get(entityId);
    }

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
        private long geometryVersion = Long.MIN_VALUE;
        private long boundsVersion = Long.MIN_VALUE;
        private GeometryTraitsRef geometryTraitsRef;
        private VisibilityMetadata visibilityMetadata;

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

        long geometryVersion() {
            return geometryVersion;
        }

        void setGeometryVersion(long geometryVersion) {
            this.geometryVersion = geometryVersion;
        }

        long boundsVersion() {
            return boundsVersion;
        }

        void setBoundsVersion(long boundsVersion) {
            this.boundsVersion = boundsVersion;
        }

        GeometryTraitsRef geometryTraitsRef() {
            return geometryTraitsRef;
        }

        void setGeometryTraitsRef(GeometryTraitsRef geometryTraitsRef) {
            this.geometryTraitsRef = geometryTraitsRef;
        }

        VisibilityMetadata visibilityMetadata() {
            return visibilityMetadata;
        }

        void setVisibilityMetadata(VisibilityMetadata visibilityMetadata) {
            this.visibilityMetadata = visibilityMetadata;
        }

        ExecutionKey stateKey() {
            return compiledRenderSetting != null ? compiledRenderSetting.pipelineStateKey() : null;
        }
    }
}
