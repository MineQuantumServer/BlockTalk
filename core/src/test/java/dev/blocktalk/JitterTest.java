package dev.blocktalk;

import dev.blocktalk.audio.JitterBuffer;
import dev.blocktalk.protocol.VoiceSession.Voice;
import dev.blocktalk.model.Models.Codec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JitterTest {
    Voice packet(int seq,byte value) {return new Voice(1,seq,Codec.OPUS_VOICE,new byte[]{value},(byte)1,0);}
    @Test void reordersAndDropsDuplicates() {var b=new JitterBuffer(1);b.offer(packet(10,(byte)10));b.offer(packet(12,(byte)12));b.offer(packet(11,(byte)11));b.offer(packet(11,(byte)99));assertEquals(10,b.poll(20_000_000L).data()[0]);assertEquals(11,b.poll(40_000_000L).data()[0]);assertEquals(12,b.poll(60_000_000L).data()[0]);}
    @Test void missingProducesPlcThenStops() {var b=new JitterBuffer(1);b.offer(packet(1,(byte)1));b.poll(20_000_000L);assertNull(b.poll(40_000_000L).data());assertFalse(b.poll(60_000_000L).end());assertFalse(b.poll(80_000_000L).end());assertTrue(b.poll(100_000_000L).end());}
    @Test void sequenceWrap() {var b=new JitterBuffer(1);b.offer(packet(65535,(byte)1));b.offer(packet(0,(byte)2));assertEquals(1,b.poll(20_000_000L).data()[0]);assertEquals(2,b.poll(40_000_000L).data()[0]);assertEquals(-1,JitterBuffer.distance(65535,0));}
    @Test void boundedAndExpired() {var b=new JitterBuffer(1);for(int i=0;i<1000;i++)b.offer(packet(i,(byte)1));assertTrue(b.size()<=12);assertTrue(b.poll(400_000_000L).end());}
    @Test void speakerBuffersIndependentAndSessionEnd() {var a=new JitterBuffer(1);var b=new JitterBuffer(1);a.offer(packet(1,(byte)3));b.offer(packet(1,(byte)7));assertEquals(3,a.poll(20_000_000L).data()[0]);assertEquals(7,b.poll(20_000_000L).data()[0]);a.offer(new Voice(1,2,Codec.OPUS_VOICE,new byte[0],null,30_000_000L));assertTrue(a.poll(40_000_000L).end());assertFalse(b.poll(40_000_000L).end());}
}
