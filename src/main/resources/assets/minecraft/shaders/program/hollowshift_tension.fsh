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

    // Harder contrast that only pushes downward: shadows become opaque, highlights dim
    // instead of blowing out (bright sand shouldn't glow in a terrified scene).
    col = (col - 0.35) * (1.0 + t * 0.3) + 0.35;
    col -= t * 0.05;
    col = max(col, 0.0);
    col = col / (1.0 + col * t * 0.45);

    // Unsteady light, as if every flame in the world is struggling.
    float wobble = noise(vec2(time * 7.0, 0.0)) - 0.5;
    col *= 1.0 + Flicker * smoothstep(0.5, 1.0, t) * wobble * 0.06;

    // The edges close in.
    float inner = mix(0.95, 0.35, t);
    float vig = smoothstep(inner, inner + 0.55, r);
    col *= 1.0 - vig * mix(0.25, 0.92, t);

    // Peripheral decay: dark, mossy veins crawl in at the edge of vision, then recede.
    if (Decay > 0.001) {
        float n = noise(ca * 7.0 + time * 0.4) * 0.55 + noise(ca * 19.0 - time * 0.25) * 0.3
                + noise(ca * 45.0) * 0.15;
        float veins = smoothstep(0.42, 0.62, n);
        float edge = smoothstep(0.45, 0.9, r + (n - 0.5) * 0.25); // ragged, not a clean circle
        float k = smoothstep(0.0, 0.4, Decay) * edge;
        vec3 rot = mix(vec3(0.10, 0.13, 0.06), vec3(0.24, 0.30, 0.14), n); // dark moss
        col = mix(col, rot, k * (0.55 + 0.45 * veins));
    }

    // Tiny per-pixel noise hides the banding that dark gradients otherwise show.
    col += (hash(gl_FragCoord.xy + fract(time) * 97.0) - 0.5) / 255.0;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
