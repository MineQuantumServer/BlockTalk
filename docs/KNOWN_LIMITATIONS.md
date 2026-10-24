# Current limits — 0.1.0-alpha.1

This is an experimental client, not an official TeamSpeak product. Build success is
not a claim of official-client audio interoperability.

## Verification still required

- One user-operated official TeamSpeak client participated in bidirectional human
  listening with the standalone production core. Official -> BlockTalk played 308
  decoded frames, with user confirmation. The first reverse five-second burst was
  later reported to have severe noise; an unchanged retry was reported normal.
  The intermittent noise remains unexplained and is not considered fixed.
  Client version and separate-device topology were not supplied. Simultaneous speakers,
  sustained quality and combined Minecraft-UI/official-peer acceptance remain open.
  See `OFFICIAL_INTEROP.md` for the observations and limits.
- A clean launcher-managed Minecraft installation and joining a vanilla/Paper server
  with only the client mod remain unverified. The actual packaged JAR was exercised
  under the MDK runtime without source folders/project library JARs.
- Long calls, unplug/replug, OS device rerouting, many users, low FPS and long paused
  sessions need extended manual testing. Unit tests cover bounds and cancellation,
  not physical device failure behavior. Drivers that keep a disconnected line alive or
  transparently reroute the system default device cannot always be detected by Java Sound.
- Linux/macOS/ARM native audio is intentionally unavailable; settings still open.
  CI jobs for Windows/Linux are configured but have not run on GitHub in this delivery.

## Deliberate first-version scope

- TS3 protocol; only 48 kHz, 20 ms Opus Voice and Opus Music. Other codecs and packet
  durations are disabled/rejected. Opus Music encoding uses duplicated mono microphone
  input, while remote stereo is decoded and played as stereo. No FEC, AGC, VAD, noise
  suppression or echo cancellation is claimed. Use headphones.
- DNS A/AAAA and `_ts3._udp` SRV (unless an explicit port is supplied). No TSDNS,
  TeamSpeak server nicknames, SRV target failover or official account services.
- Native device rates are 48 kHz or a linear-resampled 44.1 kHz fallback. Other rates
  fail closed. The fallback has unit tests but was not selected by the tested hardware.
- Basic channel browsing/switching, events, local mute and volume are provided.
  Channel text chat/private messages are deferred. No file transfer, permissions editor,
  spatial audio, world position sync or recording.
- Favorite selection and device selection currently cycle through saved entries;
  channel/user lists scroll with the mouse wheel. UI has been inspected at the default
  854×480 window / GUI scale 2; other GUI scales and keyboard-only navigation need review.
- Device previews use the last saved settings. Choose a device and Save before starting
  local mic monitoring; the monitor is unavailable while connected. Cancel stops the
  monitor and discards unsaved preferences; favorite CRUD is saved explicitly.
- Deafen blocks local receiving/playback and transmitting. It does not request the
  server to stop sending UDP packets. Permission/codec errors use numeric allowlists;
  unknown errors show a generic protocol failure without untrusted server messages.
- Server identity falls back to a fingerprint of its authenticated protocol public key
  if the server does not return a stable UID. A server key rotation changes volume keys.
- Identity improvement stops at 24/30 seconds. A server requiring more must be configured
  differently or await a future controlled identity work extension; requirements are
  not bypassed. Resetting an identity deletes its private file and loses old UID permissions.

The protocol is reverse engineered and the inherited cryptographic implementation has
not received an independent security audit. Reconnection is bounded and never opens
PTT automatically. Do not treat the alpha artifact as having completed release acceptance.
