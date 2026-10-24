package dev.blocktalk.client.mixin;

import dev.blocktalk.client.BlockTalkMod;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"))
    private void blocktalk$release(long window,int key,int scan,int action,int modifiers,CallbackInfo ci) {
        if(BlockTalkMod.runtime!=null&&action==GLFW.GLFW_RELEASE&&BlockTalkMod.PTT.matches(key,scan))BlockTalkMod.runtime.gate.key(false);
    }
}
