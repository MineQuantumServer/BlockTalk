package dev.blocktalk.audio;

import dev.blocktalk.opus.NativeOpus;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.util.Arrays;
import dev.blocktalk.model.Models.Codec;

public final class OpusCodec implements AutoCloseable {
    private final int channels;
    private final NativeOpus opus;
    private Pointer encoder,decoder;
    public static String initialize() {return NativeOpus.api().opus_get_version_string();}
    public OpusCodec(Codec codec,boolean encoding) {
        opus=NativeOpus.api();if(codec==Codec.UNSUPPORTED)throw new IllegalArgumentException("codec");channels=codec.channels;
        IntByReference error=new IntByReference();
        try {
            if(encoding) {
                encoder=opus.opus_encoder_create(48000,channels,codec==Codec.OPUS_VOICE?NativeOpus.OPUS_APPLICATION_VOIP:NativeOpus.OPUS_APPLICATION_AUDIO,error);check(error.getValue());
                check(opus.opus_encoder_ctl(encoder,NativeOpus.OPUS_SET_BITRATE_REQUEST,codec==Codec.OPUS_VOICE?24000:64000));
                check(opus.opus_encoder_ctl(encoder,NativeOpus.OPUS_SET_INBAND_FEC_REQUEST,0));
                check(opus.opus_encoder_ctl(encoder,NativeOpus.OPUS_SET_COMPLEXITY_REQUEST,5));
            }else {decoder=opus.opus_decoder_create(48000,channels,error);check(error.getValue());}
        }catch(Throwable e){close();throw e;}
    }
    public byte[] encode(short[] mono) {
        if(mono.length!=960)throw new IllegalArgumentException("20 ms frame required");
        short[] samples=mono;
        if(channels==2){samples=new short[1920];for(int i=0;i<960;i++)samples[i*2]=samples[i*2+1]=mono[i];}
        byte[] bytes=new byte[400];int n=opus.opus_encode(encoder,samples,960,bytes,bytes.length);check(n);return Arrays.copyOf(bytes,n);
    }
    public short[] decode(byte[] data) {
        if(data!=null&&data.length>0&&opus.opus_packet_get_nb_samples(data,data.length,48000)!=960)throw new IllegalArgumentException("Unsupported Opus duration");
        short[] pcm=new short[960*channels];int n=opus.opus_decode(decoder,data,data==null?0:data.length,pcm,960,0);check(n);return Arrays.copyOf(pcm,n*channels);
    }
    public int channels(){return channels;}
    private static void check(int code){if(code<0)throw new IllegalStateException("Opus error "+code);}
    @Override public void close(){if(encoder!=null){opus.opus_encoder_destroy(encoder);encoder=null;}if(decoder!=null){opus.opus_decoder_destroy(decoder);decoder=null;}}
}
