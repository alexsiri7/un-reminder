"""A workflow triggered by another workflow's completion must act only on push runs.

The factory's daily build-drift check dispatches main CI (`workflow_dispatch`),
and a maintainer may re-run it. A `workflow_run` consumer — say, a deploy that
waits for CI to go green — would fire on those runs too and redeploy prod from
a commit nobody pushed (#481). Every job in such a workflow must therefore be
gated on `github.event.workflow_run.event == 'push'`.
"""

from pathlib import Path

import pytest
import yaml

WORKFLOW_DIR = Path(__file__).resolve().parents[1] / ".github" / "workflows"

PUSH_ONLY_GUARD = "github.event.workflow_run.event == 'push'"


def workflow_files() -> list[Path]:
    return sorted(
        path
        for pattern in ("*.yml", "*.yaml")
        for path in WORKFLOW_DIR.glob(pattern)
    )


def triggers(definition: dict) -> dict:
    # PyYAML reads the bare key `on` as the boolean True.
    on = definition.get("on", definition.get(True)) or {}
    if isinstance(on, str):
        return {on: None}
    if isinstance(on, list):
        return dict.fromkeys(on)
    return on


def unguarded_jobs(definition: dict) -> list[str]:
    if "workflow_run" not in triggers(definition):
        return []
    return [
        name
        for name, job in (definition.get("jobs") or {}).items()
        if PUSH_ONLY_GUARD not in str(job.get("if", ""))
    ]


def test_workflows_are_found() -> None:
    """A wrong path would make every test below pass vacuously."""
    assert workflow_files(), f"no workflow files under {WORKFLOW_DIR}"


def test_an_unguarded_workflow_run_consumer_is_caught() -> None:
    definition = yaml.safe_load(
        """
        on:
          workflow_run:
            workflows: [CI]
            types: [completed]
        jobs:
          guarded:
            if: github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push'
          deploy:
            if: github.event.workflow_run.conclusion == 'success'
        """
    )
    assert unguarded_jobs(definition) == ["deploy"]


@pytest.mark.parametrize("workflow", workflow_files(), ids=lambda path: path.name)
def test_workflow_run_consumers_act_only_on_push(workflow: Path) -> None:
    definition = yaml.safe_load(workflow.read_text()) or {}
    unguarded = unguarded_jobs(definition)
    assert not unguarded, (
        f"{workflow.name} reacts to workflow_run, but jobs {unguarded} lack "
        f"`if: {PUSH_ONLY_GUARD}`. A dispatched or re-run upstream workflow "
        "would trigger them, so a build-drift dispatch of CI could deploy."
    )
