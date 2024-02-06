package endorh.smartcompletion.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.SmartCommandCompletion;
import endorh.smartcompletion.SortedMatchedSuggestions;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static endorh.smartcompletion.SmartCommandCompletion.enableCompletionKeys;
import static endorh.smartcompletion.SmartCommandCompletion.sort;

/**
 * Sends additional suggestion requests to the command dispatcher (which may be a remote server,
 * the integrated server or the dispatcher for client commands), to obtain all base suggestions
 * for the current argument (without the partially typed query), and for the current word
 * (removing the last partially typed word in a multi-word argument (denoted by spaces)).<br>
 * <br>
 * All the suggestions are eventually combined, filtered and sorted by
 * {@link SmartCommandCompletion#sort}.<br>
 * <br>
 * The {@link MixinSuggestionsList} mixin then retrieves the combined suggestions from the duck
 * for this mixin, {@link SmartCommandSuggestions}, and displays them accordingly.
 */
@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions implements SmartCommandSuggestions {
   // Injected fields
   @Unique private @Nullable CompletableFuture<Suggestions> smartcompletion$pendingBlindSuggestions;
   @Unique private @Nullable CompletableFuture<Suggestions> smartcompletion$pendingWordBlindSuggestions;
   @Unique private @Nullable CompletableFuture<Suggestions> smartcompletion$dummyPendingSuggestions;
   @Unique private @Nullable String smartcompletion$lastArgumentQuery;
   @Unique private @Nullable String smartcompletion$lastWordQuery;
   @Unique private @Nullable StringRange smartcompletion$lastSuggestionsRange;
   @Unique private @Nullable StringRange smartcompletion$lastWordSuggestionsRange;
   @Unique private @Nullable SortedMatchedSuggestions smartcompletion$lastSuggestionMatches;

   // Shadowed accessors
   @Shadow @Final Minecraft minecraft;
   @Shadow @Final EditBox input;
   @Shadow @Final Font font;
   @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow private @Nullable ParseResults<SharedSuggestionProvider> currentParse;

   @Shadow private @Nullable CommandSuggestions.SuggestionsList suggestions;
   @Shadow @Final private boolean commandsOnly;
   @Shadow private boolean allowSuggestions;
   @Shadow @Final int suggestionLineLimit;
   @Shadow @Final boolean anchorToBottom;
   @Shadow public abstract void showSuggestions(boolean bl);

   /**
    * Send an additional <em>blind</em> suggestions request to the command dispatcher,
    * requesting suggestions from the context of the same command without the partially typed
    * query for the last argument, and an optional additional <em>word-blind</em> request,
    * lacking the last partially typed word if the current query contains more than one word.
    *
    * @see #smartcompletion$onShowSuggestions$onSortSuggestions
    */
   @Inject(method = "updateCommandInfo", at = @At("RETURN"))
   public void smartcompletion$onUpdateCommandInfo(CallbackInfo ci) {
      assert minecraft.player != null;
      if (currentParse == null || pendingSuggestions == null) return;

      String command = input.getValue();
      CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.player.connection.getCommands();
      int cursorPosition = input.getCursorPosition();
      SuggestionContext<SharedSuggestionProvider> suggestionContext;
      if (cursorPosition < currentParse.getContext().getRange().getStart()) return;
      suggestionContext = currentParse.getContext().findSuggestionContext(cursorPosition);

      // Blind query
      int startPos = suggestionContext.startPos;
      String blindCommand = command.substring(0, startPos);
      String lastArgumentQuery = command.substring(startPos, cursorPosition);
      smartcompletion$lastArgumentQuery = lastArgumentQuery;
      int lastWordPos = startPos + 1 + lastArgumentQuery.lastIndexOf(0x20); // U+0020: SPACE
      smartcompletion$lastSuggestionsRange = StringRange.between(startPos, command.length());
      StringReader blindReader = new StringReader(blindCommand);

      // Word blind query
      String wordBlindCommand = null;
      StringReader wordBlindReader = null;
      boolean hasWordBlind = lastWordPos > startPos;
      if (hasWordBlind) {
         wordBlindCommand = command.substring(0, lastWordPos);
         smartcompletion$lastWordQuery = command.substring(lastWordPos, cursorPosition);
         smartcompletion$lastWordSuggestionsRange = StringRange.between(lastWordPos, command.length());
         wordBlindReader = new StringReader(wordBlindCommand);
      } else {
         smartcompletion$lastWordQuery = null;
         smartcompletion$lastWordSuggestionsRange = null;
      }

      // Abort if not a command and skip command slash
      boolean skipSlash = blindReader.canRead() && blindReader.peek() == '/';
      if (!commandsOnly && !skipSlash) return;
      if (skipSlash) {
         blindReader.skip();
         if (hasWordBlind) wordBlindReader.skip();
      }

      // Create blind context
      ParseResults<SharedSuggestionProvider> blindParse = dispatcher.parse(
         blindReader, minecraft.player.connection.getSuggestionsProvider());
      SuggestionContext<SharedSuggestionProvider> blindSuggestionContext;
      if (cursorPosition < blindParse.getContext().getRange().getStart()) return;
      blindSuggestionContext = blindParse.getContext().findSuggestionContext(cursorPosition);
      if (blindSuggestionContext.startPos != startPos) return;

      // Create partially blind context
      ParseResults<SharedSuggestionProvider> wordBlindParse = null;
      if (hasWordBlind) {
         wordBlindParse = dispatcher.parse(
            wordBlindReader, minecraft.player.connection.getSuggestionsProvider());
         if (cursorPosition < wordBlindParse.getContext().getRange().getStart()) return;
      }

      // Await all results
      CompletableFuture<Suggestions> blindSuggestions = dispatcher.getCompletionSuggestions(blindParse, startPos);
      CompletableFuture<Suggestions> wordBlindSuggestions = null;
      CompletableFuture<Pair<Suggestions, Optional<Suggestions>>> combinedBlindSuggestions;
      if (hasWordBlind) {
         wordBlindSuggestions = dispatcher.getCompletionSuggestions(wordBlindParse, lastWordPos);
         combinedBlindSuggestions = blindSuggestions.thenCombine(wordBlindSuggestions, (blind, wordBlind) -> Pair.of(blind, Optional.of(wordBlind)));
      } else {
         combinedBlindSuggestions = blindSuggestions.thenApply(blind -> Pair.of(blind, Optional.empty()));
      }
      smartcompletion$pendingBlindSuggestions = blindSuggestions;
      smartcompletion$pendingWordBlindSuggestions = wordBlindSuggestions;
      pendingSuggestions.thenAcceptBoth(combinedBlindSuggestions, (informed, combinedBlind) -> {
         if (allowSuggestions && smartcompletion$isAutoSuggestions(minecraft) || suggestions != null) {
            Suggestions blind = combinedBlind.getLeft();
            @Nullable Suggestions wordBlind = combinedBlind.getRight().orElse(null);
            // Force trigger showSuggestions, since we can't create the inner class ourselves
            if (!informed.isEmpty() || !blind.isEmpty() || wordBlind != null && !wordBlind.isEmpty()) {
               smartcompletion$lastSuggestionMatches = sort(
                  blind, wordBlind, informed,
                  smartcompletion$lastSuggestionsRange,
                  smartcompletion$lastArgumentQuery,
                  smartcompletion$lastWordSuggestionsRange,
                  smartcompletion$lastWordQuery);
               // If there are matches, show suggestions ensuring the empty check within `showSuggestions` passes
               if (!smartcompletion$lastSuggestionMatches.isEmpty()) {
                  if (informed.isEmpty()) {
                     // Temporarily swap pendingSuggestions with a non-empty instance to pass the check
                     if (blind.isEmpty() && wordBlind != null) {
                        smartcompletion$dummyPendingSuggestions = pendingSuggestions;
                        pendingSuggestions = smartcompletion$pendingWordBlindSuggestions;
                     } else {
                        smartcompletion$dummyPendingSuggestions = pendingSuggestions;
                        pendingSuggestions = smartcompletion$pendingBlindSuggestions;
                     }
                  }
                  showSuggestions(false);
               }
            }
         }
      });
   }

   /**
    * Restore {@link #pendingSuggestions} once the empty check within
    * {@link CommandSuggestions#showSuggestions} has passed.
    *
    * @see #smartcompletion$onUpdateCommandInfo
    */
   @Inject(
      method = "showSuggestions",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/CommandSuggestions;sortSuggestions(Lcom/mojang/brigadier/suggestion/Suggestions;)Ljava/util/List;")
   ) public void smartcompletion$onShowSuggestions$onSortSuggestions(CallbackInfo ci) {
      // Restore the original pendingSuggestions, once the empty check within `showSuggestions` has passed
      if (smartcompletion$dummyPendingSuggestions != null) {
         pendingSuggestions = smartcompletion$dummyPendingSuggestions;
         smartcompletion$dummyPendingSuggestions = null;
      }
   }

   /**
    * Show suggestions when completing with {@code <Ctrl>+<Space>} if
    * {@link SmartCommandCompletion#enableCompletionKeys} is {@code true}.
    */
   @Inject(
      method = "keyPressed",
      at = @At(value = "RETURN", ordinal = 2),
      cancellable = true
   ) public void smartcompletion$onKeyPressed(
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

   // Duck implementations
   @Override public EditBox smartcompletion$getInput() {
      return input;
   }
   @Override public Font smartcompletion$getFont() {
      return font;
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

   @Override public @Nullable Suggestions smartcompletion$getLastWordBlindSuggestions() {
      return smartcompletion$pendingWordBlindSuggestions != null && smartcompletion$pendingWordBlindSuggestions.isDone()
         ? smartcompletion$pendingWordBlindSuggestions.join() : null;
   }

   @Override public @Nullable Suggestions smartcompletion$getLastSuggestions() {
      return pendingSuggestions != null && pendingSuggestions.isDone()
         ? pendingSuggestions.join() : null;
   }

   @Override public @Nullable SortedMatchedSuggestions smartcompletion$getLastSuggestionMatches() {
      return smartcompletion$lastSuggestionMatches;
   }

   @Override public boolean smartcompletion$hasUnparsedInput() {
      return currentParse != null && currentParse.getReader().canRead();
   }

   /**
    * Check if the auto-suggestions option is enabled.
    */
   @Unique private static boolean smartcompletion$isAutoSuggestions(Minecraft minecraft) {
      return minecraft.options.autoSuggestions().get();
   }
}
