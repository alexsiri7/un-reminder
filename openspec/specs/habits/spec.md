# Habits

## Purpose

A habit is the thing the user wants to do more of, described at six sizes so the ask can shrink or grow with the user. Habits carry the constraints that decide when, where and in which activity they can be offered, and they live only on the device.

## Requirements

### Requirement: Habits are managed by the user

The user SHALL be able to create, edit and delete habits from a habit list and a habit editor. Each habit SHALL have a name, a dedication level from 0 to 5, an auto-adjust toggle, an active toggle, a daily limit (1–10, default 1), a cooldown (1h, 2h, 3h, 6h, 12h or none, default 3h), zero or more locations, zero or more time windows, and a set of supported activity modes. Only the user SHALL change the active toggle; the app SHALL never deactivate a habit on its own.

#### Scenario: New habit defaults
- GIVEN the user creates a habit named "stretch" and changes nothing else
- WHEN it is saved
- THEN it is active at dedication level 0 with auto-adjust on, a daily limit of 1, a 3-hour cooldown, anywhere, and any activity

#### Scenario: Pausing is the user's decision
- GIVEN an active habit that has been dismissed many times
- WHEN any amount of time passes
- THEN the habit is still active unless the user switched it off in the editor

### Requirement: Each habit has a six-step description ladder

Every habit SHALL hold one description per dedication level, 0 (the smallest version) through 5 (the full version). The editor SHALL show the current level's description by default and expand to all six on request.

#### Scenario: Ladder collapsed by default
- GIVEN a habit at dedication level 2
- WHEN its editor opens
- THEN only the level-2 description is shown
- AND expanding the ladder shows all six levels

### Requirement: The ladder can be filled by AI

The editor SHALL offer an autofill action, enabled once the name has at least two characters, that fills all six ladder descriptions from the habit name using the cloud generation service. A failure SHALL be shown to the user with its cause rather than leaving the fields silently unchanged.

#### Scenario: Autofill
- GIVEN a new habit named "meditation"
- WHEN the user taps autofill
- THEN all six ladder descriptions are filled

#### Scenario: Autofill blocked by the spend cap
- GIVEN the user's generation budget is spent
- WHEN the user taps autofill
- THEN the editor says the budget is spent and links to cloud settings

### Requirement: Habits declare where and in which activity they can be done

A habit with no locations SHALL mean "anywhere"; with locations it SHALL be doable only at one of them. A habit with no supported modes SHALL mean "any activity"; otherwise it SHALL be doable only in the chosen subset of walking, sitting and transport. A habit with no windows SHALL be unconstrained by time; otherwise it SHALL be doable only inside one of its active windows.

#### Scenario: Sitting-only habit
- GIVEN a habit restricted to sitting
- WHEN the user's resolved activity is walking
- THEN the habit is not doable now, with activity as the reason

#### Scenario: Existing habits after modes were introduced
- GIVEN habits created before activity modes existed
- WHEN the app is upgraded
- THEN every one of them supports any activity

### Requirement: The editor shows the habit's state and words

The editor SHALL show why the habit is or is not doable right now, the habit's growth counters, and a preview of an unused notification text from its local pool without any cloud call. With no pool yet, the preview SHALL say so inline.

#### Scenario: Preview with an empty pool
- GIVEN a habit whose variant pool is still generating
- WHEN the user taps preview
- THEN an inline message says there is nothing to preview yet
