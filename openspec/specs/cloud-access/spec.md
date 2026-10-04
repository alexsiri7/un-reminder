# Cloud Access

## Purpose

The app has to hold its own credential for the generation service without anyone handing it over, and when generation stops the user must learn why, because an expired budget, a rejected token and a service outage need different remedies and otherwise look identical.

## Requirements

### Requirement: The app obtains its own token

The app SHALL register itself with the generation service whenever it has no token: during onboarding and lazily whenever background work finds none, so an upgraded install or one whose token was cleared heals itself. Background attempts after a failure SHALL back off, so they spend neither the registration ceiling nor the device's integrity quota. When the service rejects a self-registered token that is more than an hour old, the app SHALL discard it and register again rather than retry the dead token.

#### Scenario: Upgraded install
- GIVEN an install upgraded from a build that used pasted tokens and has none stored
- WHEN the next background refill runs
- THEN the app registers and the refill proceeds with the new token

#### Scenario: Revoked token
- GIVEN a self-registered token the operator has revoked
- WHEN a request is rejected with 401
- THEN the app discards the token and registers again

### Requirement: A token can be pasted for sideloaded builds

Cloud settings SHALL offer, under a collapsed advanced section, a masked field to paste a token, rejecting anything not shaped like one. A pasted token SHALL never be discarded automatically on rejection.

#### Scenario: Malformed paste
- GIVEN the advanced section is open
- WHEN the user saves "hello"
- THEN the save is refused as not a token

### Requirement: Cloud settings show the credential and the last failure

Cloud settings SHALL show the stored token's non-secret id — as registered on a named device, as pasted, or as no token yet — and a re-register action that reports why it failed. They SHALL show why generation or registration last failed, distinguishing at least: token rejected, personal budget spent, service budget spent, service unavailable, no Play integrity on this device, build not configured for integrity, registration verdict rejected, and registration ceiling reached.

#### Scenario: Budget spent
- GIVEN background generation was refused for the user's daily cap
- WHEN the user opens cloud settings
- THEN it says their daily generation budget is spent

#### Scenario: Re-register without Play services
- GIVEN a device without Play services
- WHEN the user taps re-register
- THEN it says this device cannot provide Play integrity

### Requirement: The user can regenerate every pool

Cloud settings SHALL offer a regenerate-all action that generates a fresh batch for every active habit and swaps each in only once it lands, leaving a habit's previous variants in place if its generation fails, and showing in-flight, done and failed counts while work is outstanding.

#### Scenario: One habit fails
- GIVEN three active habits
- WHEN the user regenerates all and one batch fails
- THEN two habits have new pools, the third keeps its old one, and the button shows one failed
