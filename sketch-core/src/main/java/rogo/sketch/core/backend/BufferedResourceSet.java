package rogo.sketch.core.backend;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Shared buffered resource ownership for async-produce/sync-consume style
 * textures and buffers.
 */
public final class BufferedResourceSet<T> implements AutoCloseable {
    private final BufferedResourceDescriptor descriptor;
    private final Consumer<T> disposer;
    private final Object[] resources;
    private int readIndex;
    private int writeIndex;
    private ResourceEpoch publishedEpoch = ResourceEpoch.ZERO;
    private ResourceEpoch nextEpoch = new ResourceEpoch(1L);

    private BufferedResourceSet(
            BufferedResourceDescriptor descriptor,
            Consumer<T> disposer,
            Object[] resources) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.disposer = Objects.requireNonNull(disposer, "disposer");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.readIndex = 0;
        this.writeIndex = resources.length > 1 ? 1 : 0;
    }

    public static <T> BufferedResourceSet<T> create(
            BufferedResourceDescriptor descriptor,
            IntFunction<T> resourceFactory,
            Consumer<T> disposer) {
        Objects.requireNonNull(resourceFactory, "resourceFactory");
        int slotCount = slotCount(descriptor);
        Object[] resources = new Object[slotCount];
        for (int i = 0; i < slotCount; i++) {
            resources[i] = resourceFactory.apply(i);
        }
        return new BufferedResourceSet<>(descriptor, disposer, resources);
    }

    public BufferedResourceDescriptor descriptor() {
        return descriptor;
    }

    public int slotCount() {
        return resources.length;
    }

    public BufferedResourceView<T> readView() {
        return new BufferedResourceView<>(readResource(), publishedEpoch);
    }

    public BufferedResourceView<T> writeView() {
        return new BufferedResourceView<>(writeResource(), nextEpoch);
    }

    public @Nullable T readResource() {
        return resourceAt(readIndex);
    }

    public @Nullable T writeResource() {
        return resourceAt(writeIndex);
    }

    public ResourceEpoch publishedEpoch() {
        return publishedEpoch;
    }

    public ResourceEpoch nextEpoch() {
        return nextEpoch;
    }

    public ResourceEpoch claimNextEpoch() {
        ResourceEpoch claimed = nextEpoch;
        nextEpoch = nextEpoch.next();
        return claimed;
    }

    public boolean promote(ResourceEpoch epoch) {
        if (epoch == null || epoch.value() <= publishedEpoch.value()) {
            return false;
        }
        publishedEpoch = epoch;
        if (resources.length > 1) {
            int previousRead = readIndex;
            readIndex = writeIndex;
            writeIndex = nextWriteIndex(previousRead);
        }
        return true;
    }

    public void recreate(IntFunction<T> resourceFactory) {
        Objects.requireNonNull(resourceFactory, "resourceFactory");
        for (int i = 0; i < resources.length; i++) {
            disposeResource(resourceAt(i));
            resources[i] = resourceFactory.apply(i);
        }
        resetState();
    }

    public void resetState() {
        readIndex = 0;
        writeIndex = resources.length > 1 ? 1 : 0;
        publishedEpoch = ResourceEpoch.ZERO;
        nextEpoch = new ResourceEpoch(1L);
    }

    @Override
    public void close() {
        for (int i = 0; i < resources.length; i++) {
            disposeResource(resourceAt(i));
            resources[i] = null;
        }
    }

    private int nextWriteIndex(int previousRead) {
        if (resources.length <= 1) {
            return 0;
        }
        int candidate = (previousRead + 1) % resources.length;
        if (candidate == readIndex) {
            candidate = (candidate + 1) % resources.length;
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private @Nullable T resourceAt(int index) {
        return index >= 0 && index < resources.length ? (T) resources[index] : null;
    }

    private void disposeResource(@Nullable T resource) {
        if (resource != null) {
            disposer.accept(resource);
        }
    }

    private static int slotCount(BufferedResourceDescriptor descriptor) {
        if (descriptor == null) {
            return 1;
        }
        return switch (descriptor.bufferingMode()) {
            case SINGLE -> 1;
            case DOUBLE_BUFFERED, ASYNC_PRODUCE_SYNC_CONSUME -> Math.max(2, descriptor.ringSize());
            case RING_N -> Math.max(2, descriptor.ringSize());
        };
    }
}
