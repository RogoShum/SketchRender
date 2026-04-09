package rogo.sketch.core.backend;

import rogo.sketch.core.api.ResourceObject;

/**
 * Marker interface for backend-owned installed GPU resources.
 * <p>
 * Core descriptor/spec objects remain resource-authoring inputs. Objects that
 * implement this interface represent live backend state that can actually be
 * bound, executed, or attached by a backend runtime.
 * </p>
 */
public interface BackendInstalledResource extends ResourceObject {
}

