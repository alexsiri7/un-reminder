# In-App Feedback

## Purpose

Showing a problem is faster than describing it. The user can mark up the screen they are looking at and send it as an issue without leaving the app, even when offline.

## Requirements

### Requirement: Annotated screenshots become issues

The feedback screen SHALL capture the current screen, let the user draw on it in red, yellow or green, add a description, and submit both as a GitHub issue. Feedback SHALL be sent only when the user submits it.

#### Scenario: Submit
- GIVEN the user opened feedback from the recent list
- WHEN they circle a row in red, type "wrong label" and submit
- THEN an issue is created with the annotated screenshot and the description

### Requirement: Feedback survives being offline

When feedback cannot be sent, it SHALL be queued on the device and sent automatically once connectivity returns.

#### Scenario: Offline submit
- GIVEN the device is offline
- WHEN the user submits feedback
- THEN it is queued
- AND it is sent after connectivity returns without the user acting again
