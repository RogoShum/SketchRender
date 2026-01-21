package rogo.sketch.render.pipeline;

import rogo.sketch.util.KeyId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration settings for the GraphicsPipeline.
 */
public class PipelineConfig {

    public enum TranslucencyStrategy {
        /**
         * Render each stage's solid geometry followed immediately by its translucent
         * geometry.
         * Order: Stage A Solid -> Stage A Translucent -> Stage B Solid -> Stage B
         * Translucent
         */
        INTERLEAVED,

        /**
         * Specific stages are designated as "translucent flush points".
         * All solid geometry renders normally, but translucent rendering is accumulated
         * and only executed when a dedicated translucent stage is reached.
         * Example: If B and E are dedicated translucent stages:
         * - A solid (skip translucent)
         * - B solid + flush A,B translucent
         * - C solid (skip translucent)
         * - D solid (skip translucent)
         * - E solid + flush C,D,E translucent
         */
        DEDICATED_STAGES,

        /**
         * Flexible per-stage configuration. Each stage can individually specify
         * whether its translucent rendering follows immediately after its solid
         * rendering
         * (true) or is deferred to be rendered in order after all solid stages (false).
         */
        FLEXIBLE
    }

    private TranslucencyStrategy translucencyStrategy = TranslucencyStrategy.INTERLEAVED;
    private boolean throwOnSortFail = false;
    private boolean cpuSortingEnabled = true; // Enable CPU sorting for translucent batches within a VBO

    // DEDICATED_STAGES configuration
    private final Set<KeyId> dedicatedTranslucentStages = new HashSet<>();

    // FLEXIBLE configuration: per-stage translucent behavior
    // true = translucent follows solid immediately, false = deferred to end
    // null = use global default (INTERLEAVED behavior)
    private final Map<KeyId, Boolean> stageTranslucentFollowsSolid = new LinkedHashMap<>();

    public PipelineConfig() {
    }

    public TranslucencyStrategy getTranslucencyStrategy() {
        return translucencyStrategy;
    }

    public void setTranslucencyStrategy(TranslucencyStrategy translucencyStrategy) {
        this.translucencyStrategy = translucencyStrategy;
    }

    public boolean isThrowOnSortFail() {
        return throwOnSortFail;
    }

    public void setThrowOnSortFail(boolean throwOnSortFail) {
        this.throwOnSortFail = throwOnSortFail;
    }

    public boolean isCpuSortingEnabled() {
        return cpuSortingEnabled;
    }

    public void setCpuSortingEnabled(boolean cpuSortingEnabled) {
        this.cpuSortingEnabled = cpuSortingEnabled;
    }

    // === DEDICATED_STAGES configuration ===

    /**
     * Add a stage as a dedicated translucent flush point.
     * When this stage is executed, it will flush all accumulated translucent
     * commands
     * from preceding stages (since the last flush).
     * 
     * @param stageId Stage identifier
     */
    public void addDedicatedTranslucentStage(KeyId stageId) {
        dedicatedTranslucentStages.add(stageId);
    }

    /**
     * Remove a stage from dedicated translucent stages.
     * 
     * @param stageId Stage identifier
     */
    public void removeDedicatedTranslucentStage(KeyId stageId) {
        dedicatedTranslucentStages.remove(stageId);
    }

    /**
     * Check if a stage is a dedicated translucent flush point.
     * 
     * @param stageId Stage identifier
     * @return true if this stage flushes translucent rendering
     */
    public boolean isDedicatedTranslucentStage(KeyId stageId) {
        return dedicatedTranslucentStages.contains(stageId);
    }

    /**
     * Get all dedicated translucent stages.
     * 
     * @return Set of stage identifiers
     */
    public Set<KeyId> getDedicatedTranslucentStages() {
        return new HashSet<>(dedicatedTranslucentStages);
    }

    // === FLEXIBLE configuration ===

    /**
     * Configure whether a stage's translucent rendering follows immediately after
     * its solid rendering (FLEXIBLE mode only).
     * 
     * @param stageId      Stage identifier
     * @param followsSolid true to render translucent immediately after solid,
     *                     false to defer to end
     */
    public void setStageTranslucentFollowsSolid(KeyId stageId, boolean followsSolid) {
        stageTranslucentFollowsSolid.put(stageId, followsSolid);
    }

    /**
     * Get whether a stage's translucent rendering follows its solid rendering.
     * 
     * @param stageId Stage identifier
     * @return true if follows immediately, false if deferred, null if not
     *         configured
     */
    public Boolean getStageTranslucentFollowsSolid(KeyId stageId) {
        return stageTranslucentFollowsSolid.get(stageId);
    }

    /**
     * Check if a stage should render translucent immediately after solid (FLEXIBLE
     * mode).
     * 
     * @param stageId      Stage identifier
     * @param defaultValue Default value if not configured
     * @return true if translucent follows solid
     */
    public boolean shouldStageRenderTranslucentImmediately(KeyId stageId, boolean defaultValue) {
        Boolean configured = stageTranslucentFollowsSolid.get(stageId);
        return configured != null ? configured : defaultValue;
    }
}
