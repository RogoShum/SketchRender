package rogo.sketch.core.backend;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Backend-owned resource allocation and execution-plan installation service.
 */
public interface ResourceAllocator extends BackendResourceRegistry {
    ResourceAllocator NO_OP = new ResourceAllocator() {
        @Override
        public Texture installTexture(
                KeyId resourceId,
                ResolvedImageResource descriptor,
                String imagePath,
                ByteBuffer imageData) {
            return BackendResourceRegistry.NO_OP.installTexture(resourceId, descriptor, imagePath, imageData);
        }

        @Override
        public RenderTarget installRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
            return BackendResourceRegistry.NO_OP.installRenderTarget(resourceId, descriptor);
        }

        @Override
        public BackendUniformBuffer installUniformBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceRegistry.NO_OP.installUniformBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendStorageBuffer installStorageBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceRegistry.NO_OP.installStorageBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendCounterBuffer installCounterBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceRegistry.NO_OP.installCounterBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendIndirectBuffer installIndirectBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                long commandCapacity) {
            return BackendResourceRegistry.NO_OP.installIndirectBuffer(resourceId, descriptor, commandCapacity);
        }

        @Override
        public BackendReadbackBuffer installReadbackBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                int initialElementCapacity) {
            return null;
        }
    };

    default <C extends RenderContext> boolean installExecutionPlan(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan plan,
            long frameEpoch,
            int framesInFlight,
            boolean uploadGeometryData) {
        installExecutionPlan(plan, frameEpoch, framesInFlight);
        return false;
    }

    default void installImmediateResourceBindings(List<RenderPacket> packets) {
    }

    default void shutdown() {
    }

    default <T> BufferedResourceSet<T> createBufferedResources(
            BufferedResourceDescriptor descriptor,
            IntFunction<T> resourceFactory,
            Consumer<T> disposer) {
        return BufferedResourceSet.create(descriptor, resourceFactory, disposer);
    }

    default TrackedTransientAllocation allocateTransient(String ownerId, int byteCount) {
        return TrackedTransientAllocation.allocate(ownerId, byteCount);
    }
}
