#version 120

// The Hollow Shift: vignette, film grain, colour fringing.

#define VIGNETTE 1.0 // How far the edges close in. [0.0 0.5 1.0 1.5]
#define GRAIN 0.5 // Film grain. [0.0 0.25 0.5 0.75 1.0]
#define ABERRATION // Colour fringing at the edges when dread is high.

uniform sampler2D colortex0;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;

varying vec2 texcoord;

#include "/lib/dread.glsl"

void main() {
    vec2 uv = texcoord;
    vec2 c = uv - 0.5;
    float dread = hollowDread();
    float aspect = viewWidth / viewHeight;
    float r = length(vec2(c.x * aspect, c.y)) / length(vec2(0.5 * aspect, 0.5));

    vec3 col;
#ifdef ABERRATION
    float ab = smoothstep(0.6, 1.0, dread) * 0.012 * r;
    col.r = texture2D(colortex0, uv + c * ab).r;
    col.g = texture2D(colortex0, uv).g;
    col.b = texture2D(colortex0, uv - c * ab).b;
#else
    col = texture2D(colortex0, uv).rgb;
#endif

    float inner = mix(0.9, 0.4, dread);
    col *= 1.0 - smoothstep(inner, inner + 0.6, r) * mix(0.2, 0.85, dread) * VIGNETTE;

    float g = fract(sin(dot(uv * vec2(viewWidth, viewHeight) + mod(frameTimeCounter, 100.0) * 61.0,
                            vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    col += g * GRAIN * 0.06 * (0.4 + 0.6 * dread);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
