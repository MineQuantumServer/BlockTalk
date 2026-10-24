# Dependency audit and reproducible inputs

Checked 2026-10-24. All production versions are fixed. No JitPack branch, SNAPSHOT,
`latest`, runtime executable download or opaque official-client component is used.
The source tree began empty; the Gradle Wrapper came from the official MDK.

## Build baseline

| Input | Fixed version/source | Evidence |
|---|---|---|
| Java | 21 language/toolchain; exercised Dragonwell 21.0.11.0.11+10, Windows amd64 | Actual compiler/JVM invocation |
| Minecraft | 1.21.1 | Official version manifest resolved by NeoForm Runtime |
| NeoForge | 21.1.264 | Actual resolved POM, sources and runtime; metadata limits Minecraft to 1.21.1 |
| ModDevGradle | 2.0.148 | Official MDK build at `f7002b1b6011723c7b12fde95147689491a6ddab` |
| Gradle | 9.2.1 | Wrapper distribution SHA256 `72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f` |
| MDK | [official repository](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/f7002b1b6011723c7b12fde95147689491a6ddab) | `licenses/MDK-TEMPLATE.txt`; only wrapper/template baseline retained |

Minecraft/NeoForge/build tools are development/runtime prerequisites, not redistributed
inside the MOD. Gradle lockfiles record resolved dependency graphs. SHA256 manifests
for vendored sources/native binaries and patch diffs accompany the subprojects.

## Production runtime graph

| Component | Acquisition and fixed source | License and packaging |
|---|---|---|
| ts3j | Local source subproject, [db57d60c989e399626aa16d921390f5033e6cdeb](https://github.com/Manevolent/ts3j/tree/db57d60c989e399626aa16d921390f5033e6cdeb), upstream POM 1.0.3 | Apache-2.0 root; embedded API/event MIT headers; QuickLZ Java port explicitly GPL 1/2/3. This combined distribution chooses GPL-3.0-or-later. Original sources/notices retained. Local version `1.0.3-blocktalk.1`, JIJ |
| BC provider | Maven Central `org.bouncycastle:bcprov-jdk18on:1.86` | [MIT-style BC license](https://github.com/bcgit/bc-java/blob/r1rv86/LICENSE.html), local copy in `licenses/BOUNCY-CASTLE.html`, JIJ, no transitive runtime libraries |
| dnsjava | Maven Central `dnsjava:dnsjava:3.6.3` | [BSD-3-Clause](https://github.com/dnsjava/dnsjava/blob/v3.6.3/LICENSE), local copy; JIJ. Its SLF4J API is resolved to Minecraft's 2.0.9 |
| Opus | Official [Opus 1.6.1 source](https://opus-codec.org/downloads/) archive, included in `third_party/opus/opus-1.6.1.tar.gz` | BSD-3-Clause and patent grant in `licenses/OPUS-BSD-3-CLAUSE.txt`; locally built DLL plus original minimal JNA binding, JIJ |
| JNA | `net.java.dev.jna:jna:5.14.0` | Apache-2.0 / LGPL-2.1-or-later choice, [upstream license](https://github.com/java-native-access/jna/blob/5.14.0/LICENSE); supplied by Minecraft, not bundled. No runtime transitive dependencies |
| Gson | `com.google.code.gson:gson:2.10.1` | Apache-2.0; supplied by Minecraft, not bundled; no runtime transitive dependencies |
| SLF4J | `org.slf4j:slf4j-api:2.0.9` | MIT; Minecraft supplies API/provider, neither is bundled |

The complete standalone graph is `core` -> patched ts3j -> BC/dnsjava,
and `core` -> local Opus binding -> JNA, plus Gson/SLF4J. The standalone integration
task adds `slf4j-nop:2.0.9` only in its own configuration; it never enters the game
runtime. There are no other MOD runtime transitive dependencies. Minecraft retains
its own LWJGL/OpenAL/logging libraries without copying or relocation.

Testing: JUnit 4.13.2 for preserved upstream tests; JUnit BOM/Jupiter 5.11.4,
Platform 1.11.4, OpenTest4J 1.3.0 and API Guardian 1.1.2 for BlockTalk tests.
These do not ship. GitHub Action revisions were resolved with `git ls-remote` and
are pinned by full commit in the workflow.

## Rejected candidates

- [henkelmax/opus4j](https://github.com/henkelmax/opus4j/tree/362db14553d39c4dc080e4edd50630b17dbf9682),
  commit `362db14553d39c4dc080e4edd50630b17dbf9682`, README 2.1.0. Its build depends
  on `de.maxhenkel.native-utils` and JNI binaries; the inspected repository included
  `OPUS_LICENSE` but no explicit license for its Java/JNI wrapper. Opus's license
  does not authorize that separate code. No candidate code/binary is distributed.
- [opus-java](https://github.com/discord-java/opus-java/tree/e3a62858f34999d70c915eb0fb6fa8314d3fa283),
  1.1.1 / commit `e3a62858f34999d70c915eb0fb6fa8314d3fa283`, was evaluated as an
  alternative. Its root grant is Apache-2.0, but its binding credits an older project
  without a repository license. Its Windows binary reported libopus 1.1, not a current
  codec. Initial exploratory tests used it; it was completely removed before delivery.
  BlockTalk instead declares only the small official C ABI surface it actually uses
  and builds a current Opus library from source. No wrapper redistribution uncertainty
  is carried into the Opus subproject.

## Native provenance

Opus source SHA256:
`6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1`.
Windows x86_64 DLL SHA256:
`f24700de54c47a9af248665d3d2de3e13386887cdab0582c5d3d909af9c4d295`.
The loader verifies this exact resource hash and extracts into an owner-only
temporary directory. The DLL and binding share one nested JAR so named-module
resource lookup does not cross an unrelated resource-only module.

Built using Zig 0.14.1 Windows x86_64 (archive SHA256
`554f5378228923ffd558eac35e21af020c73789d87afeabf4bfd16f2e6feed2c`),
CMake 3.31.6 and Ninja 1.11.1.4. See `tools/rebuild_opus.py`. Optional neural DRED,
OSCE and Deep PLC are disabled. Normal Gradle builds consume the reviewed vendored
DLL; rebuilding it is a separate explicit development operation. No toolchain is
required by end users. No runtime native download occurs. Other architectures are
rejected before load; voices disable while settings remain available.

## Protocol and patch validation

Signatures checked in upstream source: `LocalTeamspeakClientSocket.connect`,
`addListener`, `setVoiceHandler`, `joinChannel`, `getClientInfo`, `getChannelInfo`,
`executeCommand`; `PacketBody0Voice` codec, sequence and empty end-marker layout;
`LocalIdentity` generation/key offset/public UID; listener event maps. The original
identity's INI obfuscation is not called encryption and is not used as private storage.

Patches in `third_party/ts3j/PATCHES.diff`:

1. Null-safe cancellation and closing network/socket/event/microphone resources.
2. Correct unresolved-address handling; standard reflection replaces the only
   commons-lang 2.6 usage, eliminating that obsolete dependency.
3. Bound command waits; raw packet/exception diagnostics disabled.
4. Preserve numeric authentication errors instead of leaking arbitrary server text.
5. Store the server's public-key fingerprint as a stable preference-key fallback.
6. Use an EAX instance per cryptographic operation. BC 1.86 rejects repeated nonce/key
   initialization on a shared instance, including TS3's fixed initial transformation.
   This incompatibility was actually observed on the test server. The patch preserves
   the protocol's parameters/wire encryption rather than downgrading BC or changing
   algorithms. Known-answer upstream crypto tests and live authentication pass.
7. Replace the sole ini4j usage with a bounded reader for the identity field only.
   OSV's 2026-10-24 query reported CVE-2022-41404 / GHSA-jr6h-r7vg-f9mc for 0.5.4;
   [the reviewed advisory](https://github.com/advisories/GHSA-jr6h-r7vg-f9mc) has no
   patched release. There is no recursive INI interpolation in the replacement.
   The dependency is removed, and preserved identity import/export tests still run.

An OSV batch query on the same date returned no records for the pinned BC/dnsjava,
Minecraft-provided JNA/Gson/SLF4J versions. This is a point-in-time database result,
not a guarantee of security. The original upstream POM is retained only as provenance;
it is not used to resolve the production dependency graph.

Upstream BC 1.67 and dnsjava 2.1.8 were not retained. BC's [release/advisory pages](https://www.bouncycastle.org/download/bouncy-castle-java/)
were checked; 1.86 includes current hardening. This is not a security audit or a
claim that an implementation of a reverse-engineered protocol is risk-free. The
vendored NaCl implementation remains covered by the ts3j repository's Apache grant
and retained source notices; it is not replaced by an unverified algorithm rewrite.

Actual Java 21 checks include compiler execution, upstream cryptographic vectors,
identity persistence, live authentication/channel changes, Windows native load,
mono/stereo Opus encode/decode/PLC, and real-microphone UDP routing in both directions
between BlockTalk sessions. A later official-peer human listening test confirmed
both directions through the same standalone production core, with an unexplained
first-burst severe-noise report followed by a normal retry. No claim of general
official-version compatibility or sustained audio quality follows from that test;
see `OFFICIAL_INTEROP.md`.
