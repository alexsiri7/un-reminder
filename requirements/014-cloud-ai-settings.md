---
id: "014"
title: "Cloud AI settings screen"
status: "done"
updated: 2026-09-24
---

## Why

Each user holds their own Worker token (req 019), so it cannot be built into the APK. A Play install obtains one itself (req 020); sideloaded and debug builds, which cannot, need one entered on the device.

## What

Settings screen showing the stored token's non-secret `ur1_<id>` prefix — "registered as `ur1_<id>` on `<device>`" for a self-registered one, "token: `ur1_<id>`" for a pasted one, or "no token yet" — so a user can tell Alex which one to revoke, and a "re-register" button that obtains a fresh token and shows why it failed if it does. A collapsed "advanced" section holds a masked field to paste a Worker token by hand, with a save that rejects anything not shaped like one. "Regenerate all variants" button generates a fresh batch for every active habit and swaps it in only once it has landed; a failed generation leaves the habit's previous variants in place, and the button shows in-flight / done / failed counts while work is outstanding.
