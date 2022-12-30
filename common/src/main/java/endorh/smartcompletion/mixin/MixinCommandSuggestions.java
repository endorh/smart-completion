package endorh.smartcompletion.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

import static endorh.smartcompletion.SmartCommandCompletion.enableCompletionKeys;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions implements SmartCommandSuggestions {
	@Unique private @Nullable CompletableFuture<Suggestions> pendingBlindSuggestions;
	@Unique private @Nullable CompletableFuture<Suggestions> dummyPendingSuggestions;
	@Unique private @Nullable String lastArgumentQuery;
	@Unique private @Nullable StringRange lastSuggestionsRange;
	
	@Shadow @Final Minecraft minecraft;
	@Shadow @Final EditBox input;
	@Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
	@Shadow private @Nullable ParseResults<SharedSuggestionProvider> currentParse;
	
	@Shadow private @Nullable CommandSuggestions.SuggestionsList suggestions;
	@Shadow public abstract void showSuggestions(boolean bl);
	@Shadow @Final private boolean commandsOnly;
	@Shadow private boolean allowSuggestions;
	@Shadow @Final int suggestionLineLimit;
	@Shadow @Final boolean anchorToBottom;
	
	@Inject(method="updateCommandInfo", at=@At("RETURN"))
	public void onUpdateCommandInfo(CallbackInfo ci) {
		assert minecraft.player != null;
		if (currentParse == null || pendingSuggestions == null) return;
		String command = input.getValue();
		CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.player.connection.getCommands();
		int cursorPosition = input.getCursorPosition();
		SuggestionContext<SharedSuggestionProvider> suggestionContext;
		try {
			suggestionContext = currentParse.getContext().findSuggestionContext(cursorPosition);
		} catch (IllegalStateException e) {
			return;
		}
		int startPos = suggestionContext.startPos;
		lastArgumentQuery = command.substring(startPos, cursorPosition);
		lastSuggestionsRange = StringRange.between(startPos, command.length());
		String blindCommand = command.substring(0, startPos);
		StringReader reader = new StringReader(blindCommand);
		boolean skipSlash = reader.canRead() && reader.peek() == '/';
		if (!commandsOnly && !skipSlash) return;
		if (skipSlash) reader.skip();
		ParseResults<SharedSuggestionProvider> blindParse = dispatcher.parse(reader, minecraft.player.connection.getSuggestionsProvider());
		SuggestionContext<SharedSuggestionProvider> blindSuggestionContext;
		try {
			blindSuggestionContext = blindParse.getContext().findSuggestionContext(cursorPosition);
		} catch (IllegalStateException e) {
			return;
		}
		if (blindSuggestionContext.startPos != startPos) return;
		pendingBlindSuggestions = dispatcher.getCompletionSuggestions(blindParse, startPos);
		pendingSuggestions.thenAcceptBoth(pendingBlindSuggestions, (informed, blind) -> {
			// Force trigger showSuggestions, since we can't create the inner class ourselves
			if (allowSuggestions && isAutoSuggestions(minecraft) || suggestions != null) {
				if (!informed.isEmpty() || !blind.isEmpty()) {
					if (informed.isEmpty()) {
						dummyPendingSuggestions = pendingSuggestions;
						pendingSuggestions = pendingBlindSuggestions;
					}
					showSuggestions(false);
				}
			}
		});
	}
	
	@Inject(
	  method = "showSuggestions",
	  at = @At(
		 value = "INVOKE",
		 target = "Lnet/minecraft/client/gui/components/CommandSuggestions;sortSuggestions(Lcom/mojang/brigadier/suggestion/Suggestions;)Ljava/util/List;")
	) public void adjustSuggestions(CallbackInfo ci) {
		// Restore the original pendingSuggestions, since the empty check has already passed
		if (dummyPendingSuggestions != null) {
			pendingSuggestions = dummyPendingSuggestions;
			dummyPendingSuggestions = null;
		}
	}
	
	@Inject(
	  method = "keyPressed",
	  at = @At(value = "RETURN", ordinal = 2),
	  cancellable = true
	) public void onKeyPressed(
	  int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir
	) {
		if (!enableCompletionKeys) return;
		boolean handled = false;
		if (keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
			handled = true;
			showSuggestions(true);
		}
		if (handled) {
			cir.cancel();
			cir.setReturnValue(true);
		}
	}
	
	@Override public @Nullable String getLastArgumentQuery() {
		return lastArgumentQuery;
	}
	
	@Override public int getSuggestionLineLimit() {
		return suggestionLineLimit;
	}
	
	@Override public boolean isAnchorToBottom() {
		return anchorToBottom;
	}
	
	@Override public @Nullable StringRange getLastArgumentRange() {
		return lastSuggestionsRange;
	}
	
	@Override public @Nullable Suggestions getLastBlindSuggestions() {
		return pendingBlindSuggestions != null && pendingBlindSuggestions.isDone()
		       ? pendingBlindSuggestions.join() : null;
	}
	
	@Override public @Nullable Suggestions getLastSuggestions() {
		return pendingSuggestions != null && pendingSuggestions.isDone()
		       ? pendingSuggestions.join() : null;
	}
	
	@Override public boolean hasUnparsedInput() {
		return currentParse != null && currentParse.getReader().canRead();
	}
	
	private static boolean isAutoSuggestions(Minecraft minecraft) {
		#if POST_MC_1_19_2
			return minecraft.options.autoSuggestions().get();
		#else
			return minecraft.options.autoSuggestions;
		#endif
	}
}
