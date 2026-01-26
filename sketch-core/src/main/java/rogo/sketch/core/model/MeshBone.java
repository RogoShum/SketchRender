package rogo.sketch.core.model;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a bone in a mesh hierarchy.
 * Bones form a tree structure where each bone can have a parent and multiple children.
 */
public class MeshBone {
    private final String name;
    private final int id;

    // Hierarchy
    @Nullable
    private MeshBone parent;
    private final List<MeshBone> children;

    // Joint transform data
    private final Matrix4f localTransform;       // Transform relative to parent
    private final Matrix4f inverseBindPose;      // Inverse bind pose matrix
    private Matrix4f globalTransform;            // Cached global transform
    private boolean globalTransformDirty = true;

    private boolean visible = true;

    public MeshBone(String name, int id) {
        this(name, id, new Matrix4f(), new Matrix4f());
    }

    public MeshBone(String name, int id, Matrix4f localTransform, Matrix4f inverseBindPose) {
        this.name = name;
        this.id = id;
        this.children = new ArrayList<>();
        this.localTransform = new Matrix4f(localTransform);
        this.inverseBindPose = new Matrix4f(inverseBindPose);
        this.globalTransform = new Matrix4f();
    }

    /**
     * Set the parent bone and add this bone as a child to the parent
     */
    public void setParent(@Nullable MeshBone parent) {
        // Remove from current parent if any
        if (this.parent != null) {
            this.parent.children.remove(this);
        }

        this.parent = parent;

        // Add to new parent
        if (parent != null) {
            parent.children.add(this);
        }

        markGlobalTransformDirty();
    }

    /**
     * Add a child bone
     */
    public void addChild(MeshBone child) {
        child.setParent(this);
    }

    /**
     * Remove a child bone
     */
    public void removeChild(MeshBone child) {
        if (children.remove(child)) {
            child.parent = null;
            child.markGlobalTransformDirty();
        }
    }

    /**
     * Set the local transform (relative to parent)
     */
    public void setLocalTransform(Matrix4f transform) {
        this.localTransform.set(transform);
        markGlobalTransformDirty();
    }

    /**
     * Update the local transform and mark global transform as dirty
     */
    public void updateLocalTransform(Matrix4f transform) {
        setLocalTransform(transform);
    }

    /**
     * Get the global transform (world space)
     * This is computed on-demand and cached
     */
    public Matrix4f getGlobalTransform() {
        if (globalTransformDirty) {
            updateGlobalTransform();
        }
        return new Matrix4f(globalTransform);
    }

    /**
     * Update the global transform based on parent hierarchy
     */
    private void updateGlobalTransform() {
        if (parent != null) {
            // Global = Parent.Global * Local
            parent.getGlobalTransform().mul(localTransform, globalTransform);
        } else {
            // No parent, global = local
            globalTransform.set(localTransform);
        }
        globalTransformDirty = false;
    }

    /**
     * Mark this bone and all children as having dirty global transforms
     */
    private void markGlobalTransformDirty() {
        if (!globalTransformDirty) {
            globalTransformDirty = true;

            // Mark all children as dirty too
            for (MeshBone child : children) {
                child.markGlobalTransformDirty();
            }
        }
    }

    /**
     * Get the final bone matrix for skinning
     * This is GlobalTransform * InverseBindPose
     */
    public Matrix4f getBoneMatrix() {
        Matrix4f boneMatrix = new Matrix4f();
        getGlobalTransform().mul(inverseBindPose, boneMatrix);
        return boneMatrix;
    }

    /**
     * Check if this bone is a root bone (has no parent)
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Check if this bone is a leaf bone (has no children)
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Get all descendant bones (children, grandchildren, etc.)
     */
    public List<MeshBone> getAllDescendants() {
        List<MeshBone> descendants = new ArrayList<>();
        collectDescendants(descendants);
        return descendants;
    }

    private void collectDescendants(List<MeshBone> collector) {
        for (MeshBone child : children) {
            collector.add(child);
            child.collectDescendants(collector);
        }
    }

    /**
     * Find a bone by name in this bone's hierarchy
     */
    @Nullable
    public MeshBone findBone(String name) {
        if (this.name.equals(name)) {
            return this;
        }

        for (MeshBone child : children) {
            MeshBone found = child.findBone(name);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Get the depth of this bone in the hierarchy (root = 0)
     */
    public int getDepth() {
        int depth = 0;
        MeshBone current = parent;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    // Getters
    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    @Nullable
    public MeshBone getParent() {
        return parent;
    }

    public List<MeshBone> getChildren() {
        return new ArrayList<>(children);
    }

    public Matrix4f getLocalTransform() {
        return new Matrix4f(localTransform);
    }

    public Matrix4f getInverseBindPose() {
        return new Matrix4f(inverseBindPose);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public String toString() {
        return "MeshBone{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", children=" + children.size() +
                ", parent=" + (parent != null ? parent.name : "none") +
                '}';
    }
}
