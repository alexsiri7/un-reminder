---
id: "002"
title: "Location-based arrival triggers (geofencing)"
status: "done"
updated: 2026-05-12
---

## Why

Some habits only make sense in specific contexts (e.g. gym exercises at the gym). Geofence-triggered notifications fire exactly when the user is in the right place.

## What

Android `GeofencingClient` geofence registration for any named user-defined location. On `ENTER`, one notification is scheduled 5 minutes later (settle-in delay). Debounced: at most one arrival trigger per location per 30 minutes.
