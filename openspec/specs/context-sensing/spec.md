# Context Sensing

## Purpose

The app only asks for things the user can do where they are, when they are free, in what they are doing. Context sensing provides those three readings — time window, location and activity — cheaply, recovers from its own errors, and makes its readings and failures visible instead of silently hiding habits.

## Requirements

### Requirement: Time windows define when nudges may fire

The user SHALL be able to create, edit and delete time windows, each with a start and end time, a set of weekdays, a frequency of 1 to 3 triggers per day, and an active toggle. Scheduled nudges SHALL fire only inside an active window for the current weekday, and a window SHALL produce no more nudges per day than its frequency.

#### Scenario: Outside every window
- GIVEN one active window, weekdays 18:00–21:00
- WHEN it is Saturday at 19:00
- THEN no scheduled nudge fires

#### Scenario: Frequency reached
- GIVEN an active window with a frequency of 1 that has already produced a nudge today
- WHEN the next check runs inside the window
- THEN no further nudge fires from it today

### Requirement: Named locations are defined on a map

The user SHALL be able to add, edit and delete named locations by placing a pin on an OpenStreetMap map, with a radius from 50 to 500 metres, without leaving the app. Map tiles SHALL be cached on the device.

#### Scenario: Adding the gym
- GIVEN the locations screen
- WHEN the user adds a location, drags the pin to the gym, names it "Gym" and sets 150 m
- THEN "Gym" is listed and can be attached to habits

### Requirement: Location state follows where the device actually is

The app SHALL treat the device as at a location whenever it is within that location's radius, whether or not a boundary crossing was ever reported. When its belief is wrong in either direction it SHALL correct itself without a reinstall, reboot, permission re-grant or the user physically crossing a boundary. Correction SHALL use one-shot, bounded position requests on occasions the app already wakes for, with no continuous tracking, and computing availability SHALL never wait on a position fix or network call.

#### Scenario: Missed arrival
- GIVEN the user is at home but no arrival was ever reported
- WHEN the app next wakes for a scheduled check
- THEN home is recorded as a current location and home habits become doable

#### Scenario: Missed departure
- GIVEN the app believes the user is at the gym but they left an hour ago
- WHEN the app next wakes for a scheduled check
- THEN the gym is no longer a current location

### Requirement: Location tracking health is visible

The app SHALL show which locations it currently believes the user is at and whether location tracking is healthy. When tracking cannot work — permission missing, system location off or in reduced-accuracy mode, registration rejected by the platform, Play services unable to serve location — the app SHALL say so where the user will see it and what to do about it.

#### Scenario: System location switched off
- GIVEN the user switches system location off
- WHEN they open settings
- THEN location tracking is shown as unhealthy with the reason and the fix

### Requirement: The current activity is resolved into a mode

The app SHALL resolve the user's activity into one of three modes — walking, sitting or transport — from recently observed device activity: walking or running as walking, still as sitting, in a vehicle as transport. Cycling SHALL be a separate state that suppresses nudges, not a mode. When nothing has been observed, the observation is older than 15 minutes, the activity is unknown, or the activity permission is denied, the mode SHALL be sitting, marked as assumed. Resolution SHALL be synchronous and never wait on a sensor.

#### Scenario: On the tube
- GIVEN the last observed activity, 5 minutes ago, was in a vehicle
- WHEN the mode is resolved
- THEN it is transport, observed

#### Scenario: Stale observation
- GIVEN the last observed activity was walking 40 minutes ago
- WHEN the mode is resolved
- THEN it is sitting, assumed

#### Scenario: Permission denied
- GIVEN the activity-recognition permission is denied
- WHEN the mode is resolved
- THEN it is sitting, marked as assumed because of the permission
