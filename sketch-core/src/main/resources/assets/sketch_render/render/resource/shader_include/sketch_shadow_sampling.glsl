float sketch_shadow_enabled(vec4 shadowParams) {
    return clamp(shadowParams.x, 0.0, 1.0);
}

float sketch_shadow_bias(vec4 shadowParams) {
    return shadowParams.y;
}

float sketch_shadow_strength(vec4 shadowParams) {
    return clamp(shadowParams.z, 0.0, 1.0);
}

vec2 sketch_shadow_texel_size(vec4 shadowParams) {
    float mapSize = max(shadowParams.w, 1.0);
    return vec2(1.0 / mapSize);
}

float sketch_shadow_sample_compare(
        sampler2D shadowMap,
        vec3 shadowCoord,
        vec2 offset,
        vec4 shadowParams) {
    float closestDepth = texture(shadowMap, shadowCoord.xy + offset * sketch_shadow_texel_size(shadowParams)).r;
    return shadowCoord.z - sketch_shadow_bias(shadowParams) > closestDepth ? 1.0 : 0.0;
}

float sketch_shadow_pcf3x3(
        sampler2D shadowMap,
        vec3 shadowCoord,
        vec4 shadowParams) {
    float shadow = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            shadow += sketch_shadow_sample_compare(
                    shadowMap,
                    shadowCoord,
                    vec2(float(x), float(y)),
                    shadowParams);
        }
    }
    return (shadow / 9.0) * sketch_shadow_strength(shadowParams);
}

float sketch_projected_shadow(
        vec3 worldPos,
        mat4 lightViewProjection,
        sampler2D shadowMap,
        vec4 shadowParams) {
    vec4 lightClip = lightViewProjection * vec4(worldPos, 1.0);
    if (abs(lightClip.w) < 0.00001) {
        return 0.0;
    }
    vec3 shadowCoord = lightClip.xyz / lightClip.w;
    shadowCoord = shadowCoord * 0.5 + 0.5;
    if (shadowCoord.x < 0.0 || shadowCoord.x > 1.0
            || shadowCoord.y < 0.0 || shadowCoord.y > 1.0
            || shadowCoord.z < 0.0 || shadowCoord.z > 1.0) {
        return 0.0;
    }
    return sketch_shadow_pcf3x3(shadowMap, shadowCoord, shadowParams) * sketch_shadow_enabled(shadowParams);
}
