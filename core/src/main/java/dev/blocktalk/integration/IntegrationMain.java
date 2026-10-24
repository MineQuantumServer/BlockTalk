package dev.blocktalk.integration;

import dev.blocktalk.audio.*;
import dev.blocktalk.config.*;
import dev.blocktalk.model.Models.*;
import dev.blocktalk.protocol.*;
import java.nio.file.*;
import java.util.*;

public final class IntegrationMain {
    public static void main(String[] args) throws Exception {
        Set<String> opts=Set.of(args);
        if(opts.contains("--native-smoke")) {
            System.out.println("native="+OpusCodec.initialize());
            for(Codec codec:List.of(Codec.OPUS_VOICE,Codec.OPUS_MUSIC))try(OpusCodec enc=new OpusCodec(codec,true);OpusCodec dec=new OpusCodec(codec,false)) {
                short[] pcm=new short[960];for(int i=0;i<960;i++)pcm[i]=(short)(Math.sin(i*2*Math.PI*440/48000)*6000);
                byte[] packet=enc.encode(pcm);short[] result=dec.decode(packet);short[] plc=dec.decode(null);
                if(result.length!=960*codec.channels||plc.length!=result.length)throw new AssertionError("native duration");
                System.out.println(codec+" encode/decode/PLC PASS payload="+packet.length);
            }
            return;
        }
        if(opts.contains("--devices")) {JavaSoundBackend b=new JavaSoundBackend();System.out.println("Inputs: "+b.devices(true));System.out.println("Outputs: "+b.devices(false));return;}
        String address=System.getenv("BLOCKTALK_TS_ADDRESS");
        if(address==null||address.isBlank())throw new IllegalArgumentException("Set BLOCKTALK_TS_ADDRESS. Optional BLOCKTALK_TS_PASSWORD, BLOCKTALK_TS_NICK, BLOCKTALK_CONFIG_DIR.");
        Path dir=Path.of(Optional.ofNullable(System.getenv("BLOCKTALK_CONFIG_DIR")).orElse("work/integration-private"));
        var identity=new IdentityStore(dir).loadOrCreate();
        try(var session=new Ts3Session(identity)) {
            session.failureHandler(f->System.out.println("failure="+f));
            try {session.connect(new Server("Integration",address,Optional.ofNullable(System.getenv("BLOCKTALK_TS_NICK")).orElse("BlockTalk-Probe"),""),System.getenv("BLOCKTALK_TS_PASSWORD"));}
            catch(Exception e) {System.err.println(session.transportDiagnostics());throw e;}
            var s=session.snapshot();
            System.out.println("CONNECTED channels="+s.channels().size()+" visibleUsers="+s.users().size()+" self="+s.self()+" current="+s.channel());
            System.out.println("codecCounts="+s.channels().stream().collect(java.util.stream.Collectors.groupingBy(Channel::codec,java.util.stream.Collectors.counting())));
            System.out.println("stableServerKey="+!s.serverUid().isBlank()+" maySpeak="+session.maySpeak());
            if(opts.contains("--inspect"))System.out.println(session.channelDiagnostics());
            // Probe identity reuse without exposing private key, UID, server or other user information.
            if(!identity.getUid().equals(new IdentityStore(dir).loadOrCreate().getUid()))throw new AssertionError("Identity persistence");
            System.out.println("identity reuse PASS; official-client audio interoperability UNVERIFIED");
            if(opts.contains("--join-current")) {session.join(s.channel(),null);System.out.println("join current channel PASS");}
            if(opts.contains("--switch-channel")) {
                boolean moved=false;
                for(Channel c:s.channels())if(c.id()!=s.channel()&&!c.password()) {
                    try {session.join(c.id(),null);System.out.println("channel switch PASS codec="+session.snapshot().channels().stream().filter(ch->ch.id()==c.id()).findFirst().map(Channel::codec).orElse(Codec.UNSUPPORTED));moved=true;break;}
                    catch(Exception denied) {System.out.println("channel switch rejected="+Failure.from(denied));}
                }
                if(moved){session.join(s.channel(),null);System.out.println("return channel PASS");}else throw new IllegalStateException("No joinable channel");
            }
            if(opts.contains("--pair-smoke")) {
                // Explicit real-microphone probe. Second independent BlockTalk session, not an official peer.
                var peerIdentity=new IdentityStore(dir.resolve("peer")).loadOrCreate();
                var gate=new dev.blocktalk.input.PttGate();var settings=new Settings();settings.outputGain=0;
                Codec codec=session.snapshot().channels().stream().filter(c->c.id()==session.snapshot().channel()).findFirst().orElseThrow().codec();
                var received=new java.util.concurrent.atomic.AtomicInteger();
                try(var peer=new Ts3Session(peerIdentity);var decoder=new OpusCodec(codec,false)) {
                    peer.connect(new Server("Integration peer",address,"BlockTalk-Receive-Probe",""),System.getenv("BLOCKTALK_TS_PASSWORD"));
                    peer.join(session.snapshot().channel(),null);
                    peer.voiceHandler(v->{if(v.client()==session.snapshot().self()&&v.data().length>0){synchronized(decoder){if(decoder.decode(v.data()).length==960*codec.channels)received.incrementAndGet();}}});
                    try(var audio=new AudioEngine(new JavaSoundBackend(),gate,session,()->codec,settings,e->System.out.println("audio="+e))) {
                        audio.start(false);gate.context(true,audio.ready(),codec!=Codec.UNSUPPORTED,session.maySpeak(),true,true);
                        gate.key(true);Thread.sleep(500);gate.key(false);session.stopTalking();Thread.sleep(200);
                        if(received.get()==0)throw new IllegalStateException("No routed microphone packets");
                        System.out.println("BlockTalk-to-BlockTalk real microphone / TS UDP / decode PASS frames="+received.get());
                        int stopped=received.get();Thread.sleep(300);if(received.get()!=stopped)throw new AssertionError("Voice continued after PTT release");
                        System.out.println("PTT release network silence PASS; official-client interoperability remains UNVERIFIED");
                    }
                    var reverseCount=new java.util.concurrent.atomic.AtomicInteger();
                    try(var reverseDecoder=new OpusCodec(codec,false)) {
                        session.voiceHandler(v->{if(v.client()==peer.snapshot().self()&&v.data().length>0){synchronized(reverseDecoder){reverseDecoder.decode(v.data());reverseCount.incrementAndGet();}}});
                        try(var reverse=new AudioEngine(new JavaSoundBackend(),gate,peer,()->codec,settings,e->System.out.println("audio="+e))) {
                            reverse.start(false);gate.context(true,reverse.ready(),codec!=Codec.UNSUPPORTED,peer.maySpeak(),true,true);gate.key(true);Thread.sleep(500);gate.key(false);peer.stopTalking();Thread.sleep(200);
                            if(reverseCount.get()==0)throw new IllegalStateException("No reverse voice packets");
                            System.out.println("Reverse real microphone / TS UDP / decode PASS frames="+reverseCount.get());
                        }
                    }
                }
            }
            if(opts.contains("--audio")) {
                var gate=new dev.blocktalk.input.PttGate();
                var settings=new Settings();
                try(var audio=new AudioEngine(new JavaSoundBackend(),gate,session,()->session.snapshot().channels().stream().filter(c->c.id()==session.snapshot().channel()).findFirst().map(Channel::codec).orElse(Codec.UNSUPPORTED),settings,e->System.out.println("audio="+e))) {
                    session.voiceHandler(audio::receive);audio.start(false);
                    System.out.println("Receive enabled; transmission CLOSED. Console: status / talk SECONDS (1..5) / down / up / mute / deafen / join ID / quit.");
                    try(Scanner in=new Scanner(System.in)) {
                        while(in.hasNextLine()) {
                            String command=in.nextLine().trim();
                            gate.context(session.connected(),audio.ready(),session.snapshot().channels().stream().filter(c->c.id()==session.snapshot().channel()).anyMatch(c->c.codec()!=Codec.UNSUPPORTED),session.maySpeak(),true,true);
                            if(command.equals("quit"))break;
                            if(command.equals("status"))System.out.println("audioReady="+audio.ready()+" gate="+gate.reason()+" micLevel="+(int)(audio.level()*100)+"% "+audio.statistics()+" visibleInChannel="+session.snapshot().users().stream().filter(u->u.channel()==session.snapshot().channel()).count());
                            if(command.equals("down"))gate.key(true);
                            if(command.equals("up")){gate.key(false);session.stopTalking();}
                            if(command.equals("mute"))gate.mute(!gate.muted());
                            if(command.equals("deafen")){gate.deafen(!gate.deafened());audio.invalidate();}
                            if(command.startsWith("talk ")) {
                                int seconds;try{seconds=Integer.parseInt(command.substring(5));}catch(NumberFormatException invalid){System.out.println("Use talk 1..5");continue;}
                                if(seconds<1||seconds>5){System.out.println("Use talk 1..5");continue;}
                                long before=audio.statistics().sentPackets();gate.key(false);gate.key(true);
                                System.out.println("Timed microphone transmission requested seconds="+seconds+" gate="+gate.reason());
                                try{Thread.sleep(seconds*1000L);}finally{gate.key(false);session.stopTalking();}
                                System.out.println("Transmission CLOSED; sentPacketsDelta="+(audio.statistics().sentPackets()-before));
                            }
                            if(command.startsWith("join ")){gate.reset();audio.invalidate();session.join(Integer.parseInt(command.substring(5)),null);}
                        }
                    }
                }
            }
        } catch(Exception e) {System.err.println("probe failed: "+(e instanceof Ts3Session.SessionException se?se.failure:Failure.from(e)));System.exit(2);}
    }
}
