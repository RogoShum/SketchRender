package rogo.sketch.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.SketchRender;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsUpdateDomain;
import rogo.sketch.core.graphics.ecs.TransformWriter;
import rogo.sketch.core.object.ObjectGraphicsRegistry;
import rogo.sketch.core.object.ObjectGraphicsRootRole;
import rogo.sketch.core.object.ObjectHostContext;
import rogo.sketch.core.object.ObjectHostKind;
import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.util.KeyId;
import rogo.sketch.module.culling.entity.EntityFeatureSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minecraft-side host adapter that bridges world/render lifecycle into the
 * stage 8 object lifecycle registry.
 */
public final class MinecraftHostAdapter {
    private static final Runnable NOOP = () -> {};
    private static final MinecraftHostAdapter INSTANCE = new MinecraftHostAdapter();

    private final Set<Entity> trackedEntities =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockEntity> trackedBlockEntities =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean defaultsRegistered;

    private MinecraftHostAdapter() {
    }

    public static MinecraftHostAdapter getInstance() {
        return INSTANCE;
    }

    public void onWorldEnter(GraphicsPipeline<?> pipeline, @Nullable ClientLevel level) {
        ensureDefaultsRegistered(pipeline);
        trackedEntities.clear();
        trackedBlockEntities.clear();
    }

    public void onWorldLeave(GraphicsPipeline<?> pipeline) {
        if (pipeline == null) {
            trackedEntities.clear();
            trackedBlockEntities.clear();
            return;
        }
        pipeline.extensionHost().objectGraphicsRegistry().destroyAll(pipeline.currentLogicTick());
        trackedEntities.clear();
        trackedBlockEntities.clear();
    }

    public void onRenderStart(GraphicsPipeline<?> pipeline) {
        if (pipeline == null || Minecraft.getInstance().level == null) {
            return;
        }
        ensureDefaultsRegistered(pipeline);
        pruneTrackedEntities(pipeline);
        pruneTrackedBlockEntities(pipeline);
    }

    public void onEntityAdded(GraphicsPipeline<?> pipeline, Entity entity) {
        if (pipeline == null || entity == null) {
            return;
        }
        ensureDefaultsRegistered(pipeline);
        syncEntity(pipeline, entity);
    }

    public void onEntityRemoved(GraphicsPipeline<?> pipeline, Entity entity) {
        if (pipeline == null || entity == null) {
            return;
        }
        destroyEntityRoot(pipeline, entity);
    }

    public void onBlockEntityLevelSet(GraphicsPipeline<?> pipeline, BlockEntity blockEntity) {
        if (pipeline == null || blockEntity == null) {
            return;
        }
        ensureDefaultsRegistered(pipeline);
        if (blockEntity.isRemoved() || blockEntity.getLevel() == null) {
            return;
        }
        ObjectGraphicsRegistry registry = pipeline.extensionHost().objectGraphicsRegistry();
        if (trackedBlockEntities.contains(blockEntity) && registry.containsHost(blockEntity, ObjectGraphicsRootRole.PRIMARY)) {
            return;
        }
        syncBlockEntity(pipeline, blockEntity);
    }

    public void onBlockEntityRemoved(GraphicsPipeline<?> pipeline, BlockEntity blockEntity) {
        if (pipeline == null || blockEntity == null) {
            return;
        }
        destroyBlockEntityRoot(pipeline, blockEntity);
    }

    private void pruneTrackedEntities(GraphicsPipeline<?> pipeline) {
        ClientLevel level = Minecraft.getInstance().level;
        if (pipeline == null || level == null) {
            trackedEntities.clear();
            return;
        }
        List<Entity> stale = new ArrayList<>();
        for (Entity tracked : trackedEntities) {
            if (tracked == null || tracked.isRemoved() || !tracked.isAddedToWorld() || tracked.level() != level) {
                stale.add(tracked);
            }
        }
        for (Entity entity : stale) {
            destroyEntityRoot(pipeline, entity);
        }
    }

    private void pruneTrackedBlockEntities(GraphicsPipeline<?> pipeline) {
        List<BlockEntity> stale = new ArrayList<>();
        for (BlockEntity blockEntity : trackedBlockEntities) {
            if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() != Minecraft.getInstance().level) {
                stale.add(blockEntity);
            }
        }
        for (BlockEntity blockEntity : stale) {
            destroyBlockEntityRoot(pipeline, blockEntity);
        }
    }

    private void syncEntity(GraphicsPipeline<?> pipeline, Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || !entity.isAddedToWorld()) {
            return;
        }
        AABBf bounds = toBounds(SketchRender.getObjectAABB(entity));
        if (bounds == null) {
            return;
        }
        pipeline.extensionHost().objectGraphicsRegistry().syncHostObject(
                entity,
                ObjectHostKind.ENTITY,
                entity.getType(),
                ObjectGraphicsRootRole.PRIMARY,
                ObjectHostContext.of(pipeline, ObjectHostKind.ENTITY, entity.getType(), pipeline.currentLogicTick()),
                bounds,
                EntityFeatureSchema.FLAG_NONE);
        trackedEntities.add(entity);
    }

    private void syncBlockEntity(GraphicsPipeline<?> pipeline, BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null) {
            return;
        }
        AABBf bounds = toBounds(SketchRender.getObjectAABB(blockEntity));
        if (bounds == null) {
            return;
        }
        pipeline.extensionHost().objectGraphicsRegistry().syncHostObject(
                blockEntity,
                ObjectHostKind.BLOCK_ENTITY,
                blockEntity.getType(),
                ObjectGraphicsRootRole.PRIMARY,
                ObjectHostContext.of(pipeline, ObjectHostKind.BLOCK_ENTITY, blockEntity.getType(), pipeline.currentLogicTick()),
                bounds,
                EntityFeatureSchema.FLAG_NONE);
        trackedBlockEntities.add(blockEntity);
    }

    private void ensureDefaultsRegistered(GraphicsPipeline<?> pipeline) {
        if (defaultsRegistered || pipeline == null) {
            return;
        }
        ObjectGraphicsRegistry registry = pipeline.extensionHost().objectGraphicsRegistry();
        registry.registerFallbackFactory(ObjectHostKind.ENTITY, "minecraft_entity_root", (hostObject, rootRole, context, writer) -> {
            Entity entity = (Entity) hostObject;
            java.util.Set<KeyId> tags = entityRootTags(entity);
            writer.put(GraphicsBuiltinComponents.IDENTITY, new GraphicsBuiltinComponents.IdentityComponent(
                    KeyId.of("minecraft", "entity/" + sanitize(entity.getType().getDescriptionId()))));
            writer.put(GraphicsBuiltinComponents.LIFECYCLE, new GraphicsBuiltinComponents.LifecycleComponent(
                    entity.isAddedToWorld(),
                    entity.isRemoved() || !entity.isAddedToWorld()));
            writer.put(GraphicsBuiltinComponents.LIFECYCLE_BINDING, new GraphicsBuiltinComponents.LifecycleBindingComponent(
                    GraphicsUpdateDomain.ASYNC_TICK,
                    new GraphicsBuiltinComponents.LifecycleAuthoring() {
                        @Override
                        public GraphicsBuiltinComponents.LifecycleState sampleLifecycle() {
                            return new GraphicsBuiltinComponents.LifecycleState(
                                    entity.isAddedToWorld(),
                                    entity.isRemoved() || !entity.isAddedToWorld());
                        }

                        @Override
                        public long version() {
                            return (((long) entity.tickCount) << 1) | (entity.isRemoved() ? 1L : 0L);
                        }
                    }));
            writer.put(GraphicsBuiltinComponents.BOUNDS, staticBoundsComponent(toBounds(SketchRender.getObjectAABB(entity))));
            writer.put(GraphicsBuiltinComponents.BOUNDS_BINDING, new GraphicsBuiltinComponents.BoundsBindingComponent(
                    GraphicsUpdateDomain.ASYNC_TICK,
                    new GraphicsBuiltinComponents.BoundsAuthoring() {
                        @Override
                        public AABBf sampleBounds() {
                            return toBounds(SketchRender.getObjectAABB(entity));
                        }

                        @Override
                        public long version() {
                            return entity.tickCount;
                        }
                    }));
            writer.put(GraphicsBuiltinComponents.TRANSFORM_BINDING, new GraphicsBuiltinComponents.TransformBindingComponent(
                    GraphicsUpdateDomain.SYNC_TICK,
                    new EntityRootTransformAuthoring(entity),
                    -1));
            writer.put(GraphicsBuiltinComponents.TICK_DRIVER, new GraphicsBuiltinComponents.TickDriverComponent(NOOP));
            writer.put(GraphicsBuiltinComponents.GRAPHICS_TAGS, new GraphicsBuiltinComponents.GraphicsTagsComponent(tags));
            writer.put(GraphicsBuiltinComponents.GRAPHICS_TAGS_BINDING, new GraphicsBuiltinComponents.GraphicsTagsBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    () -> tags));
            writer.put(GraphicsBuiltinComponents.OBJECT_FLAGS, new GraphicsBuiltinComponents.ObjectFlagsComponent(EntityFeatureSchema.FLAG_NONE));
            writer.put(GraphicsBuiltinComponents.OBJECT_FLAGS_BINDING, new GraphicsBuiltinComponents.ObjectFlagsBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    () -> EntityFeatureSchema.FLAG_NONE));
        });
        registry.registerFallbackFactory(ObjectHostKind.BLOCK_ENTITY, "minecraft_block_entity_root", (hostObject, rootRole, context, writer) -> {
            BlockEntity blockEntity = (BlockEntity) hostObject;
            String typeId = BlockEntityType.getKey(blockEntity.getType()) != null
                    ? BlockEntityType.getKey(blockEntity.getType()).toString()
                    : blockEntity.getClass().getName();
            java.util.Set<KeyId> tags = blockEntityRootTags(typeId);
            writer.put(GraphicsBuiltinComponents.IDENTITY, new GraphicsBuiltinComponents.IdentityComponent(
                    KeyId.of("minecraft", "block_entity/" + sanitize(typeId))));
            writer.put(GraphicsBuiltinComponents.LIFECYCLE, new GraphicsBuiltinComponents.LifecycleComponent(
                    blockEntity.getLevel() != null && !blockEntity.isRemoved(),
                    blockEntity.isRemoved() || blockEntity.getLevel() == null));
            writer.put(GraphicsBuiltinComponents.LIFECYCLE_BINDING, new GraphicsBuiltinComponents.LifecycleBindingComponent(
                    GraphicsUpdateDomain.ASYNC_TICK,
                    new GraphicsBuiltinComponents.LifecycleAuthoring() {
                        @Override
                        public GraphicsBuiltinComponents.LifecycleState sampleLifecycle() {
                            return new GraphicsBuiltinComponents.LifecycleState(
                                    blockEntity.getLevel() != null && !blockEntity.isRemoved(),
                                    blockEntity.isRemoved() || blockEntity.getLevel() == null);
                        }

                        @Override
                        public long version() {
                            long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : -1L;
                            return (gameTime << 1) | (blockEntity.isRemoved() ? 1L : 0L);
                        }
                    }));
            writer.put(GraphicsBuiltinComponents.BOUNDS, staticBoundsComponent(toBounds(SketchRender.getObjectAABB(blockEntity))));
            writer.put(GraphicsBuiltinComponents.BOUNDS_BINDING, new GraphicsBuiltinComponents.BoundsBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    () -> toBounds(SketchRender.getObjectAABB(blockEntity))));
            writer.put(GraphicsBuiltinComponents.TRANSFORM_BINDING, new GraphicsBuiltinComponents.TransformBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    new BlockEntityRootTransformAuthoring(blockEntity),
                    -1));
            writer.put(GraphicsBuiltinComponents.GRAPHICS_TAGS, new GraphicsBuiltinComponents.GraphicsTagsComponent(tags));
            writer.put(GraphicsBuiltinComponents.GRAPHICS_TAGS_BINDING, new GraphicsBuiltinComponents.GraphicsTagsBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    () -> tags));
            writer.put(GraphicsBuiltinComponents.OBJECT_FLAGS, new GraphicsBuiltinComponents.ObjectFlagsComponent(EntityFeatureSchema.FLAG_NONE));
            writer.put(GraphicsBuiltinComponents.OBJECT_FLAGS_BINDING, new GraphicsBuiltinComponents.ObjectFlagsBindingComponent(
                    GraphicsUpdateDomain.STATIC,
                    () -> EntityFeatureSchema.FLAG_NONE));
        });
        defaultsRegistered = true;
    }

    private void destroyEntityRoot(GraphicsPipeline<?> pipeline, Entity entity) {
        ObjectGraphicsRegistry registry = pipeline.extensionHost().objectGraphicsRegistry();
        if (!trackedEntities.remove(entity) && !registry.containsHost(entity, ObjectGraphicsRootRole.PRIMARY)) {
            return;
        }
        registry.destroyHostObject(entity, ObjectGraphicsRootRole.PRIMARY, pipeline.currentLogicTick());
    }

    private void destroyBlockEntityRoot(GraphicsPipeline<?> pipeline, BlockEntity blockEntity) {
        ObjectGraphicsRegistry registry = pipeline.extensionHost().objectGraphicsRegistry();
        if (!trackedBlockEntities.remove(blockEntity) && !registry.containsHost(blockEntity, ObjectGraphicsRootRole.PRIMARY)) {
            return;
        }
        registry.destroyHostObject(blockEntity, ObjectGraphicsRootRole.PRIMARY, pipeline.currentLogicTick());
    }

    private static @Nullable AABBf toBounds(@Nullable AABB bounds) {
        if (bounds == null) {
            return null;
        }
        return new AABBf(
                (float) bounds.minX,
                (float) bounds.minY,
                (float) bounds.minZ,
                (float) bounds.maxX,
                (float) bounds.maxY,
                (float) bounds.maxZ);
    }

    private static GraphicsBuiltinComponents.BoundsComponent staticBoundsComponent(@Nullable AABBf bounds) {
        return new GraphicsBuiltinComponents.BoundsComponent(() -> bounds);
    }

    private static Set<KeyId> entityRootTags(Entity entity) {
        return Set.of(
                KeyId.of("sketch", "object_root"),
                KeyId.of("minecraft", "entity"),
                KeyId.of("minecraft", "entity_type/" + sanitize(entity.getType().getDescriptionId())));
    }

    private static Set<KeyId> blockEntityRootTags(String typeId) {
        return Set.of(
                KeyId.of("sketch", "object_root"),
                KeyId.of("minecraft", "block_entity"),
                KeyId.of("minecraft", "block_entity_type/" + sanitize(typeId)));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace(':', '/');
    }

    private static final class EntityRootTransformAuthoring implements GraphicsBuiltinComponents.TransformAuthoring {
        private final Entity entity;
        private boolean initialized;
        private float continuousPitchDeg;
        private float continuousYawDeg;

        private EntityRootTransformAuthoring(Entity entity) {
            this.entity = entity;
        }

        @Override
        public void writeTransform(TransformWriter writer) {
            writer.reset();
            writer.setPosition((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
            float sampledPitch = entity.getXRot();
            float sampledYaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
            if (!initialized) {
                continuousPitchDeg = sampledPitch;
                continuousYawDeg = sampledYaw;
                initialized = true;
            } else {
                continuousPitchDeg = unwrapDegrees(continuousPitchDeg, sampledPitch);
                continuousYawDeg = unwrapDegrees(continuousYawDeg, sampledYaw);
            }
            writer.setRotationDegrees(continuousPitchDeg, continuousYawDeg, 0.0f);
            writer.setScale(1.0f);
        }

        private static float unwrapDegrees(float previousContinuous, float sampledDegrees) {
            return previousContinuous + Mth.wrapDegrees(sampledDegrees - previousContinuous);
        }
    }

    private static final class BlockEntityRootTransformAuthoring implements GraphicsBuiltinComponents.TransformAuthoring {
        private final BlockEntity blockEntity;

        private BlockEntityRootTransformAuthoring(BlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        @Override
        public void writeTransform(TransformWriter writer) {
            writer.reset();
            writer.setPosition(
                    blockEntity.getBlockPos().getX(),
                    blockEntity.getBlockPos().getY(),
                    blockEntity.getBlockPos().getZ());
            writer.setScale(1.0f);
        }
    }
}
