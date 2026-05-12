---
id: "006"
title: "Location management (map picker)"
status: "done"
updated: 2026-05-12
---

## Why

Geofencing requires named locations with lat/lng coordinates. The user needs to define these without leaving the app.

## What

Named `Location` entity with lat/lng, radius (50–500m), and user-defined label. Map picker screen using osmdroid (OpenStreetMap, tiles cached on-device). FAB-based add flow; list item edit button. Habits link to zero or more locations via `HabitLocationCrossRef` junction table; no selection means "Anywhere".
