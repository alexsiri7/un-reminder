#!/usr/bin/env python3
"""Promote the newest draft release on the Play internal track to `completed`.

Run by the Release workflow after `r0adkll/upload-google-play` has uploaded the
AAB as a draft. Play sometimes refuses the promotion for a reason that lives only
in the Play Console UI and cannot be fixed from this repository (the app is still
in draft state, or a policy declaration under App content is outstanding). Those
refusals exit 0 with a `::warning::` annotation, a step-summary line, and
`PLAY_PROMOTION_BLOCKED` in `GITHUB_ENV` so later steps can say the release is
stuck. Every other API error is re-raised so real regressions (for example a
target SDK the store no longer accepts, #303) still turn the job red.

Only the standard library is imported at module level so the tests run without
the Google client installed.
"""

import json
import os

PKG = 'net.interstellarai.unreminder'

DRAFT_APP_FIX = (
    "Complete Play Console setup (content rating, target audience, data safety, etc.) "
    "to exit draft state."
)
HEALTH_DECLARATION_FIX = (
    "Complete the Health apps declaration under Play Console → Policy → App content, "
    "then re-run or wait for the next push."
)


def blocked_reason(status: int, message: str) -> str | None:
    """The one-line manual fix when Play refused the promotion for a Console-side
    reason, or None when the error is something code must deal with."""
    lowered = message.lower()
    if status == 400 and 'on draft app' in lowered:
        return DRAFT_APP_FIX
    if status == 403 and 'health features' in lowered:
        return HEALTH_DECLARATION_FIX
    return None


def newest_draft(releases: list[dict]) -> dict | None:
    """The draft release with the highest versionCode, or None if there is no draft."""
    drafts = [r for r in releases if r.get('status') == 'draft']
    if not drafts:
        return None
    return max(drafts, key=lambda r: max(int(v) for v in r.get('versionCodes') or [0]))


def append_line(env_var: str, line: str) -> None:
    path = os.environ.get(env_var)
    if not path:
        return
    with open(path, 'a', encoding='utf-8') as f:
        f.write(line + '\n')


def main() -> None:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.errors import HttpError

    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ['PLAY_SERVICE_ACCOUNT_JSON']),
        scopes=['https://www.googleapis.com/auth/androidpublisher'],
    )
    svc = build('androidpublisher', 'v3', credentials=creds)
    edit = svc.edits().insert(packageName=PKG, body={}).execute()
    eid = edit['id']
    track = svc.edits().tracks().get(packageName=PKG, editId=eid, track='internal').execute()
    print(f"Track state: {json.dumps(track, indent=2)}")

    draft = newest_draft(track.get('releases', []))
    if draft is None:
        print("No draft releases to promote")
        svc.edits().delete(packageName=PKG, editId=eid).execute()
        return

    # Only send the new release as completed; omit older releases so Play retires them
    draft['status'] = 'completed'
    track['releases'] = [draft]
    try:
        svc.edits().tracks().update(packageName=PKG, editId=eid, track='internal', body=track).execute()
        svc.edits().commit(packageName=PKG, editId=eid).execute()
        print("Successfully promoted draft to completed")
    except HttpError as e:
        msg = (e.content or b'').decode('utf-8', errors='replace')
        reason = blocked_reason(e.resp.status, msg)
        if reason is None:
            raise
        msg_oneline = ' '.join(msg.split())
        notice = (
            "Play refused to promote the internal release to 'completed'; it stays a draft "
            f"(reachable via Internal App Sharing). {reason} API said: {msg_oneline}"
        )
        print(f"::warning::{notice}")
        append_line('GITHUB_STEP_SUMMARY', f"⚠️ {notice}")
        append_line('GITHUB_ENV', f"PLAY_PROMOTION_BLOCKED={reason}")
        try:
            svc.edits().delete(packageName=PKG, editId=eid).execute()
        except HttpError:
            pass


if __name__ == '__main__':
    main()
