package endorh.smartcompletion.mixin;

import endorh.smartcompletion.customization.SmartCompletionSettings;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static endorh.smartcompletion.SmartCompletionMod.getSmartCompletionSettings;

/**
 * Force suggestions to show when typing the start of any command, including
 * the initial slash ({@code /}), if
 * {@link SmartCompletionSettings#enforce_show_suggestions_on_slash} is {@code true}.
 */
@Mixin(ChatScreen.class)
public abstract class MixinChatScreen extends Screen {
   // Shadow accessors
   @Shadow CommandSuggestions commandSuggestions;

   // Dummy mixin constructor
   protected MixinChatScreen(Component component) {
      super(component);
   }

   /**
    * Re-enable suggestions after the input bar has been edited if
    * {@link SmartCompletionSettings#enforce_show_suggestions_on_slash} is {@code true}.
    */
   @Inject(method = "onEdited", at = @At("RETURN"))
   public void onOnEdited(CallbackInfo ci) {
      if (getSmartCompletionSettings().enforce_show_suggestions_on_slash.get()) {
         commandSuggestions.setAllowSuggestions(true);
         if (commandSuggestions instanceof SmartCommandSuggestions scs) scs.enforceShowSuggestions();
      }
   }

   /**
    * Enable suggestions by default if
    * {@link SmartCompletionSettings#enforce_show_suggestions_on_slash} is {@code true}.
    */
   @Inject(method = "init", at = @At("RETURN"))
   public void onInit(CallbackInfo ci) {
      if (getSmartCompletionSettings().enforce_show_suggestions_on_slash.get()) {
         commandSuggestions.setAllowSuggestions(true);
         if (commandSuggestions instanceof SmartCommandSuggestions scs) scs.enforceShowSuggestions();
      }
   }
}
