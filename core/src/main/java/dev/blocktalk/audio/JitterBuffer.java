package dev.blocktalk.audio;

import dev.blocktalk.protocol.VoiceSession.Voice;
import java.util.HashMap;
import java.util.Map;

/** 16-bit sequence arithmetic, bounded delay and PLC. No tick/frame-rate dependency. */
public final class JitterBuffer {
    public record Frame(byte[] data,boolean end) {}
    private final Map<Integer,Voice> packets=new HashMap<>();
    private int expected,missing;
    private boolean active;
    private long readyAt,lastPacket;
    private Byte session;
    private final int frames;
    public JitterBuffer(int frames) { this.frames=Math.clamp(frames,1,8); }
    public synchronized boolean offer(Voice v) {
        if(v.data().length>1275) return false;
        if(active&&distance(v.sequence(),expected)<0) return false;
        boolean reset=!active || (v.start()!=null && session!=null && !v.start().equals(session));
        if(reset) {
            packets.clear();expected=v.sequence();readyAt=v.receivedNanos()+frames*20_000_000L;missing=0;active=true;
        }
        if(v.start()!=null) session=v.start();
        int delta=distance(v.sequence(),expected);
        if(delta<0) return false;
        if(delta>12 || packets.size()>=12) { packets.clear();expected=v.sequence();readyAt=v.receivedNanos()+20_000_000L; }
        packets.putIfAbsent(v.sequence(),v);lastPacket=v.receivedNanos();
        return reset;
    }
    public synchronized Frame poll(long now) {
        if(!active||now<readyAt) return null;
        if(now-lastPacket>300_000_000L) { clear();return new Frame(null,true); }
        Voice v=packets.remove(expected);expected=(expected+1)&65535;
        if(v==null) {
            if(++missing>3) { clear();return new Frame(null,true); }
            return new Frame(null,false);
        }
        missing=0;
        if(v.data().length==0) { clear();return new Frame(null,true); }
        if(now-v.receivedNanos()>240_000_000L) return new Frame(null,false);
        return new Frame(v.data(),false);
    }
    public static int distance(int a,int b) { return (short)((a-b)&65535); }
    public synchronized void clear() {packets.clear();active=false;session=null;}
    public synchronized int size() { return packets.size(); }
}
