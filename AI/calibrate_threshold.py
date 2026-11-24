"""Measure how CLIP photo similarity separates the same item from different items.

Usage:
    python calibrate_threshold.py <folder of .jpg/.png photos>

Name each photo "<category>_<anything>.jpg" (for example "Bag_black-backpack.jpg").
Each photo is compared with a re-photographed copy of itself (random crop, rotation,
lighting, colour and mirroring) and with every other photo, and the script prints how
many pairs each threshold would keep. Use the result to set app.ai.min-score and
app.ai.cross-category-min-score in the backend's application.properties.
"""
import glob
import itertools
import os
import random
import sys

from PIL import Image, ImageEnhance, ImageOps
from sentence_transformers import SentenceTransformer

MODEL_NAME = "clip-ViT-B-32"  # must match main.py


def rephotograph(im, seed):
    rnd = random.Random(seed)
    w, h = im.size
    c = rnd.uniform(0.6, 0.8)
    x, y = rnd.uniform(0, 1 - c) * w, rnd.uniform(0, 1 - c) * h
    im = im.crop((x, y, x + c * w, y + c * h))
    im = im.rotate(rnd.uniform(-25, 25), expand=True, fillcolor=(rnd.randint(0, 255),) * 3)
    im = ImageEnhance.Brightness(im).enhance(rnd.uniform(0.6, 1.3))
    im = ImageEnhance.Color(im).enhance(rnd.uniform(0.5, 1.4))
    return ImageOps.mirror(im) if seed % 2 else im


def summary(values):
    v = sorted(values)
    return f"min {v[0]:.3f}  median {v[len(v) // 2]:.3f}  max {v[-1]:.3f}  (n={len(v)})"


def main(folder):
    files = sorted(glob.glob(os.path.join(folder, "*.jpg")) + glob.glob(os.path.join(folder, "*.png")))
    if len(files) < 3:
        sys.exit("Put at least 3 photos in the folder.")
    names = [os.path.splitext(os.path.basename(f))[0] for f in files]
    photos = [Image.open(f).convert("RGB") for f in files]

    model = SentenceTransformer(MODEL_NAME)
    orig = model.encode(photos, normalize_embeddings=True)
    again = model.encode([rephotograph(p, i) for i, p in enumerate(photos)], normalize_embeddings=True)

    same = [float(orig[i] @ again[i]) for i in range(len(names))]
    same_cat, diff_cat = [], []
    for i, j in itertools.combinations(range(len(names)), 2):
        s = float(orig[i] @ again[j])
        (same_cat if names[i].split("_")[0] == names[j].split("_")[0] else diff_cat).append(s)

    print("Same item, re-photographed:       ", summary(same))
    if same_cat:
        print("Different item, same category:    ", summary(same_cat))
    if diff_cat:
        print("Different item, other category:   ", summary(diff_cat))
    print()
    for t in (0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90):
        line = f"threshold {t:.2f}: same item kept {sum(s >= t for s in same)}/{len(same)}"
        if same_cat:
            line += f", same-category false matches {sum(s >= t for s in same_cat)}/{len(same_cat)}"
        if diff_cat:
            line += f", other-category false matches {sum(s >= t for s in diff_cat)}/{len(diff_cat)}"
        print(line)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
