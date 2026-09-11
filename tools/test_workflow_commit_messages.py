"""No workflow step may commit with a CI-skip marker in its message.

GitHub honours such a marker anywhere in a push's commit message, and this
repository squash-merges with `squash_merge_commit_message = COMMIT_MESSAGES`,
so a marker in any PR commit is concatenated into main's commit message and
suppresses the push-triggered CI and Release runs (#351).

The check gates on `git commit` appearing anywhere in a step's script and then
scans the whole script, so a message assembled into a shell variable earlier in
the step is still covered. A `git commit -F <file>` whose message file is written
by a different step would slip through; no workflow here does that.
"""

import re
from pathlib import Path

import pytest
import yaml

WORKFLOW_DIR = Path(__file__).resolve().parents[1] / ".github" / "workflows"

MARKER = re.compile(
    r"\[(?:skip[ -]ci|ci[ -]skip|no[ -]ci|skip[ -]actions|actions[ -]skip)\]"
    r"|\*\*\*NO_CI\*\*\*",
    re.IGNORECASE,
)


def workflow_files() -> list[Path]:
    return sorted(
        path
        for pattern in ("*.yml", "*.yaml")
        for path in WORKFLOW_DIR.glob(pattern)
    )


def test_workflows_are_found() -> None:
    """A wrong path would make every test below pass vacuously."""
    assert workflow_files(), f"no workflow files under {WORKFLOW_DIR}"


@pytest.mark.parametrize("workflow", workflow_files(), ids=lambda path: path.name)
def test_committing_steps_carry_no_ci_skip_marker(workflow: Path) -> None:
    definition = yaml.safe_load(workflow.read_text()) or {}
    for job_name, job in (definition.get("jobs") or {}).items():
        for step in job.get("steps") or []:
            script = step.get("run")
            if not script or "git commit" not in script:
                continue
            found = MARKER.search(script)
            assert not found, (
                f"{workflow.name} / {job_name} / {step.get('name', '<unnamed>')} "
                f"commits with a CI-skip marker ({found.group()}) in its message. "
                "A squash merge carries that marker into main's commit message, "
                "which suppresses the CI and Release runs for the merge."
            )
