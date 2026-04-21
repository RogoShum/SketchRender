package rogo.sketch.core.graphics.ecs;

import java.util.*;

/**
 * Central ECS world for graphics entities.
 */
public final class GraphicsWorld {
    private final Map<Set<GraphicsComponentType<?>>, GraphicsArchetype> archetypes = new LinkedHashMap<>();
    private final Map<Integer, Integer> generations = new HashMap<>();
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private final Map<GraphicsEntityId, EntityLocation> locations = new LinkedHashMap<>();
    private int nextSlot = 0;

    public synchronized GraphicsEntityId spawn(GraphicsEntityBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        Set<GraphicsComponentType<?>> signature = normalizedSignature(blueprint.componentTypes());
        GraphicsArchetype archetype = archetypes.computeIfAbsent(signature, GraphicsArchetype::new);

        int slot = allocateSlot();
        int generation = generations.getOrDefault(slot, 0);
        GraphicsEntityId entityId = new GraphicsEntityId(slot, generation);
        GraphicsChunk.GraphicsChunkPlacement placement = archetype.add(entityId, blueprint);
        locations.put(entityId, new EntityLocation(archetype, placement.chunk(), placement.row()));
        return entityId;
    }

    public synchronized void destroy(GraphicsEntityId entityId) {
        EntityLocation location = locations.remove(entityId);
        if (location == null) {
            return;
        }
        GraphicsChunk.GraphicsChunkRemoval removal = location.chunk.removeSwap(location.row);
        if (removal.movedEntity() != null) {
            EntityLocation movedLocation = locations.get(removal.movedEntity());
            if (movedLocation != null) {
                locations.put(removal.movedEntity(), new EntityLocation(movedLocation.archetype, movedLocation.chunk, removal.movedRow()));
            }
        }
        generations.put(entityId.slot(), entityId.generation() + 1);
        freeSlots.addLast(entityId.slot());
    }

    public synchronized boolean contains(GraphicsEntityId entityId) {
        return locations.containsKey(entityId);
    }

    public synchronized int size() {
        return locations.size();
    }

    public synchronized void clear() {
        archetypes.clear();
        locations.clear();
        generations.clear();
        freeSlots.clear();
        nextSlot = 0;
    }

    public synchronized Set<GraphicsComponentType<?>> signature(GraphicsEntityId entityId) {
        EntityLocation location = locations.get(entityId);
        return location != null ? location.archetype.signature() : Set.of();
    }

    public synchronized GraphicsEntitySchema schemaOf(GraphicsEntityId entityId) {
        EntityLocation location = locations.get(entityId);
        if (location == null) {
            GraphicsCapabilityView emptyView = GraphicsCapabilityResolver.resolve(Set.of());
            return new GraphicsEntitySchema(
                    entityId,
                    Set.of(),
                    emptyView,
                    emptyView.capabilities(),
                    emptyView.authoringDescriptors());
        }
        return location.archetype.schema(entityId);
    }

    public synchronized <T> T component(GraphicsEntityId entityId, GraphicsComponentType<T> componentType) {
        EntityLocation location = locations.get(entityId);
        if (location == null) {
            return null;
        }
        return location.chunk.component(location.row, componentType);
    }

    public synchronized <T> void replaceComponent(GraphicsEntityId entityId, GraphicsComponentType<T> componentType, T value) {
        EntityLocation location = locations.get(entityId);
        if (location == null) {
            throw new IllegalArgumentException("Unknown graphics entity: " + entityId);
        }
        location.chunk.replace(location.row, componentType, value);
    }

    public synchronized RootSubjectSnapshot rootSubjectSnapshot(GraphicsEntityId entityId) {
        EntityLocation location = locations.get(entityId);
        if (location == null) {
            return null;
        }
        return snapshotRootSubject(location);
    }

    public synchronized StageEntitySnapshot stageEntitySnapshot(GraphicsEntityId entityId) {
        EntityLocation location = locations.get(entityId);
        if (location == null) {
            return null;
        }
        return snapshotStageEntity(entityId, location);
    }

    public synchronized List<StageEntitySnapshot> stageEntitySnapshots(List<GraphicsEntityId> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        List<StageEntitySnapshot> snapshots = new ArrayList<>(entityIds.size());
        for (GraphicsEntityId entityId : entityIds) {
            if (entityId == null) {
                continue;
            }
            EntityLocation location = locations.get(entityId);
            if (location == null) {
                continue;
            }
            snapshots.add(snapshotStageEntity(entityId, location));
        }
        return snapshots.isEmpty() ? List.of() : List.copyOf(snapshots);
    }

    private RootSubjectSnapshot snapshotRootSubject(EntityLocation location) {
        GraphicsChunk chunk = location.chunk;
        int row = location.row;
        return new RootSubjectSnapshot(
                chunk.component(row, GraphicsBuiltinComponents.LIFECYCLE),
                chunk.component(row, GraphicsBuiltinComponents.BOUNDS),
                chunk.component(row, GraphicsBuiltinComponents.OBJECT_FLAGS));
    }

    private StageEntitySnapshot snapshotStageEntity(GraphicsEntityId entityId, EntityLocation location) {
        GraphicsChunk chunk = location.chunk;
        int row = location.row;
        return new StageEntitySnapshot(
                entityId,
                location.archetype.schema(entityId),
                chunk.component(row, GraphicsBuiltinComponents.IDENTITY),
                chunk.component(row, GraphicsBuiltinComponents.RESOURCE_ORIGIN),
                chunk.component(row, GraphicsBuiltinComponents.GRAPHICS_TAGS),
                chunk.component(row, GraphicsBuiltinComponents.OBJECT_FLAGS),
                chunk.component(row, GraphicsBuiltinComponents.LIFECYCLE),
                chunk.component(row, GraphicsBuiltinComponents.STAGE_BINDING),
                chunk.component(row, GraphicsBuiltinComponents.CONTAINER_HINT),
                chunk.component(row, GraphicsBuiltinComponents.RASTER_RENDERABLE),
                chunk.component(row, GraphicsBuiltinComponents.COMPUTE_DISPATCH),
                chunk.component(row, GraphicsBuiltinComponents.FUNCTION_INVOKE),
                chunk.component(row, GraphicsBuiltinComponents.SUBMISSION_CAPABILITY),
                chunk.component(row, GraphicsBuiltinComponents.BOUNDS),
                chunk.component(row, GraphicsBuiltinComponents.PREPARED_MESH),
                chunk.component(row, GraphicsBuiltinComponents.RENDER_DESCRIPTOR),
                chunk.component(row, GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING),
                chunk.component(row, GraphicsBuiltinComponents.TRANSFORM_BINDING),
                chunk.component(row, GraphicsBuiltinComponents.TRANSFORM_HIERARCHY),
                chunk.component(row, GraphicsBuiltinComponents.TICK_DRIVER),
                chunk.component(row, GraphicsBuiltinComponents.ASYNC_TICK_DRIVER),
                chunk.component(row, GraphicsBuiltinComponents.DESCRIPTOR_VERSION),
                chunk.component(row, GraphicsBuiltinComponents.GEOMETRY_VERSION),
                chunk.component(row, GraphicsBuiltinComponents.BOUNDS_VERSION));
    }

    public synchronized List<GraphicsEntityId> query(GraphicsQuery query) {
        List<GraphicsEntityId> result = new ArrayList<>();
        for (GraphicsArchetype archetype : archetypes.values()) {
            if (!query.matches(archetype.signature())) {
                continue;
            }
            for (GraphicsChunk chunk : archetype.chunks()) {
                result.addAll(chunk.entities());
            }
        }
        return List.copyOf(result);
    }

    public synchronized List<GraphicsEntitySchema> schemas(GraphicsQuery query) {
        List<GraphicsEntitySchema> schemas = new ArrayList<>();
        for (GraphicsEntityId entityId : query(query)) {
            schemas.add(schemaOf(entityId));
        }
        return List.copyOf(schemas);
    }

    private int allocateSlot() {
        if (!freeSlots.isEmpty()) {
            return freeSlots.removeFirst();
        }
        return nextSlot++;
    }

    private Set<GraphicsComponentType<?>> normalizedSignature(Set<GraphicsComponentType<?>> signature) {
        return Collections.unmodifiableSet(new TreeSet<>(signature));
    }

    private record EntityLocation(
            GraphicsArchetype archetype,
            GraphicsChunk chunk,
            int row
    ) {
    }

    public record RootSubjectSnapshot(
            GraphicsBuiltinComponents.LifecycleComponent lifecycle,
            GraphicsBuiltinComponents.BoundsComponent bounds,
            GraphicsBuiltinComponents.ObjectFlagsComponent objectFlags
    ) {
    }

    public record StageEntitySnapshot(
            GraphicsEntityId entityId,
            GraphicsEntitySchema schema,
            GraphicsBuiltinComponents.IdentityComponent identity,
            GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin,
            GraphicsBuiltinComponents.GraphicsTagsComponent tags,
            GraphicsBuiltinComponents.ObjectFlagsComponent objectFlags,
            GraphicsBuiltinComponents.LifecycleComponent lifecycle,
            GraphicsBuiltinComponents.StageBindingComponent stageBinding,
            GraphicsBuiltinComponents.ContainerHintComponent containerHint,
            GraphicsBuiltinComponents.RasterRenderableComponent rasterRenderable,
            GraphicsBuiltinComponents.ComputeDispatchComponent computeDispatch,
            GraphicsBuiltinComponents.FunctionInvokeComponent functionInvoke,
            GraphicsBuiltinComponents.SubmissionCapabilityComponent submissionCapability,
            GraphicsBuiltinComponents.BoundsComponent bounds,
            GraphicsBuiltinComponents.PreparedMeshComponent preparedMesh,
            GraphicsBuiltinComponents.RenderDescriptorComponent renderDescriptor,
            GraphicsBuiltinComponents.InstanceVertexAuthoringComponent instanceVertexAuthoring,
            GraphicsBuiltinComponents.TransformBindingComponent transformBinding,
            GraphicsBuiltinComponents.TransformHierarchyComponent transformHierarchy,
            GraphicsBuiltinComponents.TickDriverComponent tickDriver,
            GraphicsBuiltinComponents.AsyncTickDriverComponent asyncTickDriver,
            GraphicsBuiltinComponents.DescriptorVersionComponent descriptorVersion,
            GraphicsBuiltinComponents.GeometryVersionComponent geometryVersion,
            GraphicsBuiltinComponents.BoundsVersionComponent boundsVersion
    ) {
    }
}
