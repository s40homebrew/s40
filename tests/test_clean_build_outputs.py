"""Cleanup safety tests use only disposable directories and sentinel files."""

import importlib.util
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[1] / "tools/clean_build_outputs.py"
spec = importlib.util.spec_from_file_location("clean_build_outputs", SCRIPT)
cleanup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)


def put(path, text="keep me"):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return path


@pytest.fixture
def project(tmp_path):
    root = tmp_path / "project with spaces"
    root.mkdir()
    put(root / "src/source.java")
    return root


def java(project, build=None, default="build/app"):
    cleanup.clean_java(
        str(project),
        str(build if build is not None else project / default),
        default,
        str(project / "src"),
    )


def test_default_removes_only_java_outputs(project, tmp_path):
    build = project / "build/app"
    put(build / "classes/Old.class")
    put(build / "preverified/resource.bin")
    keep = [
        put(build / "obj/native.o"),
        put(build / "notes.txt"),
        put(project / "build/other/classes/Other.class"),
        project / "src/source.java",
    ]
    outside = put(tmp_path / "outside/precious.txt")
    (build / "classes/link").symlink_to(outside.parent, target_is_directory=True)
    java(project)
    assert not (build / "classes").exists()
    assert not (build / "preverified").exists()
    assert all(path.exists() for path in keep + [outside])
    java(project)  # A clean tree is also valid.


@pytest.mark.parametrize(
    "target", ["build", "build/app", "build/app/classes", "build/app/preverified"]
)
def test_rejects_symlinked_default_paths_before_deleting(project, tmp_path, target):
    outside = put(tmp_path / "outside/precious.txt")
    link = project / target
    link.parent.mkdir(parents=True, exist_ok=True)
    link.symlink_to(outside.parent, target_is_directory=True)
    sentinel = None
    if target.endswith("preverified"):
        sentinel = put(project / "build/app/classes/Old.class")
    with pytest.raises(ValueError, match="symlink"):
        java(project)
    assert outside.exists()
    assert sentinel is None or sentinel.exists()


@pytest.mark.parametrize(
    "target", ["root", "home", "workspace", "project", "source", "parent"]
)
def test_rejects_broad_overrides(project, target):
    build = {
        "root": Path("/"),
        "home": Path.home(),
        "workspace": SCRIPT.parent.parent,
        "project": project,
        "source": project / "src",
        "parent": project.parent,
    }[target]
    with pytest.raises(ValueError, match="broad"):
        java(project, build)
    assert (project / "src/source.java").exists()


@pytest.mark.parametrize("target", ["", "relative/build", "/private/tmp/../build"])
def test_rejects_empty_relative_and_traversal_paths(project, target):
    with pytest.raises(ValueError, match="absolute"):
        cleanup.clean_java(str(project), target, "build/app", str(project / "src"))


def test_rejects_unrecognized_override(project, tmp_path):
    build = tmp_path / "unrelated"
    sentinel = put(build / "classes/important.txt")
    with pytest.raises(ValueError, match="Unrecognized"):
        java(project, build)
    assert sentinel.exists()
    assert not (build / cleanup.MARKER).exists()


def test_external_build_requires_empty_directory_then_supports_rebuild(
    project, tmp_path
):
    build = tmp_path / "external build"
    java(project, build)
    assert (build / cleanup.MARKER).is_file()
    put(build / "classes/Old.class")
    put(build / "preverified/Old.class")
    sentinel = put(build / "keep.txt")
    java(project, build)
    assert sentinel.exists()
    assert not (build / "classes").exists()
    assert not (build / "preverified").exists()


@pytest.mark.parametrize("kind", ["invalid", "symlink", "directory"])
def test_rejects_bad_ownership_marker(project, tmp_path, kind):
    build = tmp_path / "external"
    sentinel = put(build / "classes/important.txt")
    marker = build / cleanup.MARKER
    if kind == "invalid":
        marker.write_text("unrelated")
    elif kind == "symlink":
        marker.symlink_to(put(tmp_path / "marker", cleanup.MARKER_CONTENT))
    else:
        marker.mkdir()
    with pytest.raises(ValueError):
        java(project, build)
    assert sentinel.exists()


def test_project_build_preserves_other_build_files_and_artifacts(project):
    put(project / "build/classes/Old.class")
    put(project / "build/preverified/Old.class")
    keep = [
        put(project / "build/notes.txt"),
        put(project / "build/native.bin"),
        put(project / "dist/other.jar"),
        put(project / "dist/RM443NativeOsInfo.jar"),
        put(project / "dist/RM443NativeOsInfo.jad"),
        project / "src/source.java",
    ]
    java(project, default="build")
    assert all(path.exists() for path in keep)
    assert not (project / "build/classes").exists()
    assert not (project / "build/preverified").exists()


@pytest.mark.parametrize("target", ["build", "build/classes", "build/preverified"])
def test_project_build_rejects_redirected_paths_before_deleting(
    project, tmp_path, target
):
    outside = put(tmp_path / "outside/precious.txt")
    link = project / target
    link.parent.mkdir(parents=True, exist_ok=True)
    link.symlink_to(outside.parent)
    sentinel = (
        put(project / "build/classes/Old.class")
        if target.endswith("preverified")
        else None
    )
    with pytest.raises(ValueError, match="symlink"):
        java(project, default="build")
    assert outside.exists()
    assert sentinel is None or sentinel.exists()


def test_cli_reports_refusal_without_deleting(project, tmp_path):
    sentinel = put(tmp_path / "unrelated/classes/important.txt")
    result = subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "java",
            str(project),
            str(sentinel.parents[1]),
            "build/app",
            str(project / "src"),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 1
    assert "Build cleanup refused:" in result.stderr
    assert sentinel.exists()
