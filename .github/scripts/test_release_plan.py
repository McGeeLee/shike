import unittest

from release_plan import flatten_releases, plan_release, version_code


def release(tag, *, draft=False, prerelease=False, complete=True):
    return {
        "tag_name": tag,
        "draft": draft,
        "prerelease": prerelease,
        "assets": [{"name": name} for name in (
            [f"shike-{tag}.apk", f"shike-{tag}.apk.sha256"] if complete else []
        )],
    }


class ReleasePlanTest(unittest.TestCase):
    def test_existing_old_release_is_skipped_without_reassigning_version_code(self):
        plan = plan_release("v2.3.1", [release("v2.4.0"), release("v2.3.1")])
        self.assertFalse(plan.publish)
        self.assertIsNone(plan.version_code)

    def test_existing_latest_is_also_immutable_on_retry(self):
        self.assertFalse(plan_release("v2.4.0", [release("v2.4.0")]).publish)

    def test_new_older_release_is_rejected_even_if_snapshot_is_unsorted(self):
        with self.assertRaises(ValueError):
            plan_release("v2.3.9", [release("v2.2.0"), release("v2.4.0"), release("v2.3.1")])

    def test_incomplete_or_draft_release_is_never_overwritten(self):
        for existing in (release("v2.4.1", complete=False), release("v2.4.1", draft=True)):
            with self.subTest(existing=existing), self.assertRaises(ValueError):
                plan_release("v2.4.1", [existing])

    def test_version_code_is_monotonic_across_patch_minor_and_major(self):
        versions = [(2, 3, 999), (2, 4, 0), (2, 4, 1), (2, 999, 999), (3, 0, 0)]
        codes = [version_code(version) for version in versions]
        self.assertEqual(codes, sorted(set(codes)))
        self.assertEqual(2_004_001, version_code((2, 4, 1)))
        self.assertGreater(codes[2], 13)  # Public v2.4.0 APK's legacy run-based versionCode.

    def test_newer_version_can_be_published_and_ignores_unpublished_versions(self):
        plan = plan_release("v2.4.1", [
            release("v2.4.0"), release("v3.0.0", draft=True), release("v4.0.0", prerelease=True),
        ])
        self.assertTrue(plan.publish)
        self.assertEqual("2.4.1", plan.version_name)
        self.assertEqual(2_004_001, plan.version_code)

    def test_ambiguous_or_out_of_range_tags_are_rejected(self):
        for tag in ("v02.4.1", "v2.04.1", "v2.4.01", "v2.4", "v2.4.1-beta", "v2.1000.0", "v2.0.1000", "v2100.0.1", "v0.0.0"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                plan_release(tag, [])
        self.assertEqual(2_100_000_000, version_code((2100, 0, 0)))

    def test_paginated_snapshot_checks_all_pages(self):
        pages = [[release("v2.2.0")], [release("v2.4.0")]]
        self.assertEqual(2, len(flatten_releases(pages)))
        with self.assertRaises(ValueError):
            plan_release("v2.3.0", pages)


if __name__ == "__main__":
    unittest.main()
