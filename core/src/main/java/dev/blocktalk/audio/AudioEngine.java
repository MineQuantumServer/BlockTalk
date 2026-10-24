package dev.blocktalk.audio;

import dev.blocktalk.model.Models.*;
import dev.blocktalk.protocol.VoiceSession;
import dev.blocktalk.config.Settings;
import dev.blocktalk.input.PttGate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public final class AudioEngine implements AutoCloseable {
    private final AudioBackend backend;
    private final PttGate gate;
    private final VoiceSession session;
    private final Supplier<Codec> codec;
    private final Consumer<String> error;
    private volatile Settings settings;
    private volatile boolean running,ready,localTest;
    private volatile AudioBackend.Input input;
    private volatile AudioBackend.Output output;
    private Thread capture,playback;
    private final Map<Integer,Stream> streams=new HashMap<>();
    private final Map<Integer,Float> volumes=new ConcurrentHashMap<>();
    private final Set<Integer> muted=ConcurrentHashMap.newKeySet();
    private final AtomicLong generation=new AtomicLong();
    private final AtomicLong receivedPackets=new AtomicLong(),decodedFrames=new AtomicLong(),concealedFrames=new AtomicLong(),playedVoiceFrames=new AtomicLong(),sentPackets=new AtomicLong();
    public record Statistics(long receivedPackets,long decodedFrames,long concealedFrames,long playedVoiceFrames,long sentPackets) {}
    /** Aggregate counters only: no audio, identity, server address or user text is retained. */
    public Statistics statistics() {return new Statistics(receivedPackets.get(),decodedFrames.get(),concealedFrames.get(),playedVoiceFrames.get(),sentPackets.get());}
    private volatile long lastSent;
    private volatile float level;
    public AudioEngine(AudioBackend backend,PttGate gate,VoiceSession session,Supplier<Codec> codec,Settings settings,Consumer<String> error) {
        this.backend=backend;this.gate=gate;this.session=session;this.codec=codec;this.settings=settings;this.error=error;
        if(session!=null)session.bindGate(gate);
    }
    public void start(boolean localTest) throws Exception {
        this.localTest=localTest;
        OpusCodec.initialize();running=true;
        // Opening is performed by the controller/diagnostics worker, never a rendering callback.
        try { output=backend.openOutput(settings.outputDevice); input=backend.openInput(settings.inputDevice); }
        catch(Exception e) {close();throw e;}
        ready=true;
        capture=thread("BlockTalk-capture",this::capture); playback=thread("BlockTalk-playback",this::play);
        capture.start();playback.start();
    }
    private static Thread thread(String name,Runnable task) { Thread t=new Thread(task,name);t.setDaemon(true);return t; }
    public boolean ready() {return ready;}
    public float level() {return level;}
    public boolean transmitting() {return gate.open() && System.nanoTime()-lastSent<100_000_000L;}
    public void settings(Settings s) {settings=s;}
    public void volume(int client,float volume) {volumes.put(client,Math.clamp(volume,0,2));}
    public void mute(int client,boolean value) {if(value) muted.add(client);else muted.remove(client);}
    public boolean muted(int client) {return muted.contains(client);}
    public void receive(VoiceSession.Voice v) {
        if(!running||localTest||gate.deafened()||v.codec()==Codec.UNSUPPORTED||muted.contains(v.client())) return;
        receivedPackets.incrementAndGet();
        synchronized(streams) {
            if(streams.size()>=64 && !streams.containsKey(v.client())) return;
            Stream s=streams.get(v.client());
            if(s!=null&&s.codec!=v.codec()) {s.close();streams.remove(v.client());s=null;}
            if(s==null) {s=new Stream(v.codec(),settings.jitterFrames);streams.put(v.client(),s);}
            boolean reset=s.buffer.offer(v);
            if(reset&&s.decoder!=null){s.decoder.close();s.decoder=null;}
            s.last=System.nanoTime();
        }
    }
    public void retain(Set<Integer> visible) { synchronized(streams) { streams.entrySet().removeIf(e->{if(visible.contains(e.getKey())) return false;e.getValue().close();return true;});volumes.keySet().retainAll(visible);muted.retainAll(visible); } }
    public Set<Integer> speaking() {
        synchronized(streams) { Set<Integer> ids=new HashSet<>();long now=System.nanoTime();streams.forEach((id,s)->{if(now-s.last<200_000_000L) ids.add(id);});return ids; }
    }
    public void invalidate() {
        generation.incrementAndGet();lastSent=0;
        synchronized(streams) {streams.values().forEach(Stream::close);streams.clear();}
        AudioBackend.Output out=output;if(out!=null) out.flush();
    }
    private void capture() {
        OpusCodec encoder=null;Codec active=Codec.UNSUPPORTED;long oldToken=-1;
        byte[] bytes=new byte[1920];
        try {
            while(running) {
                long token=gate.token();long gen=generation.get();
                int read=input.read(bytes);if(!running) break;
                if(read!=bytes.length) throw new IllegalStateException("device");
                short[] pcm=new short[960];double sum=0;
                for(int i=0;i<960;i++) {short raw=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8));int sample=(int)(raw*settings.inputGain);pcm[i]=(short)Math.clamp(sample,-32768,32767);sum+=(double)sample*sample;}
                level=(float)Math.min(1,Math.sqrt(sum/960)/32768);
                if(localTest) {
                    // Local monitor path has no session reference and cannot call send.
                    short[] stereo=new short[1920];for(int i=0;i<960;i++) stereo[i*2]=stereo[i*2+1]=pcm[i];
                    synchronized(streams) {monitor=stereo;}
                    continue;
                }
                Codec c=codec.get();
                if(!gate.valid(token)||gen!=generation.get()||c==Codec.UNSUPPORTED) {session.stopTalking();oldToken=-1;continue;}
                if(encoder==null||active!=c||oldToken!=token) {if(encoder!=null) encoder.close();encoder=new OpusCodec(c,true);active=c;oldToken=token;}
                byte[] packet=encoder.encode(pcm);
                if(gen==generation.get() && session.send(packet,c,gate,token)) {lastSent=System.nanoTime();sentPackets.incrementAndGet();}
            }
        } catch(Throwable e) { if(running) failed(); }
        finally {if(encoder!=null) encoder.close();ready=false;}
    }
    private short[] monitor;
    private void play() {
        long next=System.nanoTime();
        try {
            while(running) {
                long now=System.nanoTime();
                if(now<next) {TimeUnit.NANOSECONDS.sleep(next-now);continue;}
                if(now-next>80_000_000L) {output.flush();next=now;}
                next+=20_000_000L;
                float[] mix=new float[1920];
                boolean hasVoice=false;
                synchronized(streams) {
                    if(localTest&&monitor!=null) {for(int i=0;i<mix.length;i++) mix[i]=monitor[i];monitor=null;}
                    Iterator<Map.Entry<Integer,Stream>> it=streams.entrySet().iterator();
                    while(it.hasNext()) {
                        var e=it.next();Stream s=e.getValue();
                        JitterBuffer.Frame f=s.buffer.poll(now);
                        if(f==null) {if(now-s.last>500_000_000L) {s.close();it.remove();}continue;}
                        if(f.end()||gate.deafened()) {s.close();it.remove();continue;}
                        if(s.decoder==null) s.decoder=new OpusCodec(s.codec,false);
                        short[] pcm;
                        try {pcm=s.decoder.decode(f.data());} catch(RuntimeException malformed) {s.close();it.remove();continue;}
                        if(f.data()==null)concealedFrames.incrementAndGet();else decodedFrames.incrementAndGet();
                        if(muted.contains(e.getKey())) continue;
                        hasVoice=true;
                        float volume=volumes.getOrDefault(e.getKey(),1f);
                        for(int i=0;i<Math.min(960,pcm.length/s.codec.channels);i++) for(int ch=0;ch<2;ch++) mix[i*2+ch]+=pcm[i*s.codec.channels+(s.codec.channels==1?0:ch)]*volume;
                    }
                }
                byte[] bytes=new byte[3840];
                for(int i=0;i<1920;i++) {int sample=Math.clamp((int)(mix[i]*settings.outputGain),-32768,32767);bytes[2*i]=(byte)sample;bytes[2*i+1]=(byte)(sample>>8);}
                output.write(bytes);
                if(hasVoice)playedVoiceFrames.incrementAndGet();
            }
        } catch(Throwable e) {if(running) failed();}
    }
    private void failed() {ready=false;gate.reset();running=false;error.accept("AUDIO");AudioBackend.Input in=input;if(in!=null)in.close();AudioBackend.Output out=output;if(out!=null)out.close();}
    @Override public void close() {
        gate.reset();running=false;ready=false;generation.incrementAndGet();
        AudioBackend.Input in=input;input=null;if(in!=null)in.close();AudioBackend.Output out=output;output=null;if(out!=null)out.close();
        if(capture!=null)capture.interrupt();if(playback!=null)playback.interrupt();
        join(capture);join(playback);invalidate();
    }
    private static void join(Thread t) {if(t!=null&&t!=Thread.currentThread())try{t.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static final class Stream implements AutoCloseable {
        final Codec codec;final JitterBuffer buffer;OpusCodec decoder;long last;
        Stream(Codec c,int frames) {codec=c;buffer=new JitterBuffer(frames);}
        public void close() {buffer.clear();if(decoder!=null) {decoder.close();decoder=null;}}
    }
}
