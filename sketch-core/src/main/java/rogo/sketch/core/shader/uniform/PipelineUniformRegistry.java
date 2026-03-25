package rogo.sketch.core.shader.uniform;

import rogo.sketch.core.util.KeyId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner-scoped runtime uniform providers.
 */
public class PipelineUniformRegistry {
    private final Map<KeyId, LinkedHashMap<String, ValueGetter<?>>> hooksByUniform = new ConcurrentHashMap<>();
    private final Map<String, Set<KeyId>> hooksByOwner = new ConcurrentHashMap<>();

    public void register(String ownerId, KeyId uniformId, ValueGetter<?> getter) {
        hooksByUniform.computeIfAbsent(uniformId, ignored -> new LinkedHashMap<>()).put(ownerId, getter);
        hooksByOwner.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(uniformId);
    }

    public void unregisterOwner(String ownerId) {
        Set<KeyId> ownedUniforms = hooksByOwner.remove(ownerId);
        if (ownedUniforms == null) {
            return;
        }
        for (KeyId uniformId : ownedUniforms) {
            LinkedHashMap<String, ValueGetter<?>> owners = hooksByUniform.get(uniformId);
            if (owners == null) {
                continue;
            }
            owners.remove(ownerId);
            if (owners.isEmpty()) {
                hooksByUniform.remove(uniformId);
            }
        }
    }

    public ValueGetter<?> resolve(KeyId uniformId) {
        LinkedHashMap<String, ValueGetter<?>> owners = hooksByUniform.get(uniformId);
        if (owners == null || owners.isEmpty()) {
            return null;
        }
        ValueGetter<?> resolved = null;
        for (ValueGetter<?> getter : owners.values()) {
            resolved = getter;
        }
        return resolved;
    }

    public Map<KeyId, ValueGetter<?>> snapshot() {
        Map<KeyId, ValueGetter<?>> result = new LinkedHashMap<>();
        for (KeyId keyId : hooksByUniform.keySet()) {
            ValueGetter<?> getter = resolve(keyId);
            if (getter != null) {
                result.put(keyId, getter);
            }
        }
        return result;
    }
}
