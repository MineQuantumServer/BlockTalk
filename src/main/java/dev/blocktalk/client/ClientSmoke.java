package dev.blocktalk.client;

import dev.blocktalk.client.ui.*;
import dev.blocktalk.audio.OpusCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.LoggerFactory;

/** Opt-in developer smoke run. No server connection and no microphone capture. */
final class ClientSmoke {
    private static int ticks;
    static void tick() {
        if(!Boolean.getBoolean("blocktalk.smoke"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(ticks==60) {
            mc.setScreen(new MainScreen(mc.screen));
            if("true".equals(System.getenv("BLOCKTALK_SMOKE_CONNECT"))) {
                String address=System.getenv("BLOCKTALK_TS_ADDRESS");
                if(address!=null)BlockTalkMod.runtime.connect(new dev.blocktalk.model.Models.Server("Integration",address,"BlockTalk-Game-Probe",""),System.getenv("BLOCKTALK_TS_PASSWORD"));
            }
            java.util.concurrent.CompletableFuture.runAsync(()->{try{LoggerFactory.getLogger("BlockTalkSmoke").info("Native codec: {}",OpusCodec.initialize());}catch(Throwable e){LoggerFactory.getLogger("BlockTalkSmoke").error("Native codec failed: {}",e.getClass().getSimpleName());}});
        }
        if(ticks==90||ticks==140||ticks==190)Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
        if(ticks==100)mc.setScreen(new SettingsScreen(mc.screen));
        if(ticks==150&&mc.screen!=null)mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);
        if(ticks==190)LoggerFactory.getLogger("BlockTalkSmoke").info("Runtime: {}",BlockTalkMod.runtime.diagnostics());
        if(ticks==210)mc.stop();
    }
}
