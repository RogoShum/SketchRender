package rogo.sketch.core.backend;

@FunctionalInterface
public interface CommandRecorderFactory {
    CommandRecorderFactory NO_OP = label -> new CommandRecorder() {
    };

    CommandRecorder create(String label);

    default CommandRecorder create() {
        return create("unnamed");
    }

    static CommandRecorderFactory noOp() {
        return NO_OP;
    }
}
