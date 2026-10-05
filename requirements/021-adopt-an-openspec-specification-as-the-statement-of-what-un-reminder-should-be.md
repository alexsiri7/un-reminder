---
created: '2026-09-28'
github_issue: 460
id: '021'
status: done
title: Adopt an OpenSpec specification as the statement of what Un-Reminder should
  be
updated: '2026-10-05'
---

## Why

Requirement files record changes, not what Un-Reminder should be. Auditing the app against them means replaying a change log in which later requirements silently rewrite earlier ones (016's menu was reshaped by issue #333; 009 and 010 were rewritten by 018), and their hand-kept status drifts: 017 and 018 are recorded as idea and 019 and 020 as draft although all four are built, and 020's unquoted id reads as 16. A specification per capability, changed only through spec-change pull requests, gives one document to audit the code against and lets Lachesis derive status from the work itself rather than trusting a field someone forgot to update — the same move Lachesis made in its own requirement 030.

## What

The repository holds Un-Reminder's specification in OpenSpec format under `openspec/specs/`, one file per capability: cloud-access, context-sensing, dedication-levels, generation-service, habits, in-app-feedback, notification-variants, notifications, onboarding-and-settings, pull-surfaces, trigger-history and trigger-selection. The specification is validated in CI on every pull request and push to the default branch. From then on, work on Un-Reminder starts as a spec change, behaviour the code does not yet meet is filed as spec-gap issues, and the existing requirement files are superseded by the spec.

## Issues

- #460 — Add Un-Reminder's OpenSpec specification and validate it in CI