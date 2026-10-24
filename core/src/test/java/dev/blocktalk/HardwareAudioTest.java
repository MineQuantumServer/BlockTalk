package dev.blocktalk;

import dev.blocktalk.audio.JavaSoundBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOCKTALK_HARDWARE_TEST",matches="true")
class HardwareAudioTest {
    @Test void openReadPlaySilenceAndRelease() throws Exception {
        var b=new JavaSoundBackend();
        try(var input=b.openInput("default");var output=b.openOutput("default")) {
            byte[] pcm=new byte[1920];assertEquals(pcm.length,input.read(pcm));
            output.write(new byte[3840]);input.flush();output.flush();
        }
    }
}
