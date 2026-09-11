---
created: '2026-09-11'
github_issue: 318
id: '017'
status: idea
title: Location eligibility must reflect where the device actually is
updated: '2026-09-11'
---

## Why

Location-restricted habits report themselves unavailable while the user is standing at the location. Observed on 2026-09-10: at home, with home-tagged habits showing `LOCATION` as their unavailability reason. Background location permission is granted, so the permission gate in `GeofenceManager.registerGeofence` is not the cause.

The underlying design makes this unrecoverable. `GeofenceManager.currentLocationIds` is pure event-sourced state: a set mutated only by ENTER and EXIT broadcasts arriving at `GeofenceBroadcastReceiver`, persisted to SharedPreferences and restored on start. `HabitAvailabilityService` reads that set and nothing else. Nothing in the app ever asks the device where it actually is, so once the set is wrong there is no path back to correct short of physically leaving the area and returning.

Several things can put it in that state, and all of them are silent. `registerGeofence` returns early with only a `Log.w` when permissions are missing. Its `addGeofences` failure listener only logs — unlike `GeofenceBroadcastReceiver`, which does report to Sentry — so a platform rejection such as `GEOFENCE_NOT_AVAILABLE`, which occurs when system location accuracy is switched off, leaves no trace the user or Sentry can see. `INITIAL_TRIGGER_ENTER` is set on registration, but only fires when the platform already holds a fix inside the fence, so re-registering while no recent fix exists produces nothing.

The user-facing effect is the worst possible one for this app: the habits tied to where you actually are become exactly the habits it refuses to offer, and it gives no indication that anything is broken.

## What

**Location availability follows reality.** A habit restricted to a location is available whenever the device is actually within that location's radius, and unavailable when it is not — whether or not a geofence transition was ever delivered for it.

**Location state recovers on its own.** If the app's idea of where the user is becomes wrong, it becomes right again without a reinstall, a reboot, a re-grant of permissions, or the user physically crossing a boundary. Being wrong is recoverable rather than permanent, and correction happens in both directions: a location the user is at but is not recorded at, and a location they are recorded at but have left.

**Failure is visible.** When location tracking cannot work — permissions missing, system location accuracy disabled, geofence registration rejected by the platform — the app says so where the user will see it, and says what to do about it. It does not silently show every location-restricted habit as unavailable and leave the user to work out why.

**Location state is inspectable.** The user can see which locations the app currently believes they are at, and whether location tracking is healthy, from within the app. Diagnosing this does not require a logcat.

**No new battery cost.** Correction uses one-shot, bounded location requests on occasions the app already wakes for. No continuous tracking, no polling loop, no new always-on location subscription. Computing availability never blocks on a location fix or a network call.

## Issues

- #318 — Reconcile location state against an actual position fix instead of trusting geofence events alone
- #319 — Surface location tracking health and detected locations in Settings