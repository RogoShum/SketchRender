package rogo.sketch.core.driver;

/**
 * Runtime feature flags for GL worker and upload strategies.
 * <p>
 * This class no longer reads JVM system properties directly.
 * Instead, switch preset profiles in the static initializer to test
 * different sync/async resource loading behaviors.
 * </p>
 */
public final class GLRuntimeFlags {

    public enum GLWorkerMode {
        OFF,
        SHADER_ONLY,
        SHADER_UPLOAD,
        SHADER_COMPUTE;

        public static GLWorkerMode fromProperty(String raw) {
            if (raw == null || raw.isBlank()) {
                return SHADER_ONLY;
            }
            return switch (raw.trim().toLowerCase()) {
                case "off" -> OFF;
                case "shader_upload" -> SHADER_UPLOAD;
                case "shader_compute" -> SHADER_COMPUTE;
                default -> SHADER_ONLY;
            };
        }
    }

    public enum VBOUploadStrategy {
        SUBDATA,
        PERSISTENT;

        public static VBOUploadStrategy fromProperty(String raw) {
            if (raw == null || raw.isBlank()) {
                return SUBDATA;
            }
            return "persistent".equalsIgnoreCase(raw.trim()) ? PERSISTENT : SUBDATA;
        }
    }

    /**
     * Global switch for shared-context GL worker.
     */
    public static boolean GL_WORKER_ENABLED;

    /**
     * Worker feature mode.
     */
    public static GLWorkerMode GL_WORKER_MODE;

    /**
     * VBO upload strategy for dynamic vertex data.
     */
    public static VBOUploadStrategy VBO_UPLOAD_STRATEGY;

    /**
     * If true, persistent mapped VBO uses coherent mapping.
     */
    public static boolean VBO_PERSISTENT_COHERENT;

    private static String activeProfileName = "unset";

    private GLRuntimeFlags() {
    }

    static {
        initializeProfile();
    }

    /**
     * Empty initializer entrypoint.
     * Change only this method body to switch runtime profile quickly.
     */
    private static void initializeProfile() {
        // useProfileSyncSafe();
        // useProfileShaderWorkerOnly();
        useProfileShaderUploadWorker();
        // useProfileShaderComputeWorker();
    }

    // ==================== Preset Profiles ====================

    /**
     * Fully sync-safe baseline: all GL work on main thread.
     */
    private static void useProfileSyncSafe() {
        applyProfile(profileSyncSafe());
    }

    /**
     * Async shader compile on shared GL worker only.
     */
    private static void useProfileShaderWorkerOnly() {
        applyProfile(profileShaderWorkerOnly());
    }

    /**
     * Async shader + upload-capable worker mode.
     */
    private static void useProfileShaderUploadWorker() {
        applyProfile(profileShaderUploadWorker());
    }

    /**
     * Async shader + upload + compute-capable worker mode.
     */
    private static void useProfileShaderComputeWorker() {
        applyProfile(profileShaderComputeWorker());
    }

    private static RuntimeProfile profileSyncSafe() {
        return new RuntimeProfile("sync_safe", false, GLWorkerMode.OFF, VBOUploadStrategy.SUBDATA, true);
    }

    private static RuntimeProfile profileShaderWorkerOnly() {
        return new RuntimeProfile("shader_worker_only", true, GLWorkerMode.SHADER_ONLY, VBOUploadStrategy.SUBDATA, true);
    }

    private static RuntimeProfile profileShaderUploadWorker() {
        return new RuntimeProfile("shader_upload_worker", true, GLWorkerMode.SHADER_UPLOAD, VBOUploadStrategy.PERSISTENT, true);
    }

    private static RuntimeProfile profileShaderComputeWorker() {
        return new RuntimeProfile("shader_compute_worker", true, GLWorkerMode.SHADER_COMPUTE, VBOUploadStrategy.PERSISTENT, true);
    }

    private static void applyProfile(RuntimeProfile profile) {
        activeProfileName = profile.name();
        GL_WORKER_ENABLED = profile.glWorkerEnabled();
        GL_WORKER_MODE = profile.glWorkerMode();
        VBO_UPLOAD_STRATEGY = profile.vboUploadStrategy();
        VBO_PERSISTENT_COHERENT = profile.vboPersistentCoherent();
    }

    public static String activeProfileName() {
        return activeProfileName;
    }

    public static boolean allowShaderWorker() {
        if (!GL_WORKER_ENABLED) {
            return false;
        }
        return GL_WORKER_MODE == GLWorkerMode.SHADER_ONLY
                || GL_WORKER_MODE == GLWorkerMode.SHADER_UPLOAD
                || GL_WORKER_MODE == GLWorkerMode.SHADER_COMPUTE;
    }

    public static boolean allowUploadWorker() {
        if (!GL_WORKER_ENABLED) {
            return false;
        }
        return GL_WORKER_MODE == GLWorkerMode.SHADER_UPLOAD
                || GL_WORKER_MODE == GLWorkerMode.SHADER_COMPUTE;
    }

    public static boolean allowComputeWorker() {
        return GL_WORKER_ENABLED && GL_WORKER_MODE == GLWorkerMode.SHADER_COMPUTE;
    }

    private record RuntimeProfile(
            String name,
            boolean glWorkerEnabled,
            GLWorkerMode glWorkerMode,
            VBOUploadStrategy vboUploadStrategy,
            boolean vboPersistentCoherent
    ) {
    }
}


