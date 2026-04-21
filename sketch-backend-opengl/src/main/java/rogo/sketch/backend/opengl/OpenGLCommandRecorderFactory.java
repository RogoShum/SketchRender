package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.CommandRecorder;
import rogo.sketch.core.backend.CommandRecorderFactory;

final class OpenGLCommandRecorderFactory implements CommandRecorderFactory {
    @Override
    public CommandRecorder create(String label) {
        return new OpenGLCommandRecorder();
    }
}
