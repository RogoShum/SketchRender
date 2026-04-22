package rogo.sketch.core.pipeline.module.runtime;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.extension.ExtensionHost;
import rogo.sketch.core.extension.event.HostEventRegistrar;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroRegistry;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.ModuleMetricRegistry;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.shader.uniform.PipelineUniformRegistry;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

public interface ModuleRuntimeContext {
    String moduleId();

    String ownerId();

    KeyId moduleEnabledSettingId();

    GraphicsPipeline<?> pipeline();

    default GraphicsResourceManager resourceManager() {
        return pipeline().resourceManager();
    }

    ExtensionHost extensionHost();

    HostEventRegistrar hostEvents();

    GraphicsWorld graphicsWorld();

    GraphicsEntityAssembler graphicsEntityAssembler();

    @Nullable PipelineKernel<?> kernel();

    ModuleSettingRegistry settings();

    ModuleMetricRegistry metrics();

    ModuleMacroRegistry macros();

    PipelineUniformRegistry uniforms();

    SketchDiagnostics diagnostics();

    boolean isModuleEnabled();

    void setModuleEnabled(boolean enabled);

    void registerUniform(KeyId uniformId, ValueGetter<?> getter);

    void registerMetric(MetricDescriptor descriptor, Supplier<Object> supplier);

    void registerBuiltInResource(KeyId type, KeyId name, Supplier<? extends ResourceObject> supplier);

    void unregisterOwnedResources();

    void unregisterOwnedUniforms();

    void clearOwnedMetrics();

    void clearOwnedMacros();

    void setGlobalFlag(String flagName, boolean enabled);

    void setGlobalMacro(String macroName, String value);

    <T> FrameResourceHandle<T> registerFrameResourceHandle(FrameResourceHandle<T> handle);

    @Nullable
    <T> FrameResourceHandle<T> frameResourceHandle(KeyId handleId, Class<T> valueType);

    @Nullable
    LifecyclePhase phaseForPass(String passId);

    void registerStageSubmitNode(StageSubmitNode node);

    GraphicsEntityId registerGraphicsEntity(GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime);

    void unregisterOwnedGraphics();

    void requestIndirectPlan(KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode);

    default void requestIndirectPlan(KeyId stageId, KeyId graphicsId) {
        requestIndirectPlan(stageId, graphicsId, IndirectPlanRequest.RequestMode.INDIRECT);
    }

    default void requestGpuCullPlan(KeyId stageId, KeyId graphicsId) {
        requestIndirectPlan(stageId, graphicsId, IndirectPlanRequest.RequestMode.GPU_CULL);
    }

    void clearOwnedIndirectRequests();

    void clearOwnedHostEvents();

    void rebuildGraphs();
}

