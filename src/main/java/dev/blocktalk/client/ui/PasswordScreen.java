package dev.blocktalk.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

final class PasswordScreen extends BaseScreen {
    private final Consumer<String> accepted;
    private EditBox password;
    PasswordScreen(Screen parent,Consumer<String> accepted) {super("password",parent);this.accepted=accepted;}
    @Override protected void init() {
        password=field("password",width/2-110,55,220,128,"");password.setFormatter((s,p)->net.minecraft.util.FormattedCharSequence.forward("•".repeat(s.length()),net.minecraft.network.chat.Style.EMPTY));
        setInitialFocus(password);
        button("connect",width/2-110,90,105,()->{String secret=password.getValue();password.setValue("");accepted.accept(secret);minecraft.setScreen(parent);});
        button("cancel",width/2+5,90,105,this::onClose);
    }
    @Override public void removed() {if(password!=null)password.setValue("");}
}
