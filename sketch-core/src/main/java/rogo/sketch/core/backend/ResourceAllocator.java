package rogo.sketch.core.backend;

import rogo.sketch.core.pipeline.GraphicsPipeline;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.kernel.FrameExecutionPlan;
import rogo.sketch.core.memory.TrackedTransientAllocation;
import rogo.sketch.core.resource.descriptor.ResolvedBufferResource;
import rogo.sketch.core.resource.descriptor.ResolvedImageResource;
import rogo.sketch.core.resource.descriptor.ResolvedRenderTargetSpec;
import rogo.sketch.core.resource.vision.RenderTarget;
import rogo.sketch.core.resource.vision.Texture;
import rogo.sketch.core.util.KeyId;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Backend-owned resource allocation and execution-plan installation service.
 * <p>
 * The legacy {@link BackendResourceInstaller} shape remains as the resource
 * creation surface, while phase-3 commit/install responsibilities are promoted
 * into this wider allocator contract.
 * </p>
 */
public interface ResourceAllocator extends BackendResourceInstaller {
    ResourceAllocator NO_OP = new ResourceAllocator() {
        @Override
        public Texture createTexture(
                KeyId resourceId,
                ResolvedImageResource descriptor,
                String imagePath,
                ByteBuffer imageData) {
            return BackendResourceInstaller.NO_OP.createTexture(resourceId, descriptor, imagePath, imageData);
        }

        @Override
        public RenderTarget createRenderTarget(KeyId resourceId, ResolvedRenderTargetSpec descriptor) {
            return BackendResourceInstaller.NO_OP.createRenderTarget(resourceId, descriptor);
        }

        @Override
        public BackendUniformBuffer createUniformBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceInstaller.NO_OP.createUniformBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendStorageBuffer createStorageBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceInstaller.NO_OP.createStorageBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendCounterBuffer createCounterBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                ByteBuffer initialData) {
            return BackendResourceInstaller.NO_OP.createCounterBuffer(resourceId, descriptor, initialData);
        }

        @Override
        public BackendIndirectBuffer createIndirectBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                long commandCapacity) {
            return BackendResourceInstaller.NO_OP.createIndirectBuffer(resourceId, descriptor, commandCapacity);
        }

        @Override
        public BackendReadbackBuffer createReadbackBuffer(
                KeyId resourceId,
                ResolvedBufferResource descriptor,
                int initialElementCapacity) {
            return null;
        }
    };

    BackendReadbackBuffer createReadbackBuffer(
            rogo.sketch.core.util.KeyId resourceId,
            rogo.sketch.core.resource.descriptor.ResolvedBufferResource descriptor,
            int initialElementCapacity);

    default void installExecutionPlan(FrameExecutionPlan plan, long frameEpoch, int framesInFlight) {
    }

    default <C extends RenderContext> boolean installExecutionPlan(
            GraphicsPipeline<C> pipeline,
            FrameExecutionPlan plan,
            long frameEpoch,
            int framesInFlight,
            boolean uploadGeometryData) {
        installExecutionPlan(plan, frameEpoch, framesInFlight);
        return false;
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
