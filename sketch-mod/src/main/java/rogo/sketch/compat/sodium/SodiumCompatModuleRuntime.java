package rogo.sketch.compat.sodium;

import rogo.sketch.SketchRender;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;

public class SodiumCompatModuleRuntime implements ModuleRuntime {
    public static final KeyId REGION_COUNT_METRIC = KeyId.of("sketch_render", "sodium_region_count");

    @Override
    public String id() {
        return SodiumCompatModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_section_mesh"),
                () -> MeshResource.MESH_MANAGER.meshDataBuffer());
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "chunk_draw_command"),
                () -> MeshResource.COMMAND_BUFFER);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "mesh_counter"),
                () -> MeshResource.BATCH_COUNTER);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "region_pos"),
                () -> MeshResource.REGION_INDEX_BUFFER);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "max_element_count"),
                () -> MeshResource.MAX_ELEMENT_BUFFER);
        context.registerBuiltInResource(ResourceTypes.SHADER_STORAGE_BUFFER,
                KeyId.of(SketchRender.MOD_ID, "persistent_max_element_count"),
                () -> MeshResource.PERSISTENT_MAX_ELEMENT_BUFFER);

        context.registerUniform(KeyId.of("sketch_cullFacing"), ValueGetter.create(() -> SodiumImplOptions.useBlockFaceCulling() ? 1 : 0, Integer.class));
        context.registerUniform(KeyId.of("sketch_translucentSort"), ValueGetter.create(() -> SodiumImplOptions.canApplyTranslucencySorting() ? 1 : 0, Integer.class));
        context.registerUniform(KeyId.of("sketch_renderDistance"), ValueGetter.create(MeshResource::getRenderDistance, Integer.class));
        context.registerUniform(KeyId.of("sketch_spacePartitionSize"), ValueGetter.create(MeshResource::getSpacePartitionSize, Integer.class));

        context.registerMetric(new MetricDescriptor(
                REGION_COUNT_METRIC,
                id(),
                MetricKind.COUNT,
                "metric.sodium.region_count",
                null), MeshResource.MESH_MANAGER::size);
    }
}
