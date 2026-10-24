package dev.blocktalk.config;

import com.github.manevolent.ts3j.identity.LocalIdentity;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

public final class IdentityStore {
    private final Path file;
    public IdentityStore(Path dir) { file=dir.resolve("identity/private.identity"); }
    public synchronized LocalIdentity loadOrCreate() throws Exception {
        if(Files.exists(file)) {
            if(Files.size(file)>4096) throw new java.io.IOException("Identity file invalid; manual recovery required");
            String[] s=Files.readString(file).split("\\R");
            if(s.length<3||!s[0].equals("BlockTalk identity v1")) throw new java.io.IOException("Identity file invalid; manual recovery required");
            LocalIdentity i=LocalIdentity.load(new BigInteger(s[1],16)); i.setKeyOffset(Long.parseLong(s[2]));
            if(s.length>3) i.setLastCheckedKeyOffset(Long.parseLong(s[3])); return i;
        }
        LocalIdentity i=LocalIdentity.generateNew(8); save(i); return i;
    }
    public synchronized void save(LocalIdentity i) throws Exception {
        Files.createDirectories(file.getParent()); restrict(file.getParent(),true);
        // Create a restrictive temporary file before writing any private bytes.
        Path tmp=Files.createTempFile(file.getParent(),"identity-",".tmp");
        try {
            restrict(tmp,false);
            Files.writeString(tmp,"BlockTalk identity v1\n"+i.getPrivateKey().toString(16)+"\n"+i.getKeyOffset()+"\n"+i.getLastCheckedKeyOffset()+"\n",StandardCharsets.US_ASCII);
            try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            restrict(file,false);
        } finally { Files.deleteIfExists(tmp); }
    }
    private static void restrict(Path p,boolean directory) throws Exception {
        if(Files.getFileAttributeView(p,PosixFileAttributeView.class)!=null) Files.setPosixFilePermissions(p,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        else {
            AclFileAttributeView acl=Files.getFileAttributeView(p,AclFileAttributeView.class);
            if(acl==null) throw new java.io.IOException("Private identity permissions unavailable");
            var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(p)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            acl.setAcl(List.of(entry));
        }
    }
    public synchronized void reset() throws Exception { Files.deleteIfExists(file); }
    /** One background CPU, bounded duration/target, explicit cancellation. Keep best offset. */
    public static int improve(LocalIdentity id,int target,BooleanSupplier cancelled,IntConsumer progress) throws Exception {
        if(target<8||target>24) throw new IllegalArgumentException("security target 8..24");
        MessageDigest sha=MessageDigest.getInstance("SHA-1");
        int best=id.getSecurityLevel(); long offset=Math.max(id.getKeyOffset(),id.getLastCheckedKeyOffset());
        long deadline=System.nanoTime()+30_000_000_000L;
        while(best<target && !cancelled.getAsBoolean() && System.nanoTime()<deadline) {
            byte[] hash=sha.digest((id.getPublicKeyString()+offset).getBytes(StandardCharsets.US_ASCII));
            int n=0; for(byte b:hash) { if(b==0) n+=8; else { n+=Integer.numberOfTrailingZeros(b&255); break; } }
            if(n>best) { best=n; id.setKeyOffset(offset); progress.accept(best); }
            offset++; if((offset&8191)==0) Thread.sleep(1);
        }
        id.setLastCheckedKeyOffset(offset); return best;
    }
}
