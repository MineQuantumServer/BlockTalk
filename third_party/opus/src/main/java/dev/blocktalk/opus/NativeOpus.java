package dev.blocktalk.opus;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/** Independently authored minimal binding to the BSD-licensed Opus 1.6.1 C headers.
 * No opus4j/opus-java wrapper code or prebuilt binaries are included. */
public interface NativeOpus extends Library {
    int OPUS_APPLICATION_VOIP=2048,OPUS_APPLICATION_AUDIO=2049;
    int OPUS_SET_BITRATE_REQUEST=4002,OPUS_SET_COMPLEXITY_REQUEST=4010,OPUS_SET_INBAND_FEC_REQUEST=4012;
    Pointer opus_encoder_create(int rate,int channels,int application,IntByReference error);
    int opus_encoder_ctl(Pointer encoder,int request,Object... arguments);
    int opus_encode(Pointer encoder,short[] pcm,int frameSize,byte[] output,int capacity);
    void opus_encoder_destroy(Pointer encoder);
    Pointer opus_decoder_create(int rate,int channels,IntByReference error);
    int opus_decode(Pointer decoder,byte[] input,int length,short[] output,int frameSize,int fec);
    int opus_packet_get_nb_samples(byte[] input,int length,int rate);
    void opus_decoder_destroy(Pointer decoder);
    String opus_get_version_string();
    static NativeOpus api() {return NativeLoader.get();}
}
