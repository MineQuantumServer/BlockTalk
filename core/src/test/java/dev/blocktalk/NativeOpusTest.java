package dev.blocktalk;

import dev.blocktalk.audio.OpusCodec;
import dev.blocktalk.model.Models.Codec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeOpusTest {
    @Test void repeatedNativeAllocationAndRelease() {
        for(int i=0;i<100;i++)try(var encoder=new OpusCodec(Codec.OPUS_VOICE,true);var decoder=new OpusCodec(Codec.OPUS_VOICE,false)) {assertEquals(960,decoder.decode(encoder.encode(new short[960])).length);}
    }
    @Test void twoIndependentDecodersAndPlc() throws Exception {
        for(Codec c:new Codec[]{Codec.OPUS_VOICE,Codec.OPUS_MUSIC})try(var encoder=new OpusCodec(c,true);var a=new OpusCodec(c,false);var b=new OpusCodec(c,false)) {
            short[] pcm=new short[960];for(int i=0;i<960;i++)pcm[i]=(short)(Math.sin(i*.1)*10000);
            byte[] packet=encoder.encode(pcm);
            short[] first=a.decode(packet);a.decode(null);
            assertArrayEquals(first,b.decode(packet));assertEquals(960*c.channels,a.decode(packet).length);
        }
    }
}
