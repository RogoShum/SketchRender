package rogo.sketch.compat.sodium;

import rogo.sketch.SketchRender;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.UniformCaptureTiming;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.TerrainMeshResourceSet;

public class SodiumCompatModuleRuntime implements ModuleRuntime {
    public static final KeyId REGION_COUNT_METRIC = KeyId.of("sketch_render", "sodium_region_count");

    @Override
    public String id() {
        return SodiumCompatModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        MeshResource.ensureInitialized();
        TerrainMeshResourceSet terrainResources = MeshResource.resourceSet();
        SodiumRegionMeshRegistry regionRegistry = MeshResource.regionRegistry();
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_section_mesh"),
                terrainResources::meshDataBuffer);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_draw_command"),
                terrainResources::indirectCommands);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "mesh_counter"),
                terrainResources::cullingCounter);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "region_pos"),
                terrainResources::regionIndexBuffer);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "max_element_count"),
                terrainResources::elementCounter);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "persistent_max_element_count"),
                terrainResources::maxElementReadbackBuffer);

        context.registerUniform(KeyId.of("sketch_cullFacing"), ValueGetter.create(() -> SodiumImplOptions.useBlockFaceCulling() ? 1 : 0, Integer.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_translucentSort"), ValueGetter.create(() -> SodiumImplOptions.canApplyTranslucencySorting() ? 1 : 0, Integer.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_renderDistance"), ValueGetter.create(terrainResources::renderDistance, Integer.class, UniformCaptureTiming.FRAME_SYNC));
        context.registerUniform(KeyId.of("sketch_spacePartitionSize"), ValueGetter.create(terrainResources::spacePartitionSize, Integer.class, UniformCaptureTiming.FRAME_SYNC));

        context.registerMetric(new MetricDescriptor(
                REGION_COUNT_METRIC,
                id(),
                MetricKind.COUNT,
                "metric.sodium.region_count",
                null), regionRegistry::regionCount);
    }
}
