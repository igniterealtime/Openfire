# Conversations Maestro Test Harness

Maestro UI test flows for the [Conversations](https://conversations.im) XMPP client, 
asserting against Android logcat output via the
[maestro-logcat-sidecar](https://github.com/Fishbowler/maestro-logcat-sidecar) 
(a Java process that hosts an HTTP API, and fetches logs from a running Android device 
or emulator).

## Prerequisites

- **`adb` on PATH** from the Android SDK platform-tools. The sidecar API requires it, but 
doesn't provide it. Maestro uses its own internal `dadb` library so doesn't provide it either.

  ```bash
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  ```

- **Java** on PATH (required to run the sidecar JAR). You probably had this for Openfire anyway.

- **Maestro CLI** installed:

  ```bash
  curl -fsSL "https://get.maestro.mobile.dev" | bash
  ```

## Running locally

### 1. Start Openfire

Build the distribution and start the server.

The `demoboot` flows need no extra configuration beyond launching Openfire with `-demoboot`.
For flows with a specific config, copy the matching file from `build/ci/conversations/configs/`
into your distribution's `conf/` directory as `openfire-demoboot.xml` before starting demoboot mode.

Flows are tagged by which config they need, not by feature: `fast.yaml` is tagged `sasl2`
and reuses `configs/sasl2.xml`, since FAST (XEP-0484) is only advertised as a SASL2 inline
feature and needs no config of its own — `xmpp.fast.enabled` defaults to `true`.

`fast-rotation.yaml` needs its own config (`configs/fast-rotation.xml`): FAST token rotation
happens once the time left before expiry drops to or below the rotation threshold, but the
real defaults are multi-day, so the config sets the threshold >= the expiry to make rotation
happen on every reconnect instead of waiting on the clock.

`scram-sha-256.yaml`, `scram-sha-256-plus.yaml`, `scram-sha-512.yaml` and
`scram-sha-512-plus.yaml` each need their own config that restricts the `sasl.mechs` list to
exactly one mechanism. Conversations always picks the strongest mechanism on offer, and always
prefers a channel-binding (`-PLUS`) variant when one is available, so under the default
mechanism list it would never negotiate a plain (non-PLUS) mechanism, and would always drift up
to `SCRAM-SHA-512-PLUS` rather than any other `-PLUS` variant. Restricting the offered mechanism
to exactly one is the only way to deterministically pin the client to each.

### 2. Start an Android emulator

Launch an emulator with API 34 and x86_64 architecture. With Maestro installed, you can create or
launch one with the following:

```bash
maestro start-device --platform=android --os-version=34
```

Or you can start one via Android Studio's AVD Manager. Wait until it is fully booted before
continuing.

### 3. Run the tests

From the repo root, pass a tag to select which suite to run:

```bash
.github/actions/conversationstest-action/run-tests.sh demoboot
```

All tests that don't require a specific config should be tagged `demoboot`.
A tag is required - the suites use incompatible Openfire configurations and must not be mixed.

The script downloads and installs the Conversations APK, starts the sidecar,
runs the selected Maestro flows, then stops the sidecar.

## Sidecar: manual start/stop

`run-tests.sh` manages the sidecar lifecycle automatically. If you need to run Maestro
directly (e.g. for flow authoring), start and stop the sidecar yourself:

```bash
# Before your test run (from the repo root)
.github/actions/conversationstest-action/start-sidecar-api.sh

# After your test run
.github/actions/conversationstest-action/stop-sidecar-api.sh
```

`start-sidecar-api.sh` downloads the sidecar JAR from GitHub Releases (if not already
cached), starts it in the background, and polls `GET /health` until it responds.
Logs are written to `/tmp/sidecar.log` (or `$RUNNER_TEMP/sidecar.log` in GitHub Actions).

To pin a specific sidecar version:
```bash
SIDECAR_VERSION=1.2.0 .github/actions/conversationstest-action/start-sidecar-api.sh
```

## API reference

See the [maestro-logcat-sidecar docs](https://github.com/Fishbowler/maestro-logcat-sidecar/blob/main/README.md)
for the full API reference.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/session/start` | Clears the logcat buffer. |
| `GET`  | `/assert?pattern=<regex>` | Snapshot scan. Returns `200` with matched lines, or `404` if none match. |
| `GET`  | `/health` | Liveness probe. Always returns `200 {"status":"ok"}`. |

## Configuration

| Variable | Default | Effect |
|----------|---------|--------|
| `LOGCAT_TAGS` | `conversations:V *:S` | Tags passed to `adb logcat`. Space-separated. |
| `SIDECAR_VERSION` | `1.0.0` | Which release of maestro-logcat-sidecar to download |

The `LOGCAT_TAGS` default filters to Conversations output only — override to capture additional tags if needed.

## Reusing steps across flows: subflows

Common sequences (e.g. logging in) live in `subflows/` and are pulled into a flow with `runFlow`:

```yaml
- runFlow:
    file: subflows/login.yaml
```

Subflow files need an `appId` header and a `---` separator like any flow, but no `tags` -
that's what keeps them from being picked up as a standalone flow when Maestro scans the
`flows/` directory. A `runScript` path inside a subflow resolves relative to the subflow's
own directory, not the calling flow's, hence `../scripts/...` inside `subflows/*.yaml`.

## Writing regex patterns

Use `checkForLogs.js` in a flow:

```yaml
- runScript:
    file: scripts/checkForLogs.js
    env:
      PATTERN: 'SMACK.*connected'
```

The above returns `HTTP/200` if a log line containing "SMACK" followed later by "connected" exists.

The matched lines are available in subsequent steps as `${checkForLogs.output.matchedLines}`.

For a log line produced by work that happens asynchronously after the UI has already settled
(for example, a background reconnect), pass `MAX_ATTEMPTS`/`DELAY_MS` to poll the sidecar
instead of taking a single snapshot:

```yaml
- runScript:
    file: scripts/checkForLogs.js
    env:
      PATTERN: 'quick start with HT'
      MAX_ATTEMPTS: 30 # default: 1 (single snapshot, no retry)
      DELAY_MS: 1000    # default: 1000
```
