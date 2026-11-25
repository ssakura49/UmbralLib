#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float VisualTime;
uniform float GlowStrength;
uniform float FlowSpeed;
uniform float PulseStrength;
uniform float PulseSpeed;
uniform float EdgeSoftness;
uniform float AlphaCutoff;
uniform float umbralib_FlowHighlight;
uniform float umbralib_FlowHighlightScale;
uniform float umbralib_FlowHighlightWidth;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float umbralib_pulsePixelWeight(float pixelCycles) {
    float radians = min(abs(pixelCycles), 1.0) * 3.14159265;
    return radians < 0.001 ? 1.0 : max(0.0, sin(radians) / radians);
}

void main() {
    float seconds = VisualTime;
    vec2 uv = texCoord0;
    float pulse = 1.0;
    float edge = 1.0;
    if (abs(FlowSpeed) > 0.0001) {
        uv.x = fract(uv.x - seconds * FlowSpeed);
    }
    if (EdgeSoftness > 0.0) {
        float center = 1.0 - abs(uv.y * 2.0 - 1.0);
        edge = smoothstep(0.0, EdgeSoftness, center);
    }
    if (PulseStrength > 0.0) {
        float spatialPhase = texCoord0.x * 2.0;
        float pixelWeight = umbralib_pulsePixelWeight(dFdx(spatialPhase))
                * umbralib_pulsePixelWeight(dFdy(spatialPhase));
        float wave = sin((spatialPhase - seconds * PulseSpeed) * 6.2831853) * pixelWeight;
        pulse = 1.0 - clamp(PulseStrength, 0.0, 1.0) * (0.5 - 0.5 * wave);
    }
    vec4 sampleColor = texture(Sampler0, uv);
    vec4 color = sampleColor * vertexColor * ColorModulator;
    float fog = 1.0 - smoothstep(FogStart, max(FogEnd, FogStart + 0.001), vertexDistance);
    color.a *= edge * pulse * fog;
    float flowFootprint = 0.0;
    float flowCoordinate = 0.0;
    if (umbralib_FlowHighlight > 0.0) {
        flowCoordinate = texCoord0.x * umbralib_FlowHighlightScale - seconds * FlowSpeed;
        flowFootprint = fwidth(flowCoordinate);
    }
    if (color.a < AlphaCutoff) discard;
    if (umbralib_FlowHighlight > 0.0) {
        float flowDistance = abs(fract(flowCoordinate) - 0.5);
        float flowAa = clamp(flowFootprint * 0.5, 0.0005, 0.08);
        float flowWidth = umbralib_FlowHighlightWidth;
        float flowBand = 1.0 - smoothstep(max(0.0, flowWidth - flowAa), flowWidth + flowAa, flowDistance);
        float flowVisibility = 1.0 - smoothstep(0.15, 0.5, flowFootprint);
        color.rgb *= 1.0 + umbralib_FlowHighlight * flowBand * flowVisibility;
    }
    color.rgb *= GlowStrength;
    fragColor = color;
}
