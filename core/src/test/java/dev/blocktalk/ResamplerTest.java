package dev.blocktalk;

import dev.blocktalk.audio.LinearResampler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResamplerTest {
    @Test void exactTwentyMillisecondLengthsAndChannelIsolation() {
        var r=new LinearResampler(44100,48000,2);byte[] input=new byte[882*4];
        for(int i=0;i<882;i++){input[i*4]=(byte)0x10;input[i*4+1]=0x27;input[i*4+2]=(byte)0xf0;input[i*4+3]=(byte)0xd8;}
        byte[] a=r.convert(input);byte[] b=r.convert(input);assertEquals(960*4,a.length);assertEquals(960*4,b.length);
        assertEquals(10000,(short)((b[0]&255)|(b[1]<<8)));assertEquals(-10000,(short)((b[2]&255)|(b[3]<<8)));
        assertEquals(882*4,new LinearResampler(48000,44100,2).convert(b).length);
    }
}
