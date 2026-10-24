package dev.blocktalk;

import dev.blocktalk.config.*;
import dev.blocktalk.protocol.ServerAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path dir;
    @Test void addressForms() {assertEquals(9987,ServerAddress.parse("example.org").port());assertEquals(12345,ServerAddress.parse("example.org:12345").port());assertEquals("::1",ServerAddress.parse("[::1]:9987").host());assertEquals("127.0.0.1",ServerAddress.parse("127.0.0.1").host());for(String invalid:new String[]{"::1","a:0","a:65536","a:-1","[::1]oops","a:x","https://a",""})assertThrows(IllegalArgumentException.class,()->ServerAddress.parse(invalid));}
    @Test void migrationAndAtomicSave() throws Exception {Files.writeString(dir.resolve("settings.json"),"{\"schemaVersion\":1,\"nickname\":\"Old\",\"outputGain\":99}");var store=new ConfigStore(dir);Settings s=store.load();assertEquals(2,s.schemaVersion);assertEquals(2,s.outputGain);store.save(s);assertEquals("Old",store.load().nickname);try(var list=Files.list(dir)){assertEquals(1,list.count());}}
    @Test void corruptBackedUp() throws Exception {Files.writeString(dir.resolve("settings.json"),"{not json");var store=new ConfigStore(dir);assertEquals("BlockTalk",store.load().nickname);assertTrue(store.recovered());try(var list=Files.list(dir)){assertTrue(list.anyMatch(p->p.getFileName().toString().startsWith("settings.corrupt-")));}}
    @Test void futureSchemaPreserved() throws Exception {Path p=dir.resolve("settings.json");Files.writeString(p,"{\"schemaVersion\":99}");assertThrows(java.io.IOException.class,()->new ConfigStore(dir).load());assertTrue(Files.exists(p));}
    @Test void identityPersistenceAndCancellation() throws Exception {var store=new IdentityStore(dir);var i=store.loadOrCreate();assertEquals(i.getUid().toBase64(),store.loadOrCreate().getUid().toBase64());int before=i.getSecurityLevel();assertEquals(before,IdentityStore.improve(i,24,()->true,n->fail()));}
}
