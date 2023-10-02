package endorh.smartcompletion.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.MultiMatch;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import org.apache.commons.lang3.tuple.Pair;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static endorh.smartcompletion.SmartCommandCompletion.enableCompletionKeys;
import static endorh.smartcompletion.SmartCommandCompletion.sort;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions implements SmartCommandSuggestions {
	@Unique private @Nullable CompletableFuture<Suggestions> smartcompletion$pendingBlindSuggestions;
	@Unique private @Nullable CompletableFuture<Suggestions> smartcompletion$dummyPendingSuggestions;
	@Unique private @Nullable String smartcompletion$lastArgumentQuery;
	@Unique private @Nullable StringRange smartcompletion$lastSuggestionsRange;
	@Unique private @Nullable List<Pair<Suggestion, MultiMatch>> smartcompletion$lastSuggestionMatches;
	
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
		if (cursorPosition < currentParse.getContext().getRange().getStart()) return;
		suggestionContext = currentParse.getContext().findSuggestionContext(cursorPosition);
		int startPos = suggestionContext.startPos;
		smartcompletion$lastArgumentQuery = command.substring(startPos, cursorPosition);
		smartcompletion$lastSuggestionsRange = StringRange.between(startPos, command.length());
		String blindCommand = command.substring(0, startPos);
		StringReader reader = new StringReader(blindCommand);
		boolean skipSlash = reader.canRead() && reader.peek() == '/';
		if (!commandsOnly && !skipSlash) return;
		if (skipSlash) reader.skip();
		ParseResults<SharedSuggestionProvider> blindParse = dispatcher.parse(
			reader, minecraft.player.connection.getSuggestionsProvider());
		SuggestionContext<SharedSuggestionProvider> blindSuggestionContext;
		if (cursorPosition < blindParse.getContext().getRange().getStart()) return;
		blindSuggestionContext = blindParse.getContext().findSuggestionContext(cursorPosition);
		if (blindSuggestionContext.startPos != startPos) return;
		smartcompletion$pendingBlindSuggestions = dispatcher.getCompletionSuggestions(blindParse, startPos);
		pendingSuggestions.thenAcceptBoth(smartcompletion$pendingBlindSuggestions, (informed, blind) -> {
			// Force trigger showSuggestions, since we can't create the inner class ourselves
			if (allowSuggestions && smartcompletion$isAutoSuggestions(minecraft) || suggestions != null) {
				if (!informed.isEmpty() || !blind.isEmpty()) {
					smartcompletion$lastSuggestionMatches = sort(
					  blind, informed,
						smartcompletion$lastSuggestionsRange,
						smartcompletion$lastArgumentQuery);
					if (!smartcompletion$lastSuggestionMatches.isEmpty()) {
						if (informed.isEmpty()) {
							smartcompletion$dummyPendingSuggestions = pendingSuggestions;
							pendingSuggestions = smartcompletion$pendingBlindSuggestions;
						}
						showSuggestions(false);
					}
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
		if (smartcompletion$dummyPendingSuggestions != null) {
			pendingSuggestions = smartcompletion$dummyPendingSuggestions;
			smartcompletion$dummyPendingSuggestions = null;
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
	
	@Override public @Nullable String smartcompletion$getLastArgumentQuery() {
		return smartcompletion$lastArgumentQuery;
	}
	
	@Override public int smartcompletion$getSuggestionLineLimit() {
		return suggestionLineLimit;
	}
	
	@Override public boolean smartcompletion$isAnchorToBottom() {
		return anchorToBottom;
	}
	
	@Override public @Nullable Suggestions smartcompletion$getLastBlindSuggestions() {
		return smartcompletion$pendingBlindSuggestions != null && smartcompletion$pendingBlindSuggestions.isDone()
		       ? smartcompletion$pendingBlindSuggestions.join() : null;
	}
	
	@Override public @Nullable Suggestions smartcompletion$getLastSuggestions() {
		return pendingSuggestions != null && pendingSuggestions.isDone()
		       ? pendingSuggestions.join() : null;
	}
	
	@Override public @Nullable List<Pair<Suggestion, MultiMatch>> smartcompletion$getLastSuggestionMatches() {
		return smartcompletion$lastSuggestionMatches;
	}
	
	@Override public boolean smartcompletion$hasUnparsedInput() {
		return currentParse != null && currentParse.getReader().canRead();
	}
	
	@Unique private static boolean smartcompletion$isAutoSuggestions(Minecraft minecraft) {
		#if POST_MC_1_19
			return minecraft.options.autoSuggestions().get();
		#else
			return minecraft.options.autoSuggestions;
		#endif
	}
}
