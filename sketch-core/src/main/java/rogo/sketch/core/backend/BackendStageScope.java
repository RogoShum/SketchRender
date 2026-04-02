package rogo.sketch.core.backend;

public interface BackendStageScope extends AutoCloseable {
    BackendStageScope NO_OP = () -> {
    };

    @Override
    void close();
}
