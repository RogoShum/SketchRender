package rogo.sketch.core.pipeline.flow.container;

import rogo.sketch.core.api.graphics.Graphics;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.container.GraphicsContainer;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Descriptor for lazily creating and validating a graphics container.
 */
public final class ContainerDescriptor<C extends RenderContext> {
    private final KeyId id;
    private final Supplier<GraphicsContainer<C>> supplier;
    private final BiPredicate<Graphics, RenderParameter> supports;

    public ContainerDescriptor(
            KeyId id,
            Supplier<GraphicsContainer<C>> supplier,
            BiPredicate<Graphics, RenderParameter> supports) {
        this.id = Objects.requireNonNull(id, "id");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.supports = Objects.requireNonNull(supports, "supports");
    }

    public KeyId id() {
        return id;
    }

    public Supplier<GraphicsContainer<C>> supplier() {
        return supplier;
    }

    public boolean supports(Graphics graphics, RenderParameter parameter) {
        return supports.test(graphics, parameter);
    }

    public GraphicsContainer<C> create() {
        return supplier.get();
    }

    public static <C extends RenderContext> ContainerDescriptor<C> of(
            KeyId id,
            Supplier<GraphicsContainer<C>> supplier,
            BiPredicate<Graphics, RenderParameter> supports) {
        return new ContainerDescriptor<>(id, supplier, supports);
    }
}

