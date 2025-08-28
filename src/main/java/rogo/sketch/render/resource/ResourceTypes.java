package rogo.sketch.render.resource;

import rogo.sketch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predefined resource type constants with inheritance hierarchy support
 */
public final class ResourceTypes {
    public static final Identifier TEXTURE = Identifier.of("texture");
    public static final Identifier SHADER_STORAGE_BUFFER = Identifier.of("shader_storage_buffer");
    public static final Identifier UNIFORM_BLOCK = Identifier.of("uniform_block");
    public static final Identifier COUNTER_BUFFER = Identifier.of("counter_buffer");
    public static final Identifier IMAGE_BUFFER = Identifier.of("image_buffer");
    public static final Identifier RENDER_TARGET = Identifier.of("render_target");
    public static final Identifier FRAMEBUFFER = Identifier.of("framebuffer");
    public static final Identifier SHADER_PROGRAM = Identifier.of("shader_program");
    public static final Identifier RENDER_SETTING = Identifier.of("render_setting");
    public static final Identifier PARTIAL_RENDER_SETTING = Identifier.of("partial_render_setting");
    public static final Identifier VERTEX_BUFFER = Identifier.of("vertex_buffer");

    // Resource type inheritance mapping: child -> parent
    private static final Map<Identifier, Identifier> TYPE_INHERITANCE = new HashMap<>();

    static {
        // IMAGE_BUFFER inherits from TEXTURE
        TYPE_INHERITANCE.put(IMAGE_BUFFER, TEXTURE);

        // Add more inheritance relationships as needed
        // Example: TYPE_INHERITANCE.put(SOME_SPECIALIZED_TEXTURE, TEXTURE);
    }

    /**
     * Get the parent type of a resource type, if any
     *
     * @param type the child resource type
     * @return the parent resource type, or null if no parent exists
     */
    public static Identifier getParentType(Identifier type) {
        return TYPE_INHERITANCE.get(type);
    }

    /**
     * Get all parent types up the inheritance chain
     *
     * @param type the resource type to get parents for
     * @return list of parent types in order (immediate parent first, root ancestor last)
     */
    public static List<Identifier> getInheritanceChain(Identifier type) {
        List<Identifier> chain = new ArrayList<>();
        Identifier current = type;

        while ((current = getParentType(current)) != null) {
            chain.add(current);
        }

        return chain;
    }

    /**
     * Get all types that should be searched when looking for a resource of the given type
     * This includes the type itself and all its parent types
     *
     * @param type the resource type
     * @return list of types to search, starting with the requested type
     */
    public static List<Identifier> getSearchOrder(Identifier type) {
        List<Identifier> searchOrder = new ArrayList<>();
        searchOrder.add(type); // Start with the requested type
        searchOrder.addAll(getInheritanceChain(type)); // Add parent types
        return searchOrder;
    }

    /**
     * Check if one type is compatible with another (either same type or inherits from it)
     *
     * @param childType  the potential child type
     * @param parentType the potential parent type
     * @return true if childType is the same as or inherits from parentType
     */
    public static boolean isCompatible(Identifier childType, Identifier parentType) {
        if (childType.equals(parentType)) {
            return true;
        }

        return getInheritanceChain(childType).contains(parentType);
    }

    private ResourceTypes() {
    }
}