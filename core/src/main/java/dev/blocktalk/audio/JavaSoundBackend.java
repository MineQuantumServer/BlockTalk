package dev.blocktalk.audio;

import javax.sound.sampled.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class JavaSoundBackend implements AudioBackend {
    private static final AudioFormat INPUT=new AudioFormat(48000,16,1,true,false),OUTPUT=new AudioFormat(48000,16,2,true,false);
    private static AudioFormat format(int rate,boolean input){return new AudioFormat(rate,16,input?1:2,true,false);}
    private static String id(Mixer.Info i) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((i.getName()+"|"+i.getVendor()+"|"+i.getDescription()+"|"+i.getVersion()).getBytes(StandardCharsets.UTF_8))).substring(0,24); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public List<Device> devices(boolean input) {
        List<Device> list=new ArrayList<>();list.add(new Device("default","default"));
        for(Mixer.Info i:AudioSystem.getMixerInfo()) if(AudioSystem.getMixer(i).isLineSupported(new DataLine.Info(input?TargetDataLine.class:SourceDataLine.class,input?INPUT:OUTPUT)) || AudioSystem.getMixer(i).isLineSupported(new DataLine.Info(input?TargetDataLine.class:SourceDataLine.class,format(44100,input)))) list.add(new Device(id(i),i.getName()));
        return List.copyOf(list);
    }
    private Line line(String device,boolean input,int rate) throws Exception {
        DataLine.Info type=new DataLine.Info(input?TargetDataLine.class:SourceDataLine.class,format(rate,input));
        if(device.equals("default")) return AudioSystem.getLine(type);
        for(Mixer.Info i:AudioSystem.getMixerInfo()) if(id(i).equals(device)) return AudioSystem.getMixer(i).getLine(type);
        throw new LineUnavailableException("Selected device unavailable");
    }
    public Input openInput(String id) throws Exception {
        Exception last=null;
        for(int rate:new int[]{48000,44100}) {
            TargetDataLine l=null;
            try {
                l=(TargetDataLine)line(id,true,rate);l.open(format(rate,true),rate/50*2*4);l.start();
                final TargetDataLine selected=l;final int actualRate=rate;var resampler=new LinearResampler(rate,48000,1);
                return new Input() {
                    public int read(byte[] b) {
                        int needed=actualRate/50*2;long deadline=System.nanoTime()+250_000_000L;
                        while(selected.isOpen()&&selected.available()<needed) {
                            if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())return 0;
                            try{Thread.sleep(2);}catch(InterruptedException e){Thread.currentThread().interrupt();return 0;}
                        }
                        if(!selected.isOpen())return 0;
                        if(actualRate==48000)return selected.read(b,0,b.length);
                        byte[] nativePcm=new byte[needed];int count=selected.read(nativePcm,0,nativePcm.length);if(count!=nativePcm.length)return 0;
                        byte[] converted=resampler.convert(nativePcm);System.arraycopy(converted,0,b,0,converted.length);return converted.length;
                    }
                    public void flush(){selected.flush();}public void close(){selected.stop();selected.flush();selected.close();}
                };
            }catch(Exception e){last=e;if(l!=null)l.close();}
        }
        throw last;
    }
    public Output openOutput(String id) throws Exception {
        Exception last=null;
        for(int rate:new int[]{48000,44100}) {
            SourceDataLine l=null;
            try {
                l=(SourceDataLine)line(id,false,rate);l.open(format(rate,false),rate/50*4*4);l.start();
                final SourceDataLine selected=l;final int actualRate=rate;var resampler=new LinearResampler(48000,rate,2);
                return new Output(){public void write(byte[] b){byte[] data=actualRate==48000?b:resampler.convert(b);selected.write(data,0,data.length);}public void flush(){selected.flush();}public void close(){selected.stop();selected.flush();selected.close();}};
            }catch(Exception e){last=e;if(l!=null)l.close();}
        }
        throw last;
    }
}
