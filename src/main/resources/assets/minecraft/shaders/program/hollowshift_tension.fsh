#version 150

// The Hollow Shift: tension post-processing.
// Everything here scales with Tension (0..1), so at low tension the image is untouched.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;

uniform float Tension;     // 0 calm .. 1 terrified
uniform float Decay;       // 0..1, brief rot at the edges of vision
uniform float HSTime;      // seconds
uniform float Aberration;  // 0 or 1 (config / photosensitive mode)
uniform float Flicker;     // 0 or 1 (photosensitive mode turns it off)

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float t = clamp(Tension, 0.0, 1.0);
    float time = mod(HSTime, 600.0);

    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    float aspect = InSize.x / max(InSize.y, 1.0);
    vec2 ca = vec2(c.x * aspect, c.y);
    float r = length(ca) / length(vec2(0.5 * aspect, 0.5)); // 0 at centre, 1 in the corners

    // Colour fringing at the edges, only above ~75% tension.
    float ab = Aberration * smoothstep(0.75, 1.0, t) * 0.014 * r;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + c * ab).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - c * ab).b;

    // Drain the colour: greens go olive, skin goes pale.
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), t * 0.7);

    // A cold cast.
    col *= mix(vec3(1.0), vec3(0.93, 0.97, 1.04), t);

    // Harder contrast: shadows become opaque.
    col = (col - 0.5) * (1.0 + t * 0.3) + 0.5;
    col -= t * 0.035;
    col = max(col, 0.0);

    // Unsteady light, as if every flame in the world is struggling.
    float wobble = noise(vec2(time * 7.0, 0.0)) - 0.5;
    col *= 1.0 + Flicker * smoothstep(0.5, 1.0, t) * wobble * 0.06;

    // The edges close in.
    float inner = mix(0.95, 0.35, t);
    float vig = smoothstep(inner, inner + 0.55, r);
    col *= 1.0 - vig * mix(0.25, 0.92, t);

    // Peripheral decay: dark, mossy veins crawl in at the edge of vision, then recede.
    if (Decay > 0.001) {
        float n = noise(ca * 9.0 + time * 0.4) * 0.6 + noise(ca * 23.0 - time * 0.2) * 0.4;
        float veins = smoothstep(0.45, 0.75, n);
        float edge = smoothstep(0.45, 0.95, r);
        float k = Decay * edge;
        col = mix(col, col * vec3(0.72, 0.82, 0.58), k);
        col *= 1.0 - k * (0.45 + 0.5 * veins);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
