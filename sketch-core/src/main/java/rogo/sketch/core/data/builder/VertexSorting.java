package rogo.sketch.core.data.builder;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Vector3f;

/**
 * Interface for vertex sorting algorithms, used for transparency rendering.
 * Based on Minecraft's VertexSorting interface.
 */
public interface VertexSorting {
    
    /**
     * Predefined sorting by distance to origin
     */
    VertexSorting DISTANCE_TO_ORIGIN = byDistance(0.0f, 0.0f, 0.0f);
    
    /**
     * Predefined orthographic Z sorting
     */
    VertexSorting ORTHOGRAPHIC_Z = byDistance((point) -> -point.z);
    
    /**
     * Create sorting by distance from a specific point
     */
    static VertexSorting byDistance(float x, float y, float z) {
        return byDistance(new Vector3f(x, y, z));
    }
    
    /**
     * Create sorting by distance from a reference point
     */
    static VertexSorting byDistance(Vector3f referencePoint) {
        return byDistance(referencePoint::distanceSquared);
    }
    
    /**
     * Create sorting using a custom distance function
     */
    static VertexSorting byDistance(DistanceFunction distanceFunction) {
        return new VertexSorting() {
            @Override
            public int[] sort(Vector3f[] points) {
                float[] distances = new float[points.length];
                int[] indices = new int[points.length];
                
                // Compute distances and initialize indices
                for (int i = 0; i < points.length; i++) {
                    distances[i] = distanceFunction.apply(points[i]);
                    indices[i] = i;
                }
                
                // Sort indices by distance (far to near for transparency)
                IntArrays.mergeSort(indices, (a, b) -> 
                    Float.compare(distances[b], distances[a])
                );
                
                return indices;
            }
            
            @Override
            public float calculateDistance(Vector3f point) {
                return distanceFunction.apply(point);
            }
        };
    }
    
    /**
     * Sort an array of points and return the sorted indices
     * 
     * @param points Array of 3D points to sort
     * @return Array of indices representing the sorted order
     */
    int[] sort(Vector3f[] points);
    
    /**
     * Calculate distance for a single point (used for primitive sorting)
     * 
     * @param point Point to calculate distance for
     * @return Distance value for sorting
     */
    default float calculateDistance(Vector3f point) {
        // Default implementation: try to extract distance function from sort implementation
        Vector3f[] singlePoint = {point};
        int[] result = sort(singlePoint);
        
        // For more efficient implementation, override this method in specific sorting instances
        if (this == DISTANCE_TO_ORIGIN) {
            return point.lengthSquared();
        } else if (this == ORTHOGRAPHIC_Z) {
            return -point.z;
        }
        
        // Fallback: assume distance to origin
        return point.lengthSquared();
    }
    
    /**
     * Function interface for computing distance from a point
     */
    @FunctionalInterface
    interface DistanceFunction {
        float apply(Vector3f point);
    }
}