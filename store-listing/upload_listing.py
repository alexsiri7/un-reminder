#!/usr/bin/env python3
"""Upload store listing text and screenshots for Un-Reminder to Google Play."""

import json, os, io, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseUpload

PKG  = 'net.interstellarai.unreminder'
LANG = 'en-US'
BASE = Path(__file__).parent

FONT_BOLD = "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf"
FONT_REG  = "/usr/share/fonts/truetype/ubuntu/Ubuntu-R.ttf"
FONT_MED  = "/usr/share/fonts/truetype/ubuntu/Ubuntu-M.ttf"

W, H   = 1080, 1920
FW, FH = 1024, 500

TOP    = (15, 23, 42)
BOTTOM = (30, 45, 80)
ACCENT = (100, 180, 255)
WHITE  = (240, 248, 255)
DIM    = (160, 185, 220)


def fnt(path, size):
    return ImageFont.truetype(path, size)


def gradient(size, top, bottom):
    w, h = size
    img = Image.new('RGBA', (w, h))
    for y in range(h):
        t = y / h
        r = int(top[0] * (1-t) + bottom[0] * t)
        g = int(top[1] * (1-t) + bottom[1] * t)
        b = int(top[2] * (1-t) + bottom[2] * t)
        for x in range(w):
            img.putpixel((x, y), (r, g, b, 255))
    return img


def wrap_text(draw, text, x, y, max_w, font, fill, spacing=1.5):
    words = text.split()
    lines, cur = [], []
    for word in words:
        test = ' '.join(cur + [word])
        bx = draw.textbbox((0, 0), test, font=font)
        if bx[2] - bx[0] > max_w and cur:
            lines.append(' '.join(cur))
            cur = [word]
        else:
            cur.append(word)
    if cur:
        lines.append(' '.join(cur))
    for line in lines:
        draw.text((x, y), line, font=font, fill=fill)
        bx = draw.textbbox((0, 0), line, font=font)
        y += int((bx[3] - bx[1]) * spacing)
    return y


def screenshot_1():
    img = gradient((W, H), TOP, BOTTOM)
    d = ImageDraw.Draw(img, 'RGBA')
    cx, cy = W // 2, 480
    for r, a in [(260, 25), (200, 18), (140, 12)]:
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(*ACCENT, a))
    d.ellipse([cx-100, cy-100, cx+100, cy+100], fill=(70, 140, 220, 55))
    # Bell shape (simple geometric stand-in for emoji)
    d.ellipse([cx-52, cy-70, cx+52, cy+40], fill=(*ACCENT, 220))
    d.rectangle([cx-20, cy+40, cx+20, cy+60], fill=(*ACCENT, 220))
    d.ellipse([cx-16, cy+55, cx+16, cy+80], fill=(*ACCENT, 180))

    d.text((W//2, 790), "Un-Reminder", font=fnt(FONT_BOLD, 90), fill=WHITE, anchor='mm')
    d.text((W//2, 900), "AI Habit Coach", font=fnt(FONT_MED, 42), fill=ACCENT, anchor='mm')
    d.rectangle([W//2-120, 960, W//2+120, 963], fill=(*ACCENT, 100))
    wrap_text(d, "Break the 7 PM habit trap. Get AI-written reminders that fire at random times — so you'll never tune them out.",
              80, 1010, W-160, fnt(FONT_REG, 38), DIM)
    return img.convert('RGB')


def screenshot_2():
    img = gradient((W, H), TOP, BOTTOM)
    d = ImageDraw.Draw(img, 'RGBA')
    d.text((W//2, 155), "How it works", font=fnt(FONT_BOLD, 70), fill=WHITE, anchor='mm')
    d.rectangle([160, 208, W-160, 211], fill=(*ACCENT, 80))

    features = [
        ("Random timing",     "Never the same time twice — your brain stays alert"),
        ("AI notifications",  "Every prompt is uniquely written to match your habit"),
        ("Location-aware",    "Only notifies you where you can actually act on it"),
        ("Adapts to you",     "Dedication level rises as you complete consistently"),
        ("Stays on-device",   "Habits never leave your phone — complete privacy"),
    ]
    icons = ["🎲", "🤖", "📍", "📈", "🔒"]

    y = 295
    for (title, sub), icon in zip(features, icons):
        d.rounded_rectangle([60, y, W-60, y+185], radius=18, fill=(255, 255, 255, 12))
        d.text((108, y+46), icon, font=fnt(FONT_BOLD, 56), fill=ACCENT)
        d.text((210, y+32), title, font=fnt(FONT_BOLD, 44), fill=WHITE)
        d.text((210, y+92), sub,   font=fnt(FONT_REG,  34), fill=DIM)
        y += 210
    return img.convert('RGB')


def screenshot_3():
    img = gradient((W, H), TOP, BOTTOM)
    d = ImageDraw.Draw(img, 'RGBA')
    d.text((W//2, 155), "Private by design", font=fnt(FONT_BOLD, 68), fill=WHITE, anchor='mm')
    d.rectangle([160, 208, W-160, 211], fill=(*ACCENT, 80))

    items = [
        ("No account required",   "Just install and add your habits"),
        ("No cloud sync",         "All habit data lives on your device"),
        ("AI via your proxy",     "Connect your own Cloudflare Worker"),
        ("Open source",           "Full source code on GitHub"),
        ("No ads, ever",          "Built for the author, shared with you"),
    ]
    y = 310
    for title, sub in items:
        d.rounded_rectangle([60, y, W-60, y+160], radius=18, fill=(255, 255, 255, 10))
        d.text((115, y+26), "✓", font=fnt(FONT_BOLD, 52), fill=ACCENT)
        d.text((210, y+22), title, font=fnt(FONT_BOLD, 44), fill=WHITE)
        d.text((210, y+82), sub,   font=fnt(FONT_REG,  34), fill=DIM)
        y += 185
    return img.convert('RGB')


def feature_graphic():
    img = gradient((FW, FH), TOP, BOTTOM)
    d = ImageDraw.Draw(img, 'RGBA')
    rng = random.Random(1)
    for _ in range(60):
        x, y = rng.randint(0, FW), rng.randint(0, FH)
        r = rng.choice([1, 1, 2])
        d.ellipse([x-r, y-r, x+r, y+r], fill=(255, 255, 255, rng.randint(100, 220)))
    d.text((FW//2, 155), "Un-Reminder", font=fnt(FONT_BOLD, 80), fill=WHITE, anchor='mm')
    d.text((FW//2, 250), "AI Habit Coach  •  Random timing  •  Private", font=fnt(FONT_MED, 36), fill=ACCENT, anchor='mm')
    d.rectangle([FW//2-200, 295, FW//2+200, 298], fill=(*ACCENT, 90))
    d.text((FW//2, 390), "Break the 7 PM habit trap.", font=fnt(FONT_REG, 34), fill=(200, 220, 255), anchor='mm')
    return img.convert('RGB')


def upload_image(svc, eid, img_type, img):
    buf = io.BytesIO()
    img.save(buf, format='PNG')
    buf.seek(0)
    media = MediaIoBaseUpload(buf, mimetype='image/png')
    return svc.edits().images().upload(
        packageName=PKG, editId=eid, language=LANG,
        imageType=img_type, media_body=media
    ).execute()


def main():
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ['PLAY_SERVICE_ACCOUNT_JSON']),
        scopes=['https://www.googleapis.com/auth/androidpublisher']
    )
    svc = build('androidpublisher', 'v3', credentials=creds)

    edit = svc.edits().insert(packageName=PKG, body={}).execute()
    eid  = edit['id']
    print(f"Edit created: {eid}")

    listing = {
        'language':         LANG,
        'title':            (BASE / 'en-US/title.txt').read_text().strip(),
        'shortDescription': (BASE / 'en-US/short_description.txt').read_text().strip(),
        'fullDescription':  (BASE / 'en-US/full_description.txt').read_text().strip(),
    }
    svc.edits().listings().update(packageName=PKG, editId=eid, language=LANG, body=listing).execute()
    print("Listing text updated")

    for img_type in ('phoneScreenshots', 'featureGraphic'):
        try:
            svc.edits().images().deleteall(packageName=PKG, editId=eid, language=LANG, imageType=img_type).execute()
            print(f"Cleared existing {img_type}")
        except Exception as e:
            print(f"No existing {img_type} to clear ({e})")

    for i, img in enumerate([screenshot_1(), screenshot_2(), screenshot_3()], 1):
        upload_image(svc, eid, 'phoneScreenshots', img)
        print(f"Uploaded screenshot {i}")

    upload_image(svc, eid, 'featureGraphic', feature_graphic())
    print("Uploaded feature graphic")

    result = svc.edits().commit(packageName=PKG, editId=eid).execute()
    print(f"Edit committed: {result['id']}")


if __name__ == '__main__':
    main()
