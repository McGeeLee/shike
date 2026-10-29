# Android persistence and dependency review — 2026-10-29

## Persistence behavior

Delete and undo now perform the synchronous preferences commit on an injected IO dispatcher. Entries, nutrition summaries and undo state change only after a successful commit. A failed second delete preserves the earlier undo and its photo. A failed undo retains its photo and event, displays a retry dialog and restores the undo snackbar when the dialog is dismissed. Concurrent meal saves/deletes/undo requests are gated until the current write completes, and date changes do not cause completion to replace another day's visible entries.

The existing repository rollback for `commit() == false` remains in place: Android updates the in-memory preferences even when disk persistence fails. The new ViewModel regression tests exercise that behavior through real Robolectric preferences, including retry without duplicate entries. Coroutine cancellation is rethrown.

References: [SharedPreferences.Editor.commit](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()), [Android coroutine dispatchers, main safety and cancellation](https://developer.android.com/kotlin/coroutines/coroutines-best-practices), [Compose alert dialogs](https://developer.android.com/develop/ui/compose/components/dialog).

## Stable dependency selection

Versions were checked against Google Maven and Maven Central metadata and the projects' release documentation. There are no `AGENTS.md` or `.agents/skills` files in this checkout.

| Dependency | Previous | Selected stable |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.1 | 9.5.0 |
| Gradle | 9.8.1 | 9.8.2 |
| Robolectric (new JVM regression test runtime) | — | 4.17 |
| Kotlin / Compose compiler | 2.4.21 | 2.4.21 |
| Compose BOM | 2026.09.00 | 2026.09.00 |
| Activity | 1.13.0 | 1.13.0 |
| Core / Splashscreen | 1.19.1 / 1.2.0 | unchanged |
| Lifecycle | 2.11.0 | 2.11.0 |
| ExifInterface | 1.4.2 | 1.4.2 |
| Material Components | 1.14.0 | 1.14.0 |
| Desugar JDK libraries | 2.1.5 | 2.1.5 |
| JUnit / AndroidX JUnit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 | unchanged |
| JSON-java | 20260814 | 20260814 |

[AGP 9.5.0](https://developer.android.com/build/releases/agp-9-5-0-release-notes?hl=en) requires Gradle 9.6 or newer and supports the existing API 37 build. It also fixes AGP/Gradle 9.8 deprecation and JDK 25 resource-generation issues. The project retains [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin?hl=en), JDK 25 and Java 21 bytecode. [Gradle 9.8.2](https://docs.gradle.org/9.8.2/release-notes.html) is pinned with its [official distribution SHA-256](https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256), `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.

[Robolectric 4.17 setup](https://robolectric.org/getting-started/) requires Android resources in local tests and JVM module opens on Java 17+. Both are configured only for test tasks. The [compatibility table](https://robolectric.org/compatibility_table/) supports API 37 with Java 21+, so the persistence suite runs against API 37. Its [4.17 migration notes](https://robolectric.org/upgrade_to_version_4/) cover JSpecify, native SQLite and Conscrypt defaults.

The Compose BOM already supplies stable Runtime/UI 1.12.1 and Material 3 1.4.0. SDK 37.1 remains the stable compile target; [37.2 QPR2](https://developer.android.com/about/versions/17/qpr2/release-notes) is still Beta 7.1 and is not introduced into this production build.

## Security review

The official Gradle advisories [GHSA-mvvg-497x-hmj8](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8), [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64) and [GHSA-j5m7-59rp-24f5](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5) were already fixed by the repository's previous Gradle 9.8.1. Upgrading to 9.8.2 retains these fixes; this is not a claim that 9.8.1 was vulnerable to them.

An OSV batch query for the selected major direct Android coordinates returned no advisories. This is a limited direct-dependency check, not a full transitive Android vulnerability audit. Website dependency results are recorded separately in `site-dependency-audit-2026-10-29.md`.

## Validation

Run the same build used by Android CI:

```sh
cd android
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease --stacktrace --warning-mode all
```

`ShikeViewModelPersistenceTest` runs as part of `testDebugUnitTest`, so CI executes the failure/retry regressions without needing an emulator. It covers failed delete, failed replacement delete, failed undo and successful retry, photo cleanup timing, date changes during persistence, duplicate actions, thrown write exceptions and cancellation.
