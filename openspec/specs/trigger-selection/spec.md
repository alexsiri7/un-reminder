# Trigger Selection

## Purpose

Fixed-time reminders become invisible. Nudges instead arrive at unpredictable moments inside the user's windows, and each one names a single habit chosen from those the user could actually do right now, favouring the ones not asked about recently. Fewer nudges that can be acted on beat more nudges that cannot.

## Requirements

### Requirement: Checks run at randomised intervals

The app SHALL check for a nudge at a random interval of 15–30 minutes, lengthened to 60–90 minutes after a nudge has fired, and SHALL keep that chain alive across reboots and process death with a watchdog that restarts it. Only checks falling inside an active window SHALL be able to fire.

#### Scenario: After a nudge
- GIVEN a check inside a window has just posted a nudge
- WHEN the next check is scheduled
- THEN it is between 60 and 90 minutes away

#### Scenario: Dead chain
- GIVEN the check chain has stopped after a reboot
- WHEN the watchdog runs
- THEN the chain is scheduled again

### Requirement: Eligibility is decided at fire time

At each check the app SHALL first correct its location state and resolve the activity, then treat a habit as eligible only if it is active, supports the current mode, is anywhere or at a current location, is in one of its windows or has none, has not been completed today, has not reached its daily limit, and is not within its cooldown after its last dismissed, fired, expired, later or opened trigger. A cooldown of none SHALL disable that exclusion. When nothing is eligible, the check SHALL post nothing.

#### Scenario: Done today
- GIVEN a habit completed this morning
- WHEN a check runs this afternoon
- THEN that habit is not eligible

#### Scenario: Cooldown after Later
- GIVEN a habit with a 3-hour cooldown answered with Later an hour ago
- WHEN a check runs
- THEN that habit is not eligible

### Requirement: Cycling suppresses nudges

While the resolved activity is cycling, no nudge SHALL be posted and the suppressed check SHALL NOT count against any habit.

#### Scenario: On the bike
- GIVEN the user is cycling
- WHEN a check runs inside a window with eligible habits
- THEN no notification is posted
- AND no habit's dedication level or cooldown is affected

### Requirement: One habit is chosen by weighted chance

Among eligible habits, the app SHALL pick one at random with weight `1 + min(minutesSinceLastFired, 1440) / 120`, a never-fired habit taking the maximum weight. No outcome SHALL grant a habit extra weight or priority.

#### Scenario: Long-neglected habit
- GIVEN two eligible habits, one fired 10 minutes ago and one not fired for two days
- WHEN a habit is chosen
- THEN the neglected one is about twelve times as likely to be picked
