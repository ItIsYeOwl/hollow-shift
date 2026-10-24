#version 120

// The Hollow Shift: fog and colour.

#define FOG_STRENGTH 1.0 // How hard the fog closes in when it's dark. [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define DESATURATION 0.5 // How much colour drains out of the world. [0.0 0.25 0.5 0.75 1.0]

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float near;
uniform float far;
uniform int isEyeInWater;

varying vec2 texcoord;

#include "/lib/dread.glsl"

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - z * (far - near));
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
    float dread = hollowDread();

    if (isEyeInWater == 0) {
        float k = clamp(dread * FOG_STRENGTH, 0.0, 1.0);
        float fogEnd = mix(far, 22.0, k);
        float fogStart = mix(far * 0.6, 2.0, k);
        float f = depth >= 1.0
                ? k * 0.92                                            // the sky itself goes grey
                : smoothstep(fogStart, fogEnd, linearDepth(depth)) * k;
        col = mix(col, vec3(0.050, 0.052, 0.060), clamp(f, 0.0, 0.97));
    }

    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), DESATURATION * (0.35 + 0.65 * dread));
    col *= mix(vec3(1.0), vec3(0.94, 0.97, 1.03), dread);
    col = max((col - 0.5) * (1.0 + dread * 0.25) + 0.5 - dread * 0.03, 0.0);

/* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, 1.0);
}
