package rogo.sketch.core.object;

/**
 * Stable external reference to an internal root graphics entity.
 */
public record ObjectGraphicsHandle(
        long handleId,
        ObjectGraphicsRootRole rootRole
) {
}
