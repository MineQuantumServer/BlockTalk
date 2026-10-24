package dev.blocktalk;

import dev.blocktalk.model.Models.*;
import dev.blocktalk.input.PttGate;
import dev.blocktalk.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleTest {
    @TempDir Path dir;
    @Test void cancelledConnectionAndLateCallbacksStayDisconnected() throws Exception {
        Fake fake=new Fake();
        try(var client=new BlockTalkClient(dir,i->fake,false)) {
            client.connect(new Server("test","localhost","test",""),"SECRET_PASSWORD");
            assertTrue(fake.entered.await(5,TimeUnit.SECONDS));client.disconnect();fake.finished.countDown();
            await(()->client.view().state()==State.DISCONNECTED);fake.failure.accept(Failure.UNREACHABLE);fake.change.run();
            Thread.sleep(150);assertEquals(State.DISCONNECTED,client.view().state());assertFalse(client.gate.open());
            String diagnostic=client.diagnostics();assertFalse(diagnostic.contains("SECRET_PASSWORD"));assertFalse(diagnostic.contains("localhost"));assertFalse(diagnostic.contains(client.view().uid())&&!client.view().uid().isBlank());
        }
    }
    @Test void reconnectStopsOnAuthenticationKickBanAndLimit() {for(Failure f:List.of(Failure.PASSWORD,Failure.IDENTITY,Failure.PERMISSION,Failure.KICKED,Failure.BANNED,Failure.PROTOCOL))assertFalse(BlockTalkClient.shouldRetry(f,true,0));assertTrue(BlockTalkClient.shouldRetry(Failure.UNREACHABLE,true,4));assertFalse(BlockTalkClient.shouldRetry(Failure.UNREACHABLE,true,5));assertFalse(BlockTalkClient.shouldRetry(Failure.UNREACHABLE,false,0));}
    @Test void shutdownThenLogoutIsIdempotent() {var client=new BlockTalkClient(dir);client.close();assertDoesNotThrow(client::disconnect);assertDoesNotThrow(client::close);}
    @Test void manualDisconnectCancelsScheduledNetworkRetry() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var client=new BlockTalkClient(dir,i->{calls.incrementAndGet();return new Fake(){public void connect(Server s,String p)throws Exception{throw new Ts3Session.SessionException(Failure.UNREACHABLE);}};},false)) {
            client.connect(new Server("test","localhost","test",""),null);await(()->client.view().state()==State.RECONNECTING);
            client.disconnect();await(()->client.view().state()==State.DISCONNECTED);Thread.sleep(1200);assertEquals(1,calls.get());
        }
    }
    @Test void authenticationFailureDoesNotScheduleRetry() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var client=new BlockTalkClient(dir,i->{calls.incrementAndGet();return new Fake(){public void connect(Server s,String p)throws Exception{throw new Ts3Session.SessionException(Failure.PASSWORD);}};},false)) {
            client.connect(new Server("test","localhost","test",""),"SENSITIVE");await(()->client.view().state()==State.FAILED);Thread.sleep(1200);assertEquals(1,calls.get());assertFalse(client.diagnostics().contains("SENSITIVE"));
        }
    }
    static void await(java.util.function.BooleanSupplier c) throws Exception {long until=System.nanoTime()+5_000_000_000L;while(!c.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);assertTrue(c.getAsBoolean());}
    static class Fake implements VoiceSession {
        CountDownLatch entered=new CountDownLatch(1),finished=new CountDownLatch(1);Consumer<Failure> failure=f->{};Runnable change=()->{};
        public void connect(Server s,String password) throws Exception {entered.countDown();finished.await(5,TimeUnit.SECONDS);}
        public void join(int c,String p){}public Snapshot snapshot(){return new Snapshot(1,1,"serverUid",List.of(new Channel(1,0,0,"a",false,Codec.OPUS_VOICE,0)),List.of());}
        public void voiceHandler(Consumer<Voice> h){}public void changed(Runnable r){change=r;}public void failureHandler(Consumer<Failure> h){failure=h;}
        public boolean send(byte[] d,Codec c,PttGate g,long t){return g.valid(t);}public void stopTalking(){}public void setMuted(boolean m,boolean d){}public void close(){finished.countDown();}
    }
}
