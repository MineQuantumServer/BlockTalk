package dev.blocktalk.audio;

import java.util.List;

public interface AudioBackend {
    record Device(String id,String name) {}
    interface Input extends AutoCloseable { int read(byte[] data); void flush(); void close(); }
    interface Output extends AutoCloseable { void write(byte[] data); void flush(); void close(); }
    List<Device> devices(boolean input);
    Input openInput(String id) throws Exception;
    Output openOutput(String id) throws Exception;
}
