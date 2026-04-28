package rogo.sketch.core.pipeline.flow.v2;

import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBf;
import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.SubmissionCapability;
import rogo.sketch.core.api.model.PreparedMesh;
import rogo.sketch.core.graphics.ecs.GraphicsBuiltinComponents;
import rogo.sketch.core.graphics.ecs.GraphicsCapabilityView;
import rogo.sketch.core.graphics.ecs.GraphicsEntityId;
import rogo.sketch.core.graphics.ecs.GraphicsEntitySchema;
import rogo.sketch.core.graphics.ecs.GraphicsUniformSubject;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.pipeline.StageRouteDescriptor;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable stage-local snapshot of ECS entities prepared for the current
 * frame.
 */
public final class StageEntityView {
    private final KeyId stageId;
    private final PipelineType pipelineType;
    private final List<Entry> entries;
    private final List<Entry> rasterEntries;
    private final List<Entry> computeEntries;
    private final List<Entry> functionEntries;
    private final List<GraphicsEntityId> rasterEntityIds;
    private final List<GraphicsEntityId> computeEntityIds;
    private final List<GraphicsEntityId> functionEntityIds;

    public StageEntityView(
            KeyId stageId,
            PipelineType pipelineType,
            List<Entry> entries
    ) {
        this.stageId = stageId;
        this.pipelineType = pipelineType;
        if (entries == null || entries.isEmpty()) {
            this.entries = List.of();
            this.rasterEntries = List.of();
            this.computeEntries = List.of();
            this.functionEntries = List.of();
            this.rasterEntityIds = List.of();
            this.computeEntityIds = List.of();
            this.functionEntityIds = List.of();
            return;
        }

        List<Entry> entryCopy = Collections.unmodifiableList(new ArrayList<>(entries));
        List<Entry> raster = new ArrayList<>();
        List<Entry> compute = new ArrayList<>();
        List<Entry> function = new ArrayList<>();
        List<GraphicsEntityId> rasterIds = new ArrayList<>();
        List<GraphicsEntityId> computeIds = new ArrayList<>();
        List<GraphicsEntityId> functionIds = new ArrayList<>();

        for (Entry entry : entryCopy) {
            if (entry == null) {
                continue;
            }
            if (entry.isRasterRenderable()) {
                raster.add(entry);
                rasterIds.add(entry.entityId());
            }
            if (entry.isComputeDispatch()) {
                compute.add(entry);
                computeIds.add(entry.entityId());
            }
            if (entry.isFunctionInvoke()) {
                function.add(entry);
                functionIds.add(entry.entityId());
            }
        }

        this.entries = entryCopy;
        this.rasterEntries = raster.isEmpty() ? List.of() : Collections.unmodifiableList(raster);
        this.computeEntries = compute.isEmpty() ? List.of() : Collections.unmodifiableList(compute);
        this.functionEntries = function.isEmpty() ? List.of() : Collections.unmodifiableList(function);
        this.rasterEntityIds = rasterIds.isEmpty() ? List.of() : Collections.unmodifiableList(rasterIds);
        this.computeEntityIds = computeIds.isEmpty() ? List.of() : Collections.unmodifiableList(computeIds);
        this.functionEntityIds = functionIds.isEmpty() ? List.of() : Collections.unmodifiableList(functionIds);
    }

    public KeyId stageId() {
        return stageId;
    }

    public PipelineType pipelineType() {
        return pipelineType;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> rasterEntries() {
        return rasterEntries;
    }

    public List<Entry> computeEntries() {
        return computeEntries;
    }

    public List<Entry> functionEntries() {
        return functionEntries;
    }

    public List<GraphicsEntityId> rasterEntityIds() {
        return rasterEntityIds;
    }

    public List<GraphicsEntityId> computeEntityIds() {
        return computeEntityIds;
    }

    public List<GraphicsEntityId> functionEntityIds() {
        return functionEntityIds;
    }

    public record Entry(
            GraphicsEntityId entityId,
            GraphicsEntitySchema schema,
            GraphicsCapabilityView capabilityView,
            GraphicsBuiltinComponents.IdentityComponent identity,
            @Nullable GraphicsBuiltinComponents.ResourceOriginComponent resourceOrigin,
            @Nullable GraphicsBuiltinComponents.GraphicsTagsComponent tags,
            GraphicsUniformSubject uniformSubject,
            GraphicsBuiltinComponents.LifecycleComponent lifecycle,
            GraphicsBuiltinComponents.StageBindingComponent stageBinding,
            @Nullable GraphicsBuiltinComponents.StageRoutesComponent stageRoutes,
            @Nullable StageRouteDescriptor stageRoute,
            GraphicsBuiltinComponents.ContainerHintComponent containerHint,
            @Nullable GraphicsBuiltinComponents.RasterRenderableComponent rasterRenderable,
            @Nullable GraphicsBuiltinComponents.ComputeDispatchComponent computeDispatch,
            @Nullable GraphicsBuiltinComponents.FunctionInvokeComponent functionInvoke,
            @Nullable GraphicsBuiltinComponents.SubmissionCapabilityComponent submissionCapabilityComponent,
            @Nullable GraphicsBuiltinComponents.BoundsComponent boundsComponent,
            @Nullable GraphicsBuiltinComponents.PreparedMeshComponent preparedMeshComponent,
            @Nullable GraphicsBuiltinComponents.RenderDescriptorComponent renderDescriptor,
            @Nullable GraphicsBuiltinComponents.InstanceVertexAuthoringComponent instanceVertexAuthoring,
            @Nullable GraphicsBuiltinComponents.InstanceCountComponent instanceCount,
            @Nullable GraphicsBuiltinComponents.TransformBindingComponent transformBinding,
            @Nullable GraphicsBuiltinComponents.TickDriverComponent tickDriver,
            @Nullable GraphicsBuiltinComponents.AsyncTickDriverComponent asyncTickDriver,
            @Nullable GraphicsBuiltinComponents.DescriptorVersionComponent descriptorVersion,
            @Nullable GraphicsBuiltinComponents.GeometryVersionComponent geometryVersion,
            @Nullable GraphicsBuiltinComponents.BoundsVersionComponent boundsVersion
    ) {
        public boolean shouldRender() {
            return lifecycle == null || lifecycle.shouldRender();
        }

        public boolean shouldDiscard() {
            return lifecycle != null && lifecycle.shouldDiscard();
        }

        public boolean isRasterRenderable() {
            return rasterRenderable != null;
        }

        public boolean isComputeDispatch() {
            return computeDispatch != null;
        }

        public boolean isFunctionInvoke() {
            return functionInvoke != null;
        }

        public GraphicsUniformSubject uniformSubject() {
            return uniformSubject;
        }

        public KeyId identifier() {
            return identity != null ? identity.identifier() : KeyId.of("sketch", "unknown_entity");
        }

        public boolean hasTag(KeyId tag) {
            return tags != null && tags.hasTag(tag);
        }

        public KeyId stageId() {
            return stageRoute != null ? stageRoute.stageId() : stageBinding != null ? stageBinding.stageId() : null;
        }

        public PipelineType pipelineType() {
            return stageRoute != null ? stageRoute.pipelineType() : stageBinding != null ? stageBinding.pipelineType() : null;
        }

        public @Nullable StageRouteDescriptor stageRoute() {
            return stageRoute;
        }

        public @Nullable GraphicsBuiltinComponents.StageRoutesComponent stageRoutes() {
            return stageRoutes;
        }

        public RenderParameter renderParameter() {
            return stageRoute != null ? stageRoute.renderParameter() : stageBinding != null ? stageBinding.renderParameter() : null;
        }

        public KeyId containerType() {
            return containerHint != null && containerHint.containerType() != null
                    ? containerHint.containerType()
                    : rogo.sketch.core.pipeline.flow.ecs.GraphicsContainerHints.DEFAULT;
        }

        public Object sortKey() {
            return containerHint != null ? containerHint.sortKey() : Long.valueOf(orderHint());
        }

        public long orderHint() {
            return containerHint != null ? containerHint.orderHint() : 0L;
        }

        public int layerHint() {
            return containerHint != null ? containerHint.layerHint() : 0;
        }

        public AABBf bounds() {
            return boundsComponent != null && boundsComponent.reader() != null
                    ? boundsComponent.reader().readBounds()
                    : null;
        }

        public PreparedMesh preparedMesh() {
            return preparedMeshComponent != null ? preparedMeshComponent.preparedMesh() : null;
        }

        public int resolvedInstanceCount() {
            return instanceCount != null ? instanceCount.instanceCount() : 1;
        }

        public SubmissionCapability submissionCapability() {
            return submissionCapabilityComponent != null && submissionCapabilityComponent.capability() != null
                    ? submissionCapabilityComponent.capability()
                    : SubmissionCapability.DIRECT_BATCHABLE;
        }

        public long descriptorVersionValue() {
            return descriptorVersion != null ? descriptorVersion.version()
                    : renderDescriptor != null ? renderDescriptor.descriptorVersion() : 0L;
        }

        public long geometryVersionValue() {
            return geometryVersion != null ? geometryVersion.version() : 0L;
        }

        public long boundsVersionValue() {
            return boundsVersion != null ? boundsVersion.version() : 0L;
        }

        public CompiledRenderSetting buildRenderDescriptor() {
            return renderDescriptor != null ? renderDescriptor.build(renderParameter()) : null;
        }

        public ComputeDispatchCommand dispatchCommand() {
            return computeDispatch != null ? computeDispatch.dispatchCommand() : null;
        }

        public void tick() {
            if (tickDriver != null) {
                tickDriver.tick();
            }
        }

        public void asyncTick() {
            if (asyncTickDriver != null) {
                asyncTickDriver.tick();
            }
        }

        public void swapData() {
            if (asyncTickDriver != null) {
                asyncTickDriver.swap();
            }
        }
    }
}
