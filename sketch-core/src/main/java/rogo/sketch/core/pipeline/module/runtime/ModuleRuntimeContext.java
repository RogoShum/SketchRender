package rogo.sketch.core.pipeline.module.runtime;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroRegistry;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.ModuleMetricRegistry;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.uniform.PipelineUniformRegistry;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

public interface ModuleRuntimeContext {
    String moduleId();

    String ownerId();

    KeyId moduleEnabledSettingId();

    GraphicsPipeline<?> pipeline();

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

    void registerCompute(KeyId stageId, Graphics graphics, ModuleGraphicsLifetime lifetime);

    void registerFunction(KeyId stageId, Graphics graphics, ModuleGraphicsLifetime lifetime);

    void registerGraphics(KeyId stageId, Graphics graphics, RenderParameter renderParameter, ModuleGraphicsLifetime lifetime);

    void registerGraphics(KeyId stageId, Graphics graphics, RenderParameter renderParameter, PipelineType pipelineType, ModuleGraphicsLifetime lifetime);

    void registerGraphics(
            KeyId stageId,
            Graphics graphics,
            RenderParameter renderParameter,
            PipelineType pipelineType,
            KeyId containerType,
            Supplier<? extends GraphicsContainer<? extends RenderContext>> containerSupplier,
            ModuleGraphicsLifetime lifetime);

    void unregisterOwnedGraphics();

    void requestIndirectPlan(KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode);

    default void requestIndirectPlan(KeyId stageId, KeyId graphicsId) {
        requestIndirectPlan(stageId, graphicsId, IndirectPlanRequest.RequestMode.INDIRECT);
    }

    default void requestGpuCullPlan(KeyId stageId, KeyId graphicsId) {
        requestIndirectPlan(stageId, graphicsId, IndirectPlanRequest.RequestMode.GPU_CULL);
    }

    void clearOwnedIndirectRequests();

    void rebuildGraphs();
}

