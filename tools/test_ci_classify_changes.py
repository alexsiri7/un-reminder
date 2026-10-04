"""The `changes` job in ci.yml decides whether the other CI jobs run their steps.

A wrong `docs_only=true` silently skips lint, tests and the release build while
every required check still reports success, so the classification script is run
here against real git diffs rather than trusted by inspection.
"""

import os
import subprocess
from pathlib import Path

import pytest
import yaml

CI_WORKFLOW = Path(__file__).resolve().parents[1] / ".github" / "workflows" / "ci.yml"
ZERO_SHA = "0" * 40


def ci_jobs() -> dict:
    return yaml.safe_load(CI_WORKFLOW.read_text())["jobs"]


def classify_script() -> str:
    (step,) = [s for s in ci_jobs()["changes"]["steps"] if s.get("id") == "classify"]
    return step["run"]


def git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=repo, check=True, capture_output=True, text=True
    ).stdout.strip()


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    git(tmp_path, "init", "-q")
    git(tmp_path, "config", "user.name", "test")
    git(tmp_path, "config", "user.email", "test@example.com")
    (tmp_path / "README.md").write_text("base\n")
    git(tmp_path, "add", ".")
    git(tmp_path, "commit", "-qm", "base")
    return tmp_path


def commit(repo: Path, *paths: str) -> str:
    for path in paths:
        file = repo / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(f"changed {path}\n")
    git(repo, "add", ".")
    git(repo, "commit", "-qm", "change")
    return git(repo, "rev-parse", "HEAD")


def classify(repo: Path, base: str, head: str) -> str:
    output = repo / "github_output"
    output.write_text("")
    # GitHub runs `run:` scripts with `bash -e`.
    subprocess.run(
        ["bash", "-e", "-c", classify_script()],
        cwd=repo,
        check=True,
        env={**os.environ, "BASE": base, "GITHUB_SHA": head, "GITHUB_OUTPUT": str(output)},
    )
    return output.read_text().strip()


def test_requirements_only_diff_is_docs_only(repo: Path) -> None:
    base = git(repo, "rev-parse", "HEAD")
    head = commit(repo, "requirements/001-a.md", "requirements/002-b.md")
    assert classify(repo, base, head) == "docs_only=true"


def test_requirements_diff_touching_code_is_not_docs_only(repo: Path) -> None:
    base = git(repo, "rev-parse", "HEAD")
    head = commit(repo, "requirements/001-a.md", "app/src/Main.kt")
    assert classify(repo, base, head) == "docs_only=false"


def test_unresolvable_base_is_not_docs_only(repo: Path) -> None:
    head = commit(repo, "requirements/001-a.md")
    assert classify(repo, ZERO_SHA, head) == "docs_only=false"


@pytest.mark.parametrize(
    "job_id", [job_id for job_id, job in ci_jobs().items() if job.get("needs") == "changes"]
)
def test_jobs_gated_on_changes_still_run_when_it_fails(job_id: str) -> None:
    """Without a status function, a failed `changes` job skips its dependents
    outright, and a skipped job satisfies a required check without running."""
    assert ci_jobs()[job_id].get("if") == "${{ !cancelled() }}"
