---
id: "002"
title: "Location-based arrival triggers (geofencing)"
status: "done"
updated: 2026-05-12
---

## Why

Some habits only make sense in specific contexts (e.g. gym exercises at the gym). Geofence-triggered notifications fire exactly when the user is in the right place.

## What

Android `GeofencingClient` geofence registration for any named user-defined location. On
`ENTER`, the location is added to the active-location state set; on `EXIT` it is removed.
The stochastic WorkManager pipeline reads this state on each check cycle and restricts
eligible habits to those associated with the current location(s). A habit's per-habit
cooldown prevents back-to-back triggers regardless of geofence transitions.
