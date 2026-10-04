# Pull Surfaces

## Purpose

Pushing a single habit at a moment the app guessed is not enough. The user also needs places to pull from when they have a moment, a win small enough to be always available, and progress numbers that only go up. The day, not the habit, is the unit of success: doing anything at all counts.

## Requirements

### Requirement: The Now menu offers what could be done

The Now menu SHALL list the user's active habits three at a time, with a load-more control that adds three more until none remain. Doable habits SHALL come first, then recently dismissed ones, then those blocked by pacing, activity, time, place and finally those already done today, shuffled within each group. Every row below the doable group SHALL say why it ranks lower, and every row SHALL be completable. With no active habit, the menu SHALL offer a route to create one.

#### Scenario: Nothing strictly doable
- GIVEN every active habit is out of its window
- WHEN the user opens the Now menu
- THEN habits are still listed, each marked as out of hours, with a working Did it

#### Scenario: Load more
- GIVEN five active habits
- WHEN the user opens the menu and taps load more once
- THEN all five are shown and load more is gone

### Requirement: The menu shows what filtered it

The menu header SHALL show the resolved activity and the resolved location using the same resolution as nudges, refreshed whenever the menu is resumed. An assumed activity SHALL be marked as assumed, cycling SHALL be shown as holding nudges back, and unhealthy location tracking SHALL show its fault in place of a location name.

#### Scenario: Assumed activity
- GIVEN no recent activity observation
- WHEN the menu is shown
- THEN the header reads sitting, marked as assumed

### Requirement: Completing from a pull surface counts fully

Tapping a row or the widget card SHALL open that variant's view. Did it on a row or the widget SHALL complete the habit in one tap. A completion from the menu, widget or variant view SHALL affect daily limits, cooldowns, dedication level and growth counters exactly as one from a nudge.

#### Scenario: Did it from the menu
- GIVEN "stretch" at level 0 with auto-adjust on
- WHEN the user taps Did it on its menu row
- THEN it is completed for today and promoted to level 1

### Requirement: Growth counters only go up

The app SHALL keep three counters that only ever increase and are never broken by an empty day: days on which any habit was completed, and per habit the days on which it was completed and its total completions. Every completion from any surface SHALL move all three that apply. There SHALL be no streaks, freezes or penalties. The overall counter SHALL be visible on the menu and the per-habit pair on the habit.

#### Scenario: Two completions in one day
- GIVEN "read" has 4 days and 6 completions
- WHEN the user completes it twice today, for the first time today
- THEN "read" shows 5 days and 8 completions
- AND the overall days counter rose by one

### Requirement: An evening invitation rescues an empty day

On a day with no completion yet, the app SHALL post at most one invitation late in the day, at a time the user can set (20:30 by default) and switch off, inviting them to make the day count and offering to show the menu or to decline for tonight. Declining SHALL NOT count as dismissing any habit or affect any dedication level.

#### Scenario: Already done today
- GIVEN the user completed a habit this morning
- WHEN the evening arrives
- THEN no invitation is posted

### Requirement: A widget shows one doable habit

The home-screen widget SHALL show one habit from the best non-empty group of the menu's ordering, with a single Did it action. Tapping the card SHALL open that variant's view. It SHALL refresh at least every 30 minutes and whenever context changes what is doable. It SHALL always show a habit while any habit is active, and prompt to create one otherwise.

#### Scenario: Context change
- GIVEN the widget shows a home habit
- WHEN the user leaves home
- THEN the widget refreshes, preferring a habit doable where the user now is

#### Scenario: Everything blocked
- GIVEN every active habit is out of its window
- WHEN the widget refreshes
- THEN it still shows a habit with its Did it action
