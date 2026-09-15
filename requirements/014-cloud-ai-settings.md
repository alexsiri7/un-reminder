---
id: "014"
title: "Cloud AI settings screen"
status: "done"
updated: 2026-09-15
---

## Why

Each user holds their own Worker token (req 019), issued out of band, so it has to be entered on the device rather than built into the APK.

## What

Settings screen with a masked field to paste the Worker token and a save that rejects anything not shaped like one; the non-secret `ur1_<id>` prefix of the stored token is shown so a user can tell Alex which one to revoke. "Regenerate all variants" button generates a fresh batch for every active habit and swaps it in only once it has landed; a failed generation leaves the habit's previous variants in place, and the button shows in-flight / done / failed counts while work is outstanding.
