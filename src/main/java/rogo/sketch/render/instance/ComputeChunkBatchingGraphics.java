package rogo.sketch.render.instance;

import rogo.sketch.util.Identifier;

public class ComputeChunkBatchingGraphics extends ComputeGraphics {

    public ComputeChunkBatchingGraphics(Identifier identifier) {
        super(identifier, (c) -> {
        }, () -> {
        });
    }

    @Override
    public boolean shouldDiscard() {
        return false;
    }

    @Override
    public boolean shouldRender() {
        return true;
    }
}