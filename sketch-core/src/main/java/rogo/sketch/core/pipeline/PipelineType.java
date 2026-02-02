package rogo.sketch.core.pipeline;

import rogo.sketch.core.pipeline.flow.RenderFlowType;
import rogo.sketch.core.util.KeyId;

import java.util.Objects;

/**
 * Represents a type of rendering pipeline with identity and priority.
 * Different pipeline types can have different resource managers and execution
 * priorities.
 * <p>
 * Common pipeline types include compute shaders, opaque rasterization, and
 * translucent rendering.
 * Pipeline types are executed in order of their priority (lower values execute
 * first).
 * </p>
 */
public abstract class PipelineType implements Comparable<PipelineType> {
    private final KeyId identifier;

    protected PipelineType(KeyId identifier) {
        this.identifier = identifier;
    }

    protected PipelineType(String identifier) {
        this(KeyId.valueOf(identifier));
    }

    /**
     * Get the priority of this pipeline type.
     * Lower values execute first.
     *
     * @return Priority value
     */
    public abstract int getPriority();
    
    /**
     * Get the default render flow type for this pipeline type.
     *
     * @return The default RenderFlowType
     */
    public abstract RenderFlowType getDefaultFlowType();

    /**
     * Get the unique identifier for this pipeline type.
     *
     * @return Pipeline type identifier
     */
    public KeyId getIdentifier() {
        return identifier;
    }

    @Override
    public int compareTo(PipelineType other) {
        return Integer.compare(this.getPriority(), other.getPriority());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        PipelineType that = (PipelineType) obj;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        return "PipelineType{" +
                "id=" + identifier +
                ", priority=" + getPriority() +
                '}';
    }

    // ===== Predefined Pipeline Types =====

    /**
     * Compute shader pipeline - executed first for compute operations.
     * Priority: 100
     */
    public static final PipelineType COMPUTE = new PipelineType("compute") {
        @Override
        public int getPriority() {
            return 100;
        }
        
        @Override
        public RenderFlowType getDefaultFlowType() {
            return RenderFlowType.COMPUTE;
        }
    };

    /**
     * Function pipeline
     * Priority: 200
     */
    public static final PipelineType FUNCTION = new PipelineType("function") {
        @Override
        public int getPriority() {
            return 200;
        }
        
        @Override
        public RenderFlowType getDefaultFlowType() {
            return RenderFlowType.FUNCTION;
        }
    };

    /**
     * Standard rasterization pipeline for opaque geometry.
     * Priority: 300
     */
    public static final PipelineType RASTERIZATION = new PipelineType("rasterization") {
        @Override
        public int getPriority() {
            return 300;
        }
        
        @Override
        public RenderFlowType getDefaultFlowType() {
            return RenderFlowType.RASTERIZATION;
        }
    };

    /**
     * Translucent rasterization pipeline for transparent/translucent geometry.
     * Priority: 400 (executed after opaque geometry)
     */
    public static final PipelineType TRANSLUCENT = new PipelineType("translucent") {
        @Override
        public int getPriority() {
            return 400;
        }
        
        @Override
        public RenderFlowType getDefaultFlowType() {
            return RenderFlowType.RASTERIZATION;
        }
    };
}