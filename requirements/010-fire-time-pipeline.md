---
id: "010"
title: "Fire-time eligibility pipeline"
status: "done"
updated: 2026-05-12
---

## Why

Not all habits should fire at all times. The eligibility check ensures notifications are contextually relevant: right location, right time, not already done today, not on cooldown.

## What

On each trigger: resolve location state, filter eligible habits (`active`, location match, time window match, not completed today, not on cooldown), weighted-random selection biased toward habits not recently prompted (`weight = 1 + min(minutesSince, 1440) / 120`), pick unused variation, post notification, record trigger outcome.
