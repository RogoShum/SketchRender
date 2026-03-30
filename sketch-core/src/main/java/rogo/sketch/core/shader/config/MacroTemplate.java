package rogo.sketch.core.shader.config;

import rogo.sketch.core.api.ResourceObject;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Macro template resource - a reusable collection of macro definitions.
 */
public class MacroTemplate implements ResourceObject {
    private final KeyId id;
    private final Map<String, MacroEntryDescriptor> entries;
    private final Set<String> requires;
    private boolean disposed = false;

    public MacroTemplate(KeyId id, Map<String, MacroEntryDescriptor> entries, Set<String> requires) {
        this.id = id;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.requires = Collections.unmodifiableSet(new LinkedHashSet<>(requires));
    }

    public KeyId getId() {
        return id;
    }

    public Map<String, MacroEntryDescriptor> getEntries() {
        return entries;
    }

    public Map<String, String> getMacros() {
        Map<String, String> macros = new LinkedHashMap<>();
        for (MacroEntryDescriptor entry : entries.values()) {
            if (entry.type() != MacroEntryType.FLAG && entry.value() != null) {
                macros.put(entry.name(), entry.value());
            }
        }
        return Collections.unmodifiableMap(macros);
    }

    public Set<String> getFlags() {
        Set<String> flags = new LinkedHashSet<>();
        for (MacroEntryDescriptor entry : entries.values()) {
            if (entry.type() == MacroEntryType.FLAG) {
                flags.add(entry.name());
            }
        }
        return Collections.unmodifiableSet(flags);
    }

    public Set<String> getRequires() {
        return requires;
    }

    public boolean isConditionMet(MacroContext context) {
        for (String required : requires) {
            if (!context.isDefined(required)) {
                return false;
            }
        }
        return true;
    }

    public boolean defines(String name) {
        return entries.containsKey(name);
    }

    public String getValue(String name) {
        MacroEntryDescriptor descriptor = entries.get(name);
        if (descriptor == null) {
            return null;
        }
        return descriptor.type() == MacroEntryType.FLAG ? "1" : descriptor.value();
    }

    public Map<String, String> mergeWith(Map<String, String> baseMacros) {
        HashMap<String, String> merged = new HashMap<>(baseMacros);
        for (MacroEntryDescriptor entry : entries.values()) {
            merged.put(entry.name(), entry.type() == MacroEntryType.FLAG ? "1" : Objects.toString(entry.value(), ""));
        }
        return merged;
    }

    public List<MacroSnapshotEntry> snapshotEntries() {
        List<MacroSnapshotEntry> result = new ArrayList<>();
        for (MacroEntryDescriptor entry : entries.values()) {
            String value = entry.type() == MacroEntryType.FLAG ? "1" : Objects.toString(entry.value(), "");
            result.add(new MacroSnapshotEntry(
                    entry.name(),
                    value,
                    MacroContext.MacroLayer.RESOURCE_PACK,
                    id.toString(),
                    null,
                    entry.type(),
                    entry.editable(),
                    entry.displayKey(),
                    entry.summaryKey(),
                    entry.detailKey(),
                    entry.controlSpec()));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public String toString() {
        return "MacroTemplate{" +
                "id=" + id +
                ", entries=" + entries.size() +
                ", requires=" + requires +
                '}';
    }

    public static class Builder {
        private final KeyId id;
        private final LinkedHashMap<String, MacroEntryDescriptor> entries = new LinkedHashMap<>();
        private final LinkedHashSet<String> requires = new LinkedHashSet<>();

        public Builder(KeyId id) {
            this.id = id;
        }

        public Builder macro(String name, String value) {
            entries.put(name, MacroEntryDescriptor.constantValue(name, value));
            return this;
        }

        public Builder flag(String name) {
            entries.put(name, MacroEntryDescriptor.constantFlag(name));
            return this;
        }

        public Builder entry(MacroEntryDescriptor descriptor) {
            entries.put(descriptor.name(), descriptor);
            return this;
        }

        public Builder require(String name) {
            requires.add(name);
            return this;
        }

        public MacroTemplate build() {
            return new MacroTemplate(id, entries, requires);
        }
    }

    public static Builder builder(KeyId id) {
        return new Builder(id);
    }
}
