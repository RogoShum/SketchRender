package rogo.sketch.core.pipeline.kernel.annotation;

import java.lang.annotation.*;

/**
 * Marks a method or type as requiring execution on a worker thread (no GL context).
 * <p>
 * When {@link rogo.sketch.core.pipeline.kernel.ThreadDomainGuard} is enabled,
 * calls from the main thread will throw {@link IllegalStateException}.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AsyncOnly {
    /**
     * Optional description of why this method requires the async thread.
     */
    String value() default "";
}

