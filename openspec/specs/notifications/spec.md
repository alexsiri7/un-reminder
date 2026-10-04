# Notifications

## Purpose

A nudge is an invitation to the thing itself, not a logging prompt. Its answers separate "not now" from "too big" from "never saw it", because each wants a different response from the app, and at most one unanswered nudge stands at a time so the tray never teaches the user to ignore it.

## Requirements

### Requirement: A nudge reads as its words

A trigger notification SHALL show the variant text as its title and, beneath it, the habit name prefixed by one of 20 emoji rotated on the trigger. It SHALL be dressed in one of several presentation styles derived from the variant, so the same variant always looks the same and consecutive nudges tend to look different. A variant with a video SHALL show a video indicator; the video itself is watched from the variant view.

#### Scenario: Variant with a video
- GIVEN the chosen variant carries a video link
- WHEN the nudge is posted
- THEN the notification shows a video indicator and no Watch button

### Requirement: A nudge offers Open and Later

Every trigger notification SHALL offer exactly two actions, Open and Later. Open, or tapping the body, SHALL open the variant view for that trigger and record OPENED. Later SHALL clear the notification and record LATER. Swiping the notification away SHALL be the only dismissal and SHALL record DISMISSED.

#### Scenario: Later
- GIVEN a nudge in the tray
- WHEN the user taps Later
- THEN the tray is empty and the trigger reads LATER

#### Scenario: Swipe
- GIVEN a nudge in the tray
- WHEN the user swipes it away
- THEN the trigger reads DISMISSED

### Requirement: Outcomes have distinct meanings

OPENED, LATER and EXPIRED SHALL resolve the trigger and start the habit's normal cooldown without changing its dedication level; only DISMISSED SHALL feed demotion. LATER SHALL grant no priority, shortened cooldown or weight boost anywhere, and SHALL NOT hold back other habits. A recorded outcome SHALL never be overwritten, except that OPENED MAY become COMPLETED.

#### Scenario: Later is not a snooze
- GIVEN the user tapped Later on "stretch"
- WHEN its cooldown ends
- THEN "stretch" is selected with exactly the weight of any other recently prompted habit

#### Scenario: Opened then done
- GIVEN a trigger recorded OPENED
- WHEN the user taps Did it in its variant view
- THEN it reads COMPLETED

### Requirement: Only one unanswered nudge stands

Before a new nudge is considered posted, any earlier nudge still awaiting an answer SHALL be cancelled and its trigger recorded as EXPIRED. The replacement SHALL be posted first, so a failed post leaves the earlier nudge answerable, and an answer the user gave in the meantime SHALL NOT be overwritten.

#### Scenario: Superseded
- GIVEN an untouched nudge for "read" is in the tray
- WHEN a nudge for "stretch" fires
- THEN only the "stretch" nudge is in the tray
- AND the "read" trigger reads EXPIRED

#### Scenario: Already answered
- GIVEN the earlier trigger was answered with Later
- WHEN a new nudge fires
- THEN the earlier trigger still reads LATER

### Requirement: The variant view is where the habit is done

The variant view SHALL show one variant — its mascot, its text as headline, the habit name and current dedication level — in one of five layouts derived from the same seed as the notification style, so a variant looks recognisably itself on every surface. It SHALL offer Did it, which records a completion and returns, and Watch when the variant has a video. Opened from a trigger it SHALL record OPENED if the trigger is still unanswered, and SHALL withdraw Did it if a swipe or Later already resolved it. Opened from the menu or widget it SHALL record nothing on opening, and Did it SHALL record a new completion exactly as the widget does.

#### Scenario: Opened from the Now menu
- GIVEN the user taps a row on the Now menu
- WHEN the variant view opens
- THEN no trigger is recorded
- AND tapping Did it records one completion for that habit
