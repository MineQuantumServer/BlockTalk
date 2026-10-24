package dev.blocktalk.client.ui;

import dev.blocktalk.client.BlockTalkMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class Hud {
    public static void render(GuiGraphics g,DeltaTracker time) {
        var rt=BlockTalkMod.runtime;var config=rt.settings();if(!config.hud)return;
        var v=rt.view();var font=Minecraft.getInstance().font;List<Component> lines=new ArrayList<>();
        lines.add(Component.literal("BlockTalk ").append(BaseScreen.tr("state."+v.state().name().toLowerCase(Locale.ROOT))));
        v.channels().stream().filter(c->c.id()==v.channelId()).findFirst().ifPresent(c->lines.add(Component.literal(c.name())));
        lines.add(BaseScreen.tr(v.deafened()?"deafen":v.muted()?"mute":v.transmitting()?"transmitting":"gate."+v.gateReason()));
        v.users().stream().filter(u->u.speaking()).limit(config.hudUsers).forEach(u->lines.add(Component.literal("> "+u.name())));
        g.pose().pushPose();g.pose().translate(Math.min(config.hudX,Math.max(0,g.guiWidth()-180*config.hudScale)),Math.min(config.hudY,Math.max(0,g.guiHeight()-(lines.size()*11+6)*config.hudScale)),0);g.pose().scale(config.hudScale,config.hudScale,1);
        g.fill(0,0,180,lines.size()*11+6,((int)(config.hudOpacity*255)<<24));
        for(int i=0;i<lines.size();i++)g.drawString(font,font.plainSubstrByWidth(lines.get(i).getString(),174),3,3+i*11,v.transmitting()?0x80ff80:0xffffff);
        g.pose().popPose();
    }
}
