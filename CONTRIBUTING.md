# Contributing

## Scope discipline

Each change should belong to one technical stage in `docs/stage-gates.md`. Avoid combining platform bring-up, input policy, object editing and file workflow in one change.

## Upstream source workflow

Do not vendor full upstream repositories. Use the lock file and generated work trees:

```text
sources.lock.toml
    ↓
scripts/fetch-sources.sh
    ↓
.work/src/<project>
    ↓
patches/<project>/*.patch
```

Rules:

- Build from immutable commit SHAs.
- Patch application failure is fatal.
- Generated work trees and binary bundles are never committed.
- Keep GTK patches narrow and independently reviewable.
- Preserve the normal desktop build whenever Inkscape code is changed.

## Interaction invariant

Every object-editing interaction must expose separate completion and cancellation paths:

```text
Armed → Active → Committed
               ↘ Aborted
```

A canceled sequence must not later be committed by a delayed release event.

## Evidence

A completed stage should include reproducible evidence: build manifest, APK checksum, target device/system version, test logs and exact reproduction commands. Do not commit private user documents or raw logs containing sensitive filenames.

## Licensing

Before copying source code from another project, record its license and compatibility in `LICENSES/README.md`. Architectural inspiration does not require code copying.
