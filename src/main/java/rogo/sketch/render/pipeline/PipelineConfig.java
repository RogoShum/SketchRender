package rogo.sketch.render.pipeline;

/**
 * Configuration settings for the GraphicsPipeline.
 */
public class PipelineConfig {
    
    public enum TranslucencyStrategy {
        /**
         * Render each stage's solid geometry followed immediately by its translucent geometry.
         * Order: Stage A Solid -> Stage A Translucent -> Stage B Solid -> Stage B Translucent
         */
        INTERLEAVED,
        
        /**
         * Render all solid geometry from all stages first, then render all translucent geometry.
         * Order: Stage A Solid -> Stage B Solid -> ... -> Stage A Translucent -> Stage B Translucent
         */
        GROUPED
    }

    private TranslucencyStrategy translucencyStrategy = TranslucencyStrategy.INTERLEAVED;
    private boolean throwOnSortFail = false;
    private boolean cpuSortingEnabled = true; // Enable CPU sorting for translucent batches within a VBO

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
}
