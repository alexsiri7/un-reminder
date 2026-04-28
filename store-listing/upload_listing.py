#!/usr/bin/env python3
"""Upload store listing text and screenshots for Un-Reminder to Google Play."""

import json, os, io
from pathlib import Path
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseUpload

PKG  = 'net.interstellarai.unreminder'
LANG = 'en-US'
BASE = Path(__file__).parent


def upload_image(svc, eid, img_type, path):
    with open(path, 'rb') as f:
        data = f.read()
    media = MediaIoBaseUpload(io.BytesIO(data), mimetype='image/png')
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

    shots = BASE / 'screenshots'

    for img_type in ('phoneScreenshots', 'sevenInchScreenshots', 'tenInchScreenshots',
                     'featureGraphic', 'icon'):
        try:
            svc.edits().images().deleteall(
                packageName=PKG, editId=eid, language=LANG, imageType=img_type
            ).execute()
            print(f"Cleared {img_type}")
        except Exception as e:
            print(f"No existing {img_type} ({e})")

    for n in range(1, 4):
        upload_image(svc, eid, 'phoneScreenshots',     shots / f'phone_{n}.png')
        print(f"Uploaded phone screenshot {n}")
        upload_image(svc, eid, 'sevenInchScreenshots', shots / f'tablet7_{n}.png')
        print(f"Uploaded 7-inch screenshot {n}")
        upload_image(svc, eid, 'tenInchScreenshots',   shots / f'tablet10_{n}.png')
        print(f"Uploaded 10-inch screenshot {n}")

    upload_image(svc, eid, 'featureGraphic', shots / 'feature_graphic.png')
    print("Uploaded feature graphic")

    upload_image(svc, eid, 'icon', shots / 'icon.png')
    print("Uploaded icon")

    result = svc.edits().commit(packageName=PKG, editId=eid).execute()
    print(f"Edit committed: {result['id']}")


if __name__ == '__main__':
    main()
