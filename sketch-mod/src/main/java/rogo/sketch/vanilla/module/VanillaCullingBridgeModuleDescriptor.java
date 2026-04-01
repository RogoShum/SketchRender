package rogo.sketch.vanilla.module;

import net.minecraftforge.fml.loading.FMLEnvironment;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.setting.BooleanSetting;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.pipeline.module.setting.SettingGroup;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.CullingModuleDescriptor;

import java.util.List;

public class VanillaCullingBridgeModuleDescriptor implements ModuleDescriptor {
    public static final String MODULE_ID = "vanilla_culling_bridge";
    public static final KeyId DEBUG_GROUP = KeyId.of("sketch_render", "vanilla_culling_debug_group");
    public static final KeyId DEBUG_CULL = KeyId.of("sketch_render", "vanilla_culling_debug_geometry");
    public static final KeyId DEBUG_TEXTURE = KeyId.of("sketch_render", "vanilla_culling_debug_texture");

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 220;
    }

    @Override
    public void describe(ModuleDescriptorContext context) {
        context.registerSetting(new SettingGroup(
                DEBUG_GROUP,
                CullingModuleDescriptor.MODULE_ID,
                "debug.dashboard.group.culling_debug",
                null,
                CullingModuleDescriptor.GROUP_GENERAL,
                true,
                () -> !FMLEnvironment.production,
                List.of()));

        context.registerSetting(new BooleanSetting(
                DEBUG_CULL,
                CullingModuleDescriptor.MODULE_ID,
                "debug.dashboard.check_cull",
                "sketch_render.detail.debug",
                DEBUG_GROUP,
                ChangeImpact.RUNTIME_ONLY,
                true,
                () -> !FMLEnvironment.production,
                List.of(),
                false));

        context.registerSetting(new BooleanSetting(
                DEBUG_TEXTURE,
                CullingModuleDescriptor.MODULE_ID,
                "debug.dashboard.check_texture",
                "sketch_render.detail.check_texture",
                DEBUG_GROUP,
                ChangeImpact.RUNTIME_ONLY,
                true,
                () -> !FMLEnvironment.production,
                List.of(),
                false));
    }

    @Override
    public ModuleRuntime createRuntime() {
        return new VanillaCullingBridgeModuleRuntime();
    }
}
