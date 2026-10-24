package dev.blocktalk.opus;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.*;

final class NativeLoader {
    private static NativeOpus instance;
    static synchronized NativeOpus get() {
        if(instance!=null)return instance;
        if(!Platform.isWindows()||!Platform.is64Bit()||!System.getProperty("os.arch").equalsIgnoreCase("amd64"))throw new UnsupportedOperationException("BlockTalk native audio supports Windows x86_64 only");
        try {
            byte[] bytes;
            try(var stream=NativeLoader.class.getResourceAsStream("/natives/win32-x86-64/libopus.dll")) {
                if(stream==null)throw new java.io.IOException("Opus resource missing");bytes=stream.readAllBytes();
            }
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!hash.equals("f24700de54c47a9af248665d3d2de3e13386887cdab0582c5d3d909af9c4d295"))throw new SecurityException("Opus resource checksum mismatch");
            Path dir=Files.createTempDirectory("blocktalk-opus-");restrict(dir);dir.toFile().deleteOnExit();
            Path file=dir.resolve("libopus.dll");Files.createFile(file);restrict(file);Files.write(file,bytes);file.toFile().deleteOnExit();
            instance=Native.load(file.toAbsolutePath().toString(),NativeOpus.class);return instance;
        }catch(java.io.IOException|java.security.GeneralSecurityException e){throw new IllegalStateException("Opus extraction failed",e);}
    }
    private static void restrict(Path p) throws java.io.IOException {
        var acl=Files.getFileAttributeView(p,AclFileAttributeView.class);
        if(acl!=null)acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(p)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        else if(Files.getFileAttributeView(p,PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(p,PosixFilePermissions.fromString(Files.isDirectory(p)?"rwx------":"rw-------"));
        else throw new java.io.IOException("Native extraction permissions unsupported");
    }
}
