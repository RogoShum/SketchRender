package rogo.sketch.core.driver;

/**
 * Marker task for compute dispatch work on shared GL worker.
 */
@FunctionalInterface
public interface ComputeDispatchTask {
    void run() throws Exception;
}


