"""Validate an immutable, monotonically versioned Android release before signing."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re


TAG_PATTERN = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
MAX_VERSION_CODE = 2_100_000_000


@dataclass(frozen=True)
class ReleasePlan:
    publish: bool
    version_name: str
    version_code: int | None
    reason: str


def parse_tag(tag: str) -> tuple[int, int, int]:
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        raise ValueError("Release tag must use canonical vMAJOR.MINOR.PATCH format.")
    return tuple(int(part) for part in match.groups())


def version_code(version: tuple[int, int, int]) -> int:
    major, minor, patch = version
    if not 0 <= minor < 1000 or not 0 <= patch < 1000:
        raise ValueError("Release minor and patch versions must be between 0 and 999.")
    code = major * 1_000_000 + minor * 1000 + patch
    if not 1 <= code <= MAX_VERSION_CODE:
        raise ValueError("Generated Android versionCode is outside the supported range.")
    return code


def flatten_releases(snapshot: list) -> list[dict]:
    """Accept both a REST response and `gh api --paginate --slurp` pages."""
    if not isinstance(snapshot, list):
        raise ValueError("Expected a GitHub releases response or paginated response.")
    releases = []
    for item in snapshot:
        page = item if isinstance(item, list) else [item]
        if not all(isinstance(release, dict) for release in page):
            raise ValueError("Invalid release metadata.")
        releases.extend(page)
    return releases


def plan_release(tag: str, snapshot: list) -> ReleasePlan:
    requested_version = parse_tag(tag)
    requested_code = version_code(requested_version)
    releases = flatten_releases(snapshot)
    for release in releases:
        if release.get("tag_name") != tag:
            continue
        required_assets = {f"shike-{tag}.apk", f"shike-{tag}.apk.sha256"}
        assets = {asset.get("name") for asset in release.get("assets", [])}
        if release.get("draft") or release.get("prerelease") or not required_assets <= assets:
            raise ValueError(
                f"Release {tag} already exists but is not a complete stable APK release; "
                "refusing to replace its assets or status. Publish a new version instead."
            )
        return ReleasePlan(False, tag[1:], None, f"Release {tag} is already published; preserving it unchanged.")

    published_versions = []
    for release in releases:
        if release.get("draft") or release.get("prerelease"):
            continue
        try:
            published_versions.append(parse_tag(release.get("tag_name", "")))
        except ValueError:
            continue
    if published_versions and requested_version <= max(published_versions):
        raise ValueError("A new stable release must be newer than every published stable version.")
    return ReleasePlan(True, tag[1:], requested_code, f"Publish new stable release {tag}.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--releases-file", type=Path, required=True)
    parser.add_argument("--github-env", type=Path)
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--require-publish", action="store_true")
    args = parser.parse_args()
    try:
        plan = plan_release(args.tag, json.loads(args.releases_file.read_text()))
        if args.require_publish and not plan.publish:
            raise ValueError(plan.reason)
    except (ValueError, TypeError) as error:
        parser.exit(1, f"Release validation failed: {error}\n")
    print(plan.reason)
    if args.github_output:
        with args.github_output.open("a") as output:
            output.write(f"publish={str(plan.publish).lower()}\n")
    if args.github_env and plan.publish:
        with args.github_env.open("a") as environment:
            environment.write(f"SHIKE_VERSION_NAME={plan.version_name}\nSHIKE_VERSION_CODE={plan.version_code}\n")
    if args.summary:
        with args.summary.open("a") as summary:
            summary.write(f"### Android release\n{plan.reason}\n")
            if plan.publish:
                summary.write(f"- versionName: `{plan.version_name}`\n- versionCode: `{plan.version_code}`\n")


if __name__ == "__main__":
    main()
