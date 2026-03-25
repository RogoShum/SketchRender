package rogo.sketch.core.pipeline.module.setting;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base metadata for a module-owned setting node.
 */
public abstract class SettingNode<T> {
    private final KeyId id;
    private final String moduleId;
    private final String displayKey;
    private final @Nullable String detailKey;
    private final @Nullable KeyId parentId;
    private final ChangeImpact changeImpact;
    private final boolean visibleInGui;
    private final List<DependencyRule> dependencies;
    private final T defaultValue;

    protected SettingNode(
            KeyId id,
            String moduleId,
            String displayKey,
            @Nullable String detailKey,
            @Nullable KeyId parentId,
            ChangeImpact changeImpact,
            boolean visibleInGui,
            List<DependencyRule> dependencies,
            T defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.displayKey = Objects.requireNonNull(displayKey, "displayKey");
        this.detailKey = detailKey;
        this.parentId = parentId;
        this.changeImpact = Objects.requireNonNull(changeImpact, "changeImpact");
        this.visibleInGui = visibleInGui;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        this.defaultValue = defaultValue;
    }

    public KeyId id() {
        return id;
    }

    public String moduleId() {
        return moduleId;
    }

    public String displayKey() {
        return displayKey;
    }

    public @Nullable String detailKey() {
        return detailKey;
    }

    public @Nullable KeyId parentId() {
        return parentId;
    }

    public ChangeImpact changeImpact() {
        return changeImpact;
    }

    public boolean visibleInGui() {
        return visibleInGui;
    }

    public List<DependencyRule> dependencies() {
        return dependencies;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean isGroup() {
        return false;
    }

    public abstract Class<T> valueType();

    public abstract T coerceValue(Object rawValue);
}
