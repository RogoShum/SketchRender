package rogo.sketch.core.object;

import org.joml.primitives.AABBf;
import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.extension.event.ObjectLifecycleEventBus;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsComponentType;
import rogo.sketch.core.graphics.ecs.GraphicsEntityBlueprint;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.module.diagnostic.SketchDiagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Host-object to root-graphics registry used by stage 8 lifecycle ingress.
 */
public final class ObjectGraphicsRegistry {
    private static final String DIAGNOSTIC_MODULE = "object_graphics_registry";

    private final GraphicsPipeline<?> pipeline;
    private final ObjectLifecycleEventBus lifecycleEventBus;
    private final AtomicLong nextHandleId = new AtomicLong(1L);
    private final EnumMap<ObjectHostKind, KindRegistration> registrations =
            new EnumMap<>(ObjectHostKind.class);
    private final IdentityHashMap<Object, Map<ObjectGraphicsRootRole, Registration>> registrationsByHost =
            new IdentityHashMap<>();
    private final Map<Long, Registration> registrationsByHandle = new LinkedHashMap<>();

    public ObjectGraphicsRegistry(
            GraphicsPipeline<?> pipeline,
            ObjectLifecycleEventBus lifecycleEventBus) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.lifecycleEventBus = Objects.requireNonNull(lifecycleEventBus, "lifecycleEventBus");
        for (ObjectHostKind kind : ObjectHostKind.values()) {
            registrations.put(kind, new KindRegistration());
        }
    }

    public synchronized void registerExactFactory(
            ObjectHostKind hostKind,
            Object exactTypeKey,
            String ownerId,
            ObjectGraphicsFactory<?> factory) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(exactTypeKey, "exactTypeKey");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(factory, "factory");
        registrations.get(hostKind).exactFactories.put(exactTypeKey, new FactoryRegistration(ownerId, factory));
    }

    public synchronized void registerClassFactory(
            ObjectHostKind hostKind,
            Class<?> hostClass,
            String ownerId,
            ObjectGraphicsFactory<?> factory) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(hostClass, "hostClass");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(factory, "factory");
        registrations.get(hostKind).classFactories.put(hostClass, new FactoryRegistration(ownerId, factory));
    }

    public synchronized void registerFallbackFactory(
            ObjectHostKind hostKind,
            String ownerId,
            ObjectGraphicsFactory<?> factory) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(factory, "factory");
        registrations.get(hostKind).fallbackFactory = new FactoryRegistration(ownerId, factory);
    }

    public synchronized void registerExactAugmentor(
            ObjectHostKind hostKind,
            Object exactTypeKey,
            String ownerId,
            ObjectGraphicsAugmentor<?> augmentor) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(exactTypeKey, "exactTypeKey");
        registerAugmentorList(
                registrations.get(hostKind).exactAugmentors
                        .computeIfAbsent(exactTypeKey, ignored -> new ArrayList<>()),
                ownerId,
                augmentor);
    }

    public synchronized void registerClassAugmentor(
            ObjectHostKind hostKind,
            Class<?> hostClass,
            String ownerId,
            ObjectGraphicsAugmentor<?> augmentor) {
        Objects.requireNonNull(hostKind, "hostKind");
        Objects.requireNonNull(hostClass, "hostClass");
        registerAugmentorList(
                registrations.get(hostKind).classAugmentors
                        .computeIfAbsent(hostClass, ignored -> new ArrayList<>()),
                ownerId,
                augmentor);
    }

    public synchronized void registerFallbackAugmentor(
            ObjectHostKind hostKind,
            String ownerId,
            ObjectGraphicsAugmentor<?> augmentor) {
        Objects.requireNonNull(hostKind, "hostKind");
        registerAugmentorList(registrations.get(hostKind).fallbackAugmentors, ownerId, augmentor);
    }

    public synchronized @Nullable ObjectGraphicsHandle resolveHandle(
            Object hostObject,
            ObjectGraphicsRootRole rootRole) {
        Map<ObjectGraphicsRootRole, Registration> roleMap = registrationsByHost.get(hostObject);
        if (roleMap == null) {
            return null;
        }
        Registration registration = roleMap.get(rootRole);
        return registration != null ? registration.handle : null;
    }

    public synchronized @Nullable GraphicsEntityId resolveRootEntityId(ObjectGraphicsHandle handle) {
        if (handle == null) {
            return null;
        }
        Registration registration = registrationsByHandle.get(handle.handleId());
        return registration != null ? registration.entityId : null;
    }

    public synchronized boolean containsHost(Object hostObject, ObjectGraphicsRootRole rootRole) {
        return resolveHandle(hostObject, rootRole) != null;
    }

    public synchronized @Nullable ObjectGraphicsHandle syncHostObject(
            Object hostObject,
            ObjectHostKind hostKind,
            Object exactTypeKey,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            @Nullable AABBf bounds,
            int flags) {
        if (hostObject == null || hostKind == null || rootRole == null || context == null) {
            return null;
        }
        Registration registration = lookupRegistration(hostObject, rootRole);
        if (registration == null) {
            registration = spawnRoot(hostObject, hostKind, exactTypeKey, rootRole, context, bounds, flags);
        }
        if (registration == null) {
            return null;
        }
        lifecycleEventBus.post(ObjectLifecycleEventBus.OBJECT_SYNC, new ObjectSyncEvent(
                hostObject,
                hostKind,
                registration.handle,
                rootRole,
                registration.entityId,
                context.logicTick()));
        return registration.handle;
    }

    public synchronized void destroyHostObject(
            Object hostObject,
            ObjectGraphicsRootRole rootRole,
            int logicTick) {
        if (hostObject == null || rootRole == null) {
            return;
        }
        Map<ObjectGraphicsRootRole, Registration> roleMap = registrationsByHost.get(hostObject);
        if (roleMap == null) {
            return;
        }
        Registration registration = roleMap.remove(rootRole);
        if (registration == null) {
            return;
        }
        if (roleMap.isEmpty()) {
            registrationsByHost.remove(hostObject);
        }
        registrationsByHandle.remove(registration.handle.handleId());
        lifecycleEventBus.post(ObjectLifecycleEventBus.OBJECT_DESPAWN, new ObjectDespawnEvent(
                hostObject,
                registration.hostKind,
                registration.handle,
                rootRole,
                registration.entityId,
                logicTick));
        pipeline.destroyGraphicsEntity(registration.entityId);
    }

    public synchronized void destroyAll(int logicTick) {
        List<Registration> snapshot = new ArrayList<>(registrationsByHandle.values());
        registrationsByHandle.clear();
        registrationsByHost.clear();
        for (Registration registration : snapshot) {
            lifecycleEventBus.post(ObjectLifecycleEventBus.OBJECT_DESPAWN, new ObjectDespawnEvent(
                    registration.hostObject,
                    registration.hostKind,
                    registration.handle,
                    registration.rootRole,
                    registration.entityId,
                    logicTick));
            pipeline.destroyGraphicsEntity(registration.entityId);
        }
    }

    private Registration spawnRoot(
            Object hostObject,
            ObjectHostKind hostKind,
            Object exactTypeKey,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            @Nullable AABBf bounds,
            int flags) {
        FactoryRegistration factory = resolveFactory(hostKind, exactTypeKey, hostObject.getClass());
        if (factory == null) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "No object graphics factory for host kind " + hostKind + " and class " + hostObject.getClass().getName());
            return null;
        }

        MutableBlueprint mutableBlueprint = new MutableBlueprint();
        applyFactory(factory, hostObject, rootRole, context, mutableBlueprint.writer(factory.ownerId()));
        for (AugmentorRegistration augmentor : resolveAugmentors(hostKind, exactTypeKey, hostObject.getClass())) {
            applyAugmentor(augmentor, hostObject, rootRole, context, mutableBlueprint.writer(augmentor.ownerId()));
        }
        validateRoot(hostObject, hostKind, mutableBlueprint);

        GraphicsEntityBlueprint blueprint = mutableBlueprint.build();
        GraphicsEntityId entityId = pipeline.spawnGraphicsEntity(blueprint);
        ObjectGraphicsHandle handle = new ObjectGraphicsHandle(nextHandleId.getAndIncrement(), rootRole);
        Registration registration = new Registration(hostObject, hostKind, rootRole, handle, entityId);
        registrationsByHost
                .computeIfAbsent(hostObject, ignored -> new LinkedHashMap<>())
                .put(rootRole, registration);
        registrationsByHandle.put(handle.handleId(), registration);

        GraphicsEntitySchema schema = pipeline.graphicsWorld().schemaOf(entityId);
        lifecycleEventBus.post(ObjectLifecycleEventBus.OBJECT_SPAWN, new ObjectSpawnEvent(
                hostObject,
                hostKind,
                handle,
                rootRole,
                entityId,
                schema,
                bounds != null ? new AABBf(bounds) : null,
                flags,
                context.logicTick()));
        return registration;
    }

    private @Nullable Registration lookupRegistration(Object hostObject, ObjectGraphicsRootRole rootRole) {
        Map<ObjectGraphicsRootRole, Registration> roleMap = registrationsByHost.get(hostObject);
        return roleMap != null ? roleMap.get(rootRole) : null;
    }

    private @Nullable FactoryRegistration resolveFactory(
            ObjectHostKind hostKind,
            Object exactTypeKey,
            Class<?> hostClass) {
        KindRegistration kindRegistration = registrations.get(hostKind);
        FactoryRegistration exactFactory = kindRegistration.exactFactories.get(exactTypeKey);
        if (exactFactory != null) {
            return exactFactory;
        }
        for (Class<?> current = hostClass; current != null; current = current.getSuperclass()) {
            FactoryRegistration classFactory = kindRegistration.classFactories.get(current);
            if (classFactory != null) {
                return classFactory;
            }
        }
        return kindRegistration.fallbackFactory;
    }

    private List<AugmentorRegistration> resolveAugmentors(
            ObjectHostKind hostKind,
            Object exactTypeKey,
            Class<?> hostClass) {
        KindRegistration kindRegistration = registrations.get(hostKind);
        List<AugmentorRegistration> ordered = new ArrayList<>();
        List<AugmentorRegistration> exact = kindRegistration.exactAugmentors.get(exactTypeKey);
        if (exact != null) {
            ordered.addAll(exact);
        }
        for (Class<?> current = hostClass; current != null; current = current.getSuperclass()) {
            List<AugmentorRegistration> augmentors = kindRegistration.classAugmentors.get(current);
            if (augmentors != null) {
                ordered.addAll(augmentors);
            }
        }
        ordered.addAll(kindRegistration.fallbackAugmentors);
        ordered.sort(Comparator.comparing(AugmentorRegistration::ownerId));
        return List.copyOf(ordered);
    }

    @SuppressWarnings("unchecked")
    private static void applyFactory(
            FactoryRegistration registration,
            Object hostObject,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            ObjectGraphicsBlueprintWriter writer) {
        ((ObjectGraphicsFactory<Object>) registration.factory)
                .contributeRoot(hostObject, rootRole, context, writer);
    }

    @SuppressWarnings("unchecked")
    private static void applyAugmentor(
            AugmentorRegistration registration,
            Object hostObject,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            ObjectGraphicsBlueprintWriter writer) {
        ((ObjectGraphicsAugmentor<Object>) registration.augmentor)
                .augment(hostObject, rootRole, context, writer);
    }

    private static void registerAugmentorList(
            List<AugmentorRegistration> registrations,
            String ownerId,
            ObjectGraphicsAugmentor<?> augmentor) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(augmentor, "augmentor");
        registrations.add(new AugmentorRegistration(ownerId, augmentor));
    }

    private static void validateRoot(
            Object hostObject,
            ObjectHostKind hostKind,
            MutableBlueprint blueprint) {
        if (!blueprint.has(GraphicsBuiltinComponents.IDENTITY)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName() + " is missing IDENTITY");
        }
        if (!blueprint.has(GraphicsBuiltinComponents.LIFECYCLE)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName() + " is missing LIFECYCLE");
        }
        if (!blueprint.has(GraphicsBuiltinComponents.BOUNDS)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName() + " is missing BOUNDS");
        }
        if (!blueprint.has(GraphicsBuiltinComponents.TRANSFORM_BINDING)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName() + " is missing TRANSFORM_BINDING");
        }
        if (blueprint.has(GraphicsBuiltinComponents.BOUNDS_BINDING) && !blueprint.has(GraphicsBuiltinComponents.BOUNDS)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName()
                            + " has BOUNDS_BINDING without concrete BOUNDS");
        }
        if (blueprint.has(GraphicsBuiltinComponents.GRAPHICS_TAGS_BINDING) && !blueprint.has(GraphicsBuiltinComponents.GRAPHICS_TAGS)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName()
                            + " has GRAPHICS_TAGS_BINDING without concrete GRAPHICS_TAGS");
        }
        if (blueprint.has(GraphicsBuiltinComponents.OBJECT_FLAGS_BINDING) && !blueprint.has(GraphicsBuiltinComponents.OBJECT_FLAGS)) {
            SketchDiagnostics.get().warn(
                    DIAGNOSTIC_MODULE,
                    "Root graphics for " + hostKind + " " + hostObject.getClass().getName()
                            + " has OBJECT_FLAGS_BINDING without concrete OBJECT_FLAGS");
        }
    }

    private static final class KindRegistration {
        private final Map<Object, FactoryRegistration> exactFactories = new HashMap<>();
        private final Map<Class<?>, FactoryRegistration> classFactories = new LinkedHashMap<>();
        private FactoryRegistration fallbackFactory;
        private final Map<Object, List<AugmentorRegistration>> exactAugmentors = new HashMap<>();
        private final Map<Class<?>, List<AugmentorRegistration>> classAugmentors = new LinkedHashMap<>();
        private final List<AugmentorRegistration> fallbackAugmentors = new ArrayList<>();
    }

    private record FactoryRegistration(
            String ownerId,
            ObjectGraphicsFactory<?> factory
    ) {
    }

    private record AugmentorRegistration(
            String ownerId,
            ObjectGraphicsAugmentor<?> augmentor
    ) {
    }

    private record Registration(
            Object hostObject,
            ObjectHostKind hostKind,
            ObjectGraphicsRootRole rootRole,
            ObjectGraphicsHandle handle,
            GraphicsEntityId entityId
    ) {
    }

    private static final class MutableBlueprint {
        private final Map<GraphicsComponentType<?>, Object> components = new LinkedHashMap<>();
        private final Map<GraphicsComponentType<?>, String> owners = new LinkedHashMap<>();

        public boolean has(GraphicsComponentType<?> componentType) {
            return components.containsKey(componentType);
        }

        public ObjectGraphicsBlueprintWriter writer(String ownerId) {
            return new ObjectGraphicsBlueprintWriter() {
                @Override
                public <T> void put(GraphicsComponentType<T> componentType, T value) {
                    Objects.requireNonNull(componentType, "componentType");
                    Objects.requireNonNull(value, "value");
                    String existingOwner = owners.get(componentType);
                    if (existingOwner != null && !existingOwner.equals(ownerId)) {
                        String message = "Component conflict on " + componentType.id()
                                + " between '" + existingOwner + "' and '" + ownerId + "'";
                        SketchDiagnostics.get().warn(DIAGNOSTIC_MODULE, message);
                        throw new IllegalStateException(message);
                    }
                    owners.put(componentType, ownerId);
                    components.put(componentType, value);
                }

                @Override
                public void remove(GraphicsComponentType<?> componentType) {
                    String existingOwner = owners.get(componentType);
                    if (existingOwner != null && !existingOwner.equals(ownerId)) {
                        String message = "Component removal conflict on " + componentType.id()
                                + " between '" + existingOwner + "' and '" + ownerId + "'";
                        SketchDiagnostics.get().warn(DIAGNOSTIC_MODULE, message);
                        throw new IllegalStateException(message);
                    }
                    owners.remove(componentType);
                    components.remove(componentType);
                }

                @Override
                public boolean has(GraphicsComponentType<?> componentType) {
                    return components.containsKey(componentType);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> T component(GraphicsComponentType<T> componentType) {
                    return (T) components.get(componentType);
                }
            };
        }

        public GraphicsEntityBlueprint build() {
            GraphicsEntityBlueprint.Builder builder = GraphicsEntityBlueprint.builder();
            for (Map.Entry<GraphicsComponentType<?>, Object> entry : components.entrySet()) {
                putUnchecked(builder, entry.getKey(), entry.getValue());
            }
            return builder.build();
        }

        @SuppressWarnings("unchecked")
        private static <T> void putUnchecked(
                GraphicsEntityBlueprint.Builder builder,
                GraphicsComponentType<?> componentType,
                Object value) {
            builder.put((GraphicsComponentType<T>) componentType, (T) value);
        }
    }
}
