package rogo.sketch.core.object;

/**
 * Produces the base root graphics blueprint for a host object.
 */
public interface ObjectGraphicsFactory<T> {
    void contributeRoot(
            T hostObject,
            ObjectGraphicsRootRole rootRole,
            ObjectHostContext context,
            ObjectGraphicsBlueprintWriter writer);
}
