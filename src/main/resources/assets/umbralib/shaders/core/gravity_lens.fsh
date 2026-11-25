#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 umbralib_LensCenter;
uniform float umbralib_LensRadius;
uniform float umbralib_LensDepth;
uniform float umbralib_LensStrength;
uniform float umbralib_LensAge;
uniform float umbralib_Aspect;
uniform float umbralib_DepthBias;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 original = texture(Sampler0, texCoord0);
    vec2 metric = (texCoord0 - umbralib_LensCenter) * vec2(umbralib_Aspect, 1.0);
    float radius = max(umbralib_LensRadius, 0.00001);
    float distanceFromCenter = length(metric) / radius;
    if (distanceFromCenter >= 1.0 || umbralib_LensStrength <= 0.001) {
        fragColor = original;
        return;
    }

    float destinationDepth = texture(Sampler1, texCoord0).r;
    if (destinationDepth + umbralib_DepthBias < umbralib_LensDepth) {
        fragColor = original;
        return;
    }

    float edge = 1.0 - smoothstep(0.72, 1.0, distanceFromCenter);
    float coreFade = smoothstep(0.025, 0.15, distanceFromCenter);
    vec2 radial = metric / max(length(metric), 0.00001);
    vec2 tangent = vec2(-radial.y, radial.x);
    float flow = sin(umbralib_LensAge * 0.075 - distanceFromCenter * 10.0);
    float strength = clamp(umbralib_LensStrength, 0.0, 1.0);
    vec2 bend = radial * (0.34 + 0.09 * flow) + tangent * 0.30;
    vec2 sampleMetric = metric + bend * radius * edge * coreFade * strength;
    vec2 sampleUv = umbralib_LensCenter + sampleMetric / vec2(umbralib_Aspect, 1.0);
    if (any(lessThan(sampleUv, vec2(0.001))) || any(greaterThan(sampleUv, vec2(0.999)))) {
        fragColor = original;
        return;
    }

    float sourceDepth = texture(Sampler1, sampleUv).r;
    if (sourceDepth + umbralib_DepthBias < umbralib_LensDepth) {
        fragColor = original;
        return;
    }
    fragColor = texture(Sampler0, sampleUv);
}
