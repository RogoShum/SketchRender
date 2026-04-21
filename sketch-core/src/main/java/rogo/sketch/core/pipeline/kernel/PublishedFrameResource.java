package rogo.sketch.core.pipeline.kernel;

/**
 * Envelope for a published frame resource.
 *
 * @param handle   typed resource handle
 * @param payload  payload
 * @param epoch    producer epoch
 * @param sequence slot-local publication sequence
 * @param <T>      payload type
 */
public record PublishedFrameResource<T>(
        FrameResourceHandle<T> handle,
        T payload,
        long epoch,
        long sequence
) {
}
