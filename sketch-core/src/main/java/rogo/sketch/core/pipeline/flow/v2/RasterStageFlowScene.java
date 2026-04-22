package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.api.model.BakedTypeMesh;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.backend.BackendGeometryBinding;
import rogo.sketch.core.data.format.VertexBufferKey;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.packet.ExecutionKey;
import rogo.sketch.core.packet.PacketBuildContext;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.GraphicsStage;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.data.PipelineDataStore;
import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.pipeline.flow.RenderPostProcessors;
import rogo.sketch.core.pipeline.flow.impl.RasterizationPostProcessor;
import rogo.sketch.core.pipeline.flow.plan.ResourceGroupCompiler;
import rogo.sketch.core.pipeline.geometry.GeometryEncodeResult;
import rogo.sketch.core.pipeline.geometry.GeometrySourceKey;
import rogo.sketch.core.pipeline.geometry.RasterGeometryEncoder;
import rogo.sketch.core.pipeline.module.diagnostic.RenderTraceRecorder;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;
import rogo.sketch.core.pipeline.parmeter.RasterizationParameter;
import rogo.sketch.core.shader.uniform.FrameUniformSnapshot;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.core.vertex.GeometryResourceCoordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;

public final class RasterStageFlowScene<C extends RenderContext> implements StageFlowScene<C> {
    private final KeyId stageId;
    private final ShaderVariantKey stageVariantKey;
    private final PipelineType pipelineType;
    private final GraphicsResourceManager shaderResourceManager;
    private final GeometryResourceCoordinator resourceManager;
    private final Supplier<PipelineDataStore> dataStoreSupplier;
    private final RasterGeometryEncoder geometryEncoder = new RasterGeometryEncoder();
    private final ResourceGroupCompiler resourceGroupCompiler = new ResourceGroupCompiler();
    private final DrawStreamCompiler drawStreamCompiler;
    private final RasterEntityStateCache stateCache = new RasterEntityStateCache();
    private PreparedStageGeometryView preparedGeometryView;
    private final Map<GeometryBatchKey, VisibleInstanceSlice> visibleSlices = new LinkedHashMap<>();
    private final RenderTraceRecorder renderTraceRecorder;
    private final Set<String> emittedDropDiagnostics = new HashSet<>();
    private long visibilityRevision = 0L;

    public RasterStageFlowScene(
            GraphicsStage stage,
            KeyId stageId,
            PipelineType pipelineType,
            GraphicsResourceManager shaderResourceManager,
            GeometryResourceCoordinator resourceManager,
            Supplier<PipelineDataStore> dataStoreSupplier,
            RenderTraceRecorder renderTraceRecorder) {
        this.stageId = stageId;
        this.stageVariantKey = stage != null ? stage.getStageVariantKey() : ShaderVariantKey.EMPTY;
        this.pipelineType = pipelineType;
        this.shaderResourceManager = shaderResourceManager;
        this.resourceManager = resourceManager;
        this.dataStoreSupplier = dataStoreSupplier;
        this.renderTraceRecorder = renderTraceRecorder;
        this.drawStreamCompiler = new DrawStreamCompiler(
                stageId,
                pipelineType,
                resourceManager,
                geometryEncoder,
                new DrawStreamCompiler.TraceHooks() {
                    @Override
                    public void drop(GraphicsUniformSubject subject, String reason) {
                        traceDrop(subject, reason);
                    }

                    @Override
                    public void drop(List<GraphicsUniformSubject> subjects, String reason) {
                        traceDrop(subjects, reason);
                    }

                    @Override
                    public void packetBuilt(List<GraphicsUniformSubject> subjects, ExecutionKey stateKey) {
                        tracePacketBuilt(stageId, subjects, stateKey);
                    }

                    @Override
                    public void stagePlanned(List<GraphicsUniformSubject> subjects) {
                        traceStagePlanned(stageId, subjects);
                    }
                });
        this.preparedGeometryView = new PreparedStageGeometryView(stageId, pipelineType, List.of());
    }

    @Override
    public PipelineType pipelineType() {
        return pipelineType;
    }

    @Override
    public void prepareForFrame(GraphicsWorld world, StageEntityView view, C context, FrameUniformSnapshot frameUniformSnapshot) {
        List<StageEntityView.Entry> entries = view != null ? view.rasterEntries() : List.of();
        stateCache.retainOnly(view != null ? view.rasterEntityIds() : List.of());
        for (StageEntityView.Entry entry : entries) {
            if (entry == null || entry.shouldDiscard()) {
                continue;
            }
            refreshEntry(entry, false);
        }
        preparedGeometryView = prepareStageGeometryView(entries, frameUniformSnapshot);
    }

    @Override
    public void tick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.rasterEntries()) {
            entry.tick();
        }
    }

    @Override
    public void asyncTick(GraphicsWorld world, StageEntityView view, C context) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.rasterEntries()) {
            entry.asyncTick();
        }
    }

    @Override
    public void swapData(GraphicsWorld world, StageEntityView view) {
        if (view == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.rasterEntries()) {
            entry.swapData();
        }
    }

    @Override
    public void cleanupDiscardedEntities(GraphicsWorld world, GraphicsEntityAssembler assembler, StageEntityView view) {
        if (view == null || assembler == null) {
            return;
        }
        for (StageEntityView.Entry entry : view.rasterEntries()) {
            if (entry.shouldDiscard()) {
                assembler.destroy(entry.entityId());
            }
        }
    }

    @Override
    public Map<ExecutionKey, List<RenderPacket>> createRenderPackets(
            StageEntityView view,
            RenderFlowType flowType,
            RenderPostProcessors postProcessors,
            C context,
            FrameUniformSnapshot frameUniformSnapshot) {
        if (view == null || view.isEmpty()) {
            return Collections.emptyMap();
        }

        prepareVisibility(view);
        if (visibleSlices.isEmpty()) {
            return Collections.emptyMap();
        }

        StageGeometryView geometryView = buildStageGeometryView();
        if (geometryView == null || geometryView.isEmpty()) {
            return Collections.emptyMap();
        }

        PipelineDataStore dataStore = dataStoreSupplier.get();
        PacketBuildContext packetBuildContext = new PacketBuildContext(pipelineType, resourceManager, dataStore);
        RasterizationPostProcessor processor = postProcessors.get(flowType);
        return drawStreamCompiler.compile(geometryView, packetBuildContext, processor);
    }

    @Override
    public void clear() {
        visibleSlices.clear();
        stateCache.retainOnly(List.of());
        preparedGeometryView = new PreparedStageGeometryView(stageId, pipelineType, List.of());
    }

    private PreparedStageGeometryView prepareStageGeometryView(
            List<StageEntityView.Entry> entries,
            FrameUniformSnapshot frameUniformSnapshot) {
        if (entries == null || entries.isEmpty()) {
            return new PreparedStageGeometryView(stageId, pipelineType, List.of());
        }

        Map<GeometryBatchKey, PreparedVisibleBatchBuilder> grouped = new LinkedHashMap<>();
        long preparedOrder = 0L;
        for (StageEntityView.Entry entry : entries) {
            if (entry == null || !(entry.renderParameter() instanceof RasterizationParameter rasterParameter)) {
                continue;
            }

            RasterEntityStateCache.Entry cached = stateCache.get(entry.entityId());
            if (cached == null || cached.geometryTraitsRef() == null || cached.compiledRenderSetting() == null) {
                cached = refreshEntry(entry, true);
            }
            if (cached == null || cached.geometryTraitsRef() == null || cached.compiledRenderSetting() == null) {
                continue;
            }

            GeometryTraitsRef geometryTraits = cached.geometryTraitsRef();
            GeometryBatchKey geometryBatchKey = geometryTraits.geometryBatchKey();
            VertexBufferKey vertexBufferKey = VertexBufferKey.fromParameter(
                    rasterParameter,
                    geometryTraits.sourceKey().sharedSourceId(),
                    geometryBindingToken(geometryBatchKey));
            long initialOrder = preparedOrder;
            PreparedVisibleBatchBuilder builder = grouped.computeIfAbsent(
                    geometryBatchKey,
                    ignored -> new PreparedVisibleBatchBuilder(
                            geometryBatchKey,
                            initialOrder,
                            rasterParameter,
                            geometryTraits,
                            vertexBufferKey,
                            geometryTraits.preparedMesh() instanceof BakedTypeMesh bakedTypeMesh
                                    ? bakedTypeMesh.sourceGeometryBinding()
                                    : null,
                            new ArrayList<>(),
                            new LinkedHashMap<>()));
            builder.entries().add(entry);

            CompiledRenderSetting compiledRenderSetting = cached.compiledRenderSetting();
            PreparedCompiledSettingBuilder compiledBuilder = builder.compiledSettings().computeIfAbsent(
                    compiledRenderSetting,
                    ignored -> new PreparedCompiledSettingBuilder(
                            compiledRenderSetting,
                            new ArrayList<>(),
                            new LinkedHashMap<>()));
            compiledBuilder.entries().add(entry);
            compiledBuilder.preparedMeshes().computeIfAbsent(
                    GeometrySourceKey.fromPreparedMesh(entry.preparedMesh()),
                    ignored -> new PreparedMeshGroupBuilder(entry.preparedMesh(), new ArrayList<>()))
                    .entries()
                    .add(entry);
            preparedOrder++;
        }

        if (grouped.isEmpty()) {
            return new PreparedStageGeometryView(stageId, pipelineType, List.of());
        }

        List<PreparedStageGeometryView.PreparedVisibleBatch> preparedBatches = new ArrayList<>(grouped.size());
        for (PreparedVisibleBatchBuilder builder : grouped.values()) {
            List<PreparedStageGeometryView.PreparedCompiledSettingSlice> compiledSettingSlices =
                    new ArrayList<>(builder.compiledSettings().size());
            for (PreparedCompiledSettingBuilder compiledBuilder : builder.compiledSettings().values()) {
                List<PreparedStageGeometryView.PreparedMeshSlice> preparedMeshSlices =
                        new ArrayList<>(compiledBuilder.preparedMeshes().size());
                for (PreparedMeshGroupBuilder meshBuilder : compiledBuilder.preparedMeshes().values()) {
                    preparedMeshSlices.add(new PreparedStageGeometryView.PreparedMeshSlice(
                            meshBuilder.preparedMesh(),
                            meshBuilder.entries()));
                }
                List<PreparedStageGeometryView.PreparedResourceGroupSlice> preparedResourceGroups =
                        resourceGroupCompiler.prepare(
                                compiledBuilder.compiledRenderSetting(),
                                builder.geometryBatchKey(),
                                compiledBuilder.entries(),
                                frameUniformSnapshot);
                compiledSettingSlices.add(new PreparedStageGeometryView.PreparedCompiledSettingSlice(
                        compiledBuilder.compiledRenderSetting(),
                        compiledBuilder.entries(),
                        preparedMeshSlices,
                        preparedResourceGroups));
            }
            preparedBatches.add(new PreparedStageGeometryView.PreparedVisibleBatch(
                    builder.geometryBatchKey(),
                    builder.firstPreparedOrder(),
                    builder.rasterParameter(),
                    builder.geometryTraits(),
                    builder.vertexBufferKey(),
                    builder.sharedSourceGeometryBinding(),
                    builder.entries(),
                    compiledSettingSlices));
        }
        return new PreparedStageGeometryView(stageId, pipelineType, preparedBatches);
    }

    private void prepareVisibility(StageEntityView view) {
        visibleSlices.clear();
        List<StageEntityView.Entry> entries = view != null ? view.rasterEntries() : List.of();
        if (entries.isEmpty()) {
            return;
        }

        long currentRevision = ++visibilityRevision;
        Map<GeometryBatchKey, List<StageEntityView.Entry>> visibleByBucket = new LinkedHashMap<>();
        Map<GeometryBatchKey, Long> firstVisibleOrder = new LinkedHashMap<>();
        long orderCounter = 0L;

        for (StageEntityView.Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (entry.shouldDiscard()) {
                traceDrop(entry.uniformSubject(), "visibility_should_discard");
                continue;
            }
            if (!entry.shouldRender()) {
                traceDrop(entry.uniformSubject(), "visibility_should_render_false");
                continue;
            }

            RasterEntityStateCache.Entry cached = stateCache.get(entry.entityId());
            if (cached == null || cached.geometryTraitsRef() == null) {
                cached = refreshEntry(entry, true);
            }
            if (cached == null || cached.geometryTraitsRef() == null) {
                traceDrop(entry.uniformSubject(), "missing_geometry_bucket");
                continue;
            }

            GeometryBatchKey geometryBatchKey = cached.geometryTraitsRef().geometryBatchKey();
            if (preparedGeometryView.batch(geometryBatchKey) == null) {
                traceDrop(entry.uniformSubject(), "missing_prepared_geometry_batch");
                continue;
            }
            traceVisible(entry.uniformSubject());
            visibleByBucket.computeIfAbsent(geometryBatchKey, ignored -> new ArrayList<>()).add(entry);
            firstVisibleOrder.putIfAbsent(geometryBatchKey, orderCounter++);
        }

        for (Map.Entry<GeometryBatchKey, List<StageEntityView.Entry>> entry : visibleByBucket.entrySet()) {
            visibleSlices.put(entry.getKey(), new VisibleInstanceSlice(
                    entry.getKey(),
                    entry.getValue(),
                    currentRevision,
                    firstVisibleOrder.getOrDefault(entry.getKey(), Long.MAX_VALUE)));
        }
    }

    private StageGeometryView buildStageGeometryView() {
        if (preparedGeometryView == null || preparedGeometryView.isEmpty()) {
            return new StageGeometryView(stageId, pipelineType, visibilityRevision, List.of());
        }
        List<StageGeometryView.VisibleBatch> batches = new ArrayList<>();
        for (VisibleInstanceSlice visibleSlice : visibleSlices.values()) {
            PreparedStageGeometryView.PreparedVisibleBatch preparedBatch = preparedGeometryView.batch(visibleSlice.geometryBatchKey());
            StageGeometryView.VisibleBatch visibleBatch = buildVisibleBatch(visibleSlice, preparedBatch);
            if (visibleBatch != null) {
                batches.add(visibleBatch);
            }
        }
        return new StageGeometryView(stageId, pipelineType, visibilityRevision, batches);
    }

    private StageGeometryView.VisibleBatch buildVisibleBatch(
            VisibleInstanceSlice visibleSlice,
            PreparedStageGeometryView.PreparedVisibleBatch preparedBatch) {
        if (visibleSlice == null || visibleSlice.visibleEntries().isEmpty() || preparedBatch == null) {
            return null;
        }
        BackendGeometryBinding installedGeometryBinding = resourceManager.getIfPresent(preparedBatch.vertexBufferKey());
        List<StageGeometryView.CompiledSettingSlice> compiledSettingSlices =
                finalizeCompiledSettingSlices(preparedBatch, visibleSlice.visibleEntries());

        return new StageGeometryView.VisibleBatch(
                visibleSlice,
                visibleSlice.geometryBatchKey(),
                visibleSlice.firstVisibleOrderKey(),
                preparedBatch.rasterParameter(),
                preparedBatch.geometryTraits(),
                preparedBatch.vertexBufferKey(),
                installedGeometryBinding,
                preparedBatch.sharedSourceGeometryBinding(),
                visibleSlice.visibleEntries(),
                compiledSettingSlices);
    }

    private List<StageGeometryView.CompiledSettingSlice> finalizeCompiledSettingSlices(
            PreparedStageGeometryView.PreparedVisibleBatch preparedBatch,
            List<StageEntityView.Entry> visibleEntries) {
        if (preparedBatch == null || visibleEntries == null || visibleEntries.isEmpty()) {
            return List.of();
        }
        if (visibleEntries.size() == preparedBatch.preparedEntries().size()) {
            List<StageGeometryView.CompiledSettingSlice> directSlices = new ArrayList<>(preparedBatch.compiledSettingSlices().size());
            for (PreparedStageGeometryView.PreparedCompiledSettingSlice preparedSlice : preparedBatch.compiledSettingSlices()) {
                List<StageGeometryView.PreparedMeshSlice> preparedMeshSlices =
                        new ArrayList<>(preparedSlice.preparedMeshSlices().size());
                for (PreparedStageGeometryView.PreparedMeshSlice preparedMeshSlice : preparedSlice.preparedMeshSlices()) {
                    preparedMeshSlices.add(new StageGeometryView.PreparedMeshSlice(
                            preparedMeshSlice.preparedMesh(),
                            preparedMeshSlice.entries()));
                }
                List<ResourceGroupSlice> resourceGroups =
                        resourceGroupCompiler.finalizePrepared(
                                preparedSlice.preparedResourceGroups(),
                                preparedSlice.entries());
                directSlices.add(new StageGeometryView.CompiledSettingSlice(
                        preparedSlice.compiledRenderSetting(),
                        preparedSlice.entries(),
                        preparedMeshSlices,
                        resourceGroups));
            }
            return directSlices;
        }

        Map<StageEntityView.Entry, Boolean> allowed = new IdentityHashMap<>();
        for (StageEntityView.Entry visibleEntry : visibleEntries) {
            if (visibleEntry != null) {
                allowed.put(visibleEntry, Boolean.TRUE);
            }
        }
        if (allowed.isEmpty()) {
            return List.of();
        }

        List<StageGeometryView.CompiledSettingSlice> finalized = new ArrayList<>();
        for (PreparedStageGeometryView.PreparedCompiledSettingSlice preparedSlice : preparedBatch.compiledSettingSlices()) {
            List<StageEntityView.Entry> selectedEntries = new ArrayList<>();
            for (StageEntityView.Entry entry : preparedSlice.entries()) {
                if (allowed.containsKey(entry)) {
                    selectedEntries.add(entry);
                }
            }
            if (selectedEntries.isEmpty()) {
                continue;
            }

            List<StageGeometryView.PreparedMeshSlice> preparedMeshSlices = new ArrayList<>();
            for (PreparedStageGeometryView.PreparedMeshSlice preparedMeshSlice : preparedSlice.preparedMeshSlices()) {
                List<StageEntityView.Entry> meshEntries = new ArrayList<>();
                for (StageEntityView.Entry entry : preparedMeshSlice.entries()) {
                    if (allowed.containsKey(entry)) {
                        meshEntries.add(entry);
                    }
                }
                if (!meshEntries.isEmpty()) {
                    preparedMeshSlices.add(new StageGeometryView.PreparedMeshSlice(
                            preparedMeshSlice.preparedMesh(),
                            meshEntries));
                }
            }
            List<ResourceGroupSlice> resourceGroups =
                    resourceGroupCompiler.finalizePrepared(
                            preparedSlice.preparedResourceGroups(),
                            selectedEntries);
            finalized.add(new StageGeometryView.CompiledSettingSlice(
                    preparedSlice.compiledRenderSetting(),
                    selectedEntries,
                    preparedMeshSlices,
                    resourceGroups));
        }
        return finalized;
    }

    private RasterEntityStateCache.Entry refreshEntry(StageEntityView.Entry entry, boolean forceFullRefresh) {
        if (entry == null) {
            return null;
        }

        RasterEntityStateCache.Entry state = stateCache.upsert(entry.entityId());

        long descriptorVersion = resolveDescriptorVersion(entry);
        boolean descriptorDirty = forceFullRefresh
                || state.compiledRenderSetting() == null
                || state.descriptorVersion() != descriptorVersion;
        if (descriptorDirty) {
            state.setCompiledRenderSetting(resolveCompiledRenderSetting(entry));
            state.setDescriptorVersion(descriptorVersion);
        }

        long geometryVersion = resolveGeometryVersion(entry);
        boolean geometryDirty = forceFullRefresh
                || descriptorDirty
                || state.geometryTraitsRef() == null
                || state.geometryVersion() != geometryVersion;
        if (geometryDirty) {
            state.setGeometryTraitsRef(resolveGeometryTraits(entry, entry.renderParameter(), state.compiledRenderSetting()));
            state.setGeometryVersion(geometryVersion);
        }

        long boundsVersion = resolveBoundsVersion(entry);
        boolean boundsDirty = forceFullRefresh
                || state.visibilityMetadata() == null
                || state.boundsVersion() != boundsVersion;
        if (boundsDirty) {
            state.setVisibilityMetadata(resolveVisibilityMetadata(entry));
            state.setBoundsVersion(boundsVersion);
        }
        return state;
    }

    private CompiledRenderSetting resolveCompiledRenderSetting(StageEntityView.Entry entry) {
        CompiledRenderSetting compiledRenderSetting = entry != null ? entry.buildRenderDescriptor() : null;
        if (compiledRenderSetting == null
                || compiledRenderSetting.renderSetting() == null
                || stageVariantKey == null
                || stageVariantKey.isEmpty()) {
            return compiledRenderSetting;
        }
        return RenderSettingCompiler.compile(
                compiledRenderSetting.renderSetting(),
                shaderResourceManager,
                stageVariantKey);
    }

    private long resolveDescriptorVersion(StageEntityView.Entry entry) {
        return entry != null ? entry.descriptorVersionValue() : 0L;
    }

    private long resolveGeometryVersion(StageEntityView.Entry entry) {
        if (entry == null) {
            return 0L;
        }
        long componentVersion = entry.geometryVersionValue();
        if (componentVersion != 0L) {
            return componentVersion;
        }
        PreparedMesh mesh = entry.preparedMesh();
        GeometrySourceKey sourceKey = GeometrySourceKey.fromPreparedMesh(mesh);
        return Objects.hash(sourceKey, entry.submissionCapability());
    }

    private long resolveBoundsVersion(StageEntityView.Entry entry) {
        if (entry == null) {
            return 0L;
        }
        long componentVersion = entry.boundsVersionValue();
        if (componentVersion != 0L) {
            return componentVersion;
        }
        if (entry.bounds() == null) {
            return 0L;
        }
        return Objects.hash(
                entry.bounds().minX,
                entry.bounds().minY,
                entry.bounds().minZ,
                entry.bounds().maxX,
                entry.bounds().maxY,
                entry.bounds().maxZ);
    }

    private VisibilityMetadata resolveVisibilityMetadata(StageEntityView.Entry entry) {
        if (entry == null) {
            return new VisibilityMetadata(null, null, 0L, 0);
        }
        return new VisibilityMetadata(entry.bounds(), entry.sortKey(), entry.orderHint(), entry.layerHint());
    }

    private GeometryTraitsRef resolveGeometryTraits(
            StageEntityView.Entry entry,
            RenderParameter renderParameter,
            CompiledRenderSetting compiledRenderSetting) {
        GeometryEncodeResult encodeResult = geometryEncoder.inspect(entry);
        KeyId vertexLayoutKey = compiledRenderSetting != null
                ? compiledRenderSetting.pipelineStateDescriptor().vertexLayoutKey()
                : KeyId.of("sketch:empty_vertex_layout");
        GeometryBatchKey geometryBatchKey = new GeometryBatchKey(
                encodeResult.sourceKey().sharedBatchKey(),
                vertexLayoutKey,
                renderParameter != null ? renderParameter.primitiveType() : null,
                GeometryBatchKey.submissionClassOf(entry != null ? entry.submissionCapability() : null));
        return new GeometryTraitsRef(
                entry != null ? entry.preparedMesh() : null,
                encodeResult.sourceKey(),
                geometryBatchKey,
                encodeResult.vertexCount(),
                encodeResult.indexCount(),
                encodeResult.indexed());
    }

    private long geometryBindingToken(GeometryBatchKey geometryBatchKey) {
        long stageHash = Integer.toUnsignedLong(stageId.hashCode());
        long batchHash = Integer.toUnsignedLong(Objects.hash(pipelineType, geometryBatchKey));
        return (stageHash << 32) ^ batchHash;
    }

    private List<GraphicsUniformSubject> sourceGraphicsOf(List<StageEntityView.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<GraphicsUniformSubject> subjects = new ArrayList<>(entries.size());
        for (StageEntityView.Entry entry : entries) {
            GraphicsUniformSubject subject = entry != null ? entry.uniformSubject() : null;
            if (subject != null) {
                subjects.add(subject);
            }
        }
        return List.copyOf(subjects);
    }

    private void traceVisible(GraphicsUniformSubject subject) {
        if (renderTraceRecorder != null && subject != null) {
            renderTraceRecorder.recordVisible(stageId, subject);
        }
    }

    private void tracePacketBuilt(KeyId packetStageId, List<GraphicsUniformSubject> subjects, ExecutionKey stateKey) {
        if (renderTraceRecorder == null || subjects == null) {
            return;
        }
        for (GraphicsUniformSubject subject : subjects) {
            if (subject != null) {
                renderTraceRecorder.recordPacketBuilt(packetStageId, subject, stateKey);
            }
        }
    }

    private void traceStagePlanned(KeyId packetStageId, List<GraphicsUniformSubject> subjects) {
        if (renderTraceRecorder == null || subjects == null) {
            return;
        }
        for (GraphicsUniformSubject subject : subjects) {
            if (subject != null) {
                renderTraceRecorder.recordStagePlanned(packetStageId, subject);
            }
        }
    }

    private void traceDrop(GraphicsUniformSubject subject, String reason) {
        if (renderTraceRecorder != null && subject != null) {
            renderTraceRecorder.recordDrop(stageId, subject, reason);
        }
        emitDropDiagnostic(subject, reason);
    }

    private void traceDrop(List<GraphicsUniformSubject> subjects, String reason) {
        if (subjects == null) {
            return;
        }
        for (GraphicsUniformSubject subject : subjects) {
            if (subject != null) {
                if (renderTraceRecorder != null) {
                    renderTraceRecorder.recordDrop(stageId, subject, reason);
                }
                emitDropDiagnostic(subject, reason);
            }
        }
    }

    private void emitDropDiagnostic(GraphicsUniformSubject subject, String reason) {
        if (subject == null || reason == null || reason.isBlank()) {
            return;
        }
        String dedupeKey = stageId + "|" + subject.identifier() + "|" + reason;
        if (!emittedDropDiagnostics.add(dedupeKey)) {
            return;
        }
        SketchDiagnostics.get().warn(
                "raster-stage-flow",
                "Dropped raster entity " + subject.identifier()
                        + " in stage " + stageId
                        + " because " + reason);
    }

    private record PreparedMeshGroupBuilder(
            PreparedMesh preparedMesh,
            List<StageEntityView.Entry> entries
    ) {
    }

    private record PreparedCompiledSettingBuilder(
            CompiledRenderSetting compiledRenderSetting,
            List<StageEntityView.Entry> entries,
            Map<GeometrySourceKey, PreparedMeshGroupBuilder> preparedMeshes
    ) {
    }

    private record PreparedVisibleBatchBuilder(
            GeometryBatchKey geometryBatchKey,
            long firstPreparedOrder,
            RasterizationParameter rasterParameter,
            GeometryTraitsRef geometryTraits,
            VertexBufferKey vertexBufferKey,
            BackendGeometryBinding sharedSourceGeometryBinding,
            List<StageEntityView.Entry> entries,
            Map<CompiledRenderSetting, PreparedCompiledSettingBuilder> compiledSettings
    ) {
    }
}
