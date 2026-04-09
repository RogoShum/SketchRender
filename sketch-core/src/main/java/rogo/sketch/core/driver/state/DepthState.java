package rogo.sketch.core.driver.state;

/**
 * Runtime depth state compiled from authoring-side depth-test and depth-mask overrides.
 */
public record DepthState(
        boolean testEnabled,
        CompareOp compareOp,
        boolean writeEnabled
) {
    public DepthState {
        compareOp = compareOp != null ? compareOp : CompareOp.LESS;
    }
}

