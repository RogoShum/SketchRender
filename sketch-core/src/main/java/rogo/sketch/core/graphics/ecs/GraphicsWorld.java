package rogo.sketch.core.graphics.ecs;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Central ECS world for graphics entities.
 */
public final class GraphicsWorld {
    private final Map<Set<GraphicsComponentType<?>>, GraphicsArchetype> archetypes = new LinkedHashMap<>();
    private final Map<Integer, Integer> generations = new HashMap<>();
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private final Map<GraphicsEntityId, EntityLocation> locations = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private int nextSlot = 0;

    public GraphicsEntityId spawn(GraphicsEntityBlueprint blueprint) {
        Objects.requireNonNull(blueprint, "blueprint");
        writeLock.lock();
        try {
            Set<GraphicsComponentType<?>> signature = normalizedSignature(blueprint.componentTypes());
            GraphicsArchetype archetype = archetypes.computeIfAbsent(signature, GraphicsArchetype::new);

            int slot = allocateSlot();
            int generation = generations.getOrDefault(slot, 0);
            GraphicsEntityId entityId = new GraphicsEntityId(slot, generation);
            GraphicsChunk.GraphicsChunkPlacement placement = archetype.add(entityId, blueprint);
            locations.put(entityId, new EntityLocation(archetype, placement.chunk(), placement.row()));
            return entityId;
        } finally {
            writeLock.unlock();
        }
    }

    public void destroy(GraphicsEntityId entityId) {
        writeLock.lock();
        try {
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
        } finally {
            writeLock.unlock();
        }
    }

    public boolean contains(GraphicsEntityId entityId) {
        readLock.lock();
        try {
            return locations.containsKey(entityId);
        } finally {
            readLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return locations.size();
        } finally {
            readLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            archetypes.clear();
            locations.clear();
            generations.clear();
            freeSlots.clear();
            nextSlot = 0;
        } finally {
            writeLock.unlock();
        }
    }

    public Set<GraphicsComponentType<?>> signature(GraphicsEntityId entityId) {
        readLock.lock();
        try {
            EntityLocation location = locations.get(entityId);
            return location != null ? location.archetype.signature() : Set.of();
        } finally {
            readLock.unlock();
        }
    }

    public GraphicsEntitySchema schemaOf(GraphicsEntityId entityId) {
        readLock.lock();
        try {
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
        } finally {
            readLock.unlock();
        }
    }

    public <T> T component(GraphicsEntityId entityId, GraphicsComponentType<T> componentType) {
        readLock.lock();
        try {
            EntityLocation location = locations.get(entityId);
            if (location == null) {
                return null;
            }
            return location.chunk.component(location.row, componentType);
        } finally {
            readLock.unlock();
        }
    }

    public <T> void replaceComponent(GraphicsEntityId entityId, GraphicsComponentType<T> componentType, T value) {
        writeLock.lock();
        try {
            EntityLocation location = locations.get(entityId);
            if (location == null) {
                throw new IllegalArgumentException("Unknown graphics entity: " + entityId);
            }
            location.chunk.replace(location.row, componentType, value);
        } finally {
            writeLock.unlock();
        }
    }

    public RootSubjectSnapshot rootSubjectSnapshot(GraphicsEntityId entityId) {
        readLock.lock();
        try {
            EntityLocation location = locations.get(entityId);
            if (location == null) {
                return null;
            }
            return snapshotRootSubject(location);
        } finally {
            readLock.unlock();
        }
    }

    public StageEntitySnapshot stageEntitySnapshot(GraphicsEntityId entityId) {
        readLock.lock();
        try {
            EntityLocation location = locations.get(entityId);
            if (location == null) {
                return null;
            }
            return snapshotStageEntity(entityId, location);
        } finally {
            readLock.unlock();
        }
    }

    public List<StageEntitySnapshot> stageEntitySnapshots(List<GraphicsEntityId> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        readLock.lock();
        try {
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
        } finally {
            readLock.unlock();
        }
    }

    public List<GraphicsEntityId> query(GraphicsQuery query) {
        readLock.lock();
        try {
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
        } finally {
            readLock.unlock();
        }
    }

    public List<GraphicsEntitySchema> schemas(GraphicsQuery query) {
        List<GraphicsEntityId> entityIds = query(query);
        if (entityIds.isEmpty()) {
            return List.of();
        }
        readLock.lock();
        try {
            List<GraphicsEntitySchema> schemas = new ArrayList<>(entityIds.size());
            for (GraphicsEntityId entityId : entityIds) {
                EntityLocation location = locations.get(entityId);
                if (location != null) {
                    schemas.add(location.archetype.schema(entityId));
                }
            }
            return schemas.isEmpty() ? List.of() : List.copyOf(schemas);
        } finally {
            readLock.unlock();
        }
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
                chunk.component(row, GraphicsBuiltinComponents.STAGE_ROUTES),
                chunk.component(row, GraphicsBuiltinComponents.CONTAINER_HINT),
                chunk.component(row, GraphicsBuiltinComponents.RASTER_RENDERABLE),
                chunk.component(row, GraphicsBuiltinComponents.COMPUTE_DISPATCH),
                chunk.component(row, GraphicsBuiltinComponents.FUNCTION_INVOKE),
                chunk.component(row, GraphicsBuiltinComponents.SUBMISSION_CAPABILITY),
                chunk.component(row, GraphicsBuiltinComponents.BOUNDS),
                chunk.component(row, GraphicsBuiltinComponents.PREPARED_MESH),
                chunk.component(row, GraphicsBuiltinComponents.RENDER_DESCRIPTOR),
                chunk.component(row, GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING),
                chunk.component(row, GraphicsBuiltinComponents.INSTANCE_COUNT),
                chunk.component(row, GraphicsBuiltinComponents.TRANSFORM_BINDING),
                chunk.component(row, GraphicsBuiltinComponents.TRANSFORM_HIERARCHY),
                chunk.component(row, GraphicsBuiltinComponents.TICK_DRIVER),
                chunk.component(row, GraphicsBuiltinComponents.ASYNC_TICK_DRIVER),
                chunk.component(row, GraphicsBuiltinComponents.DESCRIPTOR_VERSION),
                chunk.component(row, GraphicsBuiltinComponents.GEOMETRY_VERSION),
                chunk.component(row, GraphicsBuiltinComponents.BOUNDS_VERSION),
                collectExtensionComponents(chunk, row));
    }

    private Map<GraphicsComponentType<?>, Object> collectExtensionComponents(GraphicsChunk chunk, int row) {
        Map<GraphicsComponentType<?>, Object> rowSnapshot = chunk.snapshotRow(row);
        if (rowSnapshot.isEmpty()) {
            return Map.of();
        }
        Map<GraphicsComponentType<?>, Object> extensions = new LinkedHashMap<>();
        for (Map.Entry<GraphicsComponentType<?>, Object> entry : rowSnapshot.entrySet()) {
            if (!StageEntitySnapshot.supportsComponent(entry.getKey())) {
                extensions.put(entry.getKey(), entry.getValue());
            }
        }
        return extensions.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
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
            GraphicsBuiltinComponents.StageRoutesComponent stageRoutes,
            GraphicsBuiltinComponents.ContainerHintComponent containerHint,
            GraphicsBuiltinComponents.RasterRenderableComponent rasterRenderable,
            GraphicsBuiltinComponents.ComputeDispatchComponent computeDispatch,
            GraphicsBuiltinComponents.FunctionInvokeComponent functionInvoke,
            GraphicsBuiltinComponents.SubmissionCapabilityComponent submissionCapability,
            GraphicsBuiltinComponents.BoundsComponent bounds,
            GraphicsBuiltinComponents.PreparedMeshComponent preparedMesh,
            GraphicsBuiltinComponents.RenderDescriptorComponent renderDescriptor,
            GraphicsBuiltinComponents.InstanceVertexAuthoringComponent instanceVertexAuthoring,
            GraphicsBuiltinComponents.InstanceCountComponent instanceCount,
            GraphicsBuiltinComponents.TransformBindingComponent transformBinding,
            GraphicsBuiltinComponents.TransformHierarchyComponent transformHierarchy,
            GraphicsBuiltinComponents.TickDriverComponent tickDriver,
            GraphicsBuiltinComponents.AsyncTickDriverComponent asyncTickDriver,
            GraphicsBuiltinComponents.DescriptorVersionComponent descriptorVersion,
            GraphicsBuiltinComponents.GeometryVersionComponent geometryVersion,
            GraphicsBuiltinComponents.BoundsVersionComponent boundsVersion,
            Map<GraphicsComponentType<?>, Object> extensionComponents
    ) {
        private static final Map<GraphicsComponentType<?>, Function<StageEntitySnapshot, Object>> SNAPSHOT_ACCESSORS = buildAccessorMap();

        public StageEntitySnapshot {
            extensionComponents = extensionComponents == null || extensionComponents.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(extensionComponents));
        }

        public Object component(GraphicsComponentType<?> componentType) {
            if (componentType == null) {
                return null;
            }
            Function<StageEntitySnapshot, Object> accessor = SNAPSHOT_ACCESSORS.get(componentType);
            if (accessor != null) {
                return accessor.apply(this);
            }
            return extensionComponents.get(componentType);
        }

        public Map<GraphicsComponentType<?>, Object> componentSnapshot() {
            Map<GraphicsComponentType<?>, Object> snapshot = new LinkedHashMap<>();
            for (Map.Entry<GraphicsComponentType<?>, Function<StageEntitySnapshot, Object>> entry : SNAPSHOT_ACCESSORS.entrySet()) {
                Object value = entry.getValue().apply(this);
                if (value != null) {
                    snapshot.put(entry.getKey(), value);
                }
            }
            if (!extensionComponents.isEmpty()) {
                snapshot.putAll(extensionComponents);
            }
            return snapshot.isEmpty() ? Map.of() : Collections.unmodifiableMap(snapshot);
        }

        static boolean supportsComponent(GraphicsComponentType<?> componentType) {
            return componentType != null && SNAPSHOT_ACCESSORS.containsKey(componentType);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Map<GraphicsComponentType<?>, Function<StageEntitySnapshot, Object>> buildAccessorMap() {
            Map<GraphicsComponentType<?>, Function<StageEntitySnapshot, Object>> accessors = new LinkedHashMap<>();
            accessors.put(GraphicsBuiltinComponents.IDENTITY, snapshot -> snapshot.identity());
            accessors.put(GraphicsBuiltinComponents.RESOURCE_ORIGIN, snapshot -> snapshot.resourceOrigin());
            accessors.put(GraphicsBuiltinComponents.GRAPHICS_TAGS, snapshot -> snapshot.tags());
            accessors.put(GraphicsBuiltinComponents.OBJECT_FLAGS, snapshot -> snapshot.objectFlags());
            accessors.put(GraphicsBuiltinComponents.LIFECYCLE, snapshot -> snapshot.lifecycle());
            accessors.put(GraphicsBuiltinComponents.STAGE_BINDING, snapshot -> snapshot.stageBinding());
            accessors.put(GraphicsBuiltinComponents.STAGE_ROUTES, snapshot -> snapshot.stageRoutes());
            accessors.put(GraphicsBuiltinComponents.CONTAINER_HINT, snapshot -> snapshot.containerHint());
            accessors.put(GraphicsBuiltinComponents.RASTER_RENDERABLE, snapshot -> snapshot.rasterRenderable());
            accessors.put(GraphicsBuiltinComponents.COMPUTE_DISPATCH, snapshot -> snapshot.computeDispatch());
            accessors.put(GraphicsBuiltinComponents.FUNCTION_INVOKE, snapshot -> snapshot.functionInvoke());
            accessors.put(GraphicsBuiltinComponents.SUBMISSION_CAPABILITY, snapshot -> snapshot.submissionCapability());
            accessors.put(GraphicsBuiltinComponents.BOUNDS, snapshot -> snapshot.bounds());
            accessors.put(GraphicsBuiltinComponents.PREPARED_MESH, snapshot -> snapshot.preparedMesh());
            accessors.put(GraphicsBuiltinComponents.RENDER_DESCRIPTOR, snapshot -> snapshot.renderDescriptor());
            accessors.put(GraphicsBuiltinComponents.INSTANCE_VERTEX_AUTHORING, snapshot -> snapshot.instanceVertexAuthoring());
            accessors.put(GraphicsBuiltinComponents.INSTANCE_COUNT, snapshot -> snapshot.instanceCount());
            accessors.put(GraphicsBuiltinComponents.TRANSFORM_BINDING, snapshot -> snapshot.transformBinding());
            accessors.put(GraphicsBuiltinComponents.TRANSFORM_HIERARCHY, snapshot -> snapshot.transformHierarchy());
            accessors.put(GraphicsBuiltinComponents.TICK_DRIVER, snapshot -> snapshot.tickDriver());
            accessors.put(GraphicsBuiltinComponents.ASYNC_TICK_DRIVER, snapshot -> snapshot.asyncTickDriver());
            accessors.put(GraphicsBuiltinComponents.DESCRIPTOR_VERSION, snapshot -> snapshot.descriptorVersion());
            accessors.put(GraphicsBuiltinComponents.GEOMETRY_VERSION, snapshot -> snapshot.geometryVersion());
            accessors.put(GraphicsBuiltinComponents.BOUNDS_VERSION, snapshot -> snapshot.boundsVersion());
            return Collections.unmodifiableMap(accessors);
        }
    }
}
