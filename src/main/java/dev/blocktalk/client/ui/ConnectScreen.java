package dev.blocktalk.client.ui;

import dev.blocktalk.client.BlockTalkMod;
import dev.blocktalk.config.Settings;
import dev.blocktalk.model.Models.Server;
import dev.blocktalk.protocol.ServerAddress;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;

public final class ConnectScreen extends BaseScreen {
    private Settings draft;
    private int selected=-1;
    private EditBox name,address,nick,channel;
    private String error="";
    public ConnectScreen(Screen parent) {super("connect_favorites",parent);draft=BlockTalkMod.runtime.settings();}
    @Override protected void init() {
        int x=width/2-120;
        button("favorite_next",x,35,240,()->{if(draft.favorites.isEmpty())return;selected=(selected+1)%draft.favorites.size();Server s=draft.favorites.get(selected);name.setValue(s.name());address.setValue(s.address());nick.setValue(s.nickname());channel.setValue(s.defaultChannel());});
        name=field("name",x,62,240,80,"");address=field("address",x,90,240,253,"");nick=field("nickname",x,118,240,30,draft.nickname);channel=field("default_channel",x,146,240,120,"");
        button("new",x,174,75,()->{selected=-1;name.setValue("");address.setValue("");channel.setValue("");});
        button("save",x+80,174,75,()->{try{Server s=server();if(selected<0){draft.favorites.add(s);selected=draft.favorites.size()-1;}else draft.favorites.set(selected,s);BlockTalkMod.runtime.save(draft);error="saved";}catch(Exception e){error="invalid_address";}});
        button("delete",x+160,174,80,()->{if(selected>=0){draft.favorites.remove(selected);selected=-1;BlockTalkMod.runtime.save(draft);}});
        button("connect",x,202,115,()->{try{Server s=server();minecraft.setScreen(new PasswordScreen(this,p->BlockTalkMod.runtime.connect(s,p)));}catch(Exception e){error="invalid_address";}});
        button("back",x+125,202,115,this::onClose);
    }
    private Server server() {ServerAddress.parse(address.getValue());return new Server(name.getValue().isBlank()?"TeamSpeak":name.getValue(),address.getValue(),nick.getValue(),channel.getValue());}
    @Override public void render(GuiGraphics g,int x,int y,float p) {super.render(g,x,y,p);if(!error.isEmpty())text(g,tr(error),width/2-120,231,240,0xffff80);text(g,tr("address_help"),10,height-15,width-20,0xaaaaaa);}
}
