package dev.blocktalk.client.ui;

import dev.blocktalk.client.BlockTalkMod;
import dev.blocktalk.config.Settings;
import dev.blocktalk.audio.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SettingsScreen extends BaseScreen {
    private Settings draft;
    private int page;
    private volatile List<AudioBackend.Device> inputs=List.of(),outputs=List.of();
    private EditBox nickname;
    public SettingsScreen(Screen parent) {super("settings",parent);draft=BlockTalkMod.runtime.settings();}
    @Override protected void init() {
        int x=width/2-125;
        button("tab."+page,x,34,250,()->{capture();page=(page+1)%5;rebuildWidgets();});
        int y=64;
        if(page==0) {
            nickname=field("nickname",x,y,250,30,draft.nickname);
            button("connect_favorites",x,y+28,250,()->minecraft.setScreen(new ConnectScreen(this)));
            toggle("reconnect",x,y+56,()->draft.reconnect,v->draft.reconnect=v);
            toggle("keep_connection",x,y+84,()->draft.keepConnection,v->draft.keepConnection=v);
        } else if(page==1) {
            CompletableFuture.supplyAsync(()->List.of(new JavaSoundBackend().devices(true),new JavaSoundBackend().devices(false))).thenAccept(devices->{inputs=devices.get(0);outputs=devices.get(1);});
            button("input_device",x,y,250,()->{draft.inputDevice=next(inputs,draft.inputDevice);rebuildWidgets();}).setMessage(tr("selected_input",name(inputs,draft.inputDevice)));
            button("output_device",x,y+24,250,()->{draft.outputDevice=next(outputs,draft.outputDevice);rebuildWidgets();}).setMessage(tr("selected_output",name(outputs,draft.outputDevice)));
            slider("input_gain",x,y+48,draft.inputGain,3,v->draft.inputGain=(float)v);
            slider("output_gain",x,y+72,draft.outputGain,2,v->draft.outputGain=(float)v);
            button("jitter",x,y+96,250,()->{draft.jitterFrames=draft.jitterFrames%8+1;rebuildWidgets();});
            button("mic_test",x,y+120,250,()->BlockTalkMod.runtime.testMicrophone(!BlockTalkMod.runtime.localTest()));
        } else if(page==2) {
            button("key_settings",x,y,250,()->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options)));
            toggle("gui_ptt",x,y+28,()->draft.guiPtt,v->draft.guiPtt=v);
        } else if(page==3) {
            toggle("hud",x,y,()->draft.hud,v->draft.hud=v);
            slider("hud_scale",x,y+24,draft.hudScale/2,1,v->draft.hudScale=Math.max(0.5f,(float)v*2));
            slider("hud_opacity",x,y+48,draft.hudOpacity,1,v->draft.hudOpacity=(float)v);
            slider("hud_x",x,y+72,draft.hudX,Math.max(1,width-180),v->draft.hudX=(int)v);
            slider("hud_y",x,y+96,draft.hudY,Math.max(1,height-100),v->draft.hudY=(int)v);
            button("hud_users",x,y+120,250,()->{draft.hudUsers=draft.hudUsers%12+1;rebuildWidgets();});
        } else {
            BlockTalkMod.runtime.prepareIdentity();
            button("improve_identity",x,y+35,250,()->{if(BlockTalkMod.runtime.improving())BlockTalkMod.runtime.cancelImprove();else BlockTalkMod.runtime.improveIdentity();});
            button("reset_identity",x,y+61,250,()->minecraft.setScreen(new ConfirmScreen(ok->{if(ok)BlockTalkMod.runtime.resetIdentity();minecraft.setScreen(this);},tr("reset_identity"),tr("identity_warning"))));
            button("copy_diagnostics",x,y+87,250,()->minecraft.keyboardHandler.setClipboard(BlockTalkMod.runtime.diagnostics()));
        }
        button("save",x,height-28,78,()->{capture();BlockTalkMod.runtime.testMicrophone(false);BlockTalkMod.runtime.save(draft);onClose();});
        button("cancel",x+86,height-28,78,this::onClose);
        button("defaults",x+172,height-28,78,()->{draft=new Settings();rebuildWidgets();});
    }
    private void capture() {if(nickname!=null&&page==0)draft.nickname=nickname.getValue();nickname=null;}
    private void toggle(String name,int x,int y,java.util.function.BooleanSupplier get,java.util.function.Consumer<Boolean> set) {Button b=button(name,x,y,250,()->{set.accept(!get.getAsBoolean());rebuildWidgets();});b.setMessage(tr(name).copy().append(": ").append(tr(get.getAsBoolean()?"on":"off")));}
    private void slider(String name,int x,int y,double value,double max,java.util.function.DoubleConsumer set) {
        addRenderableWidget(new AbstractSliderButton(x,y,250,20,tr(name),value/max) {
            {updateMessage();}
            protected void updateMessage(){setMessage(tr(name).copy().append(": "+String.format(java.util.Locale.ROOT,"%.2f",this.value*max)));}
            protected void applyValue(){set.accept(this.value*max);}
        });
    }
    private static String next(List<AudioBackend.Device> devices,String id) {if(devices.isEmpty())return id;for(int i=0;i<devices.size();i++)if(devices.get(i).id().equals(id))return devices.get((i+1)%devices.size()).id();return "default";}
    private static String name(List<AudioBackend.Device> ds,String id) {if(id.equals("default"))return tr("system_default").getString();return ds.stream().filter(d->d.id().equals(id)).map(AudioBackend.Device::name).findFirst().orElse(id);}
    @Override public void onClose() {BlockTalkMod.runtime.testMicrophone(false);super.onClose();}
    @Override public void render(GuiGraphics g,int mx,int my,float p) {
        super.render(g,mx,my,p);int x=width/2-125;
        if(page==1) {text(g,tr("audio_status",draft.jitterFrames,(int)(BlockTalkMod.runtime.view().level()*100)),x,53,250,0xaaaaaa);}
        if(page==2) {text(g,tr("privacy_help"),x,135,250,0xffff80);text(g,tr("privacy_help2"),x,151,250,0xffff80);}
        if(page==3)text(g,tr("hud_count",draft.hudUsers),x,53,250,0xaaaaaa);
        if(page==4) {var v=BlockTalkMod.runtime.view();text(g,tr("identity_uid",v.uid()),x,64,250,0xffffff);text(g,tr("identity_level",BlockTalkMod.runtime.securityLevel()),x,80,250,0xaaaaaa);text(g,net.minecraft.network.chat.Component.literal(BlockTalkMod.runtime.nativeStatus()),x,180,250,0xaaaaaa);}
    }
}
