package rogo.sketch.vanilla.module;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import rogo.sketch.core.instance.TransformComputeGraphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.module.runtime.ModuleGraphicsLifetime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntime;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeContext;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.descriptor.ImageFormat;
import rogo.sketch.core.resource.descriptor.ImageUsage;
import rogo.sketch.core.resource.descriptor.RenderTargetResolutionMode;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.descriptor.SamplerFilter;
import rogo.sketch.core.resource.descriptor.SamplerWrap;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.TransformModule;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.resource.BuildInRTTexture;
import rogo.sketch.vanilla.resource.BuildInRenderTarget;

import java.util.List;
import java.util.Set;

public class VanillaModuleRuntime implements ModuleRuntime {
    public static final BuildInRTTexture MAIN_COLOR = new BuildInRTTexture(
            KeyId.of("minecraft", "main_color"),
            () -> Minecraft.getInstance().getMainRenderTarget(),
            ImageFormat.RGBA8_UNORM,
            Set.of(ImageUsage.SAMPLED, ImageUsage.COLOR_ATTACHMENT, ImageUsage.TRANSFER_SRC, ImageUsage.TRANSFER_DST),
            false,
            SamplerFilter.NEAREST,
            SamplerFilter.NEAREST,
            SamplerWrap.CLAMP_TO_EDGE,
            SamplerWrap.CLAMP_TO_EDGE);
    public static final BuildInRTTexture MAIN_DEPTH = new BuildInRTTexture(
            KeyId.of("minecraft", "main_depth"),
            () -> Minecraft.getInstance().getMainRenderTarget(),
            ImageFormat.D32_FLOAT,
            Set.of(ImageUsage.SAMPLED, ImageUsage.DEPTH_ATTACHMENT, ImageUsage.TRANSFER_SRC, ImageUsage.TRANSFER_DST),
            true,
            SamplerFilter.NEAREST,
            SamplerFilter.NEAREST,
            SamplerWrap.CLAMP_TO_EDGE,
            SamplerWrap.CLAMP_TO_EDGE);
    public static final BuildInRenderTarget MAIN_TARGET = new BuildInRenderTarget(
            () -> Minecraft.getInstance().getMainRenderTarget().frameBufferId,
            KeyId.of("minecraft:main_target"),
            new ResolvedRenderTargetSpec(
                    KeyId.of("minecraft:main_target"),
                    RenderTargetResolutionMode.SCREEN_SIZE,
                    1,
                    1,
                    1.0f,
                    1.0f,
                    List.of(KeyId.of("minecraft", "main_color")),
                    KeyId.of("minecraft", "main_depth"),
                    null));

    @Override
    public String id() {
        return VanillaModuleDescriptor.MODULE_ID;
    }

    @Override
    public void onKernelInit(ModuleRuntimeContext context) {
        context.registerBuiltInResource(ResourceTypes.TEXTURE, KeyId.of("minecraft", "main_color"), () -> MAIN_COLOR);
        context.registerBuiltInResource(ResourceTypes.TEXTURE, KeyId.of("minecraft", "main_depth"), () -> MAIN_DEPTH);
        context.registerBuiltInResource(ResourceTypes.RENDER_TARGET, KeyId.of("minecraft:main_target"), () -> MAIN_TARGET);

        context.registerUniform(KeyId.of("viewMatrix"), ValueGetter.create((instance) -> ((RenderContext) instance).viewMatrix(), Matrix4f.class, RenderContext.class));
        context.registerUniform(KeyId.of("modelMatrix"), ValueGetter.create((instance) -> ((RenderContext) instance).modelMatrix(), Matrix4f.class, RenderContext.class));
        context.registerUniform(KeyId.of("projectionMatrix"), ValueGetter.create((instance) -> ((RenderContext) instance).projectionMatrix(), Matrix4f.class, RenderContext.class));
        context.registerUniform(KeyId.of("partialTicks"), ValueGetter.create((instance) -> ((RenderContext) instance).partialTicks(), Float.class, RenderContext.class));
        context.registerUniform(KeyId.of("renderTick"), ValueGetter.create((instance) -> ((RenderContext) instance).renderTick(), Integer.class, RenderContext.class));
        context.registerUniform(KeyId.of("windowWidth"), ValueGetter.create((instance) -> ((RenderContext) instance).windowWidth(), Integer.class, RenderContext.class));
        context.registerUniform(KeyId.of("windowHeight"), ValueGetter.create((instance) -> ((RenderContext) instance).windowHeight(), Integer.class, RenderContext.class));
        context.registerUniform(KeyId.of("windowSize"), ValueGetter.create((instance) -> {
            RenderContext renderContext = (RenderContext) instance;
            return new Vector2f(renderContext.windowWidth(), renderContext.windowHeight());
        }, Vector2f.class, RenderContext.class));
    }

    @Override
    public ModuleSession createSession() {
        return new ModuleSession() {
            @Override
            public String id() {
                return "vanilla_session";
            }

            @Override
            public void onWorldEnter(ModuleSessionContext context) {
                TransformModule transformModule = context.pipeline().getModuleByName("transform");
                if (transformModule == null || transformModule.matrixManager() == null) {
                    return;
                }
                context.registerCompute(
                        MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier(),
                        new TransformComputeGraphics(
                                KeyId.of("sketch_render", "transform_matrix_compute_sync"),
                                KeyId.of("sketch_render", "transform_matrix_sync"),
                                true,
                                transformModule.matrixManager()).setPriority(98),
                        ModuleGraphicsLifetime.SESSION);
                context.registerCompute(
                        MinecraftRenderStages.PREPARE_FRUSTUM.getIdentifier(),
                        new TransformComputeGraphics(
                                KeyId.of("sketch_render", "transform_matrix_compute_async"),
                                KeyId.of("sketch_render", "transform_matrix_async"),
                                false,
                                transformModule.matrixManager()).setPriority(99),
                        ModuleGraphicsLifetime.SESSION);
            }
        };
    }
}

