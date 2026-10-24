package dev.blocktalk.protocol;

import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket;
import com.github.manevolent.ts3j.identity.LocalIdentity;
import com.github.manevolent.ts3j.protocol.ProtocolRole;
import com.github.manevolent.ts3j.protocol.packet.PacketBody0Voice;
import com.github.manevolent.ts3j.command.*;
import com.github.manevolent.ts3j.command.parameter.CommandSingleParameter;
import com.github.manevolent.ts3j.event.*;
import com.github.manevolent.ts3j.api.Wrapper;
import com.github.manevolent.ts3j.enums.CodecType;
import dev.blocktalk.model.Models;
import dev.blocktalk.model.Models.*;
import dev.blocktalk.input.PttGate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** All ts3j types stay behind this boundary. Each object owns exactly one connection. */
public final class Ts3Session implements VoiceSession {
    private final LocalTeamspeakClientSocket socket=new LocalTeamspeakClientSocket();
    private final Map<Integer,Map<String,String>> channels=new LinkedHashMap<>(),users=new LinkedHashMap<>();
    private volatile Consumer<Voice> voice=v->{};
    private volatile Consumer<Failure> failure=f->{};
    private volatile Runnable changed=()->{};
    private volatile boolean closed;
    private volatile String serverUid="";
    private volatile Failure pendingFailure;
    private volatile String lastException="none";
    private volatile int channelInfoError=-999;
    private boolean talking;
    private int flags=5;
    private byte voiceSession=1;
    private Codec lastCodec=Codec.OPUS_VOICE;
    private volatile int currentChannel=-1;
    private volatile PttGate sendGate;
    private void resetSendGate() {PttGate g=sendGate;if(g!=null)g.reset();}

    public Ts3Session(LocalIdentity identity) {
        socket.setIdentity(identity);
        socket.setExceptionHandler(e->{
            if(lastException.equals("none")) lastException=e.getClass().getSimpleName()+Arrays.stream(e.getStackTrace()).limit(8).map(t->" "+t.getClassName()+":"+t.getLineNumber()).collect(java.util.stream.Collectors.joining());
            Failure f=Failure.from(e);
            if(pendingFailure==null) pendingFailure=f;
        });
        socket.setVoiceHandler(p->{
            if(closed) return;
            byte[] data=p.getCodecData();
            if(data!=null && data.length<=1275) voice.accept(new Voice(p.getClientId(),p.getPacketId(),Codec.of(p.getCodecType().getIndex()),data.clone(),p.getServerFlag0(),System.nanoTime()));
        });
        socket.addListener(new TS3Listener() {
            public void onChannelList(ChannelListEvent e) { channel(e); }
            public void onChannelCreate(ChannelCreateEvent e) { channel(e); }
            public void onChannelEdit(ChannelEditedEvent e) { channel(e); }
            public void onChannelMoved(ChannelMovedEvent e) { channel(e); }
            public void onChannelPasswordChanged(ChannelPasswordChangedEvent e) { channel(e); }
            public void onChannelDeleted(ChannelDeletedEvent e) { synchronized(Ts3Session.this) { channels.remove(e.getInt("cid")); } changed.run(); }
            public void onClientJoin(ClientJoinEvent e) { user(e); }
            public void onClientChanged(ClientUpdatedEvent e) { user(e); }
            public void onClientMoved(ClientMovedEvent e) { user(e); }
            public void onClientLeave(ClientLeaveEvent e) { synchronized(Ts3Session.this) { users.remove(e.getInt("clid")); } changed.run(); }
            public void onDisconnected(DisconnectedEvent e) {
                resetSendGate();
                if(!closed) failure.accept(e.getReasonId()==6?Failure.BANNED:e.getReasonId()==5?Failure.KICKED:Failure.UNREACHABLE);
            }
        });
    }
    private void channel(Wrapper e) {if(e.getInt("cid")==currentChannel)resetSendGate();synchronized(this) { merge(channels,e.getInt("cid"),e); } changed.run(); }
    private void user(Wrapper e) {
        // Close the gate before publishing any local move/permission/state change. Lock order
        // remains gate -> socket state, matching send(), so an old encoded frame cannot cross it.
        if(e.getInt("clid")==socket.getClientId())resetSendGate();
        synchronized(this) {
            int id=e.getInt("clid"); merge(users,id,e);
            if(id==socket.getClientId() && e.getInt("ctid")>0) currentChannel=e.getInt("ctid");
        }
        changed.run();
    }
    static void merge(Map<Integer,Map<String,String>> target,int id,Wrapper e) {
        if(id<0 || target.size()>8192) return;
        Map<String,String> dst=target.computeIfAbsent(id,k->new HashMap<>());
        for(var entry:e.getMap().entrySet()) {
            String value=Objects.requireNonNullElse(entry.getValue(),"");
            if(entry.getKey().length()<100 && value.length()<4096) dst.put(entry.getKey(),value);
        }
    }
    @Override public void connect(Server s,String password) throws Exception {
        socket.setNickname(s.nickname().isBlank()?"BlockTalk":s.nickname());
        ServerAddress a=ServerAddress.parse(s.address());
        var remote=AddressResolver.resolve(a);
        if(remote.isUnresolved()) throw new java.net.UnknownHostException();
        if(closed) throw new CancellationException();
        try { socket.connect(remote,password,15000); }
        catch(Exception e) { if(pendingFailure!=null && pendingFailure!=Failure.PROTOCOL) throw new SessionException(pendingFailure); throw e; }
        if(closed) { close(); throw new CancellationException(); }
        // Initial channel/client notifications are the authoritative snapshot. A barrier drains
        // the same serial callback executor before CONNECTED is exposed; later deltas merge there.
        CountDownLatch barrier=new CountDownLatch(1);
        callbacks().execute(barrier::countDown); if(!barrier.await(5,TimeUnit.SECONDS)) throw new TimeoutException();
        try { socket.subscribeAll(); } catch(CommandException denied) { /* permission-respecting visibility */ }
        serverUid=socket.getServerUniqueIdentifier();
        if(serverUid.isBlank())try {
            var info=socket.executeCommand(new SingleCommand("serverinfo",ProtocolRole.CLIENT)).get(5000).iterator();
            if(info.hasNext())serverUid=Objects.requireNonNullElse(info.next().toMap().get("virtualserver_unique_identifier"),"");
        }catch(Exception ignored) { }
        if(serverUid.isBlank() && socket.getServerFingerprint()!=null)serverUid="key:"+socket.getServerFingerprint();
        try {var self=socket.getClientInfo(socket.getClientId());if(self!=null)user(self);}catch(Exception ignored) { }
        // whoami and serverinfo are real client protocol commands, never ServerQuery connections.
        try {
            var who=socket.executeCommand(new SingleCommand("whoami",ProtocolRole.CLIENT)).get(5000).iterator();
            if(who.hasNext()) { var m=who.next().toMap(); synchronized(this) { if(m.containsKey("cid")) currentChannel=Integer.parseInt(m.get("cid")); } }
        } catch(Exception ignored) { }
        if(currentChannel<0) synchronized(this) { var self=users.get(socket.getClientId()); if(self!=null) currentChannel=num(self,"ctid",num(self,"cid",-1)); }
        refreshChannel(currentChannel);
        if(!s.defaultChannel().isBlank()) {
            Channel target=snapshot().channels().stream().filter(c->c.name().equals(s.defaultChannel())||Integer.toString(c.id()).equals(s.defaultChannel())).findFirst().orElse(null);
            if(target!=null) join(target.id(),null);
        }
    }
    private final ExecutorService events=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4096),r->{Thread t=new Thread(r,"BlockTalk-events");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private ExecutorService callbacks() { return events; }
    { socket.setCommandExecutorService(events); }
    @Override public void join(int channel,String password) throws Exception {
        resetSendGate();
        stopTalking();
        if(channel!=snapshot().channel()) socket.joinChannel(channel,password);
        synchronized(this) { currentChannel=channel; }
        refreshChannel(channel);changed.run();
    }
    private void refreshChannel(int channel) {
        try {
            var c=socket.getChannelInfo(channel);
            if(c!=null) {Map<String,String> data=new HashMap<>(c.getMap());data.put("cid",Integer.toString(channel));channel(new com.github.manevolent.ts3j.api.Channel(data));}
        } catch(CommandException denied) {channelInfoError=denied.getErrorId();}catch(Exception failed) {channelInfoError=-1;}
    }
    @Override public synchronized Snapshot snapshot() {
        List<Channel> cs=new ArrayList<>();
        channels.forEach((id,c)->cs.add(new Channel(id,num(c,"cpid",num(c,"pid",0)),num(c,"channel_order",0),Models.clean(c.get("channel_name"),160),num(c,"channel_flag_password",0)==1,Codec.of(num(c,"channel_codec",-1)),num(c,"channel_needed_talk_power",0))));
        List<User> us=new ArrayList<>();
        users.forEach((id,u)->us.add(new User(id,num(u,"ctid",num(u,"cid",-1)),u.getOrDefault("client_unique_identifier",""),Models.clean(u.get("client_nickname"),80),num(u,"client_input_muted",0)==1,num(u,"client_output_muted",0)==1,false,num(u,"client_talk_power",0),num(u,"client_is_talker",0)==1)));
        return new Snapshot(socket.getClientId(),currentChannel,serverUid,List.copyOf(cs),List.copyOf(us));
    }
    static int num(Map<String,String> m,String k,int fallback) { try { return Integer.parseInt(m.get(k)); } catch(Exception e) { return fallback; } }
    @Override public void voiceHandler(Consumer<Voice> h) { voice=h; }
    public boolean connected() { return !closed && socket.isConnected(); }
    public String transportDiagnostics() {
        return "state="+socket.getState()+" lastExceptionType="+lastException+" pending="+pendingFailure;
    }
    public synchronized String channelDiagnostics() { return "channelInfoError="+channelInfoError+" serverUidAvailable="+!serverUid.isBlank()+"\n"+channels.entrySet().stream().map(e->e.getKey()+":codec="+num(e.getValue(),"channel_codec",-1)+":keys="+e.getValue().keySet().stream().sorted().toList()).collect(java.util.stream.Collectors.joining("\n")); }
    @Override public void changed(Runnable h) { changed=h; }
    @Override public void failureHandler(Consumer<Failure> h) { failure=h; }
    @Override public void bindGate(PttGate gate) {sendGate=gate;}
    @Override public boolean send(byte[] data,Codec codec,PttGate gate,long token) throws Exception {
        synchronized(gate) {
            if(closed || !gate.valid(token)||data.length==0||data.length>400||codec==Codec.UNSUPPORTED) return false;
            synchronized(this) {
                PacketBody0Voice p=new PacketBody0Voice(ProtocolRole.CLIENT); p.setCodecType(CodecType.fromId(codec.id)); p.setCodecData(data);
                if(flags>0) p.setServerFlag0(voiceSession);
                socket.writePacket(p); flags--; talking=true; lastCodec=codec; return true;
            }
        }
    }
    @Override public synchronized void stopTalking() {
        if(!talking) return;
        talking=false; flags=5; voiceSession=(byte)(voiceSession%7+1);
        try {
            if(!closed && socket.isConnected()) { PacketBody0Voice p=new PacketBody0Voice(ProtocolRole.CLIENT); p.setCodecType(CodecType.fromId(lastCodec.id));p.setCodecData(new byte[0]);socket.writePacket(p); }
        } catch(Exception ignored) { }
    }
    @Override public void setMuted(boolean muted,boolean deafened) throws Exception {
        socket.executeCommand(new SingleCommand("clientupdate",ProtocolRole.CLIENT,new CommandSingleParameter("client_input_muted",muted||deafened?"1":"0"),new CommandSingleParameter("client_output_muted",deafened?"1":"0"))).get(5000);
    }
    @Override public void close() {
        if(closed) return; closed=true;
        resetSendGate();
        try { if(socket.isConnected()) socket.writePacket(new com.github.manevolent.ts3j.protocol.packet.PacketBody2Command(ProtocolRole.CLIENT,new SingleCommand("clientdisconnect",ProtocolRole.CLIENT))); } catch(Exception ignored) { }
        try { socket.close(); } catch(Exception ignored) { }
        events.shutdownNow();
        synchronized(this) { channels.clear();users.clear(); }
    }
    public static final class SessionException extends Exception { public final Failure failure; public SessionException(Failure f) { super(f.name()); failure=f; } }
}
