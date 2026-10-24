package dev.blocktalk.config;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static java.nio.file.StandardCopyOption.*;

public final class ConfigStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path dir;
    private volatile boolean recovered;
    public ConfigStore(Path dir) { this.dir=dir; }
    public Path directory() { return dir; }
    public boolean recovered() { return recovered; }
    public synchronized Settings load() throws IOException {
        Files.createDirectories(dir); Path file=dir.resolve("settings.json");
        if(!Files.exists(file)) return new Settings();
        try {
            if(Files.size(file)>1024*1024) throw new IllegalArgumentException("size");
            JsonObject object=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!object.has("schemaVersion")) object.addProperty("schemaVersion",1);
            if(object.get("schemaVersion").getAsInt()>2) throw new IOException("Unsupported settings schema; file preserved");
            Settings s=JSON.fromJson(object,Settings.class); s.validate(); return s;
        } catch(JsonParseException | IllegalArgumentException | IllegalStateException ex) {
            Files.move(file,dir.resolve("settings.corrupt-"+System.currentTimeMillis()+".json"));
            recovered=true; return new Settings();
        }
    }
    public synchronized void save(Settings s) throws IOException { s.validate(); atomic(dir.resolve("settings.json"),JSON.toJson(s).getBytes(StandardCharsets.UTF_8)); }
    public static Settings copy(Settings s) { return JSON.fromJson(JSON.toJson(s),Settings.class); }
    public static void atomic(Path file,byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent()); Path tmp=Files.createTempFile(file.getParent(),".blocktalk-",".tmp");
        try {
            try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.WRITE)) { ByteBuffer b=ByteBuffer.wrap(bytes); while(b.hasRemaining()) ch.write(b); ch.force(true); }
            try { Files.move(tmp,file,ATOMIC_MOVE,REPLACE_EXISTING); } catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
