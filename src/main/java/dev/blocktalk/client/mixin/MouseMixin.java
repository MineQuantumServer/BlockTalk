package dev.blocktalk.client.mixin;

import dev.blocktalk.client.BlockTalkMod;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseMixin {
    @Inject(method="onPress",at=@At("HEAD"))
    private void blocktalk$release(long window,int button,int action,int modifiers,CallbackInfo ci) {
        if(BlockTalkMod.runtime!=null&&action==GLFW.GLFW_RELEASE&&BlockTalkMod.PTT.matchesMouse(button))BlockTalkMod.runtime.gate.key(false);
    }
}
