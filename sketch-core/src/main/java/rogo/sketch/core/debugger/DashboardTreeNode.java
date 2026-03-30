package rogo.sketch.core.debugger;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.util.KeyId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DashboardTreeNode {
    private final String id;
    private final String displayKey;
    private final @Nullable String summaryKey;
    private final @Nullable String detailKey;
    private final boolean group;
    private final boolean active;
    private final boolean enabled;
    private final @Nullable String disabledDetailKey;
    private final @Nullable String blockedByNodeId;
    private final List<String> blockedByDisplayPath;
    private final @Nullable String controlId;
    private final @Nullable KeyId settingId;
    private final @Nullable ControlSpec controlSpec;
    private final @Nullable Object value;
    private final List<String> macroNames;
    private final List<DashboardTreeNode> children;

    private DashboardTreeNode(
            String id,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            boolean group,
            boolean active,
            boolean enabled,
            @Nullable String disabledDetailKey,
            @Nullable String blockedByNodeId,
            List<String> blockedByDisplayPath,
            @Nullable String controlId,
            @Nullable KeyId settingId,
            @Nullable ControlSpec controlSpec,
            @Nullable Object value,
            List<String> macroNames,
            List<DashboardTreeNode> children) {
        this.id = id;
        this.displayKey = displayKey;
        this.summaryKey = summaryKey;
        this.detailKey = detailKey;
        this.group = group;
        this.active = active;
        this.enabled = enabled;
        this.disabledDetailKey = disabledDetailKey;
        this.blockedByNodeId = blockedByNodeId;
        this.blockedByDisplayPath = Collections.unmodifiableList(new ArrayList<>(blockedByDisplayPath));
        this.controlId = controlId;
        this.settingId = settingId;
        this.controlSpec = controlSpec;
        this.value = value;
        this.macroNames = Collections.unmodifiableList(new ArrayList<>(macroNames));
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    public static DashboardTreeNode group(
            String id,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            List<DashboardTreeNode> children) {
        return new DashboardTreeNode(id, displayKey, summaryKey, detailKey, true, true, true,
                null, null, List.of(), null, null, null, null, List.of(), children);
    }

    public static DashboardTreeNode control(
            String id,
            String displayKey,
            @Nullable String summaryKey,
            @Nullable String detailKey,
            boolean active,
            boolean enabled,
            @Nullable String disabledDetailKey,
            String controlId,
            @Nullable String blockedByNodeId,
            List<String> blockedByDisplayPath,
            @Nullable KeyId settingId,
            @Nullable ControlSpec controlSpec,
            @Nullable Object value,
            List<String> macroNames) {
        return new DashboardTreeNode(id, displayKey, summaryKey, detailKey, false, active, enabled, disabledDetailKey,
                blockedByNodeId, blockedByDisplayPath, controlId, settingId, controlSpec, value, macroNames, List.of());
    }

    public String id() {
        return id;
    }

    public String displayKey() {
        return displayKey;
    }

    public @Nullable String summaryKey() {
        return summaryKey;
    }

    public @Nullable String detailKey() {
        return detailKey;
    }

    public boolean group() {
        return group;
    }

    public boolean active() {
        return active;
    }

    public boolean enabled() {
        return enabled;
    }

    public @Nullable String disabledDetailKey() {
        return disabledDetailKey;
    }

    public @Nullable String blockedByNodeId() {
        return blockedByNodeId;
    }

    public List<String> blockedByDisplayPath() {
        return blockedByDisplayPath;
    }

    public @Nullable String controlId() {
        return controlId;
    }

    public @Nullable KeyId settingId() {
        return settingId;
    }

    public @Nullable ControlSpec controlSpec() {
        return controlSpec;
    }

    public @Nullable Object value() {
        return value;
    }

    public List<String> macroNames() {
        return macroNames;
    }

    public List<DashboardTreeNode> children() {
        return children;
    }
}
