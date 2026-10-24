"""
Generates every texture and sound for The Hollow Shift from code.

These are deliberately simple placeholders that already fit the mood. Replace any file
with a hand-made one later (same path, same name) and the mod picks it up.

Needs: Python 3, numpy, Pillow, and ffmpeg with libvorbis on the PATH.
Run from the project folder:   python tools/generate_assets.py
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(ROOT, "src/main/resources/assets/hollowshift")
PACK = os.path.join(ROOT, "src/client/resources/resourcepacks/hollow_atmosphere")
RNG = np.random.default_rng(1337)
SR = 32000


def path(*parts):
    p = os.path.join(*parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


# =============================================================================
# Textures
# =============================================================================

def hollow_texture():
    """Matte black that swallows light. The faintest grain so it isn't a flat hole in the screen."""
    base = RNG.normal(11, 2.2, (64, 64)).clip(5, 18).astype(np.uint8)
    img = np.stack([base, base, (base * 1.08).clip(0, 255).astype(np.uint8), np.full_like(base, 255)], -1)
    Image.fromarray(img, "RGBA").save(path(MOD, "textures/entity/hollow.png"))


def torch_column(flame_top, flame_low, ember):
    """A 16x16 torch: 2px stick in columns 7-8, rows 6-15, as the vanilla torch model expects."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in (7, 8):
        if flame_top:
            px[x, 6] = flame_top
        if flame_low:
            px[x, 7] = flame_low
        px[x, 8] = ember
        for y in range(9, 16):
            shade = 1.0 if x == 7 else 0.8
            px[x, y] = (int(104 * shade), int(76 * shade), int(44 * shade), 255)
    return img


def atmosphere_torch():
    """Unsteady, uneven flame: mostly normal, sometimes dim, occasionally guttering."""
    frames = [
        ((255, 246, 188, 255), (255, 196, 84, 255), (226, 120, 40, 255)),   # 0 normal
        ((255, 226, 150, 255), (240, 168, 66, 255), (204, 104, 36, 255)),   # 1 slightly low
        ((232, 176, 96, 255), (196, 116, 44, 255), (150, 78, 30, 255)),     # 2 dim
        ((255, 250, 210, 255), (255, 212, 110, 255), (236, 136, 52, 255)),  # 3 flare
        (None, (150, 82, 30, 255), (110, 60, 26, 255)),                     # 4 gutter
    ]
    sheet = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(torch_column(*f), (0, 16 * i))
    sheet.save(path(PACK, "assets/minecraft/textures/block/torch.png"))
    order = [(0, 5), (1, 2), (0, 7), (3, 1), (0, 3), (1, 4), (2, 2), (0, 6), (4, 1), (2, 1), (0, 9), (1, 3)]
    meta = {"animation": {"interpolate": False, "frames": [{"index": i, "time": t} for i, t in order]}}
    with open(path(PACK, "assets/minecraft/textures/block/torch.png.mcmeta"), "w") as fh:
        json.dump(meta, fh, indent=2)


def burnt_torch():
    img = torch_column(None, None, (40, 36, 34, 255))
    px = img.load()
    for x in (7, 8):
        px[x, 7] = (70, 66, 62, 255)     # ash
        for y in range(9, 12):
            px[x, y] = (34, 26, 20, 255)  # charred
    img.save(path(MOD, "textures/item/burnt_torch.png"))


def colormap(hot_dry, hot_wet, cold, desat, darken, name):
    """Approximation of the vanilla biome colour gradient, drained and darkened."""
    h = w = 256
    xs, ys = np.meshgrid(np.arange(w), np.arange(h))
    temp = 1 - xs / 255.0
    humid = np.where(temp > 0, (1 - ys / 255.0) / np.maximum(temp, 1e-6), 0).clip(0, 1)
    hd, hw, cd = (np.array(c, float) for c in (hot_dry, hot_wet, cold))
    hot = hd[None, None] * (1 - humid[..., None]) + hw[None, None] * humid[..., None]
    col = hot * temp[..., None] + cd[None, None] * (1 - temp[..., None])
    lum = (col @ np.array([0.299, 0.587, 0.114]))[..., None]
    col = col * (1 - desat) + lum * desat
    col = col * (1 - darken) + np.array([6, 4, 0]) * darken  # drift toward olive-brown
    img = np.concatenate([col.clip(0, 255).astype(np.uint8), np.full((h, w, 1), 255, np.uint8)], -1)
    Image.fromarray(img, "RGBA").save(path(PACK, f"assets/minecraft/textures/colormap/{name}.png"))


def silhouette_image(size, figure_alpha):
    """Fog, and something standing in it."""
    s = size
    ys = np.linspace(0, 1, s)[:, None]
    fog = (14 + 34 * ys ** 1.4 + RNG.normal(0, 2, (s, s))).clip(0, 255)
    img = Image.fromarray(np.stack([fog, fog, fog * 1.06], -1).clip(0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img)
    cx, top, k = s / 2, s * 0.18, s / 128
    d.rectangle([cx - 5 * k, top, cx + 5 * k, top + 10 * k], fill=(4, 4, 6))              # head
    d.rectangle([cx - 6 * k, top + 11 * k, cx + 6 * k, top + 52 * k], fill=(4, 4, 6))     # body
    d.rectangle([cx - 12 * k, top + 12 * k, cx - 9 * k, top + 66 * k], fill=(4, 4, 6))    # arms, too long
    d.rectangle([cx + 9 * k, top + 12 * k, cx + 12 * k, top + 66 * k], fill=(4, 4, 6))
    d.rectangle([cx - 6 * k, top + 53 * k, cx - 1 * k, top + 96 * k], fill=(4, 4, 6))     # legs
    d.rectangle([cx + 1 * k, top + 53 * k, cx + 6 * k, top + 96 * k], fill=(4, 4, 6))
    fogged = img.filter(ImageFilter.GaussianBlur(0.6 * k))
    return Image.blend(img, fogged, 1 - figure_alpha)


def icons():
    silhouette_image(128, 0.85).save(path(MOD, "icon.png"))
    silhouette_image(64, 0.8).save(path(PACK, "pack.png"))


# =============================================================================
# Audio
# =============================================================================

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def bandpass(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = np.clip(np.minimum((f - lo) / (lo * 0.3 + 1), (hi - f) / (hi * 0.3 + 1)), 0, 1)
    return np.fft.irfft(spec * mask, len(x))


def reverb(x, seconds=2.2, wet=0.5):
    n = int(seconds * SR)
    ir = RNG.normal(0, 1, n) * np.exp(-np.arange(n) / (SR * seconds / 6))
    ir = bandpass(ir, 120, 5000)
    ir /= np.sqrt(np.sum(ir ** 2))
    size = len(x) + n
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)
    out = np.concatenate([x, np.zeros(n)]) * (1 - wet) + y * wet
    return out


def env(n, attack, release):
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] = np.minimum(e[-r:], np.linspace(1, 0, r))
    return e


def normalize(x, peak_db):
    m = np.max(np.abs(x)) or 1.0
    return x / m * (10 ** (peak_db / 20))


def write_ogg(x, rel, peak_db=-3.0):
    x = normalize(x, peak_db)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        out = path(MOD, "sounds", rel + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name,
                        "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    os.unlink(tmp.name)


def drone():
    """24 s seamless loop. Every frequency completes whole cycles, so the loop point is invisible."""
    dur = 24.0
    t = t_axis(dur)
    x = np.zeros_like(t)
    for f, a in [(41 + 1 / 24, 1.0), (55 + 2 / 24, 0.6), (61.75, 0.45), (82.5, 0.25), (123.25, 0.12)]:
        x += a * np.sin(2 * np.pi * f * t)
    x *= 0.75 + 0.25 * np.sin(2 * np.pi * t / 8)          # slow breathing swell, 3 per loop
    rumble = bandpass(RNG.normal(0, 1, len(t)), 30, 180)    # circular FFT filter = loops cleanly
    x += 0.5 * rumble / np.max(np.abs(rumble))
    write_ogg(x, "ambient/drone", -6)


def heartbeat():
    t = t_axis(0.9)
    def thump(start, amp):
        tt = np.clip(t - start, 0, None)
        return amp * np.sin(2 * np.pi * 52 * tt) * np.exp(-tt * 18) * (t >= start)
    x = thump(0.02, 1.0) + thump(0.27, 0.7)
    write_ogg(x, "ambient/heartbeat", -4)


def breath(name, dur, seed):
    r = np.random.default_rng(seed)
    t = t_axis(dur)
    noise = bandpass(r.normal(0, 1, len(t)), 280, 1600)
    half = dur * 0.45
    shape = np.where(t < half, np.sin(np.pi * t / half / 2) ** 2,
                     np.exp(-((t - half) / (dur - half)) * 2.2) * np.sin(np.pi * np.minimum(1, (t - half) / 0.12) / 2))
    write_ogg(reverb(noise * shape, 1.2, 0.25), f"ambient/{name}", -8)


def whisper(name, seed):
    r = np.random.default_rng(seed)
    t = t_axis(1.7)
    noise = bandpass(r.normal(0, 1, len(t)), 1800, 6500)
    syll = np.zeros_like(t)
    pos = 0.05
    while pos < 1.5:
        length = r.uniform(0.09, 0.22)
        s = (t >= pos) & (t < pos + length)
        syll[s] = np.sin(np.pi * (t[s] - pos) / length) * r.uniform(0.5, 1)
        pos += length + r.uniform(0.02, 0.09)
    write_ogg(reverb(noise * syll, 1.5, 0.35), f"ambient/{name}", -10)


def metal_grind():
    t = t_axis(4.5)
    drift = 1 + 0.03 * np.sin(2 * np.pi * 0.4 * t)
    x = sum(a * np.sin(2 * np.pi * f * drift * t + RNG.uniform(0, 6)) for f, a in
            [(173, 1), (419, 0.6), (731, 0.45), (1187, 0.3), (1913, 0.2)])
    x *= 0.6 + 0.4 * bandpass(RNG.normal(0, 1, len(t)), 4, 18) / 0.3
    x += 0.3 * bandpass(RNG.normal(0, 1, len(t)), 900, 4000)
    x *= env(len(t), 0.8, 1.5)
    write_ogg(reverb(x, 3.0, 0.6), "ambient/metal_grind", -9)


def cave_tracks():
    """Replacements for vanilla cave ambience: further away, lower, wronger."""
    # 1: a deep impact, very far off
    t = t_axis(1.0)
    thud = np.sin(2 * np.pi * 38 * t) * np.exp(-t * 6) + 0.4 * bandpass(RNG.normal(0, 1, len(t)), 40, 300) * np.exp(-t * 9)
    write_ogg(reverb(thud, 4.0, 0.75), "ambient/cave1", -6)
    # 2: a slow descending groan
    t = t_axis(5.0)
    f = 140 * np.exp(-t * 0.18)
    groan = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.3 * np.sin(2 * np.pi * np.cumsum(f * 1.51) / SR)
    write_ogg(reverb(groan * env(len(t), 1.2, 2.0), 3.5, 0.65), "ambient/cave2", -9)
    # 3: something metallic dragged across stone
    t = t_axis(3.5)
    scrape = bandpass(RNG.normal(0, 1, len(t)), 1500, 5000) * (0.5 + 0.5 * np.sin(2 * np.pi * 2.5 * t) ** 2)
    scrape += 0.4 * np.sin(2 * np.pi * 611 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t))
    write_ogg(reverb(scrape * env(len(t), 0.4, 1.2), 3.5, 0.7), "ambient/cave3", -12)
    # 4: a breath that isn't yours, echoing
    t = t_axis(3.0)
    b = bandpass(RNG.normal(0, 1, len(t)), 250, 1200) * np.sin(np.pi * t / 3.0) ** 3
    write_ogg(reverb(b, 4.0, 0.8), "ambient/cave4", -10)


def hollow_sounds():
    for i in (1, 2):
        t = t_axis(0.35)
        step = (np.sin(2 * np.pi * (60 + 8 * i) * t) * np.exp(-t * 20)
                + 0.5 * bandpass(RNG.normal(0, 1, len(t)), 80, 900) * np.exp(-t * 30))
        write_ogg(step, f"entity/hollow_step{i}", -6)

    # The catch: a dissonant cluster that hits all at once, with a noise burst underneath.
    t = t_axis(1.8)
    cluster = sum(np.sign(np.sin(2 * np.pi * f * t)) * 0.25 + np.sin(2 * np.pi * f * t)
                  for f in (196, 207.7, 277.2, 293.7, 415.3))
    cluster = bandpass(cluster, 80, 6000) * np.exp(-t * 2.2)
    burst = bandpass(RNG.normal(0, 1, len(t)), 200, 7000) * np.exp(-t * 7) * 1.5
    write_ogg(reverb(cluster + burst, 2.0, 0.35), "entity/hollow_catch", -3)

    # Vanish: an inhale played backwards, gone.
    t = t_axis(1.2)
    swell = bandpass(RNG.normal(0, 1, len(t)), 300, 3000) * (t / 1.2) ** 3 * env(len(t), 0, 0.05)
    write_ogg(swell, "entity/hollow_vanish", -10)


def silence():
    write_ogg(RNG.normal(0, 1e-4, int(0.3 * SR)), "silent", -60)


# =============================================================================
# sounds.json
# =============================================================================

def sounds_json():
    def s(*names, subtitle=None, stream=False):
        entry = {"sounds": [{"name": f"hollowshift:{n}", "stream": True} if stream else f"hollowshift:{n}" for n in names]}
        if subtitle:
            entry["subtitle"] = f"subtitles.hollowshift.{subtitle}"
        return entry

    data = {
        "ambient.drone": s("ambient/drone", stream=True),
        "ambient.heartbeat": s("ambient/heartbeat"),
        "ambient.breathing": s("ambient/breath1", "ambient/breath2", subtitle="breathing"),
        "ambient.whisper": s("ambient/whisper1", "ambient/whisper2", "ambient/whisper3", subtitle="whisper"),
        "ambient.metal_grind": s("ambient/metal_grind", subtitle="metal_grind"),
        "entity.hollow.step": s("entity/hollow_step1", "entity/hollow_step2", subtitle="hollow_step"),
        "entity.hollow.catch": s("entity/hollow_catch", subtitle="hollow_catch"),
        "entity.hollow.vanish": s("entity/hollow_vanish", subtitle="hollow_vanish"),
    }
    for lie in ("footsteps_behind", "door_creaks", "breathing_close", "something_watches", "your_name"):
        data[f"lie.{lie}"] = s("silent", subtitle=f"lie.{lie}")
    with open(path(MOD, "sounds.json"), "w") as fh:
        json.dump(data, fh, indent=2)

    # The atmosphere pack: replace cave ambience, and knock passive mobs slightly off-key.
    caves = [f"hollowshift:ambient/cave{i}" for i in range(1, 5)] + ["hollowshift:ambient/metal_grind"]
    def offkey(files, subtitle):
        return {"replace": True, "subtitle": subtitle,
                "sounds": [{"name": f, "pitch": p} for f in files for p in (0.82, 0.9)]}
    pack = {
        "ambient.cave": {"replace": True, "sounds": [{"name": c, "volume": 0.9} for c in caves]},
        "entity.cow.ambient": offkey([f"mob/cow/say{i}" for i in range(1, 5)], "subtitles.entity.cow.ambient"),
        "entity.sheep.ambient": offkey([f"mob/sheep/say{i}" for i in range(1, 4)], "subtitles.entity.sheep.ambient"),
        "entity.pig.ambient": offkey([f"mob/pig/say{i}" for i in range(1, 4)], "subtitles.entity.pig.ambient"),
        "entity.chicken.ambient": offkey([f"mob/chicken/say{i}" for i in range(1, 4)], "subtitles.entity.chicken.ambient"),
    }
    with open(path(PACK, "assets/minecraft/sounds.json"), "w") as fh:
        json.dump(pack, fh, indent=2)
    with open(path(PACK, "pack.mcmeta"), "w") as fh:
        json.dump({"pack": {"pack_format": 34,
                            "description": "Muted colour, unsteady flames, wrong sounds"}}, fh, indent=2)


if __name__ == "__main__":
    hollow_texture()
    atmosphere_torch()
    burnt_torch()
    colormap((191, 183, 85), (71, 205, 51), (128, 180, 151), 0.55, 0.2, "grass")
    colormap((174, 164, 42), (48, 165, 24), (96, 161, 123), 0.42, 0.22, "foliage")
    icons()
    drone()
    heartbeat()
    breath("breath1", 2.6, 11)
    breath("breath2", 3.1, 12)
    for i in (1, 2, 3):
        whisper(f"whisper{i}", 20 + i)
    metal_grind()
    cave_tracks()
    hollow_sounds()
    silence()
    sounds_json()
    print("assets generated")
