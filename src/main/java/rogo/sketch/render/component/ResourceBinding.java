package rogo.sketch.render.component;

import rogo.sketch.render.resource.ResourcePair;
import rogo.sketch.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ResourceBinding {
    private final Map<Identifier, Set<ResourcePair>> uniformToTexture = new HashMap<>();

    public void add(Identifier resourceType, ResourcePair resourcePair) {
        uniformToTexture.computeIfAbsent(resourceType, k -> new HashSet<>()).add(resourcePair);
    }

    public Set<ResourcePair> getResourcePair(Identifier resourceType) {
        return uniformToTexture.get(resourceType);
    }
}