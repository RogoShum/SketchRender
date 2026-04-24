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
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.PipelineKernel;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptor;
import rogo.sketch.core.pipeline.module.descriptor.ModuleDescriptorContext;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.data.FrameDataDomain;
import rogo.sketch.core.pipeline.data.IndirectPlanData;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroDefinition;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroRegistry;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.ModuleMetricRegistry;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.session.ModuleSession;
import rogo.sketch.core.pipeline.module.session.ModuleSessionContext;
import rogo.sketch.core.pipeline.module.setting.BooleanSetting;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingChangeEvent;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.pipeline.graph.RenderGraphBuilder;
import rogo.sketch.core.pipeline.graph.TickGraphBuilder;
import rogo.sketch.core.pipeline.kernel.FrameResourceHandle;
import rogo.sketch.core.pipeline.kernel.GraphSnapshot;
import rogo.sketch.core.pipeline.kernel.LifecyclePhase;
import rogo.sketch.core.pipeline.kernel.ModulePassDefinition;
import rogo.sketch.core.pipeline.indirect.IndirectPlanRequest;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceScope;
import rogo.sketch.core.shader.uniform.PipelineUniformRegistry;
import rogo.sketch.core.shader.uniform.UniformHookRegistry;
import rogo.sketch.core.shader.uniform.ValueGetter;
import rogo.sketch.core.pipeline.submit.StageSubmitNode;
import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.function.Supplier;

/**
 * Central descriptor/runtime/session host for the new module system.
 */
public class ModuleRuntimeHost {
    private final GraphicsPipeline<?> pipeline;
    private final ModuleSettingRegistry settingRegistry = new ModuleSettingRegistry();
    private final ModuleMetricRegistry metricRegistry = new ModuleMetricRegistry();
    private final ModuleMacroRegistry macroRegistry = new ModuleMacroRegistry();
    private final PipelineUniformRegistry uniformRegistry = new PipelineUniformRegistry();
    private final Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, ModuleRecord> records = new LinkedHashMap<>();
    private final ModuleSubscriptionIndex subscriptionIndex = new ModuleSubscriptionIndex();
    private final Map<String, Set<ManagedIndirectRequest>> ownedIndirectRequests = new LinkedHashMap<>();
    private final Map<String, Map<KeyId, FrameResourceHandle<?>>> moduleFrameHandles = new LinkedHashMap<>();
    private final Map<KeyId, MetricDescriptor> descriptorMetrics = new LinkedHashMap<>();
    private PipelineKernel<?> kernel;
    private boolean processInitialized = false;
    private boolean kernelInitialized = false;
    private boolean worldActive = false;

    public ModuleRuntimeHost(GraphicsPipeline<?> pipeline) {
        this.pipeline = pipeline;
        UniformHookRegistry.getInstance().setRuntimeRegistry(uniformRegistry);
    }

    public void registerDescriptor(ModuleDescriptor descriptor) {
        if (processInitialized) {
            throw new IllegalStateException("Cannot register module descriptors after process initialization");
        }
        if (descriptors.containsKey(descriptor.id())) {
            throw new IllegalStateException("Duplicate module descriptor id: " + descriptor.id());
        }
        descriptors.put(descriptor.id(), descriptor);
    }

    public void processInitialize() {
        if (processInitialized) {
            return;
        }

        List<ModuleDescriptor> sorted = new ArrayList<>(descriptors.values());
        sorted.sort(Comparator.comparingInt(ModuleDescriptor::priority));

        for (ModuleDescriptor descriptor : sorted) {
            ensureModuleEnabledSetting(descriptor);
            descriptor.describe(new DescriptorContextImpl(descriptor.id()));
        }

        for (ModuleDescriptor descriptor : sorted) {
            ModuleRuntime runtime = descriptor.createRuntime();
            ModuleRecord record = new ModuleRecord(descriptor, runtime);
            records.put(descriptor.id(), record);
            runtime.onProcessInit(runtimeContext(record));
        }

        processInitialized = true;
    }

    public void initializeKernel(PipelineKernel<?> kernel) {
        this.kernel = kernel;
        if (kernelInitialized) {
            return;
        }

        for (ModuleRecord record : records.values()) {
            record.runtime.onKernelInit(runtimeContext(record));
            refreshEntitySubscriptions(record);
            if (isModuleEnabled(record.descriptor.id())) {
                record.runtime.onEnable(runtimeContext(record));
                scanExistingEntities(record);
            }
        }

        kernelInitialized = true;
    }

    public void enterWorld() {
        if (worldActive) {
            return;
        }

        worldActive = true;
        for (ModuleRecord record : records.values()) {
            record.session = record.runtime.createSession();
            if (record.session == null || record.session == ModuleSession.NOOP) {
                continue;
            }

            record.session.onWorldEnter(sessionContext(record));
            if (isModuleEnabled(record.descriptor.id())) {
                record.session.onEnable(sessionContext(record));
            }
        }
        for (ModuleRecord record : records.values()) {
            if (isModuleEnabled(record.descriptor.id())) {
                scanExistingEntities(record);
            }
        }
    }

    public void leaveWorld() {
        if (!worldActive) {
            return;
        }

        for (ModuleRecord record : records.values()) {
            if (record.session == null || record.session == ModuleSession.NOOP) {
                continue;
            }

            ModuleSessionContext context = sessionContext(record);
            if (isModuleEnabled(record.descriptor.id())) {
                record.session.onDisable(context);
            }
            record.session.onWorldLeave(context);
            record.session.close();
            clearOwnedState(sessionOwnerId(record.descriptor.id()));
            record.session = null;
        }
        worldActive = false;
    }

    public void onResourceReload() {
        for (ModuleRecord record : records.values()) {
            record.runtime.onResourceReload(runtimeContext(record));
            if (record.session != null && record.session != ModuleSession.NOOP) {
                record.session.onResourceReload(sessionContext(record));
            }
        }
    }

    public void shutdown() {
        leaveWorld();
        for (ModuleRecord record : records.values()) {
            ModuleRuntimeContext context = runtimeContext(record);
            if (isModuleEnabled(record.descriptor.id())) {
                record.runtime.onDisable(context);
            }
            record.runtime.onShutdown(context);
            clearOwnedState(runtimeOwnerId(record.descriptor.id()));
        }
        UniformHookRegistry.getInstance().setRuntimeRegistry(null);
        records.clear();
        descriptors.clear();
        moduleFrameHandles.clear();
        subscriptionIndex.clear();
        processInitialized = false;
        kernelInitialized = false;
    }

    public boolean isModuleEnabled(String moduleId) {
        return settingRegistry.getBoolean(moduleEnabledSettingId(moduleId), true);
    }

    public void setModuleEnabled(String moduleId, boolean enabled) {
        SettingChangeEvent event = settingRegistry.applyImmediateValue(moduleEnabledSettingId(moduleId), enabled);
        if (event != null) {
            applySettingChanges(List.of(event));
        }
    }

    public ModuleRuntime runtimeById(String moduleId) {
        ModuleRecord record = records.get(moduleId);
        return record != null ? record.runtime : null;
    }

    public ModuleSession sessionById(String moduleId) {
        ModuleRecord record = records.get(moduleId);
        return record != null ? record.session : null;
    }

    public Collection<ModuleRuntime> allRuntimes() {
        List<ModuleRuntime> runtimes = new ArrayList<>();
        for (ModuleRecord record : records.values()) {
            runtimes.add(record.runtime);
        }
        return Collections.unmodifiableList(runtimes);
    }

    public ModuleSettingRegistry settingRegistry() {
        return settingRegistry;
    }

    public ModuleMetricRegistry metricRegistry() {
        return metricRegistry;
    }

    public MetricSnapshot metricSnapshot() {
        return metricRegistry.snapshot();
    }

    public ModuleMacroRegistry macroRegistry() {
        return macroRegistry;
    }

    public PipelineUniformRegistry uniformRegistry() {
        return uniformRegistry;
    }

    public SketchDiagnostics diagnostics() {
        return SketchDiagnostics.get();
    }

    public Map<KeyId, MetricDescriptor> descriptorMetrics() {
        return Collections.unmodifiableMap(descriptorMetrics);
    }

    public <C extends RenderContext> void contributeToTickGraph(TickGraphBuilder<C> builder) {
        for (ModuleRecord record : records.values()) {
            if (isModuleEnabled(record.descriptor.id())) {
                record.runtime.contributeToTickGraph(builder);
            }
        }
    }

    public <C extends RenderContext> void contributeToFrameGraph(RenderGraphBuilder<C> builder) {
        for (ModuleRecord record : records.values()) {
            if (isModuleEnabled(record.descriptor.id())) {
                record.runtime.contributeToFrameGraph(builder);
            }
        }
    }

    public void onEntitySpawned(GraphicsEntityId entityId) {
        for (ModuleRecord record : records.values()) {
            if (isModuleEnabled(record.descriptor.id())) {
                attachMatchingEntity(record, entityId);
            }
        }
    }

    public void onEntityDestroyed(GraphicsEntityId entityId) {
        Set<String> moduleIds = new LinkedHashSet<>();
        for (ModuleSubscriptionIndex.BindingRecord binding : subscriptionIndex.bindingsForEntity(entityId)) {
            moduleIds.add(binding.moduleId());
        }
        for (String moduleId : moduleIds) {
            ModuleRecord record = records.get(moduleId);
            if (record != null) {
                record.runtime.onEntityDetached(entityId, entityAttachContext(record));
            }
        }
        subscriptionIndex.clearBindingsForEntity(entityId);
    }

    public void onEntityShapeChanged(GraphicsEntityId entityId) {
        for (ModuleRecord record : records.values()) {
            if (!isModuleEnabled(record.descriptor.id())) {
                continue;
            }
            detachIfNoLongerMatching(record, entityId);
            attachMatchingEntity(record, entityId);
        }
    }

    public boolean isInitialized() {
        return processInitialized;
    }

    public boolean isKernelInitialized() {
        return kernelInitialized;
    }

    public GraphSnapshot assembleGraphSnapshot(long version, long createdFrame) {
        Map<String, List<ModulePassDefinition>> modulePasses = new LinkedHashMap<>();
        Map<KeyId, FrameResourceHandle<?>> resourceHandles = new LinkedHashMap<>();

        for (ModuleRecord record : records.values()) {
            if (!isModuleEnabled(record.descriptor.id())) {
                continue;
            }
            ModuleGraphAssemblyCollector collector = new ModuleGraphAssemblyCollector(record.descriptor.id());
            Map<KeyId, FrameResourceHandle<?>> declaredHandles = moduleFrameHandles.get(record.descriptor.id());
            if (declaredHandles != null) {
                for (FrameResourceHandle<?> handle : declaredHandles.values()) {
                    collector.registerExistingHandle(handle);
                }
            }

            record.runtime.describeFrameResources(collector);
            record.runtime.contributeModulePasses(collector);

            if (!collector.modulePasses().isEmpty()) {
                modulePasses.put(record.descriptor.id(), collector.modulePasses());
            }
            resourceHandles.putAll(collector.resourceHandles());
        }

        return new GraphSnapshot(version, createdFrame, modulePasses, resourceHandles);
    }

    public void flushPendingSettingChanges() {
        applySettingChanges(settingRegistry.flushPendingChanges());
    }

    public void installIndirectRequests(GraphicsPipeline<?> pipeline) {
        if (pipeline == null) {
            return;
        }
        for (PipelineType pipelineType : List.of(PipelineType.RASTERIZATION, PipelineType.TRANSLUCENT)) {
            PipelineDataStore pipelineDataStore = pipeline.getPipelineDataStore(pipelineType, FrameDataDomain.ASYNC_BUILD);
            if (pipelineDataStore == null) {
                continue;
            }
            IndirectPlanData indirectPlanData = pipelineDataStore.get(IndirectPlanData.KEY);
            if (indirectPlanData == null) {
                continue;
            }
            for (Set<ManagedIndirectRequest> requests : ownedIndirectRequests.values()) {
                if (requests == null || requests.isEmpty()) {
                    continue;
                }
                for (ManagedIndirectRequest request : requests) {
                    if (request != null) {
                        indirectPlanData.request(request.stageId(), request.graphicsId(), request.requestMode());
                    }
                }
            }
        }
    }

    private void refreshEntitySubscriptions(ModuleRecord record) {
        if (record == null) {
            return;
        }
        List<GraphicsEntitySubscription> subscriptions = new ArrayList<>();
        record.runtime.registerEntitySubscriptions(subscriptions::add);
        subscriptionIndex.replaceSubscriptions(record.descriptor.id(), subscriptions);
    }

    private void scanExistingEntities(ModuleRecord record) {
        if (record == null) {
            return;
        }
        for (GraphicsEntityId entityId : pipeline.graphicsWorld().query(rogo.sketch.core.graphics.ecs.GraphicsQuery.builder().build())) {
            attachMatchingEntity(record, entityId);
        }
    }

    private void attachMatchingEntity(ModuleRecord record, GraphicsEntityId entityId) {
        if (record == null || entityId == null) {
            return;
        }
        GraphicsWorld world = pipeline.graphicsWorld();
        if (!world.contains(entityId)) {
            return;
        }
        boolean wasAttached = subscriptionIndex.hasAnyBinding(entityId, record.descriptor.id());
        boolean attached = false;
        Set<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>> signature = world.signature(entityId);
        for (GraphicsEntitySubscription subscription : subscriptionIndex.subscriptions(record.descriptor.id())) {
            if (subscription == null || !subscription.filter().matches(signature)) {
                continue;
            }
            if (subscriptionIndex.isBound(entityId, record.descriptor.id(), subscription.subscriptionId())) {
                continue;
            }
            subscriptionIndex.bind(entityId, record.descriptor.id(), subscription.subscriptionId());
            attached = true;
        }
        if (!wasAttached && attached) {
            record.runtime.onEntityAttached(entityId, new GraphicsEntitySnapshot(world, entityId), entityAttachContext(record));
        }
    }

    private void detachIfNoLongerMatching(ModuleRecord record, GraphicsEntityId entityId) {
        if (record == null || entityId == null) {
            return;
        }
        GraphicsWorld world = pipeline.graphicsWorld();
        boolean wasAttached = subscriptionIndex.hasAnyBinding(entityId, record.descriptor.id());
        Set<rogo.sketch.core.graphics.ecs.GraphicsComponentType<?>> signature = world.contains(entityId) ? world.signature(entityId) : Set.of();
        for (GraphicsEntitySubscription subscription : subscriptionIndex.subscriptions(record.descriptor.id())) {
            if (subscription == null || !subscriptionIndex.isBound(entityId, record.descriptor.id(), subscription.subscriptionId())) {
                continue;
            }
            if (subscription.filter().matches(signature)) {
                continue;
            }
            subscriptionIndex.unbind(entityId, record.descriptor.id(), subscription.subscriptionId());
        }
        if (wasAttached && !subscriptionIndex.hasAnyBinding(entityId, record.descriptor.id())) {
            record.runtime.onEntityDetached(entityId, entityAttachContext(record));
        }
    }

    private void ensureModuleEnabledSetting(ModuleDescriptor descriptor) {
        String moduleId = descriptor.id();
        KeyId enabledSettingId = moduleEnabledSettingId(moduleId);
        for (SettingNode<?> setting : settingRegistry.allSettings()) {
            if (setting.id().equals(enabledSettingId)) {
                return;
            }
        }
        settingRegistry.registerSetting(new BooleanSetting(
                enabledSettingId,
                moduleId,
                "module." + moduleId + ".enabled",
                null,
                null,
                ChangeImpact.RECREATE_SESSION_RESOURCES,
                false,
                List.of(),
                descriptor.enabledByDefault()));
    }

    private void applySettingChanges(List<SettingChangeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<String, Boolean> moduleEnableStates = new LinkedHashMap<>();
        Set<String> recreateSessionModules = new LinkedHashSet<>();
        Set<String> refreshSessionModules = new LinkedHashSet<>();
        boolean rebuildGraphs = false;

        for (SettingChangeEvent event : events) {
            if (event.settingId().equals(moduleEnabledSettingId(event.moduleId()))) {
                moduleEnableStates.put(event.moduleId(), Boolean.TRUE.equals(event.newValue()));
                continue;
            }

            ModuleRecord record = records.get(event.moduleId());
            if (record == null) {
                continue;
            }

            switch (event.changeImpact()) {
                case RECREATE_SESSION_RESOURCES -> recreateSessionModules.add(record.descriptor.id());
                case REATTACH_SESSION_GRAPHICS -> refreshSessionModules.add(record.descriptor.id());
                case REBUILD_GRAPHS -> rebuildGraphs = true;
                case RUNTIME_ONLY, UPDATE_UNIFORMS, RECOMPILE_SHADERS -> {
                    // No host-side work required. Modules and shader systems observe the new state lazily.
                }
            }
        }

        for (Map.Entry<String, Boolean> entry : moduleEnableStates.entrySet()) {
            handleModuleEnableChange(entry.getKey(), entry.getValue());
        }

        if (!moduleEnableStates.isEmpty()) {
            rebuildGraphs = true;
        }

        refreshSessionModules.removeAll(recreateSessionModules);

        for (String moduleId : recreateSessionModules) {
            if (moduleEnableStates.containsKey(moduleId) || !isModuleEnabled(moduleId)) {
                continue;
            }
            ModuleRecord record = records.get(moduleId);
            if (record != null) {
                recreateSession(record);
            }
        }

        for (String moduleId : refreshSessionModules) {
            if (moduleEnableStates.containsKey(moduleId) || !isModuleEnabled(moduleId)) {
                continue;
            }
            ModuleRecord record = records.get(moduleId);
            if (record != null) {
                refreshSessionGraphics(record);
            }
        }

        if (rebuildGraphs && kernel != null) {
            kernel.rebuildGraphs();
        }
    }

    private void handleModuleEnableChange(String moduleId, boolean enabled) {
        ModuleRecord record = records.get(moduleId);
        if (record == null) {
            return;
        }

        ModuleRuntimeContext runtimeContext = runtimeContext(record);
        if (enabled) {
            if (kernelInitialized) {
                // Runtime-owned kernel registrations are cleared on disable.
                // Re-run kernel init so built-in resources, uniforms, metrics,
                // and similar owner-scoped registrations are restored before
                // session graphics come back.
                record.runtime.onKernelInit(runtimeContext);
                refreshEntitySubscriptions(record);
            }
            record.runtime.onEnable(runtimeContext);
            if (worldActive && record.session != null && record.session != ModuleSession.NOOP) {
                record.session.onEnable(sessionContext(record));
            }
            scanExistingEntities(record);
            return;
        }

        for (GraphicsEntityId entityId : subscriptionIndex.entitiesForModule(moduleId)) {
            record.runtime.onEntityDetached(entityId, entityAttachContext(record));
        }
        subscriptionIndex.clearBindingsForModule(moduleId);
        if (worldActive && record.session != null && record.session != ModuleSession.NOOP) {
            record.session.onDisable(sessionContext(record));
        }
        record.runtime.onDisable(runtimeContext);
        clearOwnedState(sessionOwnerId(moduleId));
        clearOwnedState(runtimeOwnerId(moduleId));
    }

    private void recreateSession(ModuleRecord record) {
        if (!worldActive) {
            return;
        }
        if (record.session != null && record.session != ModuleSession.NOOP) {
            ModuleSessionContext sessionContext = sessionContext(record);
            if (isModuleEnabled(record.descriptor.id())) {
                record.session.onDisable(sessionContext);
            }
            record.session.onWorldLeave(sessionContext);
            record.session.close();
            clearOwnedState(sessionOwnerId(record.descriptor.id()));
        }
        record.session = record.runtime.createSession();
        if (record.session != null && record.session != ModuleSession.NOOP) {
            ModuleSessionContext context = sessionContext(record);
            record.session.onWorldEnter(context);
            if (isModuleEnabled(record.descriptor.id())) {
                record.session.onEnable(context);
            }
        }
    }

    private void refreshSessionGraphics(ModuleRecord record) {
        if (!worldActive || record.session == null || record.session == ModuleSession.NOOP) {
            return;
        }
        ModuleSessionContext context = sessionContext(record);
        if (isModuleEnabled(record.descriptor.id())) {
            record.session.onDisable(context);
        }
        unregisterOwnedGraphics(sessionOwnerId(record.descriptor.id()));
        if (isModuleEnabled(record.descriptor.id())) {
            record.session.onEnable(context);
        }
    }

    private void clearOwnedState(String ownerId) {
        unregisterOwnedGraphics(ownerId);
        clearOwnedIndirectRequests(ownerId);
        pipeline.extensionHost().hostEvents().clearOwner(ownerId);
        pipeline.resourceManager().unregisterOwnedResources(ownerId);
        uniformRegistry.unregisterOwner(ownerId);
        metricRegistry.unregisterOwner(ownerId);
        macroRegistry.clearOwner(ownerId);
    }

    private void requestIndirectPlan(String ownerId, KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode) {
        if (stageId == null || graphicsId == null || requestMode == null) {
            return;
        }
        ownedIndirectRequests
                .computeIfAbsent(ownerId, ignored -> new LinkedHashSet<>())
                .add(new ManagedIndirectRequest(stageId, graphicsId, requestMode));
    }

    private void clearOwnedIndirectRequests(String ownerId) {
        ownedIndirectRequests.remove(ownerId);
    }

    private void registerStageSubmitNode(String ownerId, StageSubmitNode node) {
        if (node == null) {
            return;
        }
        pipeline.registerStageSubmitNode(ownerId, node);
    }

    private GraphicsEntityId registerGraphicsEntity(String ownerId, GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime) {
        GraphicsEntityId entityId = pipeline.spawnGraphicsEntity(blueprint);
        trackOwnedGraphics(ownerId, entityId, lifetime);
        return entityId;
    }

    private void trackOwnedGraphics(String ownerId, GraphicsEntityId entityId, ModuleGraphicsLifetime lifetime) {
        if (entityId == null) {
            return;
        }
        ModuleRecord record = records.get(moduleIdFromOwner(ownerId));
        if (record != null) {
            record.ownedGraphics
                    .computeIfAbsent(ownerId, ignored -> new LinkedHashSet<>())
                    .add(new ManagedGraphicsRegistration(entityId, lifetime));
        }
    }

    private void unregisterOwnedGraphics(String ownerId) {
        ModuleRecord record = records.get(moduleIdFromOwner(ownerId));
        pipeline.unregisterStageSubmitNodes(ownerId);
        if (record == null) {
            return;
        }
        Set<ManagedGraphicsRegistration> registrations = record.ownedGraphics.remove(ownerId);
        if (registrations == null) {
            return;
        }
        for (ManagedGraphicsRegistration registration : registrations) {
            pipeline.destroyGraphicsEntity(registration.entityId());
        }
    }

    private ModuleRuntimeContext runtimeContext(ModuleRecord record) {
        return new RuntimeContextImpl(record, runtimeOwnerId(record.descriptor.id()), ResourceScope.MODULE_OWNED);
    }

    private EntityAttachContext entityAttachContext(ModuleRecord record) {
        return new EntityAttachContext(runtimeContext(record));
    }

    private ModuleSessionContext sessionContext(ModuleRecord record) {
        return new SessionContextImpl(record, sessionOwnerId(record.descriptor.id()));
    }

    private KeyId moduleEnabledSettingId(String moduleId) {
        return KeyId.of("sketch", moduleId + "_enabled");
    }

    private String runtimeOwnerId(String moduleId) {
        return moduleId + "@runtime";
    }

    private String sessionOwnerId(String moduleId) {
        return moduleId + "@session";
    }

    private String moduleIdFromOwner(String ownerId) {
        int index = ownerId.indexOf('@');
        return index >= 0 ? ownerId.substring(0, index) : ownerId;
    }

    private final class DescriptorContextImpl implements ModuleDescriptorContext {
        private final String moduleId;

        private DescriptorContextImpl(String moduleId) {
            this.moduleId = moduleId;
        }

        @Override
        public String moduleId() {
            return moduleId;
        }

        @Override
        public KeyId moduleEnabledSettingId() {
            return ModuleRuntimeHost.this.moduleEnabledSettingId(moduleId);
        }

        @Override
        public ModuleSettingRegistry settings() {
            return settingRegistry;
        }

        @Override
        public void registerSetting(SettingNode<?> setting) {
            settingRegistry.registerSetting(setting);
        }

        @Override
        public void registerMacro(ModuleMacroDefinition definition) {
            macroRegistry.registerDefinition(definition);
        }

        @Override
        public void registerMetricDescriptor(MetricDescriptor descriptor) {
            descriptorMetrics.put(descriptor.id(), descriptor);
        }
    }

    private class RuntimeContextImpl implements ModuleRuntimeContext {
        protected final ModuleRecord record;
        private final String ownerId;
        private final ResourceScope resourceScope;

        private RuntimeContextImpl(ModuleRecord record, String ownerId, ResourceScope resourceScope) {
            this.record = record;
            this.ownerId = ownerId;
            this.resourceScope = resourceScope;
        }

        @Override
        public String moduleId() {
            return record.descriptor.id();
        }

        @Override
        public String ownerId() {
            return ownerId;
        }

        @Override
        public KeyId moduleEnabledSettingId() {
            return ModuleRuntimeHost.this.moduleEnabledSettingId(moduleId());
        }

        @Override
        public GraphicsPipeline<?> pipeline() {
            return pipeline;
        }

        @Override
        public ExtensionHost extensionHost() {
            return pipeline.extensionHost();
        }

        @Override
        public HostEventRegistrar hostEvents() {
            return pipeline.extensionHost().hostEvents();
        }

        @Override
        public GraphicsWorld graphicsWorld() {
            return pipeline.graphicsWorld();
        }

        @Override
        public GraphicsEntityAssembler graphicsEntityAssembler() {
            return pipeline.graphicsEntityAssembler();
        }

        @Override
        public @Nullable PipelineKernel<?> kernel() {
            return kernel;
        }

        @Override
        public ModuleSettingRegistry settings() {
            return settingRegistry;
        }

        @Override
        public ModuleMetricRegistry metrics() {
            return metricRegistry;
        }

        @Override
        public ModuleMacroRegistry macros() {
            return macroRegistry;
        }

        @Override
        public PipelineUniformRegistry uniforms() {
            return uniformRegistry;
        }

        @Override
        public SketchDiagnostics diagnostics() {
            return SketchDiagnostics.get();
        }

        @Override
        public boolean isModuleEnabled() {
            return ModuleRuntimeHost.this.isModuleEnabled(moduleId());
        }

        @Override
        public void setModuleEnabled(boolean enabled) {
            ModuleRuntimeHost.this.setModuleEnabled(moduleId(), enabled);
        }

        @Override
        public void registerUniform(KeyId uniformId, ValueGetter<?> getter) {
            uniformRegistry.register(ownerId, uniformId, getter);
        }

        @Override
        public void registerMetric(MetricDescriptor descriptor, Supplier<Object> supplier) {
            metricRegistry.registerMetric(ownerId, descriptor, supplier);
        }

        @Override
        public void registerBuiltInResource(KeyId type, KeyId name, Supplier<? extends ResourceObject> supplier) {
            pipeline.resourceManager().registerBuiltIn(ownerId, resourceScope, type, name, (Supplier<ResourceObject>) supplier);
        }

        @Override
        public void unregisterOwnedResources() {
            pipeline.resourceManager().unregisterOwnedResources(ownerId);
        }

        @Override
        public void unregisterOwnedUniforms() {
            uniformRegistry.unregisterOwner(ownerId);
        }

        @Override
        public void clearOwnedMetrics() {
            metricRegistry.unregisterOwner(ownerId);
        }

        @Override
        public void clearOwnedMacros() {
            macroRegistry.clearOwner(ownerId);
        }

        @Override
        public void setGlobalFlag(String flagName, boolean enabled) {
            macroRegistry.setGlobalFlag(ownerId, flagName, enabled);
        }

        @Override
        public void setGlobalMacro(String macroName, String value) {
            macroRegistry.setGlobalMacro(ownerId, macroName, value);
        }

        @Override
        public <T> FrameResourceHandle<T> registerFrameResourceHandle(FrameResourceHandle<T> handle) {
            if (handle == null) {
                throw new IllegalArgumentException("handle");
            }
            moduleFrameHandles
                    .computeIfAbsent(moduleId(), ignored -> new LinkedHashMap<>())
                    .put(handle.id(), handle);
            return handle;
        }

        @Override
        public <T> FrameResourceHandle<T> frameResourceHandle(KeyId handleId, Class<T> valueType) {
            if (handleId == null || valueType == null) {
                return null;
            }
            Map<KeyId, FrameResourceHandle<?>> handles = moduleFrameHandles.get(moduleId());
            if (handles == null) {
                return null;
            }
            FrameResourceHandle<?> existing = handles.get(handleId);
            if (existing == null || !valueType.isAssignableFrom(existing.valueType())) {
                return null;
            }
            @SuppressWarnings("unchecked")
            FrameResourceHandle<T> typed = (FrameResourceHandle<T>) existing;
            return typed;
        }

        @Override
        public LifecyclePhase phaseForPass(String passId) {
            return kernel != null ? kernel.phaseForPass(moduleId(), passId) : null;
        }

        @Override
        public void registerStageSubmitNode(StageSubmitNode node) {
            ModuleRuntimeHost.this.registerStageSubmitNode(ownerId, node);
        }

        @Override
        public GraphicsEntityId registerGraphicsEntity(GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime) {
            return ModuleRuntimeHost.this.registerGraphicsEntity(ownerId, blueprint, lifetime);
        }

        @Override
        public void unregisterOwnedGraphics() {
            ModuleRuntimeHost.this.unregisterOwnedGraphics(ownerId);
        }

        @Override
        public void requestIndirectPlan(KeyId stageId, KeyId graphicsId, IndirectPlanRequest.RequestMode requestMode) {
            ModuleRuntimeHost.this.requestIndirectPlan(ownerId, stageId, graphicsId, requestMode);
        }

        @Override
        public void clearOwnedIndirectRequests() {
            ModuleRuntimeHost.this.clearOwnedIndirectRequests(ownerId);
        }

        @Override
        public void clearOwnedHostEvents() {
            pipeline.extensionHost().hostEvents().clearOwner(ownerId);
        }

        @Override
        public void rebuildGraphs() {
            if (kernel != null) {
                kernel.rebuildGraphs();
            }
        }
    }

    private final class SessionContextImpl extends RuntimeContextImpl implements ModuleSessionContext {
        private SessionContextImpl(ModuleRecord record, String ownerId) {
            super(record, ownerId, ResourceScope.SESSION_OWNED);
        }

        @Override
        public void registerAuxiliaryEntity(GraphicsEntityBlueprint blueprint, ModuleGraphicsLifetime lifetime) {
            ModuleRuntimeHost.this.registerGraphicsEntity(ownerId(), blueprint, lifetime);
        }
    }

    private static final class ModuleRecord {
        private final ModuleDescriptor descriptor;
        private final ModuleRuntime runtime;
        private ModuleSession session;
        private final Map<String, Set<ManagedGraphicsRegistration>> ownedGraphics = new LinkedHashMap<>();

        private ModuleRecord(ModuleDescriptor descriptor, ModuleRuntime runtime) {
            this.descriptor = descriptor;
            this.runtime = runtime;
        }
    }

    private record ManagedGraphicsRegistration(
            GraphicsEntityId entityId,
            ModuleGraphicsLifetime lifetime
    ) {
    }

    private record ManagedIndirectRequest(
            KeyId stageId,
            KeyId graphicsId,
            IndirectPlanRequest.RequestMode requestMode
    ) {
    }

    private final class ModuleGraphAssemblyCollector implements ModuleGraphAssemblyContext {
        private final String moduleId;
        private final Map<KeyId, FrameResourceHandle<?>> resourceHandles = new LinkedHashMap<>();
        private final List<ModulePassDefinition> modulePasses = new ArrayList<>();

        private ModuleGraphAssemblyCollector(String moduleId) {
            this.moduleId = moduleId;
        }

        @Override
        public String moduleId() {
            return moduleId;
        }

        @Override
        public <T> FrameResourceHandle<T> registerFrameResourceHandle(FrameResourceHandle<T> handle) {
            if (handle == null) {
                throw new IllegalArgumentException("handle");
            }
            resourceHandles.put(handle.id(), handle);
            moduleFrameHandles
                    .computeIfAbsent(moduleId, ignored -> new LinkedHashMap<>())
                    .put(handle.id(), handle);
            return handle;
        }

        private void registerExistingHandle(FrameResourceHandle<?> handle) {
            if (handle != null) {
                resourceHandles.put(handle.id(), handle);
            }
        }

        @Override
        public <T> FrameResourceHandle<T> frameResourceHandle(KeyId handleId, Class<T> valueType) {
            if (handleId == null || valueType == null) {
                return null;
            }
            FrameResourceHandle<?> handle = resourceHandles.get(handleId);
            if (handle == null) {
                Map<KeyId, FrameResourceHandle<?>> handles = moduleFrameHandles.get(moduleId);
                handle = handles != null ? handles.get(handleId) : null;
            }
            if (handle == null || !valueType.isAssignableFrom(handle.valueType())) {
                return null;
            }
            @SuppressWarnings("unchecked")
            FrameResourceHandle<T> typed = (FrameResourceHandle<T>) handle;
            return typed;
        }

        @Override
        public void registerModulePass(ModulePassDefinition definition) {
            if (definition != null) {
                modulePasses.add(definition);
            }
        }

        private List<ModulePassDefinition> modulePasses() {
            return List.copyOf(modulePasses);
        }

        private Map<KeyId, FrameResourceHandle<?>> resourceHandles() {
            return Map.copyOf(resourceHandles);
        }
    }
}

