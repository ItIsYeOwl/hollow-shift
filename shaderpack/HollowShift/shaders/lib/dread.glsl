// How dreadful is it where the player is standing? 0 = sunny field, 1 = dark cave at night.
// Iris shader packs can't read the mod's tension meter, so the pack works out its own
// from the same things that raise tension: darkness around the player and night time.

uniform ivec2 eyeBrightnessSmooth; // x = block light, y = sky light, 0..240
uniform int worldTime;
uniform float darknessFactor;      // the Darkness effect (given when the Hollow catches you)

float hollowDread() {
    float sky = eyeBrightnessSmooth.y / 240.0;
    float block = eyeBrightnessSmooth.x / 240.0;
    float t = float(worldTime);
    float night = smoothstep(12500.0, 13500.0, t) * (1.0 - smoothstep(22500.0, 23500.0, t));
    float dark = clamp(1.0 - max(sky * (1.0 - night * 0.85), block), 0.0, 1.0);
    return clamp(max(dark * 0.8 + night * 0.35, darknessFactor), 0.0, 1.0);
}
