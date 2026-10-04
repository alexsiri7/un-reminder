# Generation Service

## Purpose

The app must never carry an LLM key, and a handful of friends must be able to share the service without one install being able to exhaust or impersonate another. A small private web service generates text on the app's behalf, knows each install only by a token, proves each caller is a genuine Play build, and caps spend per user with a service-wide backstop.

## Requirements

### Requirement: The service generates variants and ladders

The service SHALL generate a batch of notification variants for a habit — given its title, tags, location, time of day, supported modes, optional personal style context and a count — returning each variant's text, shape, mode tags and optional video link together with the current generation version. It SHALL generate a six-entry description ladder from a habit title. It SHALL publicly report its health, spend used against caps, generation version and whether integrity checking is configured.

#### Scenario: Batch for a sitting-only habit
- GIVEN a request for 50 variants of a habit supporting only sitting
- WHEN the batch is generated
- THEN each variant carries a shape and is tagged sitting or neutral
- AND the response carries the current generation version

### Requirement: Each install holds its own token

Every generation request SHALL carry a per-user bearer token. The service SHALL store only a salted hash of each token, compare in constant time, and reject an unknown or disabled token with 401. There SHALL be no shared secret. The operator SHALL be able to mint, list (with label, creation time, enabled state, caps and exemption), disable, re-enable and set per-token caps from the command line.

#### Scenario: Revoked token
- GIVEN the operator disabled token ur1_ab12
- WHEN a request arrives with it
- THEN the request is rejected with 401

### Requirement: Only genuine Play builds may generate

Every generation request SHALL also carry a Play Integrity token bound to a hash of the request body. The service SHALL decode it with Google before any generation and SHALL reject the request unless it is fresh, matches the body, and comes from a Play-recognised app and a licensed install; device integrity SHALL be logged but not enforced. Tokens minted as integrity-exempt, for debug and sideloaded builds, SHALL skip this check. When Google cannot be asked, the service SHALL fail closed.

#### Scenario: Extracted token used from a script
- GIVEN a valid non-exempt user token used without an integrity token
- WHEN a generation request is sent
- THEN it is rejected with 403 and no generation happens

#### Scenario: Integrity key missing
- GIVEN the service has no integrity credentials configured
- WHEN a non-exempt request arrives
- THEN it is refused with 503

### Requirement: Spend is capped per user with a global backstop

The service SHALL track daily and monthly generation spend per token and for the whole service. A token over its own cap SHALL be refused while other tokens continue; the service-wide cap SHALL apply on top. A refusal SHALL say which scope and which period was hit. Requests SHALL also be rate-limited per caller.

#### Scenario: One friend regenerates everything
- GIVEN user A has spent their daily cap
- WHEN user B requests a batch
- THEN user B's request succeeds
- AND user A's next request is refused naming the user daily cap

### Requirement: A Play-verified install can register itself

The service SHALL offer an unauthenticated registration route that accepts a device label and a Play Integrity token bound to the request, verifies it exactly as generation does, and on success mints a per-user token labelled as self-registered for that device, returning it once. Registration SHALL fail closed when Google or the registration counter cannot be read, SHALL be rate-limited per caller, and SHALL stop at a service-wide daily ceiling with a distinct, reportable refusal. An integrity token spent on a registration SHALL NOT be accepted again. Self-registered tokens SHALL appear in the token listing, get the default caps, and be revocable like any other.

#### Scenario: First registration
- GIVEN a Play install with no token
- WHEN it registers with a passing verdict
- THEN it receives a new token labelled with its device
- AND only the token's salted hash is stored

#### Scenario: Daily ceiling
- GIVEN the day's registration ceiling is reached
- WHEN another install registers
- THEN it is refused with a distinct registration-ceiling error

#### Scenario: Replayed verdict
- GIVEN an integrity token already used for a successful registration
- WHEN the same request is sent again within the token's freshness window
- THEN it is refused and no second token is minted

### Requirement: The service holds nothing else about users

The service SHALL store nothing about a user beyond a token hash, its label, caps and spend counters. Habits, variants, locations, windows, outcomes and counters SHALL remain on the device, with no sync, sharing or account recovery.

#### Scenario: Storage disclosed
- GIVEN the service's token store leaks
- WHEN it is inspected
- THEN it yields no usable token and no habit data
