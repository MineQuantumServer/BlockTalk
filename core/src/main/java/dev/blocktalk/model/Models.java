package dev.blocktalk.model;

import java.util.List;

public final class Models {
    private Models() {}
    public enum State { DISCONNECTED, PREPARING_IDENTITY, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, FAILED }
    public enum Codec {
        OPUS_VOICE(4,1), OPUS_MUSIC(5,2), UNSUPPORTED(-1,0);
        public final int id, channels;
        Codec(int id,int channels) { this.id=id; this.channels=channels; }
        public static Codec of(int id) { return id==4?OPUS_VOICE:id==5?OPUS_MUSIC:UNSUPPORTED; }
    }
    public record Channel(int id,int parent,int order,String name,boolean password,Codec codec,int neededTalkPower) {}
    public record User(int id,int channel,String uid,String name,boolean muted,boolean deafened,boolean speaking,int talkPower,boolean talker) {}
    public record View(State state,String server,String uid,int selfId,int channelId,List<Channel> channels,List<User> users,String error,boolean audioReady,boolean transmitting,boolean muted,boolean deafened,String gateReason,float level) {
        public View { channels=List.copyOf(channels); users=List.copyOf(users); }
        public static View empty() { return new View(State.DISCONNECTED,"","",-1,-1,List.of(),List.of(),"",false,false,false,false,"disconnected",0); }
    }
    /** Passwords deliberately have no place in the persistent server model. */
    public record Server(String name,String address,String nickname,String defaultChannel) {
        public Server { name=clean(name,80); address=clean(address,253); nickname=clean(nickname,30); defaultChannel=clean(defaultChannel,120); }
    }
    public static String clean(String s,int max) {
        if(s==null) return "";
        return s.replaceAll("[\\p{Cntrl}§]", "").codePoints().limit(max).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
    }
}
