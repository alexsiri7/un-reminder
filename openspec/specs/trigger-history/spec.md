# Trigger History

## Purpose

The user wants to see what the app has been asking, in which words, and how each ask ended — for reflection and for spotting when something is wrong — without leaving the app or reading logs.

## Requirements

### Requirement: Recent nudges are listed with their outcomes

The app SHALL list the 20 most recent fired triggers with the text shown and the outcome, each outcome — completed, opened, later, dismissed, expired, still waiting — with its own label. Tapping a row SHALL open the variant view for that trigger.

#### Scenario: Expired and dismissed look different
- GIVEN one trigger was swiped away and another was superseded
- WHEN the recent list is shown
- THEN one reads dismissed and the other expired, with distinct labels

### Requirement: The next check and current readings are visible

The recent list SHALL show when the next scheduled check will run, or that none is scheduled, and the currently resolved activity with the age of the observation behind it, updating live.

#### Scenario: Nothing scheduled
- GIVEN the check chain is not scheduled
- WHEN the recent list is shown
- THEN it reads "not scheduled"
