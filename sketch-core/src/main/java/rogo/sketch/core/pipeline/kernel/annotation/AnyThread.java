package rogo.sketch.core.pipeline.kernel.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or type as safe to call from any thread.
 * <p>
 * The annotated code is either pure-CPU, operates on immutable data,
 * or uses explicit synchronization internally. No GL context is required,
 * and no thread-domain guard check is performed.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AnyThread {
    /**
     * Optional description of thread-safety rationale.
     */
    String value() default "";
}

