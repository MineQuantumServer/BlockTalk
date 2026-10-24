package dev.blocktalk.audio;

/** Causal linear PCM resampling with one source-sample delay and per-channel continuity. */
public final class LinearResampler {
    private final int source,target,channels;
    private final short[] previous;
    public LinearResampler(int source,int target,int channels) {this.source=source;this.target=target;this.channels=channels;previous=new short[channels];}
    public byte[] convert(byte[] bytes) {
        int frames=bytes.length/(channels*2),count=frames*target/source;
        byte[] result=new byte[count*channels*2];
        for(int i=0;i<count;i++) {
            double position=(double)i*source/target-1;int left=(int)Math.floor(position);double fraction=position-left;
            for(int ch=0;ch<channels;ch++) {
                int a=left<0?previous[ch]:sample(bytes,left*channels+ch);
                int b=sample(bytes,Math.min(frames-1,left+1)*channels+ch);
                short value=(short)Math.clamp((int)Math.round(a+(b-a)*fraction),-32768,32767);int offset=(i*channels+ch)*2;result[offset]=(byte)value;result[offset+1]=(byte)(value>>8);
            }
        }
        for(int ch=0;ch<channels;ch++)previous[ch]=sample(bytes,(frames-1)*channels+ch);
        return result;
    }
    private static short sample(byte[] bytes,int sample) {return(short)((bytes[sample*2]&255)|(bytes[sample*2+1]<<8));}
}
