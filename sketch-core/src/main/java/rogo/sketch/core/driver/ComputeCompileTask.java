package rogo.sketch.core.driver;

import java.util.concurrent.Callable;

/**
 * Marker task for compute-shader compile/link work on shared GL worker.
 */
@FunctionalInterface
public interface ComputeCompileTask<T> extends Callable<T> {
}


