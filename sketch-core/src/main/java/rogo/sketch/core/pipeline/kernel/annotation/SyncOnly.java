package rogo.sketch.core.pipeline.kernel.annotation;

import rogo.sketch.core.pipeline.kernel.ThreadDomain;

import java.lang.annotation.*;

/**
 * Marks a method or type as requiring execution on the main/render thread (GL context available).
 * <p>
 * When {@link rogo.sketch.core.pipeline.kernel.ThreadDomainGuard} is enabled,
 * calls from a non-main thread will throw {@link IllegalStateException}.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SyncOnly {
    /**
     * Optional description of why this method requires the sync thread.
     */
    String value() default "";
}

