package dev.blocktalk.client.ui;

import dev.blocktalk.client.BlockTalkMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

abstract class BaseScreen extends Screen {
    final Screen parent;
    BaseScreen(String title,Screen parent) {super(tr(title));this.parent=parent;}
    static Component tr(String key,Object... args) {return Component.translatable("blocktalk."+key,args);}
    Button button(String key,int x,int y,int w,Runnable action) {return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());}
    EditBox field(String key,int x,int y,int w,int max,String value) {EditBox e=new EditBox(font,x,y,w,20,tr(key));e.setMaxLength(max);e.setValue(value);e.setHint(tr(key));addRenderableWidget(e);return e;}
    @Override public boolean isPauseScreen() {return false;}
    @Override public void onClose() {BlockTalkMod.runtime.suspend();minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {super.render(g,mx,my,partial);g.drawCenteredString(font,title,width/2,12,0xffffff);}
    void text(GuiGraphics g,Component c,int x,int y,int max,int color) {g.drawString(font,font.plainSubstrByWidth(c.getString(),Math.max(0,max)),x,y,color);}
}
