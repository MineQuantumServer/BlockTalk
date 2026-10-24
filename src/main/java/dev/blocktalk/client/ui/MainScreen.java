package dev.blocktalk.client.ui;

import dev.blocktalk.client.BlockTalkMod;
import dev.blocktalk.model.Models.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import java.util.*;

public final class MainScreen extends BaseScreen {
    private int channelOffset,userOffset,selectedUser=-1;
    private Button connect,mute,deafen;
    public MainScreen(Screen parent) {super("panel",parent);}
    @Override protected void init() {
        int y=height-29,w=Math.min(95,(width-30)/5);
        connect=button("connect",8,y,w,()->{var s=BlockTalkMod.runtime.view().state();if(s==State.DISCONNECTED||s==State.FAILED)minecraft.setScreen(new ConnectScreen(this));else BlockTalkMod.runtime.disconnect();});
        mute=button("mute",12+w,y,w,()->BlockTalkMod.runtime.mute());
        deafen=button("deafen",16+w*2,y,w,()->BlockTalkMod.runtime.deafen());
        button("settings",20+w*3,y,w,()->minecraft.setScreen(new SettingsScreen(this)));
        button("back",24+w*4,y,w,this::onClose);
        button("local_mute",width/2+4,height-77,Math.min(100,width/2-12),()->{if(selectedUser>=0)BlockTalkMod.runtime.toggleLocalMute(selectedUser);});
        button("volume_down",width/2+4,height-53,50,()->{if(selectedUser>=0)BlockTalkMod.runtime.userVolume(selectedUser,0.5f);});
        button("volume_up",width/2+58,height-53,50,()->{if(selectedUser>=0)BlockTalkMod.runtime.userVolume(selectedUser,1.5f);});
    }
    @Override public void render(GuiGraphics g,int mx,int my,float p) {
        var v=BlockTalkMod.runtime.view();super.render(g,mx,my,p);
        connect.setMessage(tr(v.state()==State.DISCONNECTED||v.state()==State.FAILED?"connect":"disconnect"));
        mute.setMessage(tr(v.muted()?"unmute":"mute"));deafen.setMessage(tr(v.deafened()?"undeafen":"deafen"));
        text(g,tr("state."+v.state().name().toLowerCase(Locale.ROOT)),8,30,width/2-10,0x80ddff);
        text(g,net.minecraft.network.chat.Component.literal(v.server()),width/2,30,width/2-10,0xffffff);
        text(g,tr("channels"),8,47,width/2-10,0xaaaaaa);text(g,tr("users"),width/2+4,47,width/2-12,0xaaaaaa);
        var channels=ordered(v.channels());int rows=Math.max(1,(height-154)/16);
        channelOffset=Math.clamp(channelOffset,0,Math.max(0,channels.size()-rows));
        g.enableScissor(8,62,width/2-5,height-87);
        for(int i=0;i<rows&&i+channelOffset<channels.size();i++) {
            Channel c=channels.get(i+channelOffset);int depth=depth(c,v.channels());
            text(g,net.minecraft.network.chat.Component.literal((c.id()==v.channelId()?"> ":"")+"  ".repeat(Math.min(depth,6))+(c.password()?"* ":"")+c.name()),10,64+i*16,width/2-20,c.id()==v.channelId()?0x80ff80:c.codec()==Codec.UNSUPPORTED?0x999999:0xffffff);
        }
        g.disableScissor();
        var users=v.users().stream().filter(u->u.channel()==v.channelId()).toList();userOffset=Math.clamp(userOffset,0,Math.max(0,users.size()-rows));
        for(int i=0;i<rows&&i+userOffset<users.size();i++) {
            User u=users.get(i+userOffset);
            if(u.id()==selectedUser)g.fill(width/2+3,62+i*16,width-8,77+i*16,0x80336666);
            text(g,net.minecraft.network.chat.Component.literal((u.speaking()?"> ":"")+u.name()+(u.muted()?" [M]":"")+(u.deafened()?" [D]":"")+(BlockTalkMod.runtime.localMute(u.id())?" [L]":"")),width/2+6,64+i*16,width/2-20,u.speaking()?0x80ff80:0xffffff);
        }
        text(g,tr("gate."+v.gateReason()),8,height-72,width/2-15,0xffff80);
        if(!v.error().isEmpty())text(g,tr("error."+v.error()),8,height-53,width/2-15,0xff8080);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0 && y>=62 && y<height-87) {
            var v=BlockTalkMod.runtime.view();int row=(int)((y-62)/16);
            if(x<width/2) {
                var cs=ordered(v.channels());int index=row+channelOffset;
                if(index<cs.size()){Channel c=cs.get(index);if(c.password())minecraft.setScreen(new PasswordScreen(this,p->BlockTalkMod.runtime.join(c.id(),p)));else BlockTalkMod.runtime.join(c.id(),null);return true;}
            } else {var us=v.users().stream().filter(u->u.channel()==v.channelId()).toList();if(row+userOffset<us.size()){selectedUser=us.get(row+userOffset).id();return true;}}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {if(x<width/2)channelOffset=Math.max(0,channelOffset-(int)dy);else userOffset=Math.max(0,userOffset-(int)dy);return true;}
    private static int depth(Channel c,List<Channel> cs) {int n=0,id=c.parent();Set<Integer> seen=new HashSet<>();while(id>0&&seen.add(id)&&n<20){n++;int p=id;id=cs.stream().filter(x->x.id()==p).findFirst().map(Channel::parent).orElse(0);}return n;}
    private static List<Channel> ordered(List<Channel> cs) {List<Channel> out=new ArrayList<>();Set<Integer> seen=new HashSet<>();append(0,cs,out,seen);for(Channel c:cs)if(seen.add(c.id()))out.add(c);return out;}
    private static void append(int parent,List<Channel> cs,List<Channel> out,Set<Integer> seen) {
        List<Channel> siblings=new ArrayList<>(cs.stream().filter(c->c.parent()==parent).toList());int previous=0;
        while(!siblings.isEmpty()) {int p=previous;Channel next=siblings.stream().filter(c->c.order()==p).findFirst().orElse(siblings.getFirst());siblings.remove(next);previous=next.id();if(seen.add(next.id())){out.add(next);if(seen.size()<8192)append(next.id(),cs,out,seen);}}
    }
}
