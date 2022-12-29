package endorh.smartcompletion.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static endorh.smartcompletion.SmartCommandCompletion.showSuggestionsOnSlash;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen extends Screen {
	@Shadow CommandSuggestions commandSuggestions;
	
	// Dummy mixin constructor
	protected MixinChatScreen(Component component) {
		super(component);
	}
	
	@Inject(method="onEdited", at=@At("RETURN"))
	public void onOnEdited(CallbackInfo ci) {
		if (showSuggestionsOnSlash) commandSuggestions.setAllowSuggestions(true);
	}
	
	@Inject(method="init", at=@At("RETURN"))
	public void onInit(CallbackInfo ci) {
		if (showSuggestionsOnSlash) commandSuggestions.setAllowSuggestions(true);
	}
}
