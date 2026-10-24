package dev.blocktalk.protocol;

import com.github.manevolent.ts3j.command.CommandException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/** Only allowlisted error codes cross the diagnostics boundary. Never raw messages/causes. */
public enum Failure {
    PASSWORD, UNREACHABLE, IDENTITY, PERMISSION, CHANNEL_FULL, ALREADY_IN_CHANNEL, INVALID_CHANNEL, NICKNAME, KICKED, BANNED, AUDIO, NATIVE, PROTOCOL, CANCELLED;
    public boolean retryable() { return this==UNREACHABLE; }
    public static Failure from(Throwable t) {
        for(int depth=0;t!=null && depth<12;depth++,t=t.getCause()) {
            if(t instanceof CommandException c) return code(c.getErrorId());
            if(t instanceof UnknownHostException || t instanceof TimeoutException) return UNREACHABLE;
            if(t instanceof java.net.SocketException) return UNREACHABLE;
        }
        return PROTOCOL;
    }
    public static Failure code(int id) {
        return switch(id) {
            case 0x208,0x30d,0x404 -> PASSWORD;
            case 0x207 -> IDENTITY;
            case 0xa08,0xa09,0xa0a,0xa0b,0xa0c -> PERMISSION;
            case 0x309,0x30a,0x403 -> CHANNEL_FULL;
            case 0x302 -> ALREADY_IN_CHANNEL;
            case 0x300 -> INVALID_CHANNEL;
            case 0x201 -> NICKNAME;
            case 0xd01 -> BANNED;
            default -> PROTOCOL;
        };
    }
}
