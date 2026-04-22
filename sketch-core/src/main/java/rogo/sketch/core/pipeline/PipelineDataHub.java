package rogo.sketch.core.pipeline;

import rogo.sketch.core.packet.GeometryHandleKey;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.FrameDataStore;
import rogo.sketch.core.pipeline.data.GeometryFrameData;
import rogo.sketch.core.pipeline.data.IndirectBufferData;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.data.InstancedOffsetData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;
import rogo.sketch.core.vertex.MeshResidencyPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PipelineDataHub {
    private final Map<PipelineType, GeometryResourceCoordinator> resourceManagers = new LinkedHashMap<>();
    private final Map<PipelineType, MeshResidencyPool> meshResidencyPools = new LinkedHashMap<>();
    private final Map<PipelineType, FrameDataStore> frameDataStores = new LinkedHashMap<>();

    void initializePipeline(PipelineType pipelineType) {
        MeshResidencyPool meshResidencyPool = new MeshResidencyPool("mesh-residency-" + pipelineType.getIdentifier());
        GeometryResourceCoordinator manager = new GeometryResourceCoordinator(meshResidencyPool);
        PipelineDataStore renderStore = new PipelineDataStore();
        PipelineDataStore asyncStore = new PipelineDataStore();

        registerDefaultPipelineData(renderStore);
        registerDefaultPipelineData(asyncStore);

        FrameDataStore frameStore = new FrameDataStore(renderStore, asyncStore);

        resourceManagers.put(pipelineType, manager);
        meshResidencyPools.put(pipelineType, meshResidencyPool);
        frameDataStores.put(pipelineType, frameStore);
    }

    PipelineDataStore pipelineDataStore(PipelineType pipelineType, FrameDataDomain domain) {
        FrameDataStore frameDataStore = frameDataStores.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return frameDataStores.get(pt);
        });
        return frameDataStore.buffer(domain);
    }

    GeometryResourceCoordinator geometryResourceCoordinator(PipelineType pipelineType) {
        return resourceManagers.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return resourceManagers.get(pt);
        });
    }

    MeshResidencyPool meshResidencyPool(PipelineType pipelineType) {
        return meshResidencyPools.computeIfAbsent(pipelineType, pt -> {
            initializePipeline(pt);
            return meshResidencyPools.get(pt);
        });
    }

    Collection<PipelineDataStore> allPipelineDataStores(FrameDataDomain domain) {
        List<PipelineDataStore> stores = new ArrayList<>(frameDataStores.size());
        for (FrameDataStore frameDataStore : frameDataStores.values()) {
            stores.add(frameDataStore.buffer(domain));
        }
        return stores;
    }

    Collection<FrameDataStore> allFrameDataStores() {
        return frameDataStores.values();
    }

    void swapFrameDataStores() {
        for (FrameDataStore dataStore : frameDataStores.values()) {
            dataStore.swap();
        }
    }

    FrameDataStore frameDataStore(PipelineType pipelineType) {
        return frameDataStores.get(pipelineType);
    }

    private void registerDefaultPipelineData(PipelineDataStore dataStore) {
        dataStore.register(KeyId.of("indirect_buffers"), new IndirectBufferData());
        dataStore.register(IndirectPlanData.KEY, new IndirectPlanData());
        dataStore.register(KeyId.of("instanced_offsets"), new InstancedOffsetData());
        dataStore.register(GeometryFrameData.KEY, new GeometryFrameData());
    }
}
