package endorh.smartcompletion.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.AggregatedSuggestions;
import endorh.smartcompletion.CommandCompletionQueryHandler;
import endorh.smartcompletion.customization.SmartCompletionSettings;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import endorh.smartcompletion.util.ListWithAttachment;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static endorh.smartcompletion.SmartCompletionMod.getSmartCompletionSettings;

/**
 * Defers the requests for suggestions to a {@link CommandCompletionQueryHandler}
 * if {@link SmartCompletionSettings#enabled} is {@code true}.
 * The query handler may perform multiple requests for a single command and
 * aggregates its suggestions before calling {@link #updateAggregatedSuggestions}
 * to update the displayed suggestions.
 * <br>
 * We then pass the {@link AggregatedSuggestions} to the {@link SuggestionsList}
 * in {@link #smartcompletion$onShowSuggestions}, so that the
 * {@link MixinSuggestionsList} mixin may retrieve them and display them
 * accordingly.<br>
 * <br>
 * Additionally, if {@link SmartCompletionSettings#enable_completion_keys} is
 * {@code true}, we recognize the {@code <Ctrl>+<Space>} command to display the
 * suggestion list, if it is hidden.<br>
 * <br>
 * To access the public methods from this mixin, you may cast a
 * {@link CommandSuggestions} instance to the duck interface
 * {@link SmartCommandSuggestions}.
 * <pre><code>
 *    CommandSuggestions commandSuggestions;
 *    if (commandSuggestions instanceof SmartCommandSuggestions scs)
 *        scs.enforceShowSuggestions();
 * </code></pre>
 */
@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions implements SmartCommandSuggestions {
   // Injected fields
   /**
    * Query handler, responsible from managing the state of pending and received requests
    * for suggestions for the current command.
    */
   @Unique private CommandCompletionQueryHandler smartcompletion$queryHandler;
   /**
    * Combined and sorted matched suggestions for the last parsed command.
    */
   @Unique private @Nullable AggregatedSuggestions smartcompletion$lastAggregatedSuggestions;

   // Shadow accessors
   @Shadow @Final Minecraft minecraft;
   /** Command input bar */
   @Shadow @Final EditBox input;
   @Shadow @Final Font font;
   @Shadow private @Nullable ParseResults<SharedSuggestionProvider> currentParse;
   /** Displayed suggestion list. */
   @Shadow private @Nullable CommandSuggestions.SuggestionsList suggestions;
   @Shadow @Final int suggestionLineLimit;
   @Shadow @Final boolean anchorToBottom;
   @Shadow @Final private Screen screen;
   @Shadow @Final private boolean onlyShowIfCursorPastError;
   /**
    * Set to {@code true} while cycling through suggestions with {@code <Tab>} to freeze the
    * suggestion list until another change is made to the command.
    */
   @Shadow boolean keepSuggestions;
   /**
    * Vanilla field for the suggestions request.<br>
    * We only fill it with a dummy on {@link #smartcompletion$updateAggregatedSuggestions}
    * in order to pass the empty check from {@link #updateUsageInfo}.
    */
   @Shadow private @Nullable CompletableFuture<Suggestions> pendingSuggestions;
   /**
    * List of command usage tips to display when the command is incomplete and
    * no suggestions are available
    */
   @Shadow @Final private List<FormattedCharSequence> commandUsage;

   /**
    * Responsible for updating {@link #commandUsage} and calling {@link #showSuggestions} after
    * the suggestions are received.
    */
   @Shadow protected abstract void updateUsageInfo();
   /**
    * Responsible for creating the {@link SuggestionsList}.
    */
   @Shadow public abstract void showSuggestions(boolean bl);

   /**
    * Create query handler.
    */
   @Inject(method = "<init>", at = @At("RETURN"))
   public void smartcompletion$onInit(
      Minecraft minecraft, Screen screen,
      EditBox editBox, Font font,
      boolean commandsOnly, boolean onlyShowIfCursorPastError,
      int lineStartOffset, int suggestionLineLimit,
      boolean anchorToBottom, int fillColor,
      CallbackInfo ci
   ) {
      smartcompletion$queryHandler = new CommandCompletionQueryHandler(this, minecraft);
   }

   /**
    * Invalidate last aggregated suggestions as soon as the command info is updated.<br>
    * This ensures they never exceed the lifecycle of {@link #currentParse}.
    */
   @Inject(method = "updateCommandInfo", at = @At("HEAD"))
   public void smartcompletion$beforeUpdateCommandInfo(CallbackInfo ci) {
      if (!getSmartCompletionSettings().enabled.get()) return;
      if (currentParse != null && !currentParse.getReader().getString().equals(input.getValue()))
         smartcompletion$lastAggregatedSuggestions = null;
   }

   /**
    * Defer suggestion query requests to the {@link #smartcompletion$queryHandler}.<br>
    * <br>
    * By doing this, we become responsible for calling {@link #updateUsageInfo()} once
    * the suggestions have been received, which we do in
    * {@link #smartcompletion$updateAggregatedSuggestions}.
    */
   @Inject(
      method="updateCommandInfo",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;getCommands()Lcom/mojang/brigadier/CommandDispatcher;"),
      cancellable = true
   ) public void smartcompletion$onUpdateCommandInfo(CallbackInfo ci) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enabled.get()) return;

      ci.cancel();
      assert minecraft.player != null;

      // Mimic logic from the overridden segment of #updateCommandInfo
      CommandDispatcher<SharedSuggestionProvider> commandDispatcher = minecraft.player.connection.getCommands();
      String command = input.getValue();
      ParseResults<SharedSuggestionProvider> parse = currentParse;
      if (currentParse == null) {
         StringReader reader = new StringReader(command);
         if (reader.canRead() && reader.peek() == '/') reader.skip();
         currentParse = parse = commandDispatcher.parse(reader, minecraft.player.connection.getSuggestionsProvider());
      }
      if (!keepSuggestions) smartcompletion$lastAggregatedSuggestions = null;
      int i = input.getCursorPosition();
      int j = onlyShowIfCursorPastError? parse.getReader().getCursor() : 1;

      if (i >= j && !keepSuggestions) {
         // Instead of calling commandDispatcher#getCompletionSuggestions, update the query handler
         smartcompletion$queryHandler.updateQuery(command, i, parse);
      }
   }

   /**
    * Overrides the creation of the {@link SuggestionsList}.<br>
    * Relies on {@link MixinSuggestionsList} retrieving the {@link AggregatedSuggestions} from
    * the {@link ListWithAttachment} argument passed to its constructor.
    */
   @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
   public void smartcompletion$onShowSuggestions(
      boolean narrateFirstEntry, CallbackInfo ci
   ) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      AggregatedSuggestions suggestions = smartcompletion$lastAggregatedSuggestions;
      if (!settings.enabled.get() || suggestions == null) return;

      ci.cancel();
      if (!suggestions.isEmpty()) {
         // Compute dimensions (see #showSuggestions)
         List<Suggestion> sorted = suggestions.sortedSuggestions();
         int width = sorted.stream()
            .mapToInt(p -> font.width(p.getText()))
            .max().orElse(0);
         int left = Mth.clamp(
            input.getScreenX(suggestions.range().getStart()),
            0, input.getScreenX(0) + input.getInnerWidth() - width);
         int anchor = anchorToBottom ? screen.height - 12 : 72;

         // Attach aggregated suggestions for the MixinSuggestionsList to grab them
         List<Suggestion> suggestionList = ListWithAttachment.attach(sorted, suggestions);

         // Create SuggestionsList
         this.suggestions = smartcompletion$newSuggestionsList(
            left, anchor, width, suggestionList, narrateFirstEntry);
      }
   }

   /**
    * Updates the last aggregated suggestions displayed.<br>
    * <br>
    * Updates the {@link #smartcompletion$lastAggregatedSuggestions} field, and calls
    * {@link #updateUsageInfo()}, as would've been called after completing
    * {@link #pendingSuggestions} if we hadn't overridden {@link CommandSuggestions#updateCommandInfo()}.<br>
    * <br>
    * In turn, {@link #updateUsageInfo()} calls {@link #showSuggestions}, which we override
    * on {@link #smartcompletion$onShowSuggestions} to display a patched {@link SuggestionsList}
    * that can display the suggestions properly highlighted.
    * @param suggestions Aggregated suggestions for the last command.
    * @param narrateFirstEntry Whether to immediately trigger the narrator once the
    *                          {@link SuggestionsList} has been created.
    */
   @Override public void smartcompletion$updateAggregatedSuggestions(AggregatedSuggestions suggestions, boolean narrateFirstEntry) {
      // If currentParse has expired, the aggregated suggestions have as well
      if (currentParse == null) return;
      smartcompletion$lastAggregatedSuggestions = suggestions;

      // Fill pendingSuggestions with a dummy for updateUsageInfo to perform its empty check
      pendingSuggestions = suggestions.dummyEmptyEquivalentSuggestions();

      if (commandUsage.isEmpty()) {
         // Call updateUsageInfo, which in turn will call showSuggestions
         updateUsageInfo();
      } else {
         // If commandUsage is empty, unfortunately, calling updateUsageInfo a second time will
         // result in an alignment error in the GUI, so we instead call showSuggestions directly
         showSuggestions(false);
      }
   }

   /**
    * Forces the suggestions list to show again, by triggering {@link #updateUsageInfo()} from
    * {@link #smartcompletion$updateAggregatedSuggestions(AggregatedSuggestions, boolean)}.
    */
   @Override public void smartcompletion$enforceShowSuggestions() {
      AggregatedSuggestions suggestions = smartcompletion$lastAggregatedSuggestions;
      if (suggestions != null) smartcompletion$updateAggregatedSuggestions(suggestions, false);
   }

   /**
    * Show suggestions when completing with {@code <Ctrl>+<Space>} if
    * {@link SmartCompletionSettings#enable_completion_keys} is {@code true}.
    */
   @Inject(method="keyPressed", at=@At("RETURN"), cancellable=true)
   public void smartcompletion$onKeyPressed(
      int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir
   ) {
      // Only handle if input event hasn't been handled (and handling is enabled)
      if (cir.getReturnValueZ()
         || !getSmartCompletionSettings().enable_completion_keys.get()
      ) return;

      boolean handled = false;
      if (keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
         handled = true;
         showSuggestions(true);
      }
      if (handled) cir.setReturnValue(true);
   }

   // Duck implementations
   @Override public boolean smartcompletion$isKeepSuggestions() {
      return keepSuggestions;
   }
   @Override public void smartcompletion$setKeepSuggestions(boolean keepSuggestions) {
      this.keepSuggestions = keepSuggestions;
   }
   @Override public Minecraft smartcompletion$getMinecraft() {
      return minecraft;
   }
   @Override public EditBox smartcompletion$getInput() {
      return input;
   }
   @Override public Font smartcompletion$getFont() {
      return font;
   }
   @Override public int smartcompletion$getSuggestionLineLimit() {
      return suggestionLineLimit;
   }
   @Override public boolean smartcompletion$isAnchorToBottom() {
      return anchorToBottom;
   }
   @Override public @Nullable AggregatedSuggestions smartcompletion$getLastAggregatedSuggestions() {
      return smartcompletion$lastAggregatedSuggestions;
   }

   @Override public boolean smartcompletion$hasUnparsedInput() {
      return currentParse != null && currentParse.getReader().canRead();
   }

   // SuggestionsList constructor accessor
   @Unique private CommandSuggestions.SuggestionsList smartcompletion$newSuggestionsList(
      int left, int anchor, int width, List<Suggestion> list, boolean bl
   ) {
      try {
         //noinspection JavaReflectionInvocation
         return SuggestionList$init.newInstance(this, left, anchor, width, list, bl);
      } catch (InvocationTargetException e) {
         throw new RuntimeException(e.getTargetException());
      } catch (InstantiationException | IllegalAccessException e) {
         throw new ReportedException(CrashReport.forThrowable(
            e, "Could not create SuggestionsList by reflection."));
      }
   }
   @Unique private static final Constructor<CommandSuggestions.SuggestionsList> SuggestionList$init;
   static {
      try {
         //noinspection JavaReflectionMemberAccess
         SuggestionList$init = CommandSuggestions.SuggestionsList.class.getDeclaredConstructor(
            CommandSuggestions.class, int.class, int.class, int.class, List.class, boolean.class);
         SuggestionList$init.setAccessible(true);
      } catch (NoSuchMethodException | RuntimeException e) {
         throw new ReportedException(CrashReport.forThrowable(
            e, "Could not access SuggestionsList constructor by reflection."));
      }
   }
}
