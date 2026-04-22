package rogo.sketch.core.backend;

@FunctionalInterface
public interface CommandEncoderFactory {
    CommandEncoderFactory NO_OP = label -> new CommandEncoder() {
    };

    CommandEncoder create(String label);

    default CommandEncoder create() {
        return create("unnamed");
    }

    static CommandEncoderFactory noOp() {
        return NO_OP;
    }
}
