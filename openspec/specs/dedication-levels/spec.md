# Dedication Levels

## Purpose

The dedication level sets the size of the ask. It grows when the user keeps doing the habit and shrinks when they keep refusing it, so the habit stays calibrated to real behaviour without manual tuning, and it never stops the habit altogether.

## Requirements

### Requirement: Completions promote the level

When auto-adjust is on, a completion SHALL promote the habit's level by one when the recent completion count meets the threshold for its current level: level 0 on the first completion, level 1 at 3 completions in 7 days, level 2 at 5 in 7 days, level 3 at 10 in 14 days, level 4 at 20 in 28 days. Level 5 SHALL be the ceiling. Completions from any surface SHALL count equally.

#### Scenario: First completion
- GIVEN an auto-adjusting habit at level 0
- WHEN the user completes it once
- THEN it is at level 1

#### Scenario: Auto-adjust off
- GIVEN a habit with auto-adjust off at level 1 and five completions this week
- WHEN the user completes it again
- THEN its level is unchanged

### Requirement: Only explicit refusal demotes the level

When auto-adjust is on, three consecutive dismissed triggers for a habit SHALL lower its level by one, flooring at 0. Only a dismissal SHALL count: opened, later, expired and declined-invitation outcomes SHALL never lower the level.

#### Scenario: Three swipes
- GIVEN an auto-adjusting habit at level 3
- WHEN its last three triggers were all swiped away
- THEN it is at level 2

#### Scenario: Inattention is not refusal
- GIVEN an auto-adjusting habit at level 3
- WHEN three consecutive notifications for it expire unanswered
- THEN it is still at level 3

#### Scenario: Wrong moment is not refusal
- GIVEN an auto-adjusting habit at level 3
- WHEN the user taps Later on it any number of times in a row
- THEN it is still at level 3

### Requirement: A habit at the floor stays available

A habit at level 0 SHALL remain active and eligible indefinitely however often it is dismissed. Nothing SHALL tell the user a habit has been stopped.

#### Scenario: Dismissed at level 0
- GIVEN an auto-adjusting habit at level 0
- WHEN it is dismissed three more times
- THEN it stays at level 0, active and eligible
