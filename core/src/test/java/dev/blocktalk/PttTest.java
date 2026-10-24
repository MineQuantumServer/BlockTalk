package dev.blocktalk;

import dev.blocktalk.input.PttGate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PttTest {
    PttGate ready() {PttGate g=new PttGate();g.context(true,true,true,true,true,true);return g;}
    @Test void pressHoldRelease() {var g=ready();assertFalse(g.open());g.key(true);long t=g.token();assertTrue(g.valid(t));g.key(true);assertEquals(t,g.token());g.key(false);assertFalse(g.open());assertFalse(g.valid(t));}
    @Test void focusLossRequiresNewPress() {var g=ready();g.key(true);g.context(true,true,true,true,false,true);assertFalse(g.open());g.context(true,true,true,true,true,true);g.key(true);assertFalse(g.open());g.key(false);g.key(true);assertTrue(g.open());}
    @Test void editingPermissionCodecAndDeviceFailClosed() {for(int condition=0;condition<4;condition++){var g=ready();g.key(true);g.context(true,condition!=0,condition!=1,condition!=2,true,condition!=3);assertFalse(g.open());g.context(true,true,true,true,true,true);g.key(true);assertFalse(g.open());}}
    @Test void deafenPreservesManualMute() {var g=ready();g.mute(true);g.deafen(true);g.deafen(false);assertTrue(g.muted());g.mute(false);g.key(true);assertTrue(g.open());g.deafen(true);assertFalse(g.open());}
    @Test void channelGenerationInvalidatesEncodedFrames() {var g=ready();g.key(true);long old=g.token();g.reset();assertFalse(g.valid(old));g.key(false);g.key(true);assertTrue(g.open());assertFalse(g.valid(old));}
    @Test void heldAcrossConnectionCannotOpen() {var g=new PttGate();g.key(true);g.context(true,true,true,true,true,true);g.key(true);assertFalse(g.open());g.key(false);g.key(true);assertTrue(g.open());}
}
