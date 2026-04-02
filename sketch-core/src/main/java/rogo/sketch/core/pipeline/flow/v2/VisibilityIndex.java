package rogo.sketch.core.pipeline.flow.v2;

import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.util.KeyId;

import java.util.function.Consumer;

/**
 * Internal V2 visibility/order index contract.
 * <p>
 * Unlike legacy {@code GraphicsContainer}, implementations operate purely on
 * stable instance handles plus visibility metadata.
 * </p>
 */
public interface VisibilityIndex<C extends RenderContext> {
    KeyId containerType();

    void insert(InstanceHandle handle, VisibilityMetadata metadata);

    void remove(InstanceHandle handle);

    void update(InstanceHandle handle, VisibilityMetadata metadata);

    void collectVisible(C context, Consumer<InstanceHandle> sink);

    default void collectOrdered(C context, Consumer<InstanceHandle> sink) {
        collectVisible(context, sink);
    }

    boolean isEmpty();

    void clear();
}
