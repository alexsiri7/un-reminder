# Onboarding and Settings

## Purpose

Nothing fires until the user has granted permissions and made a habit and a window, so the first launch walks them through exactly that once. Settings then keep permissions, the evening invitation and diagnostics within reach.

## Requirements

### Requirement: First launch walks through setup once

On first launch the app SHALL show a full-screen onboarding with three steps: grant permissions, create a first habit, create a first time window. The user SHALL be able to skip it. Finishing or skipping SHALL be remembered and onboarding SHALL never be shown again. Onboarding SHALL also start obtaining the app's cloud credential in the background, and a failure to obtain it SHALL NOT block finishing onboarding.

#### Scenario: Skipped
- GIVEN a fresh install on its onboarding screen
- WHEN the user taps skip
- THEN the Now menu opens
- AND onboarding does not appear on the next launch

#### Scenario: Credential failure during onboarding
- GIVEN the cloud service is unreachable
- WHEN the user completes onboarding
- THEN they reach the app with their habit and window saved

### Requirement: Permissions explain their cost

Onboarding and settings SHALL show the state of the notification, location, background location and activity-recognition permissions, let the user grant each, and say what denying each one costs.

#### Scenario: Activity permission denied
- GIVEN activity recognition is denied
- WHEN the user views settings
- THEN the row says activity-specific habits and texts will assume sitting, with a way to grant it

### Requirement: Settings expose diagnostics and controls

Settings SHALL offer a manual test nudge, the evening invitation toggle and time, location tracking health, a link to cloud settings and a way to send feedback.

#### Scenario: Test nudge
- GIVEN at least one eligible habit
- WHEN the user taps the test nudge button
- THEN a nudge is posted through the normal fire-time pipeline
