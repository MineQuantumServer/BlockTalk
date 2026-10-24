# Opus binary provenance

Original codec source: https://downloads.xiph.org/releases/opus/opus-1.6.1.tar.gz

Version: 1.6.1, source archive retained in this directory.

Source SHA256: `6ffcb593207be92584df15b32466ed64bbec99109f007c82205f0194572411a1`

Windows x86_64 DLL SHA256: `f24700de54c47a9af248665d3d2de3e13386887cdab0582c5d3d909af9c4d295`

Compiler: Zig 0.14.1 (Clang 19.1.7), Ninja 1.11.1.4, CMake 3.31.6.
Build: Release, shared library, optional DRED/OSCE/Deep PLC disabled. C sources are
unmodified. Rebuild instructions and exact flags are in `../../tools/rebuild_opus.py`.
The loader verifies the audited DLL hash before loading. A different build must have
its resulting hash reviewed and explicitly updated; ordinary builds do not rebuild it.

`LICENSE` reproduces Opus's BSD-3-Clause / patent grant. The Java binding and owner-only
resource loader in `src/main/java/dev/blocktalk/opus` are original BlockTalk code under
the project's GPL-3.0-or-later. They declare the small public C ABI from the official
Opus headers. No candidate opus4j or opus-java code or native binary is included.
