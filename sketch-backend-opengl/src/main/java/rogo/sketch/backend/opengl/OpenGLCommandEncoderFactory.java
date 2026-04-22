package rogo.sketch.backend.opengl;

import rogo.sketch.core.backend.CommandEncoder;
import rogo.sketch.core.backend.CommandEncoderFactory;

final class OpenGLCommandEncoderFactory implements CommandEncoderFactory {
    @Override
    public CommandEncoder create(String label) {
        return new OpenGLCommandEncoder();
    }
}
