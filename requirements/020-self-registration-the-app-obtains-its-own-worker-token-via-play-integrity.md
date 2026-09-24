---
created: '2026-09-24'
github_issue: 439
id: 020
status: draft
title: 'Self-registration: the app obtains its own Worker token via Play Integrity'
updated: '2026-09-24'
---

## Why

Requirement 019 chose manual provisioning: Alex mints a token for each friend out of band and they enter it in the app. The stated reason was that a self-registration flow would need an identity surface the app does not otherwise have, and that at the scale of a handful of friends, minting by hand was the smaller thing to build.

That premise no longer holds. Since 019 shipped, every generation request already carries a Play Integrity token, which the Worker decodes with Google and checks for a Play-recognised, licensed install before it will generate anything. That verdict is exactly the proof a registration needs. It does not say *who* is asking, but 019 never wanted to know who; it wanted to know that the caller is a real install of this app from Play, and the Worker already establishes that on every call. A registration flow built on the same verdict introduces no account, no e-mail and no identity: the thing the manual path was chosen to avoid is not required.

Meanwhile manual minting has become the only step in onboarding that needs Alex in the loop. Every other part of getting started — permissions, first habit, first window, cloud generation — happens on the device without him. The token is the one thing a new user cannot get for themselves, so it blocks anyone he is not actively chatting with, and it turns each new install into a message exchange.

It also leaves a stale-halves problem. The app is shipped through Play and the token is delivered by hand, so the two arrive independently, and an app build that has no token silently generates nothing: notifications keep firing with whatever pool was there, the settings screen shows an empty field, and nothing tells the user that the half they are missing is the half only Alex can give them. Letting the install obtain its own token closes that gap by making the token part of the build's own first-run work rather than a separate delivery.

## What

**A Play-verified install can register itself.** The app presents an integrity token bound to its registration request, and the Worker verifies it the same way it verifies generation calls: decoded with Google, bound to the request, fresh, from a Play-recognised app and a licensed installer. On a passing verdict the Worker mints a per-user token exactly as requirement 019 describes — stored only as a salted hash, subject to the default per-user spend caps, revocable — and returns it to the device once. No account, no identity, no e-mail: the verdict is the credential, and the token record that results is indistinguishable from a manually minted one apart from how it came to exist.

**Registration is unauthenticated but bounded.** The route needs no bearer token, because it is how bearer tokens are obtained, and that makes it the one door on the Worker that anyone can knock on. It must therefore fail closed whenever Google cannot be asked — a missing service-account key or a decode outage refuses the registration rather than granting one — and it must be rate-limited, per caller and with a service-wide daily ceiling on the number of tokens minted, so that a captured verdict or a scripted client cannot mint at will. Hitting the ceiling is a distinct, reportable failure, not a silent refusal. An integrity token spent on a registration cannot be presented again for anything else.

**The app registers itself when it has no token.** At first launch, as part of onboarding, and lazily whenever background work finds no token stored — so an install upgraded from a build that predates this, or one whose token was cleared, heals itself without anyone noticing. A failed registration never blocks onboarding: the user finishes setting up habits and windows regardless, and the app retries later. When registration does fail, the user is told which of the distinguishable failures occurred — no Play services on this device, the verdict was rejected, the registration ceiling was hit, the Worker is down — because the remedies differ and a single generic error looks the same for all of them.

**Manual tokens remain the sideload path.** Minting a token by hand and pasting it into the app stays supported, moved under an advanced control in the cloud settings rather than being the first thing a user sees. This is the path for debug builds and for installs Play does not recognise, which cannot pass the integrity gate and so cannot register; the integrity-exempt token from 019 continues to exist for exactly these.

**Revocation still works per device.** A self-registered token carries a device label so that Alex can tell installs apart when listing tokens, and self-registered tokens appear in the same listing as minted ones. When a token is revoked, the app treats the rejection as "no token" and registers again rather than retrying the dead token forever; a revoked install therefore comes back with a new token and a new record, which is the intended behaviour. Revocation is for cutting off a token that has leaked or run away, not for banning a device: a Play-verified install can always register again, and this requirement does not try to prevent that.

**Nothing else changes.** Per-user spend caps and the service-wide backstop apply to self-registered tokens as they do to any other. The integrity gate on generation stays in place; registration adds a second use of the verdict, it does not weaken the first. And the app remains single-user and local-first as requirement 019 left it: the Worker still holds nothing about a user beyond a token hash, a label and a spend counter.

## Issues

- #439 — Epic: self-registration, the app obtains its own Worker token via Play Integrity
- #436 — worker: registration route mints a per-user token for a Play-verified install
- #437 — app: self-register with the Worker on first launch instead of pasting a minted token
- #438 — worker: tokens admin lists self-registered tokens; docs and req 019 text updated for self-registration
