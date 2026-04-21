package rogo.sketch.core.object;

/**
 * Adds extra components onto a root graphics blueprint.
 */
public interface ObjectGraphicsAugmentor<T> {
    String id();

    void augment(
            T hostObject,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            ObjectGraphicsBlueprintWriter writer);
}
