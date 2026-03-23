package rogo.sketch.core.pipeline.kernel;

/**
 * Envelope for a published kernel resource.
 *
 * @param key      typed resource key
 * @param payload  published payload
 * @param epoch    producer-defined epoch such as render frame or logic tick
 * @param sequence monotonically increasing slot-local publish sequence
 * @param <T>      payload type
 */
public record PublishedKernelResource<T>(
        KernelResourceKey<T> key,
        T payload,
        long epoch,
        long sequence
) {
}
