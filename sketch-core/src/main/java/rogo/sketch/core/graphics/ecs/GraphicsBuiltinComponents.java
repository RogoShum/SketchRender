package rogo.sketch.core.graphics.ecs;

import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.data.builder.VertexRecordWriter;
import rogo.sketch.core.object.ObjectGraphicsHandle;
import rogo.sketch.core.object.ObjectGraphicsRootRole;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.StageRouteDescriptor;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Built-in component descriptors used by the new graphics ECS path.
 */
public final class GraphicsBuiltinComponents {
    public static final GraphicsComponentType<IdentityComponent> IDENTITY =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_identity"), IdentityComponent.class);
    public static final GraphicsComponentType<LifecycleComponent> LIFECYCLE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_lifecycle"), LifecycleComponent.class);
    public static final GraphicsComponentType<LifecycleBindingComponent> LIFECYCLE_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_lifecycle_binding"), LifecycleBindingComponent.class);
    public static final GraphicsComponentType<StageBindingComponent> STAGE_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_stage_binding"), StageBindingComponent.class);
    public static final GraphicsComponentType<StageRoutesComponent> STAGE_ROUTES =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_stage_routes"), StageRoutesComponent.class);
    public static final GraphicsComponentType<ContainerHintComponent> CONTAINER_HINT =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_container_hint"), ContainerHintComponent.class);
    public static final GraphicsComponentType<ResourceOriginComponent> RESOURCE_ORIGIN =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_resource_origin"), ResourceOriginComponent.class);
    public static final GraphicsComponentType<GraphicsTagsComponent> GRAPHICS_TAGS =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_tags"), GraphicsTagsComponent.class);

    public static final GraphicsComponentType<RasterRenderableComponent> RASTER_RENDERABLE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_raster_renderable"), RasterRenderableComponent.class);
    public static final GraphicsComponentType<ComputeDispatchComponent> COMPUTE_DISPATCH =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_compute_dispatch"), ComputeDispatchComponent.class);
    public static final GraphicsComponentType<FunctionInvokeComponent> FUNCTION_INVOKE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_function_invoke"), FunctionInvokeComponent.class);
    public static final GraphicsComponentType<SubmissionCapabilityComponent> SUBMISSION_CAPABILITY =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_submission_capability"), SubmissionCapabilityComponent.class);

    public static final GraphicsComponentType<BoundsComponent> BOUNDS =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_bounds"), BoundsComponent.class);
    public static final GraphicsComponentType<BoundsBindingComponent> BOUNDS_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_bounds_binding"), BoundsBindingComponent.class);
    public static final GraphicsComponentType<PreparedMeshComponent> PREPARED_MESH =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_prepared_mesh"), PreparedMeshComponent.class);
    public static final GraphicsComponentType<RenderDescriptorComponent> RENDER_DESCRIPTOR =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_render_descriptor"), RenderDescriptorComponent.class);
    public static final GraphicsComponentType<InstanceVertexAuthoringComponent> INSTANCE_VERTEX_AUTHORING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_instance_vertex_authoring"), InstanceVertexAuthoringComponent.class);
    public static final GraphicsComponentType<InstanceCountComponent> INSTANCE_COUNT =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_instance_count"), InstanceCountComponent.class);
    public static final GraphicsComponentType<UniformAuthoringComponent> UNIFORM_AUTHORING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_uniform_authoring"), UniformAuthoringComponent.class);
    public static final GraphicsComponentType<SsboAuthoringComponent> SSBO_AUTHORING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_ssbo_authoring"), SsboAuthoringComponent.class);
    public static final GraphicsComponentType<TransformBindingComponent> TRANSFORM_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_transform_binding"), TransformBindingComponent.class);
    public static final GraphicsComponentType<TransformHierarchyComponent> TRANSFORM_HIERARCHY =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_transform_hierarchy"), TransformHierarchyComponent.class);
    public static final GraphicsComponentType<GraphicsTagsBindingComponent> GRAPHICS_TAGS_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_tags_binding"), GraphicsTagsBindingComponent.class);
    public static final GraphicsComponentType<ObjectFlagsComponent> OBJECT_FLAGS =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_object_flags"), ObjectFlagsComponent.class);
    public static final GraphicsComponentType<ObjectFlagsBindingComponent> OBJECT_FLAGS_BINDING =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_object_flags_binding"), ObjectFlagsBindingComponent.class);
    public static final GraphicsComponentType<ObjectModelRootComponent> OBJECT_MODEL_ROOT =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_object_model_root"), ObjectModelRootComponent.class);
    public static final GraphicsComponentType<RenderPartComponent> RENDER_PART =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_render_part"), RenderPartComponent.class);
    public static final GraphicsComponentType<RootReferenceComponent> ROOT_REFERENCE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_root_reference"), RootReferenceComponent.class);
    public static final GraphicsComponentType<AttachmentComponent> ATTACHMENT =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_attachment"), AttachmentComponent.class);
    public static final GraphicsComponentType<ModelInstanceComponent> MODEL_INSTANCE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_model_instance"), ModelInstanceComponent.class);
    public static final GraphicsComponentType<AnimationStateComponent> ANIMATION_STATE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_animation_state"), AnimationStateComponent.class);
    public static final GraphicsComponentType<SkeletonPoseComponent> SKELETON_POSE =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_skeleton_pose"), SkeletonPoseComponent.class);

    public static final GraphicsComponentType<TickDriverComponent> TICK_DRIVER =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_tick_driver"), TickDriverComponent.class);
    public static final GraphicsComponentType<AsyncTickDriverComponent> ASYNC_TICK_DRIVER =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_async_tick_driver"), AsyncTickDriverComponent.class);
    public static final GraphicsComponentType<DescriptorVersionComponent> DESCRIPTOR_VERSION =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_descriptor_version"), DescriptorVersionComponent.class);
    public static final GraphicsComponentType<GeometryVersionComponent> GEOMETRY_VERSION =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_geometry_version"), GeometryVersionComponent.class);
    public static final GraphicsComponentType<BoundsVersionComponent> BOUNDS_VERSION =
            GraphicsComponentType.of(KeyId.of("sketch", "graphics_bounds_version"), BoundsVersionComponent.class);

    private GraphicsBuiltinComponents() {
    }

    public record IdentityComponent(KeyId identifier) {
    }

    public record LifecycleComponent(
            boolean shouldRender,
            boolean shouldDiscard
    ) {
    }

    public record LifecycleBindingComponent(
            GraphicsUpdateDomain updateDomain,
            LifecycleAuthoring authoring
    ) {
    }

    public record StageBindingComponent(
            KeyId stageId,
            PipelineType pipelineType,
            RenderParameter renderParameter
    ) {
    }

    public record StageRoutesComponent(List<StageRouteDescriptor> routes) {
        public StageRoutesComponent {
            if (routes == null || routes.isEmpty()) {
                routes = List.of();
            } else {
                List<StageRouteDescriptor> normalized = new ArrayList<>(routes.size());
                LinkedHashSet<StageRouteKey> seen = new LinkedHashSet<>();
                for (StageRouteDescriptor route : routes) {
                    if (route == null) {
                        throw new IllegalArgumentException("Stage route list cannot contain null routes");
                    }
                    StageRouteKey key = new StageRouteKey(route.stageId(), route.pipelineType());
                    if (!seen.add(key)) {
                        throw new IllegalArgumentException(
                                "Duplicate stage route declared for " + route.stageId() + " / " + route.pipelineType());
                    }
                    normalized.add(route);
                }
                routes = List.copyOf(normalized);
            }
        }

        public boolean isEmpty() {
            return routes.isEmpty();
        }

        public StageRouteDescriptor firstEnabledRoute() {
            for (StageRouteDescriptor route : routes) {
                if (route != null && route.enabled()) {
                    return route;
                }
            }
            return null;
        }

        private record StageRouteKey(KeyId stageId, PipelineType pipelineType) {
            private StageRouteKey {
                Objects.requireNonNull(stageId, "stageId");
                Objects.requireNonNull(pipelineType, "pipelineType");
            }
        }
    }

    public record ContainerHintComponent(
            KeyId containerType,
            Object sortKey,
            long orderHint,
            int layerHint
    ) {
    }

    public record ResourceOriginComponent(KeyId resourceType) {
    }

    public record GraphicsTagsComponent(Set<KeyId> tags) {
        public GraphicsTagsComponent {
            tags = tags != null ? Set.copyOf(tags) : Set.of();
        }

        public boolean hasTag(KeyId tag) {
            return tag != null && tags.contains(tag);
        }
    }

    public record RasterRenderableComponent(boolean immediateSupported) {
    }

    public record ComputeDispatchComponent(ComputeDispatchCommand dispatchCommand) {
    }

    public record FunctionInvokeComponent(
            FunctionInvoker invoker,
            Object payload,
            int priority
    ) {
    }

    public record SubmissionCapabilityComponent(SubmissionCapability capability) {
        public SubmissionCapabilityComponent {
            capability = capability != null ? capability : SubmissionCapability.DIRECT_BATCHABLE;
        }
    }

    public record BoundsComponent(BoundsReader reader) {
        public AABBf bounds() {
            if (reader == null) {
                return null;
            }
            AABBf bounds = reader.readBounds();
            return bounds != null ? new AABBf(bounds) : null;
        }
    }

    public record BoundsBindingComponent(
            GraphicsUpdateDomain updateDomain,
            BoundsAuthoring authoring
    ) {
    }

    public record PreparedMeshComponent(PreparedMeshResolver resolver) {
        public PreparedMesh preparedMesh() {
            return resolver != null ? resolver.resolvePreparedMesh() : null;
        }
    }

    public record RenderDescriptorComponent(
            DescriptorStability stability,
            LongSupplier versionSupplier,
            Function<RenderParameter, CompiledRenderSetting> descriptorBuilder
    ) {
        public RenderDescriptorComponent {
            stability = stability != null ? stability : DescriptorStability.STABLE;
        }

        public long descriptorVersion() {
            return versionSupplier != null ? versionSupplier.getAsLong() : 0L;
        }

        public CompiledRenderSetting build(RenderParameter renderParameter) {
            return descriptorBuilder != null ? descriptorBuilder.apply(renderParameter) : null;
        }
    }

    public record InstanceVertexAuthoringComponent(InstanceVertexAuthoring authoring) {
    }

    public record InstanceCountComponent(IntSupplier countSupplier) {
        public int instanceCount() {
            return countSupplier != null ? Math.max(0, countSupplier.getAsInt()) : 1;
        }
    }

    public record UniformAuthoringComponent(UniformAuthoring authoring) {
    }

    public record SsboAuthoringComponent(SsboAuthoring authoring) {
    }

    public record TransformBindingComponent(
            GraphicsUpdateDomain updateDomain,
            TransformAuthoring authoring,
            int transformId
    ) {
    }

    public record TransformHierarchyComponent(TransformParentResolver parentResolver) {
        public GraphicsEntityId parentEntityId() {
            return parentResolver != null ? parentResolver.resolveParentEntity() : null;
        }
    }

    public record GraphicsTagsBindingComponent(
            GraphicsUpdateDomain updateDomain,
            GraphicsTagsAuthoring authoring
    ) {
    }

    public record ObjectFlagsComponent(int flags) {
    }

    public record ObjectFlagsBindingComponent(
            GraphicsUpdateDomain updateDomain,
            ObjectFlagsAuthoring authoring
    ) {
    }

    public record ObjectModelRootComponent(
            ObjectGraphicsHandle rootHandle,
            ObjectGraphicsRootRole rootRole
    ) {
    }

    public record RenderPartComponent(
            KeyId partKey,
            boolean visible
    ) {
    }

    public record RootReferenceComponent(
            ObjectGraphicsHandle rootHandle,
            GraphicsEntityId rootEntityId
    ) {
    }

    public record AttachmentComponent(
            KeyId targetBoneKey,
            AttachmentTransform localTransform,
            int flags
    ) {
        public AttachmentComponent {
            localTransform = localTransform != null ? localTransform : AttachmentTransform.identity();
        }
    }

    public record ModelInstanceComponent(
            KeyId modelId,
            KeyId meshProfileId,
            long version
    ) {
    }

    public record AnimationStateComponent(
            KeyId animationId,
            float timeSeconds,
            boolean looping,
            long version
    ) {
    }

    public record SkeletonPoseComponent(
            Map<KeyId, BonePose> bonePoses,
            long version
    ) {
        public SkeletonPoseComponent {
            bonePoses = bonePoses != null ? Map.copyOf(bonePoses) : Map.of();
        }

        public BonePose pose(KeyId boneKey) {
            return boneKey != null ? bonePoses.get(boneKey) : null;
        }
    }

    public record TickDriverComponent(Runnable tickAction) {
        public void tick() {
            if (tickAction != null) {
                tickAction.run();
            }
        }
    }

    public record AsyncTickDriverComponent(Runnable tickAction, Runnable swapAction) {
        public void tick() {
            if (tickAction != null) {
                tickAction.run();
            }
        }

        public void swap() {
            if (swapAction != null) {
                swapAction.run();
            }
        }
    }

    public record DescriptorVersionComponent(LongSupplier versionSupplier) {
        public long version() {
            return versionSupplier != null ? versionSupplier.getAsLong() : 0L;
        }
    }

    public record GeometryVersionComponent(LongSupplier versionSupplier) {
        public long version() {
            return versionSupplier != null ? versionSupplier.getAsLong() : 0L;
        }
    }

    public record BoundsVersionComponent(LongSupplier versionSupplier) {
        public long version() {
            return versionSupplier != null ? versionSupplier.getAsLong() : 0L;
        }
    }

    public record LifecycleState(
            boolean shouldRender,
            boolean shouldDiscard
    ) {
    }

    @FunctionalInterface
    public interface BoundsReader {
        AABBf readBounds();
    }

    @FunctionalInterface
    public interface LifecycleAuthoring {
        LifecycleState sampleLifecycle();

        default long version() {
            return Long.MIN_VALUE;
        }
    }

    @FunctionalInterface
    public interface BoundsAuthoring {
        AABBf sampleBounds();

        default long version() {
            return Long.MIN_VALUE;
        }
    }

    @FunctionalInterface
    public interface PreparedMeshResolver {
        PreparedMesh resolvePreparedMesh();
    }

    @FunctionalInterface
    public interface InstanceVertexAuthoring {
        void writeInstanceVertex(KeyId componentKey, VertexRecordWriter writer);
    }

    @FunctionalInterface
    public interface UniformAuthoring {
        void writeUniforms(UniformWriteContext context);
    }

    @FunctionalInterface
    public interface SsboAuthoring {
        void writeSsbo(SsboWriteContext context);
    }

    @FunctionalInterface
    public interface FunctionInvoker {
        void invoke(FunctionInvokeContext context);
    }

    public interface UniformWriteContext {
    }

    public interface SsboWriteContext {
    }

    public interface FunctionInvokeContext {
    }

    @FunctionalInterface
    public interface TransformAuthoring {
        void writeTransform(TransformWriter writer);
    }

    @FunctionalInterface
    public interface GraphicsTagsAuthoring {
        Set<KeyId> sampleTags();

        default long version() {
            return Long.MIN_VALUE;
        }
    }

    @FunctionalInterface
    public interface ObjectFlagsAuthoring {
        int sampleFlags();

        default long version() {
            return Long.MIN_VALUE;
        }
    }

    @FunctionalInterface
    public interface TransformParentResolver {
        GraphicsEntityId resolveParentEntity();
    }

    public record AttachmentTransform(
            float pivotX,
            float pivotY,
            float pivotZ,
            float translateX,
            float translateY,
            float translateZ,
            float rotationPitchDeg,
            float rotationYawDeg,
            float rotationRollDeg,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        public static AttachmentTransform identity() {
            return new AttachmentTransform(
                    0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f,
                    1.0f, 1.0f, 1.0f);
        }
    }

    public record BonePose(
            float positionX,
            float positionY,
            float positionZ,
            float rotationPitchDeg,
            float rotationYawDeg,
            float rotationRollDeg,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        public static BonePose identity() {
            return new BonePose(
                    0.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f,
                    1.0f, 1.0f, 1.0f);
        }
    }
}
