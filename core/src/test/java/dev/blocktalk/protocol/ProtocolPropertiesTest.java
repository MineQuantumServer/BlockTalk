package dev.blocktalk.protocol;

import com.github.manevolent.ts3j.api.Channel;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolPropertiesTest {
    @Test void emptyWireValuesDoNotAbortChannelOrUserUpdates() {
        Map<String,String> wire=new LinkedHashMap<>();wire.put("channel_name","Room");wire.put("channel_topic",null);wire.put("channel_codec","4");
        Map<Integer,Map<String,String>> channels=new HashMap<>();Ts3Session.merge(channels,3,new Channel(wire));
        assertEquals("4",channels.get(3).get("channel_codec"));assertEquals("",channels.get(3).get("channel_topic"));
        Ts3Session.merge(channels,3,new Channel(Map.of("channel_name","Renamed")));assertEquals("4",channels.get(3).get("channel_codec"));
    }
    @Test void errorCodesAreMappedFromProtocolTable() {assertEquals(Failure.PASSWORD,Failure.code(0x404));assertEquals(Failure.PASSWORD,Failure.code(0x30d));assertEquals(Failure.CHANNEL_FULL,Failure.code(0x309));assertEquals(Failure.BANNED,Failure.code(0xd01));assertEquals(Failure.NICKNAME,Failure.code(0x201));assertEquals(Failure.ALREADY_IN_CHANNEL,Failure.code(0x302));}
}
