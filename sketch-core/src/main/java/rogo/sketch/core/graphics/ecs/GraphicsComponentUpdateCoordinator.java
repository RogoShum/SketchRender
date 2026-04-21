package rogo.sketch.core.graphics.ecs;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.pipeline.module.runtime.GraphicsEntitySnapshot;
import rogo.sketch.core.pipeline.kernel.PassExecutionContext;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.transform.manager.TransformManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared update-domain scheduler for transform and authored ECS root bindings.
 */
public final class GraphicsComponentUpdateCoordinator {
    private static final long UNVERSIONED_SAMPLE = Long.MIN_VALUE;
    private final TransformManager transformManager = new TransformManager();
    private final Object bindingLock = new Object();
    private final Map<GraphicsEntityId, LifecycleBindingState> lifecycleBindings = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, BoundsBindingState> boundsBindings = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, TagsBindingState> tagsBindings = new LinkedHashMap<>();
    private final Map<GraphicsEntityId, FlagsBindingState> flagsBindings = new LinkedHashMap<>();
    private final List<AbstractBindingState<?>> syncTickBindings = new ArrayList<>();
    private final List<AbstractBindingState<?>> asyncTickBindings = new ArrayList<>();
    private final List<AbstractBindingState<?>> frameBindings = new ArrayList<>();
    private final List<AbstractBindingState<?>> tickSwapBindings = new ArrayList<>();
    private final List<AbstractBindingState<?>> dirtyBindings = new ArrayList<>();

    public void registerEntity(GraphicsWorld world, GraphicsEntityId entityId, GraphicsEntitySnapshot snapshot) {
        if (world == null || entityId == null || snapshot == null) {
            return;
        }

        GraphicsBuiltinComponents.TransformBindingComponent transformBinding =
                snapshot.component(GraphicsBuiltinComponents.TRANSFORM_BINDING);
        if (transformBinding != null && transformBinding.updateDomain() != null && transformBinding.authoring() != null) {
            if (!transformManager.isRegistered(entityId)) {
                transformManager.registerBinding(
                        world,
                        entityId,
                        transformBinding,
                        snapshot.component(GraphicsBuiltinComponents.TRANSFORM_HIERARCHY));
            }
        }

        GraphicsBuiltinComponents.LifecycleBindingComponent lifecycleBinding =
                snapshot.component(GraphicsBuiltinComponents.LIFECYCLE_BINDING);
        if (lifecycleBinding != null && lifecycleBinding.updateDomain() != null && lifecycleBinding.authoring() != null) {
            synchronized (bindingLock) {
                lifecycleBindings.computeIfAbsent(
                        entityId,
                        ignored -> registerBinding(new LifecycleBindingState(entityId, lifecycleBinding.updateDomain(), lifecycleBinding.authoring())));
            }
        }

        GraphicsBuiltinComponents.BoundsBindingComponent boundsBinding =
                snapshot.component(GraphicsBuiltinComponents.BOUNDS_BINDING);
        if (boundsBinding != null && boundsBinding.updateDomain() != null && boundsBinding.authoring() != null) {
            synchronized (bindingLock) {
                boundsBindings.computeIfAbsent(
                        entityId,
                        ignored -> registerBinding(new BoundsBindingState(entityId, boundsBinding.updateDomain(), boundsBinding.authoring())));
            }
        }

        GraphicsBuiltinComponents.GraphicsTagsBindingComponent tagsBinding =
                snapshot.component(GraphicsBuiltinComponents.GRAPHICS_TAGS_BINDING);
        if (tagsBinding != null && tagsBinding.updateDomain() != null && tagsBinding.authoring() != null) {
            synchronized (bindingLock) {
                tagsBindings.computeIfAbsent(
                        entityId,
                        ignored -> registerBinding(new TagsBindingState(entityId, tagsBinding.updateDomain(), tagsBinding.authoring())));
            }
        }

        GraphicsBuiltinComponents.ObjectFlagsBindingComponent flagsBinding =
                snapshot.component(GraphicsBuiltinComponents.OBJECT_FLAGS_BINDING);
        if (flagsBinding != null && flagsBinding.updateDomain() != null && flagsBinding.authoring() != null) {
            synchronized (bindingLock) {
                flagsBindings.computeIfAbsent(
                        entityId,
                        ignored -> registerBinding(new FlagsBindingState(entityId, flagsBinding.updateDomain(), flagsBinding.authoring())));
            }
        }

        applyInitialConcrete(world, entityId);
    }

    public void unregisterEntity(GraphicsWorld world, GraphicsEntityId entityId) {
        if (entityId == null) {
            return;
        }

        synchronized (bindingLock) {
            unregisterBinding(lifecycleBindings.remove(entityId));
            unregisterBinding(boundsBindings.remove(entityId));
            unregisterBinding(tagsBindings.remove(entityId));
            unregisterBinding(flagsBindings.remove(entityId));
        }

        var binding = transformManager.bindingFor(entityId);
        if (binding != null) {
            transformManager.unregisterBinding(binding);
        }

        if (world != null) {
            GraphicsBuiltinComponents.TransformBindingComponent bindingComponent =
                    world.component(entityId, GraphicsBuiltinComponents.TRANSFORM_BINDING);
            if (bindingComponent != null) {
                world.replaceComponent(
                        entityId,
                        GraphicsBuiltinComponents.TRANSFORM_BINDING,
                        new GraphicsBuiltinComponents.TransformBindingComponent(
                                bindingComponent.updateDomain(),
                                bindingComponent.authoring(),
                                -1));
            }
        }
    }

    public void swapTickBuffers() {
        transformManager.swapTickBuffers();
        for (AbstractBindingState<?> binding : snapshotBindings(tickSwapBindings)) {
            if (binding.swapTickBuffers()) {
                queueDirty(binding);
            }
        }
    }

    public void collectSyncTick() {
        transformManager.collectSyncTickTransforms();
        collectBindings(snapshotBindings(syncTickBindings));
    }

    public void collectAsyncTick() {
        transformManager.collectAsyncTickTransforms();
        collectBindings(snapshotBindings(asyncTickBindings));
    }

    public void collectFrame() {
        transformManager.collectFrameTransforms();
        for (AbstractBindingState<?> binding : snapshotBindings(frameBindings)) {
            if (binding.collect(GraphicsUpdateDomain.SYNC_FRAME)) {
                queueDirty(binding);
            }
        }
    }

    public void applyConcreteComponents(GraphicsWorld world) {
        if (world == null) {
            return;
        }
        List<AbstractBindingState<?>> pendingBindings;
        synchronized (bindingLock) {
            if (dirtyBindings.isEmpty()) {
                return;
            }
            pendingBindings = new ArrayList<>(dirtyBindings);
            dirtyBindings.clear();
            for (AbstractBindingState<?> binding : pendingBindings) {
                binding.clearApplyQueued();
            }
        }
        for (AbstractBindingState<?> binding : pendingBindings) {
            binding.apply(world);
        }
    }

    public void prepareAndPublishTickSnapshot(PassExecutionContext passExecutionContext) {
        transformManager.prepareAndPublishTickSnapshot(passExecutionContext);
    }

    public void prepareFrameBuffer(PassExecutionContext passExecutionContext) {
        transformManager.prepareFrameBuffer(passExecutionContext);
    }

    public void uploadFrameBuffers(PassExecutionContext passExecutionContext) {
        transformManager.uploadFrameBuffers(passExecutionContext);
    }

    public TransformManager transformManager() {
        return transformManager;
    }

    public @Nullable RootSubjectStateSnapshot rootSubjectStateSnapshot(GraphicsEntityId entityId) {
        if (entityId == null) {
            return null;
        }
        LifecycleBindingState lifecycleBinding;
        BoundsBindingState boundsBinding;
        FlagsBindingState flagsBinding;
        synchronized (bindingLock) {
            lifecycleBinding = lifecycleBindings.get(entityId);
            boundsBinding = boundsBindings.get(entityId);
            flagsBinding = flagsBindings.get(entityId);
        }
        if (lifecycleBinding == null && boundsBinding == null && flagsBinding == null) {
            return null;
        }
        return new RootSubjectStateSnapshot(
                lifecycleBinding != null ? lifecycleBinding.effectiveConcrete() : null,
                boundsBinding != null ? boundsBinding.effectiveConcrete() : null,
                flagsBinding != null ? flagsBinding.effectiveConcrete() : null);
    }

    public void cleanup() {
        synchronized (bindingLock) {
            lifecycleBindings.clear();
            boundsBindings.clear();
            tagsBindings.clear();
            flagsBindings.clear();
            syncTickBindings.clear();
            asyncTickBindings.clear();
            frameBindings.clear();
            tickSwapBindings.clear();
            dirtyBindings.clear();
        }
        transformManager.cleanup();
    }

    private void applyInitialConcrete(GraphicsWorld world, GraphicsEntityId entityId) {
        LifecycleBindingState lifecycleBinding;
        BoundsBindingState boundsBinding;
        TagsBindingState tagsBinding;
        FlagsBindingState flagsBinding;
        synchronized (bindingLock) {
            lifecycleBinding = lifecycleBindings.get(entityId);
            boundsBinding = boundsBindings.get(entityId);
            tagsBinding = tagsBindings.get(entityId);
            flagsBinding = flagsBindings.get(entityId);
        }
        if (lifecycleBinding != null) {
            lifecycleBinding.apply(world);
        }
        if (boundsBinding != null) {
            boundsBinding.apply(world);
        }
        if (tagsBinding != null) {
            tagsBinding.apply(world);
        }
        if (flagsBinding != null) {
            flagsBinding.apply(world);
        }
    }

    private <T extends AbstractBindingState<?>> T registerBinding(T binding) {
        if (binding == null) {
            return null;
        }
        switch (binding.updateDomain()) {
            case SYNC_TICK -> {
                syncTickBindings.add(binding);
                tickSwapBindings.add(binding);
            }
            case ASYNC_TICK -> {
                asyncTickBindings.add(binding);
                tickSwapBindings.add(binding);
            }
            case SYNC_FRAME -> frameBindings.add(binding);
            case STATIC -> {
            }
        }
        return binding;
    }

    private void unregisterBinding(AbstractBindingState<?> binding) {
        if (binding == null) {
            return;
        }
        syncTickBindings.remove(binding);
        asyncTickBindings.remove(binding);
        frameBindings.remove(binding);
        tickSwapBindings.remove(binding);
        dirtyBindings.remove(binding);
    }

    private static void collectBindings(List<AbstractBindingState<?>> bindings) {
        for (AbstractBindingState<?> binding : bindings) {
            binding.collect(binding.updateDomain());
        }
    }

    private void queueDirty(AbstractBindingState<?> binding) {
        synchronized (bindingLock) {
            if (binding == null || binding.applyQueued()) {
                return;
            }
            binding.markApplyQueued();
            dirtyBindings.add(binding);
        }
    }

    private List<AbstractBindingState<?>> snapshotBindings(List<AbstractBindingState<?>> bindings) {
        synchronized (bindingLock) {
            return bindings.isEmpty() ? List.of() : new ArrayList<>(bindings);
        }
    }

    private abstract static class AbstractBindingState<T> {
        private final GraphicsEntityId entityId;
        private final GraphicsUpdateDomain updateDomain;
        private T currentValue;
        private T pendingValue;
        private T frameValue;
        private T appliedValue;
        private long collectedVersion = UNVERSIONED_SAMPLE;
        private boolean applied;
        private boolean applyQueued;

        private AbstractBindingState(GraphicsEntityId entityId, GraphicsUpdateDomain updateDomain) {
            this.entityId = entityId;
            this.updateDomain = updateDomain != null ? updateDomain : GraphicsUpdateDomain.STATIC;
        }

        protected final void seedInitial() {
            long initialVersion = sampleVersion();
            T seed = copy(sample());
            this.currentValue = seed;
            this.pendingValue = copy(seed);
            this.frameValue = copy(seed);
            if (initialVersion != UNVERSIONED_SAMPLE) {
                this.collectedVersion = initialVersion;
            }
        }

        protected abstract @Nullable T sample();

        protected abstract @Nullable T copy(@Nullable T value);

        protected abstract void applyConcrete(GraphicsWorld world, GraphicsEntityId entityId, @Nullable T value);

        protected long sampleVersion() {
            return UNVERSIONED_SAMPLE;
        }

        protected boolean same(@Nullable T left, @Nullable T right) {
            return Objects.equals(left, right);
        }

        public GraphicsUpdateDomain updateDomain() {
            return updateDomain;
        }

        public boolean applyQueued() {
            return applyQueued;
        }

        public void markApplyQueued() {
            this.applyQueued = true;
        }

        public void clearApplyQueued() {
            this.applyQueued = false;
        }

        public boolean swapTickBuffers() {
            if (updateDomain == GraphicsUpdateDomain.SYNC_FRAME || updateDomain == GraphicsUpdateDomain.STATIC) {
                return false;
            }
            if (same(currentValue, pendingValue)) {
                return false;
            }
            currentValue = copy(pendingValue);
            return true;
        }

        public boolean collect(GraphicsUpdateDomain requestedDomain) {
            if (updateDomain != requestedDomain || updateDomain == GraphicsUpdateDomain.STATIC) {
                return false;
            }
            long sampleVersion = sampleVersion();
            if (sampleVersion != UNVERSIONED_SAMPLE && sampleVersion == collectedVersion) {
                return false;
            }
            T sampled = copy(sample());
            if (sampleVersion != UNVERSIONED_SAMPLE) {
                collectedVersion = sampleVersion;
            }
            if (requestedDomain == GraphicsUpdateDomain.SYNC_FRAME) {
                if (same(frameValue, sampled)) {
                    return false;
                }
                frameValue = sampled;
            } else {
                if (same(pendingValue, sampled)) {
                    return false;
                }
                pendingValue = sampled;
            }
            return true;
        }

        public void apply(GraphicsWorld world) {
            T value = updateDomain == GraphicsUpdateDomain.SYNC_FRAME ? frameValue : currentValue;
            if (!applied || !same(appliedValue, value)) {
                applyConcrete(world, entityId, value);
                appliedValue = copy(value);
                applied = true;
            }
        }

        protected final @Nullable T effectiveValue() {
            return updateDomain == GraphicsUpdateDomain.SYNC_FRAME ? frameValue : currentValue;
        }
    }

    private static final class LifecycleBindingState extends AbstractBindingState<GraphicsBuiltinComponents.LifecycleState> {
        private final GraphicsBuiltinComponents.LifecycleAuthoring authoring;

        private LifecycleBindingState(
                GraphicsEntityId entityId,
                GraphicsUpdateDomain updateDomain,
                GraphicsBuiltinComponents.LifecycleAuthoring authoring) {
            super(entityId, updateDomain);
            this.authoring = authoring;
            seedInitial();
        }

        @Override
        protected @Nullable GraphicsBuiltinComponents.LifecycleState sample() {
            return authoring != null ? authoring.sampleLifecycle() : null;
        }

        @Override
        protected long sampleVersion() {
            return authoring != null ? authoring.version() : UNVERSIONED_SAMPLE;
        }

        @Override
        protected @Nullable GraphicsBuiltinComponents.LifecycleState copy(@Nullable GraphicsBuiltinComponents.LifecycleState value) {
            return value != null
                    ? new GraphicsBuiltinComponents.LifecycleState(value.shouldRender(), value.shouldDiscard())
                    : null;
        }

        @Override
        protected void applyConcrete(
                GraphicsWorld world,
                GraphicsEntityId entityId,
                @Nullable GraphicsBuiltinComponents.LifecycleState value) {
            world.replaceComponent(
                    entityId,
                    GraphicsBuiltinComponents.LIFECYCLE,
                    new GraphicsBuiltinComponents.LifecycleComponent(
                            value == null || value.shouldRender(),
                            value != null && value.shouldDiscard()));
        }

        private @Nullable GraphicsBuiltinComponents.LifecycleComponent effectiveConcrete() {
            GraphicsBuiltinComponents.LifecycleState value = effectiveValue();
            if (value == null) {
                return null;
            }
            return new GraphicsBuiltinComponents.LifecycleComponent(
                    value.shouldRender(),
                    value.shouldDiscard());
        }
    }

    private static final class BoundsBindingState extends AbstractBindingState<AABBf> {
        private final GraphicsBuiltinComponents.BoundsAuthoring authoring;

        private BoundsBindingState(
                GraphicsEntityId entityId,
                GraphicsUpdateDomain updateDomain,
                GraphicsBuiltinComponents.BoundsAuthoring authoring) {
            super(entityId, updateDomain);
            this.authoring = authoring;
            seedInitial();
        }

        @Override
        protected @Nullable AABBf sample() {
            return authoring != null ? authoring.sampleBounds() : null;
        }

        @Override
        protected long sampleVersion() {
            return authoring != null ? authoring.version() : UNVERSIONED_SAMPLE;
        }

        @Override
        protected @Nullable AABBf copy(@Nullable AABBf value) {
            return value != null ? new AABBf(value) : null;
        }

        @Override
        protected boolean same(@Nullable AABBf left, @Nullable AABBf right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return left.minX == right.minX
                    && left.minY == right.minY
                    && left.minZ == right.minZ
                    && left.maxX == right.maxX
                    && left.maxY == right.maxY
                    && left.maxZ == right.maxZ;
        }

        @Override
        protected void applyConcrete(GraphicsWorld world, GraphicsEntityId entityId, @Nullable AABBf value) {
            world.replaceComponent(
                    entityId,
                    GraphicsBuiltinComponents.BOUNDS,
                    new GraphicsBuiltinComponents.BoundsComponent(() -> value));
        }

        private @Nullable GraphicsBuiltinComponents.BoundsComponent effectiveConcrete() {
            AABBf value = effectiveValue();
            return value != null ? new GraphicsBuiltinComponents.BoundsComponent(() -> value) : null;
        }
    }

    private static final class TagsBindingState extends AbstractBindingState<Set<KeyId>> {
        private final GraphicsBuiltinComponents.GraphicsTagsAuthoring authoring;

        private TagsBindingState(
                GraphicsEntityId entityId,
                GraphicsUpdateDomain updateDomain,
                GraphicsBuiltinComponents.GraphicsTagsAuthoring authoring) {
            super(entityId, updateDomain);
            this.authoring = authoring;
            seedInitial();
        }

        @Override
        protected @Nullable Set<KeyId> sample() {
            return authoring != null ? authoring.sampleTags() : Set.of();
        }

        @Override
        protected long sampleVersion() {
            return authoring != null ? authoring.version() : UNVERSIONED_SAMPLE;
        }

        @Override
        protected @Nullable Set<KeyId> copy(@Nullable Set<KeyId> value) {
            return value != null ? Set.copyOf(value) : Set.of();
        }

        @Override
        protected void applyConcrete(GraphicsWorld world, GraphicsEntityId entityId, @Nullable Set<KeyId> value) {
            world.replaceComponent(
                    entityId,
                    GraphicsBuiltinComponents.GRAPHICS_TAGS,
                    new GraphicsBuiltinComponents.GraphicsTagsComponent(value != null ? value : Set.of()));
        }
    }

    private static final class FlagsBindingState extends AbstractBindingState<Integer> {
        private final GraphicsBuiltinComponents.ObjectFlagsAuthoring authoring;

        private FlagsBindingState(
                GraphicsEntityId entityId,
                GraphicsUpdateDomain updateDomain,
                GraphicsBuiltinComponents.ObjectFlagsAuthoring authoring) {
            super(entityId, updateDomain);
            this.authoring = authoring;
            seedInitial();
        }

        @Override
        protected @Nullable Integer sample() {
            return authoring != null ? authoring.sampleFlags() : 0;
        }

        @Override
        protected long sampleVersion() {
            return authoring != null ? authoring.version() : UNVERSIONED_SAMPLE;
        }

        @Override
        protected @Nullable Integer copy(@Nullable Integer value) {
            return value != null ? Integer.valueOf(value) : 0;
        }

        @Override
        protected void applyConcrete(GraphicsWorld world, GraphicsEntityId entityId, @Nullable Integer value) {
            world.replaceComponent(
                    entityId,
                    GraphicsBuiltinComponents.OBJECT_FLAGS,
                    new GraphicsBuiltinComponents.ObjectFlagsComponent(value != null ? value : 0));
        }

        private @Nullable GraphicsBuiltinComponents.ObjectFlagsComponent effectiveConcrete() {
            Integer value = effectiveValue();
            return value != null ? new GraphicsBuiltinComponents.ObjectFlagsComponent(value) : null;
        }
    }

    public record RootSubjectStateSnapshot(
            @Nullable GraphicsBuiltinComponents.LifecycleComponent lifecycle,
            @Nullable GraphicsBuiltinComponents.BoundsComponent bounds,
            @Nullable GraphicsBuiltinComponents.ObjectFlagsComponent objectFlags
    ) {
    }
}
