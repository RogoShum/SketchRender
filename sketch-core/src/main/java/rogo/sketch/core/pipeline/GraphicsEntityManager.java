package rogo.sketch.core.pipeline;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsEntityAssembler;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.graphics.ecs.GraphicsWorld;
import rogo.sketch.core.pipeline.flow.v2.StageEntityView;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns ECS entity storage/assembly and converts immutable world snapshots into
 * stage-view entries consumed by packet building.
 */
public final class GraphicsEntityManager {
    private final GraphicsWorld graphicsWorld = new GraphicsWorld();
    private final GraphicsEntityAssembler graphicsEntityAssembler = new GraphicsEntityAssembler(graphicsWorld);

    public GraphicsWorld graphicsWorld() {
        return graphicsWorld;
    }

    public GraphicsEntityAssembler graphicsEntityAssembler() {
        return graphicsEntityAssembler;
    }

    public GraphicsEntityId spawn(GraphicsEntityBlueprint blueprint) {
        return graphicsEntityAssembler.spawn(blueprint);
    }

    public void destroy(GraphicsEntityId entityId) {
        graphicsEntityAssembler.destroy(entityId);
    }

    public StageEntityView.Entry snapshotEntity(GraphicsEntityId entityId) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Unknown graphics entity: " + entityId);
        }
        return snapshotEntity(snapshot, null, null);
    }

    public @Nullable StageEntityView.Entry snapshotEntityIfPresent(GraphicsEntityId entityId) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        return snapshot != null ? snapshotEntity(snapshot, null, null) : null;
    }

    public @Nullable StageEntityView.Entry snapshotEntityIfPresent(
            GraphicsEntityId entityId,
            @Nullable KeyId stageId,
            @Nullable PipelineType pipelineType) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        return snapshot != null ? snapshotEntity(snapshot, stageId, pipelineType) : null;
    }

    public List<StageEntityView.Entry> snapshotEntitiesIfPresent(List<GraphicsEntityId> entityIds) {
        return snapshotEntitiesIfPresent(entityIds, null, null);
    }

    public List<StageEntityView.Entry> snapshotEntitiesIfPresent(
            List<GraphicsEntityId> entityIds,
            @Nullable KeyId stageId,
            @Nullable PipelineType pipelineType) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        List<GraphicsWorld.StageEntitySnapshot> snapshots = graphicsWorld.stageEntitySnapshots(entityIds);
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<StageEntityView.Entry> entries = new ArrayList<>(snapshots.size());
        for (GraphicsWorld.StageEntitySnapshot snapshot : snapshots) {
            if (snapshot != null) {
                StageEntityView.Entry entry = snapshotEntity(snapshot, stageId, pipelineType);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private @Nullable StageEntityView.Entry snapshotEntity(
            GraphicsWorld.StageEntitySnapshot snapshot,
            @Nullable KeyId stageId,
            @Nullable PipelineType pipelineType) {
        GraphicsBuiltinComponents.StageBindingComponent stageBinding = snapshot.stageBinding();
        GraphicsBuiltinComponents.StageRoutesComponent stageRoutes = snapshot.stageRoutes();
        StageRouteDescriptor stageRoute = StageRouteCompiler.resolveRoute(stageBinding, stageRoutes, stageId, pipelineType);
        if (stageRoute == null && (stageId != null || pipelineType != null)) {
            return null;
        }
        KeyId effectiveStageId = stageRoute != null
                ? stageRoute.stageId()
                : stageBinding != null ? stageBinding.stageId() : null;
        PipelineType effectivePipelineType = stageRoute != null
                ? stageRoute.pipelineType()
                : stageBinding != null ? stageBinding.pipelineType() : null;
        var effectiveRenderParameter = stageRoute != null
                ? stageRoute.renderParameter()
                : stageBinding != null ? stageBinding.renderParameter() : null;
        GraphicsBuiltinComponents.ContainerHintComponent containerHint = snapshot.containerHint();
        GraphicsBuiltinComponents.IdentityComponent identity = snapshot.identity();
        GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin = snapshot.resourceOrigin();
        GraphicsBuiltinComponents.GraphicsTagsComponent tags = snapshot.tags();
        GraphicsEntitySchema schema = snapshot.schema();
        GraphicsUniformSubject uniformSubject = new GraphicsUniformSubject(
                snapshot.entityId(),
                identity,
                resourceOrigin,
                tags,
                schema,
                effectiveStageId,
                effectivePipelineType,
                effectiveRenderParameter,
                snapshot::component,
                snapshot::componentSnapshot);
        return new StageEntityView.Entry(
                snapshot.entityId(),
                schema,
                schema.capabilityView(),
                identity,
                resourceOrigin,
                tags,
                uniformSubject,
                snapshot.lifecycle(),
                stageBinding,
                stageRoutes,
                stageRoute,
                containerHint,
                snapshot.rasterRenderable(),
                snapshot.computeDispatch(),
                snapshot.functionInvoke(),
                snapshot.submissionCapability(),
                snapshot.bounds(),
                snapshot.preparedMesh(),
                snapshot.renderDescriptor(),
                snapshot.instanceVertexAuthoring(),
                snapshot.instanceCount(),
                snapshot.transformBinding(),
                snapshot.tickDriver(),
                snapshot.asyncTickDriver(),
                snapshot.descriptorVersion(),
                snapshot.geometryVersion(),
                snapshot.boundsVersion());
    }
}
