package rogo.sketch.core.backend;

public record BufferedResourceView<T>(
        T resource,
        ResourceEpoch epoch
) {
}
