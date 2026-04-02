package rogo.sketch.core.resource;

import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predefined resource type constants with inheritance hierarchy support
 */
public final class ResourceTypes {
    public static final KeyId TEXTURE = KeyId.of("texture");
    public static final KeyId IMAGE = KeyId.of("image");
    public static final KeyId STORAGE_BUFFER = KeyId.of("storage_buffer");
    public static final KeyId UNIFORM_BUFFER = KeyId.of("uniform_buffer");
    public static final KeyId ATOMIC_COUNTER = KeyId.of("atomic_counter");
    public static final KeyId SHADER_TYPE = KeyId.of("shader_type");
    @Deprecated
    public static final KeyId SHADER_STORAGE_BUFFER = STORAGE_BUFFER;
    @Deprecated
    public static final KeyId UNIFORM_BLOCK = UNIFORM_BUFFER;
    public static final KeyId COUNTER_BUFFER = KeyId.of("counter_buffer");
    @Deprecated
    public static final KeyId IMAGE_BUFFER = IMAGE;
    public static final KeyId RENDER_TARGET = KeyId.of("render_target");
    public static final KeyId FRAMEBUFFER = KeyId.of("framebuffer");
    public static final KeyId RENDER_SETTING = KeyId.of("render_setting");
    public static final KeyId PARTIAL_RENDER_SETTING = KeyId.of("partial_render_setting");
    public static final KeyId VERTEX_BUFFER = KeyId.of("vertex_buffer");
    public static final KeyId MESH = KeyId.of("mesh");
    public static final KeyId FUNCTION = KeyId.of("function");
    public static final KeyId DRAW_CALL = KeyId.of("draw_call");
    
    // Shader system types
    public static final KeyId MACRO_TEMPLATE = KeyId.of("macro_template");
    public static final KeyId SHADER_TEMPLATE = KeyId.of("shader_template");

    // Resource type inheritance mapping: child -> parent
    private static final Map<KeyId, KeyId> TYPE_INHERITANCE = new HashMap<>();

    static {
        // IMAGE inherits from TEXTURE
        TYPE_INHERITANCE.put(IMAGE, TEXTURE);

        // Add more inheritance relationships as needed
        // Example: TYPE_INHERITANCE.put(SOME_SPECIALIZED_TEXTURE, TEXTURE);
    }

    public static KeyId normalize(KeyId type) {
        if (type == null) {
            return null;
        }

        String value = type.toString();
        return switch (value) {
            case "image_buffer" -> IMAGE;
            case "shader_storage_buffer" -> STORAGE_BUFFER;
            case "uniform_block" -> UNIFORM_BUFFER;
            case "counter_buffer" -> ATOMIC_COUNTER;
            default -> type;
        };
    }

    /**
     * Get the parent type of a resource type, if any
     *
     * @param type the child resource type
     * @return the parent resource type, or null if no parent exists
     */
    public static KeyId getParentType(KeyId type) {
        return TYPE_INHERITANCE.get(normalize(type));
    }

    /**
     * Get all parent types up the inheritance chain
     *
     * @param type the resource type to get parents for
     * @return list of parent types in order (immediate parent first, root ancestor
     * last)
     */
    public static List<KeyId> getInheritanceChain(KeyId type) {
        List<KeyId> chain = new ArrayList<>();
        KeyId current = normalize(type);

        while ((current = getParentType(current)) != null) {
            chain.add(current);
        }

        return chain;
    }

    /**
     * Get all types that should be searched when looking for a resource of the
     * given type
     * This includes the type itself and all its parent types
     *
     * @param type the resource type
     * @return list of types to search, starting with the requested type
     */
    public static List<KeyId> getSearchOrder(KeyId type) {
        KeyId normalizedType = normalize(type);
        List<KeyId> searchOrder = new ArrayList<>();
        searchOrder.add(normalizedType); // Start with the requested type
        searchOrder.addAll(getInheritanceChain(normalizedType)); // Add parent types
        return searchOrder;
    }

    /**
     * Check if one type is compatible with another (either same type or inherits
     * from it)
     *
     * @param childType  the potential child type
     * @param parentType the potential parent type
     * @return true if childType is the same as or inherits from parentType
     */
    public static boolean isCompatible(KeyId childType, KeyId parentType) {
        childType = normalize(childType);
        parentType = normalize(parentType);
        if (childType.equals(parentType)) {
            return true;
        }

        return getInheritanceChain(childType).contains(parentType);
    }

    private ResourceTypes() {
    }
}
