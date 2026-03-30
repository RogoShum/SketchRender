package rogo.sketch.module.culling;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.pipeline.module.setting.DependencyRule;
import rogo.sketch.core.util.KeyId;

import java.util.List;

public class CullingModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "culling";

    public static final KeyId GROUP_GENERAL = KeyId.of("sketch_render", "culling_general");
    public static final KeyId CULL_ENTITY = KeyId.of("sketch_render", "cull_entity");
    public static final KeyId CULL_BLOCK_ENTITY = KeyId.of("sketch_render", "cull_block_entity");
    public static final KeyId CULL_CHUNK = KeyId.of("sketch_render", "cull_chunk");
    public static final KeyId ASYNC_CHUNK_REBUILD = KeyId.of("sketch_render", "async_chunk_rebuild");
    public static final KeyId AUTO_DISABLE_ASYNC = KeyId.of("sketch_render", "auto_disable_async");
    public static final KeyId DEPTH_UPDATE_DELAY = KeyId.of("sketch_render", "culling_precision");

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        var settings = context.settingsDsl();
        var macros = context.macrosDsl();

        settings.group(GROUP_GENERAL, "sketch_render.config")
                .parent(context.moduleEnabledSettingId())
                .hiddenInGui()
                .register();

        settings.floating(DEPTH_UPDATE_DELAY, "sketch_render.culling_precision")
                .detail("sketch_render.detail.culling_precision")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.UPDATE_UNIFORMS)
                .defaultValue(4.0f)
                .slider(1.0f, 10.0f, 0.5f, "%.1f")
                .register();

        settings.bool(AUTO_DISABLE_ASYNC, "sketch_render.auto_shader_async")
                .detail("sketch_render.detail.auto_shader_async")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RUNTIME_ONLY)
                .defaultValue(true)
                .register();

        settings.bool(CULL_CHUNK, "sketch_render.cull_chunk")
                .detail("sketch_render.detail.cull_chunk")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .defaultValue(true)
                .register();

        settings.bool(ASYNC_CHUNK_REBUILD, "sketch_render.async_chunk_build")
                .detail("sketch_render.detail.async_chunk_build")
                .parent(CULL_CHUNK)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .dependency(DependencyRule.requiresTrue(CULL_CHUNK))
                .defaultValue(true)
                .register();

        settings.bool(CULL_BLOCK_ENTITY, "sketch_render.cull_block_entity")
                .detail("sketch_render.detail.cull_block_entity")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .defaultValue(true)
                .register();

        settings.bool(CULL_ENTITY, "sketch_render.cull_entity")
                .detail("sketch_render.detail.cull_entity")
                .parent(GROUP_GENERAL)
                .impact(ChangeImpact.RECREATE_SESSION_RESOURCES)
                .defaultValue(true)
                .register();

        macros.flag("SKETCH_CULL_CHUNK")
                .setting(CULL_CHUNK)
                .displayKey("sketch_render.cull_chunk")
                .detail("sketch_render.detail.cull_chunk")
                .register();
        macros.flag("SKETCH_CULL_ENTITY")
                .setting(CULL_ENTITY)
                .displayKey("sketch_render.cull_entity")
                .detail("sketch_render.detail.cull_entity")
                .register();
        macros.flag("SKETCH_CULL_BLOCK_ENTITY")
                .setting(CULL_BLOCK_ENTITY)
                .displayKey("sketch_render.cull_block_entity")
                .detail("sketch_render.detail.cull_block_entity")
                .register();
    }

    @Override
    public CullingModuleRuntime createRuntime() {
        return new CullingModuleRuntime();
    }
}
