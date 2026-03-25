package rogo.sketch.vanilla.event;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import rogo.sketch.SketchRender;
import rogo.sketch.compat.sodium.SodiumCompatModuleDescriptor;
import rogo.sketch.core.event.GraphicsPipelineInitEvent;
import rogo.sketch.core.event.RegisterStaticGraphicsEvent;
import rogo.sketch.core.event.RenderFlowRegisterEvent;
import rogo.sketch.core.event.UniformHookRegisterEvent;
import rogo.sketch.core.pipeline.flow.impl.ComputeFlowStrategy;
import rogo.sketch.core.pipeline.flow.impl.FunctionFlowStrategy;
import rogo.sketch.core.pipeline.flow.impl.RasterizationFlowStrategy;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.resource.loader.DrawCallGraphicsLoader;
import rogo.sketch.core.resource.loader.FunctionGraphicsLoader;
import rogo.sketch.event.ProxyEvent;
import rogo.sketch.event.ProxyModEvent;
import rogo.sketch.module.culling.CullingModuleDescriptor;
import rogo.sketch.module.transform.TransformModuleDescriptor;
import rogo.sketch.vanilla.McGraphicsPipeline;
import rogo.sketch.vanilla.MinecraftRenderStages;
import rogo.sketch.vanilla.module.VanillaCullingBridgeModuleDescriptor;
import rogo.sketch.vanilla.module.VanillaModuleDescriptor;
import rogo.sketch.vanilla.module.VanillaTestModuleDescriptor;
import rogo.sketch.vanilla.resource.loader.VanillaTextureLoader;

public class VanillaPipelineEventHandler {
    public static void registerPersistentResource() {
        // Runtime/session-owned resources are now registered by module runtimes.
    }

    public static void onUniformInit(ProxyModEvent<UniformHookRegisterEvent> event) {
        // Uniform registration now comes from runtime/session module registries.
    }

    public static void onPipelineInit(ProxyModEvent<GraphicsPipelineInitEvent> event) {
        GraphicsPipelineInitEvent pipelineInitEvent = event.getWrapped();
        if (!(pipelineInitEvent.getPipeline() instanceof McGraphicsPipeline mcPipeline)) {
            return;
        }

        switch (pipelineInitEvent.getPhase()) {
            case EARLY -> {
                MinecraftRenderStages.registerVanillaStages(mcPipeline);
                PipelineKernel<?> kernel = mcPipeline.kernel();
                kernel.moduleRegistry().registerDescriptor(new VanillaModuleDescriptor());
                kernel.moduleRegistry().registerDescriptor(new TransformModuleDescriptor());
                kernel.moduleRegistry().registerDescriptor(new CullingModuleDescriptor());
                kernel.moduleRegistry().registerDescriptor(new VanillaCullingBridgeModuleDescriptor());
                if (SketchRender.hasSodium()) {
                    kernel.moduleRegistry().registerDescriptor(new SodiumCompatModuleDescriptor());
                }
                if (!FMLEnvironment.production) {
                    kernel.moduleRegistry().registerDescriptor(new VanillaTestModuleDescriptor());
                }
            }
            case NORMAL -> MinecraftRenderStages.registerExtraStages(mcPipeline);
            case LATE -> {
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.TEXTURE, new VanillaTextureLoader());
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.FUNCTION, new FunctionGraphicsLoader(pipelineInitEvent.getPipeline()));
                GraphicsResourceManager.getInstance().registerLoader(ResourceTypes.DRAW_CALL, new DrawCallGraphicsLoader(pipelineInitEvent.getPipeline()));
                if (!mcPipeline.getPendingStages().isEmpty()) {
                    SketchRender.LOGGER.warn("Warning: {} stages are still pending", mcPipeline.getPendingStages().size());
                }
            }
        }
    }

    @SubscribeEvent
    public void onStaticGraphicsRegister(ProxyEvent<RegisterStaticGraphicsEvent> event) {
        // Static graphics registration is now owned by module sessions.
    }

    public static void onBaseRenderFlowRegisterInit(ProxyModEvent<RenderFlowRegisterEvent> event) {
        RenderFlowRegisterEvent registerEvent = event.getWrapped();
        registerEvent.register(new ComputeFlowStrategy());
        registerEvent.register(new RasterizationFlowStrategy());
        registerEvent.register(new FunctionFlowStrategy());
    }
}