package rogo.sketch.module.culling;

import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroDefinition;
import rogo.sketch.core.pipeline.module.setting.*;
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
        context.registerSetting(new SettingGroup(
                GROUP_GENERAL,
                id(),
                "sketch_render.config",
                null,
                context.moduleEnabledSettingId(),
                false,
                List.of()));

        context.registerSetting(new FloatSetting(
                DEPTH_UPDATE_DELAY,
                id(),
                "sketch_render.culling_precision",
                "sketch_render.detail.culling_precision",
                GROUP_GENERAL,
                ChangeImpact.UPDATE_UNIFORMS,
                true,
                List.of(),
                4.0f,
                1.0f,
                10.0f,
                SliderSpec.of(1.0, 10.0, 0.5)));

        context.registerSetting(new BooleanSetting(
                AUTO_DISABLE_ASYNC,
                id(),
                "sketch_render.auto_shader_async",
                "sketch_render.detail.auto_shader_async",
                GROUP_GENERAL,
                ChangeImpact.RUNTIME_ONLY,
                true,
                List.of(),
                true));

        context.registerSetting(new BooleanSetting(
                CULL_CHUNK,
                id(),
                "sketch_render.cull_chunk",
                "sketch_render.detail.cull_chunk",
                GROUP_GENERAL,
                ChangeImpact.RECREATE_SESSION_RESOURCES,
                true,
                List.of(),
                true));

        context.registerSetting(new BooleanSetting(
                ASYNC_CHUNK_REBUILD,
                id(),
                "sketch_render.async_chunk_build",
                "sketch_render.detail.async_chunk_build",
                CULL_CHUNK,
                ChangeImpact.RECREATE_SESSION_RESOURCES,
                true,
                List.of(DependencyRule.requiresTrue(CULL_CHUNK)),
                true));

        context.registerSetting(new BooleanSetting(
                CULL_BLOCK_ENTITY,
                id(),
                "sketch_render.cull_block_entity",
                "sketch_render.detail.cull_block_entity",
                GROUP_GENERAL,
                ChangeImpact.RECREATE_SESSION_RESOURCES,
                true,
                List.of(),
                true));

        context.registerSetting(new BooleanSetting(
                CULL_ENTITY,
                id(),
                "sketch_render.cull_entity",
                "sketch_render.detail.cull_entity",
                GROUP_GENERAL,
                ChangeImpact.RECREATE_SESSION_RESOURCES,
                true,
                List.of(),
                true));

        context.registerMacro(new ModuleMacroDefinition(id(), "SKETCH_CULL_CHUNK", ModuleMacroDefinition.MacroKind.FLAG, CULL_CHUNK));
        context.registerMacro(new ModuleMacroDefinition(id(), "SKETCH_CULL_ENTITY", ModuleMacroDefinition.MacroKind.FLAG, CULL_ENTITY));
        context.registerMacro(new ModuleMacroDefinition(id(), "SKETCH_CULL_BLOCK_ENTITY", ModuleMacroDefinition.MacroKind.FLAG, CULL_BLOCK_ENTITY));
    }

    @Override
    public CullingModuleRuntime createRuntime() {
        return new CullingModuleRuntime();
    }
}
