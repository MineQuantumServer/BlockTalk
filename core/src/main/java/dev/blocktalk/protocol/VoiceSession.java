package dev.blocktalk.protocol;

import dev.blocktalk.model.Models.*;
import dev.blocktalk.input.PttGate;
import java.util.List;
import java.util.function.Consumer;

public interface VoiceSession extends AutoCloseable {
    record Voice(int client,int sequence,Codec codec,byte[] data,Byte start,long receivedNanos) {}
    record Snapshot(int self,int channel,String serverUid,List<Channel> channels,List<User> users) {}
    void connect(Server server,String password) throws Exception;
    void join(int channel,String password) throws Exception;
    Snapshot snapshot();
    default boolean maySpeak() {
        var s=snapshot();var channel=s.channels().stream().filter(c->c.id()==s.channel()).findFirst();var self=s.users().stream().filter(u->u.id()==s.self()).findFirst();
        return channel.isPresent()&&self.isPresent()&&(self.get().talker()||self.get().talkPower()>=channel.get().neededTalkPower());
    }
    void voiceHandler(Consumer<Voice> handler);
    void changed(Runnable handler);
    void failureHandler(Consumer<Failure> handler);
    default void bindGate(PttGate gate) { }
    boolean send(byte[] data,Codec codec,PttGate gate,long token) throws Exception;
    void stopTalking();
    void setMuted(boolean muted,boolean deafened) throws Exception;
    @Override void close();
}
