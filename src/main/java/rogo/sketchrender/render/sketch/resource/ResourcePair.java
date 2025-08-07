package rogo.sketchrender.render.sketch.resource;

import rogo.sketchrender.util.Identifier;

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