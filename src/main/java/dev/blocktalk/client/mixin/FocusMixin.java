package dev.blocktalk.client.mixin;

import dev.blocktalk.client.BlockTalkMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class FocusMixin {
    @Inject(method="setWindowActive",at=@At("HEAD"))
    private void blocktalk$focus(boolean focused,CallbackInfo ci) {
        if(BlockTalkMod.runtime!=null&&!focused)BlockTalkMod.runtime.focusLost();
    }
}
