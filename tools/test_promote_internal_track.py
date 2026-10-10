"""Tests for tools/promote_internal_track.py and its wiring in release.yml."""

import builtins
import importlib
import sys
import types
from pathlib import Path

import pytest
import yaml

sys.path.insert(0, str(Path(__file__).parent))

from promote_internal_track import blocked_reason, newest_draft  # noqa: E402

RELEASE_WORKFLOW = Path(__file__).resolve().parents[1] / ".github" / "workflows" / "release.yml"


def test_health_apps_declaration_gate_is_tolerated() -> None:
    """#381: a 403 asking for the Health apps declaration is a Console-only state."""
    reason = blocked_reason(
        403, "You must let us know whether your app includes any health features."
    )
    assert reason is not None
    assert "App content" in reason


def test_draft_app_gate_is_still_tolerated() -> None:
    """#204: 'completed' releases are rejected while the app itself is in draft."""
    assert blocked_reason(
        400, "Only releases with status draft may be created on draft app."
    ) is not None


def test_target_sdk_rejection_stays_red() -> None:
    """#303 was also a 403 at commit and was correctly fixed in code; a bare-403
    match would have swallowed it."""
    assert blocked_reason(403, "Target SDK of artifact is too low") is None


@pytest.mark.parametrize(
    "status, message",
    [
        (403, "on draft app"),
        (500, "health features"),
        (400, "health features"),
        (403, "something else entirely"),
    ],
)
def test_status_and_message_are_both_required(status: int, message: str) -> None:
    assert blocked_reason(status, message) is None


def test_reasons_are_single_line() -> None:
    """The reason is written to GITHUB_ENV as one KEY=value line."""
    for status, message in ((400, "on draft app"), (403, "health features")):
        assert "\n" not in blocked_reason(status, message)


def release(version_code: int, status: str) -> dict:
    return {"name": f"main-{version_code}", "versionCodes": [str(version_code)], "status": status}


def test_newest_draft_skips_completed_releases() -> None:
    drafts = newest_draft([release(202, "draft"), release(201, "completed")])
    assert drafts["versionCodes"] == ["202"]


@pytest.mark.parametrize("order", ["newest-first", "oldest-first"])
def test_newest_draft_picks_highest_version_code_regardless_of_order(order: str) -> None:
    releases = [release(203, "draft"), release(202, "draft"), release(201, "completed")]
    if order == "oldest-first":
        releases.reverse()
    assert newest_draft(releases)["versionCodes"] == ["203"]


def test_newest_draft_without_drafts() -> None:
    assert newest_draft([release(201, "completed")]) is None
    assert newest_draft([]) is None


def test_newest_draft_tolerates_missing_version_codes() -> None:
    assert newest_draft([{"status": "draft"}]) == {"status": "draft"}


def test_module_imports_without_google_client(monkeypatch: pytest.MonkeyPatch) -> None:
    """The tools-tests CI job installs only tools/requirements.txt."""
    real_import = builtins.__import__

    def refuse_google(name, *args, **kwargs):
        if name == "google" or name.startswith(("google.", "googleapiclient")):
            raise ImportError(name)
        return real_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", refuse_google)
    monkeypatch.delitem(sys.modules, "promote_internal_track", raising=False)
    importlib.import_module("promote_internal_track")


def test_release_workflow_runs_the_script() -> None:
    """Release does not run on PRs, so a wrong path would only surface post-merge."""
    definition = yaml.safe_load(RELEASE_WORKFLOW.read_text())
    steps = definition["jobs"]["release"]["steps"]
    promote = next(s for s in steps if s.get("name") == "Promote internal track draft to completed")
    assert "python3 tools/promote_internal_track.py" in promote["run"]
    assert "<<" not in promote["run"]


class FakeHttpError(Exception):
    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.resp = types.SimpleNamespace(status=status)
        self.content = message.encode()


class FakeRequest:
    def __init__(self, calls: list, name: str, result, error: Exception | None) -> None:
        self.calls, self.name, self.result, self.error = calls, name, result, error

    def execute(self, **kwargs):
        self.calls.append((self.name, kwargs))
        if self.error is not None:
            raise self.error
        return self.result


class FakeEdits:
    """Stands in for svc.edits() and svc.edits().tracks(); `errors` maps a call name
    to the error that call's request raises once the client gives up retrying, and
    `releases` is what the internal track currently holds."""

    def __init__(
        self, errors: dict[str, Exception], releases: list[dict] | None = None
    ) -> None:
        self.calls: list = []
        self.errors = errors
        self.releases = [release(202, "draft")] if releases is None else releases

    def _request(self, name: str, result=None) -> FakeRequest:
        return FakeRequest(self.calls, name, result, self.errors.get(name))

    def insert(self, **_):
        return self._request("insert", {"id": "edit-1"})

    def tracks(self):
        return self

    def get(self, **_):
        return self._request("get", {"releases": self.releases})

    def update(self, **_):
        return self._request("update")

    def commit(self, **_):
        return self._request("commit")

    def delete(self, **_):
        return self._request("delete")


@pytest.fixture
def play(monkeypatch: pytest.MonkeyPatch):
    """Runs main() against fake Google client modules backed by the given FakeEdits."""
    import promote_internal_track

    def run(edits: FakeEdits) -> None:
        service_account = types.SimpleNamespace(
            Credentials=types.SimpleNamespace(from_service_account_info=lambda *a, **k: None)
        )
        modules = {
            "google": types.ModuleType("google"),
            "google.oauth2": types.SimpleNamespace(service_account=service_account),
            "googleapiclient": types.ModuleType("googleapiclient"),
            "googleapiclient.discovery": types.SimpleNamespace(
                build=lambda *a, **k: types.SimpleNamespace(edits=lambda: edits)
            ),
            "googleapiclient.errors": types.SimpleNamespace(HttpError=FakeHttpError),
        }
        for name, module in modules.items():
            monkeypatch.setitem(sys.modules, name, module)
        monkeypatch.setenv("PLAY_SERVICE_ACCOUNT_JSON", "{}")
        promote_internal_track.main()

    return run


def test_every_play_call_retries_transient_failures(play) -> None:
    """#478: a single 503 from edits.commit turned the Release job red."""
    edits = FakeEdits({})
    play(edits)
    assert [name for name, _ in edits.calls] == ["insert", "get", "update", "commit"]
    assert all(kwargs.get("num_retries", 0) > 0 for _, kwargs in edits.calls)


def test_server_error_that_outlasts_retries_stays_red(play) -> None:
    with pytest.raises(FakeHttpError):
        play(FakeEdits({"commit": FakeHttpError(503, "The service is currently unavailable.")}))


def test_console_gate_at_commit_is_still_tolerated(play) -> None:
    edits = FakeEdits(
        {"commit": FakeHttpError(403, "Tell us whether your app includes any health features.")}
    )
    play(edits)
    name, kwargs = edits.calls[-1]
    assert name == "delete"
    assert kwargs.get("num_retries", 0) > 0


def test_track_without_draft_discards_the_edit_with_retries(play) -> None:
    edits = FakeEdits({}, releases=[release(201, "completed")])
    play(edits)
    assert [name for name, _ in edits.calls] == ["insert", "get", "delete"]
    assert all(kwargs.get("num_retries", 0) > 0 for _, kwargs in edits.calls)
