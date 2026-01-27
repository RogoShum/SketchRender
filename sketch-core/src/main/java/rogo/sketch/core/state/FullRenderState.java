package rogo.sketch.core.state;

import rogo.sketch.core.api.RenderStateComponent;
import rogo.sketch.core.util.KeyId;

import java.util.Arrays;

public class FullRenderState {
    private final RenderStateComponent[] states;
    private final int hash;

    protected FullRenderState(RenderStateComponent[] states) {
        this.states = states;
        this.hash = Arrays.hashCode(states);
    }

    public RenderStateComponent get(KeyId type) {
        int index = DefaultRenderStates.getIndex(type);
        return states[index];
    }

    public RenderStateComponent get(int index) {
        return states[index];
    }

    public RenderStateComponent[] getComponents() {
        return states;
    }

    public FullRenderState with(RenderStateComponent component) {
        int index = DefaultRenderStates.getIndex(component.getIdentifier());
        RenderStateComponent[] newStates = states.clone();
        newStates[index] = component;
        return new FullRenderState(newStates);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FullRenderState))
            return false;
        FullRenderState that = (FullRenderState) o;
        return Arrays.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}