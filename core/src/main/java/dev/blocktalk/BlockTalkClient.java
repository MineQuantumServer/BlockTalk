package dev.blocktalk;

import dev.blocktalk.audio.*;
import dev.blocktalk.config.*;
import dev.blocktalk.input.PttGate;
import dev.blocktalk.model.Models.*;
import dev.blocktalk.protocol.*;
import com.github.manevolent.ts3j.identity.LocalIdentity;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Headless runtime shared by Minecraft and the integration tool. */
public final class BlockTalkClient implements AutoCloseable {
    private final ConfigStore store;
    private final IdentityStore identities;
    public final PttGate gate=new PttGate();
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r->daemon(r,"BlockTalk-control"),new ThreadPoolExecutor.AbortPolicy());
    private final ExecutorService cleanup=Executors.newSingleThreadExecutor(r->daemon(r,"BlockTalk-cleanup"));
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"BlockTalk-watchdog"));
    private final AtomicLong epoch=new AtomicLong();
    private volatile State state=State.DISCONNECTED;
    private volatile Settings settings=new Settings();
    private volatile VoiceSession session;
    private volatile AudioEngine audio;
    private volatile LocalIdentity identity;
    private volatile String error="",nativeStatus="not_checked";
    private volatile String audioFailureType="none";
    private volatile boolean closed,localTest,improving;
    private volatile int securityLevel,retries;
    private final java.util.function.Function<LocalIdentity,VoiceSession> factory;
    private final boolean audioEnabled;
    private Server lastServer;
    private String password;
    private int previousChannel=-1;
    private volatile boolean focused=true,uiAllowed;
    private static Thread daemon(Runnable r,String name) {Thread t=new Thread(r,name);t.setDaemon(true);return t;}
    public BlockTalkClient(Path config) {
        this(config,Ts3Session::new,true);
    }
    /** Dependency injection for lifecycle tests; production always uses Ts3Session. */
    public BlockTalkClient(Path config,java.util.function.Function<LocalIdentity,VoiceSession> factory,boolean audioEnabled) {
        this.factory=factory;this.audioEnabled=audioEnabled;
        store=new ConfigStore(config);identities=new IdentityStore(config);
        submit(()->{try{settings=store.load();if(store.recovered())error="CONFIG_RECOVERED";}catch(Exception e){error="CONFIG";}});
        timer.scheduleWithFixedDelay(this::watchdog,100,100,TimeUnit.MILLISECONDS);
    }
    private void submit(Runnable r) {if(!closed)try{worker.execute(r);}catch(RejectedExecutionException ignored){error="BUSY";}}
    public Settings settings() {return ConfigStore.copy(settings);}
    public String nativeStatus() {return nativeStatus;}
    public int securityLevel() {return securityLevel;}
    public boolean improving() {return improving;}
    public synchronized void connect(Server server,String secret) {
        if(closed || state==State.CONNECTING||state==State.PREPARING_IDENTITY||state==State.CONNECTED||state==State.DISCONNECTING) return;
        ServerAddress.parse(server.address());
        long token=epoch.incrementAndGet();gate.reset();state=State.PREPARING_IDENTITY;error="";
        lastServer=server;password=secret;retries=0;
        submit(()->attempt(token,false));
    }
    private void attempt(long token,boolean retry) {
        if(token!=epoch.get()||closed) return;
        release();
        VoiceSession fresh=null;
        try {
            if(identity==null) identity=identities.loadOrCreate();
            securityLevel=identity.getSecurityLevel();
            if(token!=epoch.get()||closed) return;
            fresh=factory.apply(identity);
            fresh.bindGate(gate);
            final VoiceSession active=fresh;
            synchronized(this) {if(token!=epoch.get()||closed){fresh.close();return;}session=fresh;state=retry?State.RECONNECTING:State.CONNECTING;}
            fresh.failureHandler(f->{if(token==epoch.get())submit(()->failed(token,active,f));});
            fresh.changed(()->{if(token==epoch.get())syncChannel(active);});
            fresh.voiceHandler(v->{AudioEngine a=audio;if(token==epoch.get()&&session==active&&a!=null) a.receive(v);});
            fresh.connect(lastServer,password);
            if(token!=epoch.get()||closed) {fresh.close();return;}
            synchronized(this) {if(token!=epoch.get())return;state=State.CONNECTED;previousChannel=-1;gate.reset();}
            syncChannel(fresh);
            try {
                if(!audioEnabled)return;
                nativeStatus=OpusCodec.initialize();
                AudioEngine a=new AudioEngine(new JavaSoundBackend(),gate,fresh,this::codec,settings,s->{gate.reset();error=s;});
                audio=a;a.start(false);
                if(token!=epoch.get()) {a.close();return;}
                applyVolumes();
            } catch(Throwable e) {gate.reset();error="AUDIO";audioFailureType=e.getClass().getSimpleName();nativeStatus=nativeStatus.equals("not_checked")?"unavailable":nativeStatus;}
            updateGate();
        } catch(Throwable e) {
            if(fresh!=null)fresh.close();
            Failure f=e instanceof Ts3Session.SessionException se?se.failure:Failure.from(e);
            if(token==epoch.get())failed(token,fresh,f);
        }
    }
    private synchronized void failed(long token,VoiceSession from,Failure failure) {
        if(token!=epoch.get() || (from!=null && session!=from)) return;
        gate.reset();error=failure.name();release();
        if(shouldRetry(failure,settings.reconnect,retries)) {
            state=State.RECONNECTING;int attempt=++retries;
            timer.schedule(()->submit(()->attempt(token,true)),1L<<Math.min(attempt-1,4),TimeUnit.SECONDS);
        } else {state=State.FAILED;password=null;}
    }
    public static boolean shouldRetry(Failure f,boolean enabled,int attempts) {return enabled && f.retryable() && attempts<5;}
    private void watchdog() {
        try {
            updateGate();VoiceSession s=session;
            if(state==State.CONNECTED && s instanceof Ts3Session ts && !ts.connected()) submit(()->failed(epoch.get(),s,Failure.UNREACHABLE));
            if(!gate.open() && s!=null) s.stopTalking();
        } catch(Throwable ignored) {gate.reset();}
    }
    private void syncChannel(VoiceSession source) {
        if(session!=source) return;
        var snap=source.snapshot();
        synchronized(this) {
            if(session!=source) return;
            if(previousChannel!=snap.channel()) {gate.reset();previousChannel=snap.channel();AudioEngine a=audio;if(a!=null)a.invalidate();}
        }
        AudioEngine a=audio;
        if(a!=null) {Set<Integer> visible=new HashSet<>();snap.users().stream().filter(u->u.channel()==snap.channel()).forEach(u->visible.add(u.id()));a.retain(visible);applyVolumes();}
        updateGate();
    }
    private Codec codec() {VoiceSession s=session;if(s==null)return Codec.UNSUPPORTED;var snap=s.snapshot();return snap.channels().stream().filter(c->c.id()==snap.channel()).map(Channel::codec).findFirst().orElse(Codec.UNSUPPORTED);}
    private void updateGate() {
        VoiceSession s=session;AudioEngine a=audio;
        boolean permitted=false;
        if(s!=null)permitted=s.maySpeak();
        gate.context(state==State.CONNECTED,a!=null&&a.ready()&&!localTest,codec()!=Codec.UNSUPPORTED,permitted,focused,uiAllowed);
    }
    public void input(boolean held,boolean focused,boolean allowed) {this.focused=focused;this.uiAllowed=allowed;updateGate();gate.key(held);}
    public void focusLost() {focused=false;gate.reset();updateGate();}
    public void suspend() {gate.reset();}
    public void mute() {gate.mute(!gate.muted());sendFlags();}
    public void deafen() {gate.deafen(!gate.deafened());submit(()->{AudioEngine a=audio;if(a!=null)a.invalidate();});sendFlags();}
    private void sendFlags() {submit(()->{VoiceSession s=session;if(s!=null)try{s.setMuted(gate.muted(),gate.deafened());}catch(Exception e){error=Failure.from(e).name();}});}
    public void join(int channel,String secret) {
        gate.reset();long token=epoch.get();
        submit(()->{VoiceSession s=session;if(s==null||state!=State.CONNECTED||token!=epoch.get())return;try{AudioEngine a=audio;if(a!=null)a.invalidate();s.join(channel,secret);syncChannel(s);}catch(Exception e){error=Failure.from(e).name();}});
    }
    public synchronized void disconnect() {
        if(closed||state==State.DISCONNECTING)return;
        epoch.incrementAndGet();gate.reset();password=null;state=State.DISCONNECTING;
        VoiceSession s=session;session=null;AudioEngine a=audio;audio=null;
        cleanup.execute(()->{if(a!=null)a.close();if(s!=null)s.close();if(state==State.DISCONNECTING)state=State.DISCONNECTED;});
    }
    private void release() {AudioEngine a=audio;audio=null;if(a!=null)a.close();VoiceSession s=session;session=null;if(s!=null)s.close();localTest=false;}
    public void save(Settings value) {
        Settings copy=ConfigStore.copy(value);copy.validate();gate.reset();
        submit(()->{
            try {
                boolean device=!settings.inputDevice.equals(copy.inputDevice)||!settings.outputDevice.equals(copy.outputDevice);
                store.save(copy);settings=copy;
                AudioEngine a=audio;
                if(state==State.CONNECTED&&(device||a==null||!a.ready())){if(a!=null)a.close();audio=null;if(session!=null){a=new AudioEngine(new JavaSoundBackend(),gate,session,this::codec,copy,s->error=s);audio=a;a.start(false);}}
                if(a!=null)a.settings(copy);
                applyVolumes();
            }catch(Throwable e){error="CONFIG_OR_AUDIO";}
        });
    }
    private void applyVolumes() {
        VoiceSession s=session;AudioEngine a=audio;if(s==null||a==null)return;
        var snap=s.snapshot();for(User u:snap.users())if(!u.uid().isBlank()&&!snap.serverUid().isBlank())a.volume(u.id(),settings.userVolumes.getOrDefault(snap.serverUid()+"|"+u.uid(),1f));
    }
    public boolean localMute(int id) {AudioEngine a=audio;return a!=null&&a.muted(id);}
    public void toggleLocalMute(int id) {AudioEngine a=audio;if(a!=null)a.mute(id,!a.muted(id));}
    public void userVolume(int id,float value) {
        AudioEngine a=audio;VoiceSession s=session;if(a==null||s==null)return;a.volume(id,value);
        var snap=s.snapshot();snap.users().stream().filter(u->u.id()==id&&!u.uid().isBlank()&&!snap.serverUid().isBlank()).findFirst().ifPresent(u->{Settings copy=settings();copy.userVolumes.put(snap.serverUid()+"|"+u.uid(),value);save(copy);});
    }
    public void testMicrophone(boolean on) {
        gate.reset();submit(()->{
            if(!on&&!localTest)return;
            if(state!=State.DISCONNECTED&&state!=State.FAILED) {error="DISCONNECT_FOR_TEST";return;}
            if(audio!=null){audio.close();audio=null;}localTest=false;
            if(on)try{AudioEngine a=new AudioEngine(new JavaSoundBackend(),gate,null,()->Codec.OPUS_VOICE,settings,s->error=s);audio=a;localTest=true;a.start(true);}catch(Throwable e){localTest=false;error="AUDIO";}
        });
    }
    public boolean localTest() {return localTest;}
    public void prepareIdentity() {submit(()->{try{if(identity==null)identity=identities.loadOrCreate();securityLevel=identity.getSecurityLevel();}catch(Exception e){error="IDENTITY";}});}
    public void improveIdentity() {
        if(improving)return;improving=true;
        submit(()->{try{if(identity==null)identity=identities.loadOrCreate();securityLevel=IdentityStore.improve(identity,Math.min(24,Math.max(12,securityLevel+1)),()->!improving||closed,n->securityLevel=n);identities.save(identity);}catch(Exception e){error="IDENTITY";}finally{improving=false;}});
    }
    public void cancelImprove() {improving=false;}
    public void resetIdentity() {improving=false;disconnect();submit(()->{try{identities.reset();identity=null;prepareIdentity();}catch(Exception e){error="IDENTITY";}});}
    public View view() {
        VoiceSession s=session;var snap=s==null?new VoiceSession.Snapshot(-1,-1,"",List.of(),List.of()):s.snapshot();AudioEngine a=audio;Set<Integer> speaking=a==null?Set.of():a.speaking();
        List<User> users=snap.users().stream().map(u->new User(u.id(),u.channel(),u.uid(),u.name(),u.muted(),u.deafened(),speaking.contains(u.id()),u.talkPower(),u.talker())).toList();
        return new View(state,lastServer==null?"":lastServer.name(),identity==null?"":identity.getUid().toBase64(),snap.self(),snap.channel(),snap.channels(),users,error,a!=null&&a.ready(),a!=null&&a.transmitting(),gate.muted(),gate.deafened(),gate.reason(),a==null?0:a.level());
    }
    public String diagnostics() {View v=view();return "BlockTalk 0.1.0-alpha.1\nJava "+Runtime.version().feature()+"\nstate="+v.state()+"\nerror="+v.error()+"\naudioReady="+v.audioReady()+"\naudioFailureType="+audioFailureType+"\nnative="+nativeStatus+"\nchannels="+v.channels().size()+"\nusers="+v.users().size()+"\nserver=[redacted]\nidentity=[redacted]\n";}
    @Override public synchronized void close() {if(closed)return;gate.reset();closed=true;state=State.DISCONNECTED;epoch.incrementAndGet();improving=false;timer.shutdownNow();worker.shutdownNow();cleanup.execute(this::release);cleanup.shutdown();}
}
