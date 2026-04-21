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
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroRegistry;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.ModuleMetricRegistry;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.shader.uniform.PipelineUniformRegistry;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Entity attach context delegating to the owning module runtime context.
 */
public final class EntityAttachContext implements ModuleRuntimeContext {
    private final ModuleRuntimeContext delegate;

    public EntityAttachContext(ModuleRuntimeContext delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public GraphicsEntitySnapshot snapshot(GraphicsEntityId entityId) {
        return new GraphicsEntitySnapshot(graphicsWorld(), entityId);
    }

    @Override
    public String moduleId() {
        return delegate.moduleId();
    }

    @Override
    public String ownerId() {
        return delegate.ownerId();
    }

    @Override
    public KeyId moduleEnabledSettingId() {
        return delegate.moduleEnabledSettingId();
    }

    @Override
    public GraphicsPipeline<?> pipeline() {
        return delegate.pipeline();
    }

    @Override
    public ExtensionHost extensionHost() {
        return delegate.extensionHost();
    }

    @Override
    public HostEventRegistrar hostEvents() {
        return delegate.hostEvents();
    }

    @Override
    public GraphicsWorld graphicsWorld() {
        return delegate.graphicsWorld();
    }

    @Override
    public GraphicsEntityAssembler graphicsEntityAssembler() {
        return delegate.graphicsEntityAssembler();
    }

    @Override
    public @Nullable PipelineKernel<?> kernel() {
        return delegate.kernel();
    }

    @Override
    public ModuleSettingRegistry settings() {
        return delegate.settings();
    }

    @Override
    public ModuleMetricRegistry metrics() {
        return delegate.metrics();
    }

    @Override
    public ModuleMacroRegistry macros() {
        return delegate.macros();
    }

    @Override
    public PipelineUniformRegistry uniforms() {
        return delegate.uniforms();
    }

    @Override
    public SketchDiagnostics diagnostics() {
        return delegate.diagnostics();
    }

    @Override
    public boolean isModuleEnabled() {
        return delegate.isModuleEnabled();
    }

    @Override
    public void setModuleEnabled(boolean enabled) {
        delegate.setModuleEnabled(enabled);
    }

    @Override
    public void registerUniform(KeyId uniformId, ValueGetter<?> getter) {
        delegate.registerUniform(uniformId, getter);
    }

    @Override
    public void registerMetric(MetricDescriptor descriptor, Supplier<Object> supplier) {
        delegate.registerMetric(descriptor, supplier);
    }

    @Override
    public void registerBuiltInResource(KeyId type, KeyId name, Supplier<? extends ResourceObject> supplier) {
        delegate.registerBuiltInResource(type, name, supplier);
    }

    @Override
    public void unregisterOwnedResources() {
        delegate.unregisterOwnedResources();
    }

    @Override
    public void unregisterOwnedUniforms() {
        delegate.unregisterOwnedUniforms();
    }

    @Override
    public void clearOwnedMetrics() {
        delegate.clearOwnedMetrics();
    }

    @Override
    public void clearOwnedMacros() {
        delegate.clearOwnedMacros();
    }

    @Override
    public void setGlobalFlag(String flagName, boolean enabled) {
        delegate.setGlobalFlag(flagName, enabled);
    }

    @Override
    public void setGlobalMacro(String macroName, String value) {
        delegate.setGlobalMacro(macroName, value);
    }

    @Override
    public <T> FrameResourceHandle<T> registerFrameResourceHandle(FrameResourceHandle<T> handle) {
        return delegate.registerFrameResourceHandle(handle);
    }

    @Override
    public @Nullable <T> FrameResourceHandle<T> frameResourceHandle(KeyId handleId, Class<T> valueType) {
        return delegate.frameResourceHandle(handleId, valueType);
    }

    @Override
    public @Nullable LifecyclePhase phaseForPass(String passId) {
        return delegate.phaseForPass(passId);
    }

    @Override
    public void registerStageSubmitNode(StageSubmitNode node) {
        delegate.registerStageSubmitNode(node);
    }

    @Override
    public GraphicsEntityId registerGraphicsEntity(GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime) {
        return delegate.registerGraphicsEntity(blueprint, lifetime);
    }

    @Override
    public void unregisterOwnedGraphics() {
        delegate.unregisterOwnedGraphics();
    }

    @Override
    public void requestIndirectPlan(KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode) {
        delegate.requestIndirectPlan(stageId, graphicsId, requestMode);
    }

    @Override
    public void clearOwnedIndirectRequests() {
        delegate.clearOwnedIndirectRequests();
    }

    @Override
    public void clearOwnedHostEvents() {
        delegate.clearOwnedHostEvents();
    }

    @Override
    public void rebuildGraphs() {
        delegate.rebuildGraphs();
    }
}
