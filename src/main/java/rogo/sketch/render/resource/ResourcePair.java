package rogo.sketch.render.resource;

import rogo.sketch.util.Identifier;

public class ResourcePair {
    private final Identifier bindingName;
    private final Identifier resourceName;

    public ResourcePair(final Identifier bindingName, final Identifier resourceName) {
        this.bindingName = bindingName;
        this.resourceName = resourceName;
    }

    public Identifier getBindingName() {
        return bindingName;
    }

    public Identifier getResourceName() {
        return resourceName;
    }
}