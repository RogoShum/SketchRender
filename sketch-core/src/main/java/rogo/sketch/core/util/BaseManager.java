package rogo.sketch.core.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstract ID manager with tail-shrinking optimization.
 * Manages objects with unique integer IDs, supporting efficient registration and unregistration.
 * 
 * @param <T> The type of objects to manage
 */
public abstract class BaseManager<T> {
    protected final List<T> objects = new ArrayList<>();
    protected final IntArrayList freeIds = new IntArrayList();
    
    /**
     * Register an object and assign it a unique ID.
     * Prioritizes reusing IDs from the free list.
     * 
     * @param obj The object to register
     * @return The assigned ID
     */
    public int register(T obj) {
        if (obj == null) {
            throw new NullPointerException("Cannot register null object");
        }
        
        int id;
        if (!freeIds.isEmpty()) {
            // Reuse ID from free list (LIFO)
            id = freeIds.removeInt(freeIds.size() - 1);
            objects.set(id, obj);
        } else {
            // Append to end
            id = objects.size();
            objects.add(obj);
        }
        
        onRegister(id, obj);
        return id;
    }
    
    /**
     * Unregister an object by its ID.
     * Implements tail-shrinking: if the ID is at the end, removes it and
     * recursively removes any trailing null elements.
     * 
     * @param id The ID of the object to unregister
     * @return The unregistered object, or null if ID was invalid
     */
    public T unregister(int id) {
        if (id < 0 || id >= objects.size()) {
            return null;
        }
        
        T obj = objects.get(id);
        if (obj == null) {
            return null;
        }
        
        onUnregister(id, obj);
        
        // Check if this is the last element
        if (id == objects.size() - 1) {
            // Remove last element
            objects.remove(id);
            
            // Recursively shrink tail if previous elements are null
            shrinkTail();
        } else {
            // Not the last element - mark as null and add to free list
            objects.set(id, null);
            freeIds.add(id);
        }
        
        return obj;
    }
    
    /**
     * Shrink the tail of the list by removing trailing null elements.
     * Also removes their IDs from the free list.
     */
    private void shrinkTail() {
        while (!objects.isEmpty() && objects.get(objects.size() - 1) == null) {
            int lastIndex = objects.size() - 1;
            objects.remove(lastIndex);
            
            // Remove this index from freeIds if present
            int freeIdIndex = freeIds.indexOf(lastIndex);
            if (freeIdIndex >= 0) {
                freeIds.removeInt(freeIdIndex);
            }
        }
    }
    
    /**
     * Get an object by its ID.
     * 
     * @param id The ID of the object
     * @return The object, or null if not found or unregistered
     */
    public T get(int id) {
        if (id < 0 || id >= objects.size()) {
            return null;
        }
        return objects.get(id);
    }
    
    /**
     * Check if an ID is valid and has an object registered.
     * 
     * @param id The ID to check
     * @return true if the ID has a registered object
     */
    public boolean isRegistered(int id) {
        return id >= 0 && id < objects.size() && objects.get(id) != null;
    }
    
    /**
     * Get the number of registered objects (excluding null slots).
     * 
     * @return The count of active objects
     */
    public int getActiveCount() {
        return objects.size() - freeIds.size();
    }
    
    /**
     * Get the total capacity (including null slots).
     * 
     * @return The size of the internal list
     */
    public int getCapacity() {
        return objects.size();
    }
    
    /**
     * Iterate over all registered objects with their IDs.
     * 
     * @param action Consumer that receives the ID and object
     */
    public void forEach(IdObjectConsumer<T> action) {
        for (int i = 0; i < objects.size(); i++) {
            T obj = objects.get(i);
            if (obj != null) {
                action.accept(i, obj);
            }
        }
    }
    
    /**
     * Iterate over all registered objects.
     * 
     * @param action Consumer that receives the object
     */
    public void forEachObject(Consumer<T> action) {
        for (T obj : objects) {
            if (obj != null) {
                action.accept(obj);
            }
        }
    }
    
    /**
     * Clear all registered objects.
     */
    public void clear() {
        for (int i = 0; i < objects.size(); i++) {
            T obj = objects.get(i);
            if (obj != null) {
                onUnregister(i, obj);
            }
        }
        objects.clear();
        freeIds.clear();
    }
    
    /**
     * Called when an object is registered.
     * Subclasses can override to perform additional setup.
     * 
     * @param id The assigned ID
     * @param obj The registered object
     */
    protected void onRegister(int id, T obj) {
        // Override in subclass if needed
    }
    
    /**
     * Called when an object is about to be unregistered.
     * Subclasses can override to perform cleanup.
     * 
     * @param id The ID being unregistered
     * @param obj The object being unregistered
     */
    protected void onUnregister(int id, T obj) {
        // Override in subclass if needed
    }
    
    /**
     * Functional interface for consuming ID-object pairs.
     */
    @FunctionalInterface
    public interface IdObjectConsumer<T> {
        void accept(int id, T obj);
    }
}