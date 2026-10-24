# Validation and acceptance record

Date: 2026-10-24, Windows 11 amd64, Java 21.0.11.0.11+10. Tests use the production
protocol/audio implementation; test doubles exist only in the unit-test source set.
No password, private identity, raw audio, server address or other users' data is
included in the delivery's test records.

## Reproduce automated checks

```powershell
.\gradlew.bat clean test build
.\gradlew.bat nativeSmoke :core:nativeTest
$env:BLOCKTALK_HARDWARE_TEST='true'
.\gradlew.bat :core:hardwareTest
python tools/check_localization.py
```

`test` excludes native/hardware tests. `nativeTest` fails if the actual Windows DLL
cannot load; it is not a fake codec. `hardwareTest` is explicitly skipped unless its
environment opt-in is `true`. A skip is not a pass. The current machine's hardware
test was opted in and actually opened the default microphone/output, read one PCM
frame, wrote local silence and released both lines. It did not send microphone audio.
The 44.1 kHz fallback is unit tested; the selected real hardware used 48 kHz.

The final combined build command was:

```powershell
.\gradlew.bat clean test build nativeSmoke :core:nativeTest :core:hardwareTest prepareSmokeClientRun --write-locks --console=plain
```

Exit status: **0**. All requested Gradle clean/test/build tasks completed successfully.
Later changes were followed by build and relevant UI/protocol checks. Lockfiles are
included so ordinary builds do not need `--write-locks`.

Unit coverage includes held-vs-click PTT, release, focus loss/new press, mute/deafen
restoration, text/permission/device/codec gates, generation invalidation, cancelled
connect and stale callbacks, cancelled scheduled reconnect, authentication retry
exit, shutdown-before-logout, reorder/duplicates/loss/PLC/16-bit wrap, independent
speaker buffers, expiry/bounds/end markers, schema migration, corrupt backup,
future-schema preservation, identity reuse/cancel, address forms, allowlisted errors
and diagnostic redaction. A live-discovered empty-property bug has a regression test.
Native tests check mono/stereo decoding/PLC, independent decoder state and 100
allocation/encode/decode/close cycles.

Final reported totals: **24 BlockTalk unit tests + 18 upstream protocol tests +
2 native tests + 1 opted-in hardware test = 45 executed, 0 failures, 0 errors,
0 skips in these executed suites.** The separate external-DNS test and manual
acceptance cases below were not executed and are not included in that total.

Preserved upstream ts3j tests include cryptographic known-answer vectors, identity
encoding/import/export, packet generation, compression and fragmentation. Its external
DNS test is excluded from unit tests and can be run explicitly:
`gradlew :third_party:ts3j:externalTest`. It was not executed for this delivery.
The legacy `ServerConnectionTest` is a main-method tool, not silently counted as a test.

## Real TS protocol and microphone probe

Set `BLOCKTALK_TS_ADDRESS`, optionally `BLOCKTALK_TS_PASSWORD`, and use a dedicated
test identity directory via `BLOCKTALK_CONFIG_DIR`. Do not put credentials in committed
scripts or Gradle properties.

```powershell
.\gradlew.bat :core:integration -PprobeArgs='--switch-channel'
.\gradlew.bat :core:integration -PprobeArgs='--pair-smoke --switch-channel'
```

Both commands exited **0** against the user-supplied real server. The server's product
version was not independently established, so this is not a general TS6 compatibility
claim. Observed: authentication succeeded, 10 real Opus Voice channels and the visible
self user were returned, the saved identity UID was reused, an available other channel
was joined and the original channel restored.

The explicit pair probe opens a real microphone, uses the production AudioEngine and
creates a second independent real TS client identity. Latest recorded run received
15 forward and 20 reverse 20 ms microphone frames through the server and decoded them
with real Opus. The second direction uses a fresh encoder/decoder. Output gain is zero
to prevent acoustic feedback; no raw audio is saved. After PTT release, the received
frame count remained unchanged during the observation window. No tone, mock server,
ServerQuery bot or local audio loop replaced the UDP protocol path.

**The pair probe validates BlockTalk-to-BlockTalk network operation, not human
listening or official TeamSpeak interoperability.** A later separate official-peer
test is recorded in `OFFICIAL_INTEROP.md`: both directions were heard, but the first
reverse burst had severe noise and an unchanged retry was normal. Separate-device
topology/client version, sustained quality, simultaneous speakers and real-server
Opus Music acceptance remain open.

For interactive official-peer work:

```powershell
.\gradlew.bat :core:integration -PprobeArgs='--audio'
```

Commands: `status`, `talk SECONDS` (1–5, automatically closes), `down`, `up`, `mute`,
`deafen`, `join ID`, `quit`. Aggregate counters include accepted receive packets,
decoded/PLC frames, mixed voice frames written to output and successful voice sends.
Silence written by the playback clock is not counted as a played voice frame.
No PCM or identity/address fields are retained in those counters. The console is an explicit
developer PTT harness, not the game's hold-key implementation. There is no automatic
PTT opening. Test new channel passwords in the game's password screen.

## GUI, release packaging and physical side

`gradlew runSmokeClient` exited **0**. It opens real native Screens, captures in-game
screenshots and closes Minecraft. No microphone/server is used unless the separate
`BLOCKTALK_SMOKE_CONNECT=true` environment flag is explicitly set alongside the test
server variables. Normal `runClient` never performs these actions.

`gradlew prepareSmokeClientRun build` followed by `python tools/smoke_packaged.py`
also exited **0**. This copied the actual release JAR into a separate `mods/` folder,
removed source-folder mappings and excluded the loose core/vendor/BC/dnsjava JARs.
NeoForge discovered nested JARs, displayed the Screens and loaded **libopus 1.6.1**
from its nested native resource. This catches the development-only-dependency problem.
It still uses the MDK-resolved Minecraft runtime and does not replace a clean launcher
installation. Default 854×480 / GUI scale 2 screenshots were visually inspected.
Both English and Chinese Screens were inspected. A later packaged run explicitly
connected the real test server from the title screen, displayed 10 channels, and
reported `state=CONNECTED`, `audioReady=true`, `audioFailureType=none`, libopus 1.6.1.
Main-menu PTT remained blocked by the screen policy. No PTT was opened in that GUI run.

The dedicated NeoForge server run reached `Done`, accepted `stop`, saved the generated
world and exited with **0**. The physical-client entry point did not initialize audio
or rendering. An earlier noninteractive test server run had to be terminated (exit -1)
because its stdin was closed; the corrected interactive run stopped normally. No test
server is left running. This does not establish vanilla/Paper multiplayer compatibility.

## Failures encountered and corrected

- Initial server connection failed: upgrading BC exposed shared EAX nonce-state behavior.
  A protocol-preserving per-operation cipher patch restored real authentication.
- Empty TS properties were null and interrupted snapshot merging. Normalization fixed
  the live list from 1 known codec / 9 unknown to all 10 correctly reported Opus Voice.
- Initial development GUI lacked the core source binding; corrected MDK mod source sets.
- Logout after shutdown rejected a cleanup task; idempotent close/disconnect fixed it.
- Exploratory opus-java native reported libopus 1.1 and its inherited wrapper grant was
  unclear. It was removed; the final original binding uses source-built Opus 1.6.1.
- ini4j advisory found during audit; sole use replaced with a bounded JDK reader.
- Transient NeoForge Maven mirror error and two prototype packaged-launch script errors
  were corrected. Final build and packaged launcher checks exited 0.

No unresolved automated test failure is hidden as a skip. Full logs remain in ignored
`work/`; delivered summaries are sanitized. CI is configured for Java 21 Windows/Linux
build/test/artifact upload and Windows native smoke, but GitHub CI has not been run.

## Manual release checklist — still open unless explicitly marked

- [ ] Clean Minecraft 1.21.1 + NeoForge instance: drop in only the release JAR and load
  without official TeamSpeak installed.
- [ ] Open panel from title and in world; CRUD favorites and authenticate through UI.
  Main menu Screens and packaged loading were exercised; complete interaction awaits review.
- [x] User-operated official TS client: both directions heard through the standalone
  production core; reverse retry normal after first-burst severe noise. This is a
  limited observation, not a resolved quality defect.
- [ ] Confirm separate-device topology/client version, then two concurrent speakers.
- [ ] Hold/release PTT, unfocus, mute and deafen; verify microphone privacy in password/chat.
  Unit tests and a real UDP release probe passed; complete official-peer privacy
  observation in Minecraft remains open.
- [ ] Join passworded/full/restricted channels and confirm old audio never crosses the move.
  An allowed real channel switch passed; rejection scenarios remain manual.
- [ ] Restart game: preserve UID/settings. Interrupt network and recover with PTT still closed.
  Identity persistence and reconnect state logic passed automated tests.
- [ ] Switch/unplug devices, low FPS, single-player pause, world exit, repeated sessions.
  Default line open/read/write/close passed; prolonged scenarios remain open.
- [ ] Join a vanilla/Paper or NeoForge server that does not have BlockTalk installed.
- [ ] Sustained calls/many switches: no growing threads, native states, buffers or device handles.
  Bounds/decoder ownership and 100 native lifecycle cycles passed; sustained soak remains open.
