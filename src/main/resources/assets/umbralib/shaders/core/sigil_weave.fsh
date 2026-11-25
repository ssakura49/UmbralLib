#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float VisualTime;
uniform float SigilScale;
uniform float FlowSpeed;
uniform float PulseStrength;
uniform float GlowStrength;
uniform float EdgeSoftness;
uniform float AlphaCutoff;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float segmentDistance(vec2 point, vec2 start, vec2 end) {
    vec2 span = end - start;
    float lengthSquared = max(dot(span, span), 1.0e-6);
    float along = clamp(dot(point - start, span) / lengthSquared, 0.0, 1.0);
    return length(point - (start + span * along));
}

float stroke(float distanceToLine, float width, float aa) {
    return 1.0 - smoothstep(width - aa, width + aa, distanceToLine);
}

void main() {
    vec2 cell = vec2(fract(texCoord0.x * clamp(SigilScale, 1.0, 16.0)) - 0.5,
                     (texCoord0.y - 0.5) * 2.0);
    float aa = clamp((fwidth(cell.x) + fwidth(cell.y))
            * (1.0 + clamp(EdgeSoftness, 0.0, 1.0)), 0.001, 0.16);
    float diamond = max(
        max(stroke(segmentDistance(cell, vec2(0.0, -0.64), vec2(-0.30, 0.0)), 0.018, aa),
            stroke(segmentDistance(cell, vec2(-0.30, 0.0), vec2(0.0, 0.64)), 0.018, aa)),
        max(stroke(segmentDistance(cell, vec2(0.0, 0.64), vec2(0.30, 0.0)), 0.018, aa),
            stroke(segmentDistance(cell, vec2(0.30, 0.0), vec2(0.0, -0.64)), 0.018, aa)));
    float spine = stroke(abs(cell.x), 0.012, aa) * (1.0 - smoothstep(0.67, 0.74, abs(cell.y)));
    float crossbar = stroke(abs(cell.y), 0.012, aa) * (1.0 - smoothstep(0.20, 0.29, abs(cell.x)));
    float glyph = max(diamond, max(spine, crossbar));

    float across = abs(texCoord0.y * 2.0 - 1.0);
    float core = 1.0 - smoothstep(0.06 - aa, 0.12 + aa, across);
    float edgeLine = 1.0 - smoothstep(0.018 - aa, 0.018 + aa, abs(across - 0.88));
    float travel = fract(texCoord0.x * 3.0 - VisualTime * FlowSpeed);
    float glint = 1.0 - smoothstep(0.035, 0.12, abs(travel - 0.5));
    float pulse = 1.0 - clamp(PulseStrength, 0.0, 1.0)
            * (0.5 - 0.5 * sin(VisualTime * 2.4 + texCoord0.x * 6.2831853));
    float pattern = max(glyph * (0.38 + 0.22 * glint), max(core * 0.18, edgeLine * 0.12));

    vec4 sampleColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fog = 1.0 - smoothstep(FogStart, max(FogEnd, FogStart + 0.001), vertexDistance);
    float alpha = sampleColor.a * pattern * pulse * fog;
    if (alpha < max(AlphaCutoff, 0.001)) discard;
    vec3 color = sampleColor.rgb * GlowStrength * (0.78 + 0.22 * glyph + 0.18 * glint);
    fragColor = vec4(color, alpha);
}
