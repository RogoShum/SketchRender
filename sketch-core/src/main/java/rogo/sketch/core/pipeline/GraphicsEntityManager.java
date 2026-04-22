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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return snapshotEntity(snapshot);
    }

    public @Nullable StageEntityView.Entry snapshotEntityIfPresent(GraphicsEntityId entityId) {
        GraphicsWorld.StageEntitySnapshot snapshot = graphicsWorld.stageEntitySnapshot(entityId);
        return snapshot != null ? snapshotEntity(snapshot) : null;
    }

    public List<StageEntityView.Entry> snapshotEntitiesIfPresent(List<GraphicsEntityId> entityIds) {
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
                entries.add(snapshotEntity(snapshot));
            }
        }
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private StageEntityView.Entry snapshotEntity(GraphicsWorld.StageEntitySnapshot snapshot) {
        GraphicsBuiltinComponents.StageBindingComponent stageBinding = snapshot.stageBinding();
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
                stageBinding != null ? stageBinding.stageId() : null,
                stageBinding != null ? stageBinding.pipelineType() : null,
                stageBinding != null ? stageBinding.renderParameter() : null,
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
                containerHint,
                snapshot.rasterRenderable(),
                snapshot.computeDispatch(),
                snapshot.functionInvoke(),
                snapshot.submissionCapability(),
                snapshot.bounds(),
                snapshot.preparedMesh(),
                snapshot.renderDescriptor(),
                snapshot.instanceVertexAuthoring(),
                snapshot.transformBinding(),
                snapshot.tickDriver(),
                snapshot.asyncTickDriver(),
                snapshot.descriptorVersion(),
                snapshot.geometryVersion(),
                snapshot.boundsVersion());
    }
}
