# Notification Variants

## Purpose

The brain filters anything it has seen before, including the shape of a sentence. Each habit keeps a local pool of AI-written texts that vary in structure and suit different activities, generated ahead of time so no nudge waits on the network and the app keeps working offline.

## Requirements

### Requirement: Each habit keeps a local pool of pre-generated texts

Each habit SHALL have a local pool of up to 50 generated variants. Saving a new habit SHALL start generating its pool; the pool SHALL be topped up toward 50 when its unused count falls below 20; and a variant SHALL be used at most once. Firing a nudge SHALL never wait on the network. With an empty pool, a nudge SHALL use the description for the habit's current level, or its name when that is blank, and SHALL request a refill.

#### Scenario: Empty pool
- GIVEN a habit whose pool is empty and whose level-1 description is "one push-up"
- WHEN a nudge fires for it at level 1
- THEN the notification reads "one push-up"
- AND a refill is requested

#### Scenario: Offline
- GIVEN the device is in airplane mode and the pool has unused variants
- WHEN a nudge fires
- THEN it uses a pool variant without any network call

### Requirement: Variants vary by shape

Every variant SHALL be generated with one of six shapes — question, statement, challenge, observation, terse, time-boxed — and generation SHALL produce a spread across all six. Selection SHALL prefer a shape and a look different from the last one used for that habit, choosing at random within that preference. The Now menu and the widget SHALL draw from the same pool with the same rotation.

#### Scenario: Consecutive nudges
- GIVEN a habit whose last nudge was a question
- WHEN its next nudge is chosen and non-question variants remain
- THEN it is not a question

#### Scenario: Refilled pool
- GIVEN a pool refilled from near empty
- WHEN its variants are inspected
- THEN all six shapes are present

### Requirement: Variants suit activity modes

Every variant SHALL be tagged with the modes it suits or as mode-neutral, and generation SHALL be scoped to the habit's supported modes plus neutral. Selection SHALL prefer a variant for the current mode, then a neutral one, then any other, so a habit always has something to say.

#### Scenario: Walking text
- GIVEN the user is walking and the habit has walking-tagged variants
- WHEN a nudge fires for it
- THEN a walking-tagged variant is used

#### Scenario: No match
- GIVEN the user is on transport and the habit has no transport-tagged variants
- WHEN a nudge fires for it
- THEN a neutral variant is used

### Requirement: Pools follow the habit's words and the service's version

When a habit's name, description ladder or supported modes change, its pool SHALL be regenerated for the new habit. The generation service SHALL publish a generation version; at least daily, every active habit whose pool was generated under another version SHALL be regenerated, paced rather than all at once. Version regeneration, reactivation and manual regeneration SHALL keep the old variants firing until the new batch has landed, SHALL swap them in one step so no pool is ever empty or mixed-version, and SHALL leave the old pool in place if generation fails.

#### Scenario: Model upgrade
- GIVEN the service's generation version is raised
- WHEN the daily check runs
- THEN each active habit's pool is regenerated in turn
- AND each habit keeps firing its old variants until its new batch lands

#### Scenario: Modes changed
- GIVEN a sitting-only habit
- WHEN the user adds walking to its supported modes and saves
- THEN its pool is regenerated to include walking variants
