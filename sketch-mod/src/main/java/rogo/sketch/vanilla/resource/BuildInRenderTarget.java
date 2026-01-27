package rogo.sketch.vanilla.resource;

import rogo.sketch.core.resource.RenderTarget;
import rogo.sketch.core.util.KeyId;

import java.util.function.Supplier;

public class BuildInRenderTarget extends RenderTarget {
    protected final Supplier<Integer> fbId;

    public BuildInRenderTarget(Supplier<Integer> fbId, KeyId keyId) {
        super(fbId.get(), keyId);
        this.fbId = fbId;
    }

    @Override
    public int getHandle() {
        return fbId.get();
    }

    @Override
    public void dispose() {

    }
}