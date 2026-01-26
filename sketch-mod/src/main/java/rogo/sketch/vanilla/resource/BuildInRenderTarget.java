package rogo.sketch.vanilla.resource;

import rogo.sketch.core.resource.RenderTarget;
import rogo.sketch.core.util.KeyId;

public class BuildInRenderTarget extends RenderTarget {

    public BuildInRenderTarget(int handle, KeyId keyId) {
        super(handle, keyId);
    }

    @Override
    public void dispose() {

    }
}