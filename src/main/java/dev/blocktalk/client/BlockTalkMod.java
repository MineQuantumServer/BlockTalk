package dev.blocktalk.client;

import dev.blocktalk.BlockTalkClient;
import dev.blocktalk.client.ui.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Dist on the entry point is physical-side isolation, including on a dedicated server. */
@Mod(value="blocktalk",dist=Dist.CLIENT)
public final class BlockTalkMod {
    public static BlockTalkClient runtime;
    public static final KeyMapping PTT=new KeyMapping("key.blocktalk.ptt",KeyConflictContext.UNIVERSAL,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.blocktalk");
    public static final KeyMapping PANEL=new KeyMapping("key.blocktalk.panel",KeyConflictContext.UNIVERSAL,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.blocktalk");
    public BlockTalkMod(IEventBus bus,ModContainer container) {
        runtime=new BlockTalkClient(FMLPaths.CONFIGDIR.get().resolve("blocktalk"));
        container.registerExtensionPoint(IConfigScreenFactory.class,(IConfigScreenFactory)(mod,parent)->new SettingsScreen(parent));
        bus.addListener((RegisterKeyMappingsEvent e)->{e.register(PTT);e.register(PANEL);});
        bus.addListener((RegisterGuiLayersEvent e)->e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("blocktalk","status"),Hud::render));
        NeoForge.EVENT_BUS.addListener(BlockTalkMod::tick);
        NeoForge.EVENT_BUS.addListener(BlockTalkMod::opening);
        NeoForge.EVENT_BUS.addListener(BlockTalkMod::menu);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->{runtime.suspend();if(!runtime.settings().keepConnection)runtime.disconnect();});
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent e)->runtime.close());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre e)->sample());
    }
    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(PANEL.consumeClick() && mc.screen==null)mc.setScreen(new MainScreen(null));
        sample();
        ClientSmoke.tick();
    }
    private static boolean held(KeyMapping binding) {
        var key=binding.getKey();long window=Minecraft.getInstance().getWindow().getWindow();
        if(key.getValue()<0)return false;
        if(key.getType()==InputConstants.Type.MOUSE)return GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
        return key.getType()==InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window,key.getValue());
    }
    private static void sample() {
        Minecraft mc=Minecraft.getInstance();Screen screen=mc.screen;
        // GUI PTT is an allowlist. Every text-entry screen and every unknown mod screen blocks it.
        boolean allowed=mc.level!=null && (screen==null || runtime.settings().guiPtt && (screen instanceof PauseScreen||screen instanceof MainScreen));
        runtime.input(held(PTT),mc.isWindowActive(),allowed);
    }
    private static void opening(ScreenEvent.Opening e) {runtime.suspend();}
    private static void menu(ScreenEvent.Init.Post e) {
        if(e.getScreen() instanceof TitleScreen||e.getScreen() instanceof PauseScreen) {
            var screen=e.getScreen();e.addListener(Button.builder(Component.literal("BlockTalk"),b->Minecraft.getInstance().setScreen(new MainScreen(screen))).bounds(5,5,88,20).build());
        }
    }
}
