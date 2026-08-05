---
title: Contributing
description: Build Android Solid Services from source, run the tests, and the conventions a change is expected to follow.
tags:
  - contributing
---

# Contributing

Contributions are welcome — bug reports, fixes, documentation and pod-server compatibility
reports all help. Please open an issue first for anything large, so the approach can be agreed
before you spend time on it.

## Build from source

**Requires JDK 17** (JetBrains Runtime 17.0.9 is what the project is developed against). Set
`JAVA_HOME` if the build complains.

```sh
git clone https://github.com/erfangholami/Android-Solid-Services.git
cd Android-Solid-Services
```

```sh
./gradlew assembleFossDebug    # the app, without Google services
./gradlew assembleGmsDebug     # the app, with Firebase
```

The APK lands in `app/build/outputs/apk/foss/debug`. Versions are derived from the git tag, so a
working copy needs no version edits — `-PassVersion=X.Y.Z` overrides for a build without git.

## Run the tests

```sh
./gradlew test                                   # unit tests, all modules
./gradlew :app:testDebugUnitTest                 # one module
./gradlew connectedAndroidTest                   # instrumented — needs a device or emulator
```

The `client` module's instrumented suite drives real calls across a process boundary. That is
where most defects in this project have lived, so it is worth running before a change to the SDK
or the AIDL surface.

## Style and static analysis

```sh
./gradlew spotlessApply detekt
```

CI runs `spotlessCheck` and `detekt`, plus the unit and instrumented suites. Pre-existing detekt
findings are recorded per module in `detekt-baseline.xml`; re-record with `./gradlew detektBaseline`
after an intentional change.

## Try SDK changes in a consumer app

```sh
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to the consumer's repositories. The
[client sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample) goes one
better: point it at a sibling checkout and it builds the SDK from source, so edits show up on the
next build with nothing to publish.

## Pull requests

- Branch from `dev` and open the pull request against it.
- Run `./gradlew spotlessApply detekt test` before pushing.
- Cover behaviour with a test where you can.
- Found something odd against a particular pod server? Say **which server** and **how it
  responded** — those reports have led to several fixes. Test pods used here are Inrupt PodSpaces
  and a Community Solid Server instance.

## Writing documentation

Docs live in `docs/` and are published with MkDocs Material. `mkdocs serve` after
`pip install -r docs-requirements.txt`.

Each capability page under **Build with it** follows the same shape, so a reader can move between
them without relearning the layout:

1. **What you can build** — concrete outcomes, not API names.
2. **Setup** — the dependency and the entry point, in Client/API tabs.
3. **Recipes** — one heading per task, phrased as the task, each with copy-paste code.
4. **How it flows** — one diagram of the path a call takes.
5. **Errors you'll hit** — symptom, cause, fix.
6. **Under the hood** — collapsed: pod shape and RDF, detailed flows, failure behaviour,
   extension points, tests, specifications implemented.

Three rules that keep the pages honest:

- **Describe what the code does**, not what it should do one day.
- **When behaviour is deliberately absent, say so and say why**, so a reader can tell a decision
  from an omission.
- **Never hand-write a version number.** Dependency blocks come from `docs/_includes/`, generated
  by `./gradlew docsIncludes` from the git tag. Three sites drifted apart the last time they were
  typed by hand.

Code examples should carry their imports. A snippet that only compiles in the context of the page
above it is not much use to someone who pasted it — or to an agent that lifted it.

## Releasing

Releasing is tagging: `versionName`, `versionCode` and all three Maven coordinates derive from
`vX.Y.Z`. The only file edited by hand is `CHANGELOG.md`, which the release workflow's `versions`
gate requires. Full detail in [Releases](releases.md).
