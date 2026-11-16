# SPDX-License-Identifier: MIT
"""Remove only the generated outputs used by the project's build scripts."""

import argparse
import shutil
from pathlib import Path

MARKER = ".s40-java-build"
MARKER_CONTENT = "S40 Java build output v1\n"


def checked_child(root: Path, relative: str) -> Path:
    """Reject traversal and symlinks between a trusted root and an output."""
    parts = Path(relative).parts
    if not parts or Path(relative).is_absolute() or ".." in parts:
        raise ValueError(f"Invalid output path: {relative!r}")
    target = root
    for part in parts:
        target /= part
        if target.is_symlink():
            raise ValueError(f"Refusing symlinked output: {target}")
    return target


def check_directory(path: Path) -> None:
    if path.exists() and not path.is_dir():
        raise ValueError(f"Expected an output directory: {path}")


def clean_java(project_path: str, build_path: str, default: str, source: str) -> None:
    project = Path(project_path).resolve(strict=True)
    if not (
        default == "build"
        or (
            default.startswith("build/")
            and len(Path(default).parts) == 2
            and all(c in "abcdefghijklmnopqrstuvwxyz0123456789_-" for c in default[6:])
        )
    ):
        raise ValueError(f"Invalid default build directory: {default!r}")
    candidate = Path(build_path)
    if not build_path or not candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError("Build output must be an absolute path without '..'")
    if candidate.is_symlink():
        raise ValueError(f"Refusing symlinked build directory: {candidate}")
    is_default = candidate == Path(project_path) / default
    root = project / default if is_default else candidate.resolve()
    is_default = is_default or root == project / default
    if is_default:
        root = checked_child(project, default)

    # An override must never name a source/workspace/home root or its ancestors.
    workspace = Path(__file__).resolve().parent.parent
    for protected in (
        project,
        workspace,
        Path(source).resolve(),
        Path.home().resolve(),
    ):
        if root == protected or root in protected.parents:
            raise ValueError(f"Refusing broad build directory: {root}")

    check_directory(root)
    targets = [checked_child(root, name) for name in ("classes", "preverified")]
    for target in targets:
        check_directory(target)

    if not is_default:
        # Do not adopt an arbitrary existing directory just because an override
        # points at it. Only an empty directory can become a new build root.
        marker = checked_child(root, MARKER)
        if marker.exists():
            if not marker.is_file() or marker.read_text() != MARKER_CONTENT:
                raise ValueError(f"Invalid build ownership marker: {marker}")
        elif root.exists() and any(root.iterdir()):
            raise ValueError(
                f"Unrecognized build output: {root}; choose an empty directory"
            )
        else:
            root.mkdir(parents=True, exist_ok=True)
            marker.write_text(MARKER_CONTENT)

    # Validate every target before removing any of them. rmtree does not follow
    # symlinks found inside these generated directories.
    for target in targets:
        if target.exists():
            shutil.rmtree(target)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest="mode", required=True)
    java = modes.add_parser("java")
    java.add_argument("project")
    java.add_argument("build")
    java.add_argument("default", help="build or build/<app>, relative to project")
    java.add_argument("source")
    args = parser.parse_args()
    try:
        clean_java(args.project, args.build, args.default, args.source)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"Build cleanup refused: {exc}\n")


if __name__ == "__main__":
    main()
