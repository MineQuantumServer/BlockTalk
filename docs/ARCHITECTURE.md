# BlockTalk architecture

This is a physical-client-only NeoForge 21.1.264 mod for Minecraft 1.21.1 / Java 21.
There is no Minecraft payload registration, server-side mod, ClientQuery connection,
official client dependency, proxy, web UI or helper process. The standalone tools are
developer entry points into the same production classes; they are not needed at runtime.

## Modules and ownership

- `src/main/java/dev/blocktalk/client`: `@Mod(dist=CLIENT)`, native key mappings,
  Screens, HUD, menu/config extension registration and three small input/focus mixins.
- `core/.../protocol`: `VoiceSession` boundary and the real UDP `Ts3Session` adapter.
  Library classes never enter Screen implementations. Initial notifications and later
  deltas merge on a single bounded event executor. A barrier drains initial events.
  Empty TS wire properties normalize to empty strings; omitted properties preserve
  their preceding values. Only server-authorized visibility is retained.
- `core/.../model`: immutable connection/channel/user views; UI obtains copies.
- `core/.../input`: synchronized `PttGate`, the only microphone send authority.
- `core/.../audio`: replaceable backend interface, Java Sound devices, linear 44.1/48 kHz
  conversion, Opus instances, per-speaker jitter buffers, stereo mixing and hard clipping.
- `core/.../config`: validated JSON, schema migration, corrupt-file recovery and
  separate private identity storage.
- `third_party/ts3j`: fixed, patched upstream implementation, not a Query voice bot.
- `third_party/opus`: original minimal JNA binding and audited Windows x86_64 Opus DLL.

## Threads and cancellation

Minecraft only handles input and renders immutable views. The control worker performs
DNS, identity computation, connection/authentication, channel commands, config writes
and device opening. It has a 64-task bound. Cleanup has its own worker so a blocked
connection does not prevent cancellation. A 100 ms watchdog checks connection/gate
state and emits required end markers. ts3j's network reader/handler are distinct from
the bounded 4096-event executor. Audio capture and playback each own a daemon thread.

Audio uses 960-sample 20 ms frames at 48 kHz; Opus Voice is mono/VOIP at 24 kbps,
Opus Music is stereo/audio at 64 kbps (mono microphone duplicated to stereo).
Encoded payload is capped at 400 bytes to fit ts3j's 500-byte datagrams. No encoded
microphone queue exists. Each capture tags its gate/session epoch before the blocking
read; the final UDP path rechecks that epoch. Changes discard old captured/encoded data.

The playback thread is independent of ticks, FPS and game pause. Each of at most 64
speakers has a decoder and a maximum 12-packet reorder buffer. Configured initial
delay is 1–8 frames; late audio is discarded, PLC is bounded to three missing frames,
idle/end streams are destroyed. New voice sessions, departures, channel changes and
reconnections destroy old decoder state. Gains and local mute are applied before
mixing, then master gain and saturation limit the result. FEC is disabled.

## Privacy invariant

Send eligibility requires connected + ready input + supported channel codec + talk
power + new PTT press + focused allowed screen + neither mute nor deafen. Resetting
the gate invalidates old tokens and preserves the held state, requiring release and
another press. Deafen never overwrites manual mic mute. Raw key/mouse release and
window-focus callbacks close the gate; server/channel/device/lifecycle operations
also reset it. Text-entry screens and unknown mod screens are blocked by an allowlist.
The title screen never permits network transmission, including when connection retention
is enabled. Local monitoring uses a separate branch that cannot call the send function.

## Persistence and packaging

Favorites and preferences contain no password field. Identity data uses a separate
owner-only file (Windows ACL / POSIX 0600) and atomic replacement. Unreadable identities
are preserved and fail closed; they are never silently regenerated. Settings use
schemaVersion 2, accept/migrate version 1 and back up malformed files. Future schemas
are preserved. Writes occur on Save/explicit favorite operations, not slider rendering.

Identity security improvement uses one background CPU, at most 30 seconds, target <=24,
and cancellation, saving the best offset. Server/user volume keys use server UID or
server-public-key fingerprint plus the remote client's UID; temporary IDs are never
persistent keys. Private material and addresses are absent from allowlisted diagnostics.

The release JAR embeds core, ts3j, Opus binding/native, BC and dnsjava with
NeoForge Jar-in-Jar. Gson/JNA/SLF4J/LWJGL are supplied by Minecraft. No JNI relocation,
runtime executable download, telemetry, recording or automatic connection is used.
