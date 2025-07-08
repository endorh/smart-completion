package endorh.smartcompletion.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.Suggestion;
import endorh.smartcompletion.AggregatedSuggestions;
import endorh.smartcompletion.MeasuredHighlightedSuggestion;
import endorh.smartcompletion.MultiMatch;
import endorh.smartcompletion.customization.SmartCompletionSettings;
import endorh.smartcompletion.customization.SmartCompletionSettings.SuggestionStyleSettings;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import endorh.smartcompletion.util.ListWithAttachment;
import endorh.smartcompletion.util.PolyFill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
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

import java.util.ArrayList;
import java.util.List;

import static endorh.smartcompletion.SmartCommandCompletion.SUGGESTION_STARTS_SUB_NODE;
import static endorh.smartcompletion.SmartCommandCompletion.highlightSuggestion;
import static endorh.smartcompletion.SmartCompletionMod.getSmartCompletionSettings;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Mixin for {@link CommandSuggestions.SuggestionsList}, inner class of
 * {@link CommandSuggestions}.<br>
 * <br>
 * This mixin recovers the {@link AggregatedSuggestions} from the
 * {@link MixinCommandSuggestions} mixin, and renders them with smart highlighting.<br>
 * <br>
 * It also provides support for completion keys if {@link SmartCompletionSettings#enable_completion_keys}
 * is {@code true}, inverts the suggestion list if {@link SmartCompletionSettings#invert_suggestion_order}
 * is {@code true}, and prevents the mouse from selecting a suggestion when new suggestions are displayed
 * under it, without the mouse moving in the first place.<br>
 * <br>
 * In addition, it also can delete the input after the cursor depending
 * on the input method used to accept a suggestion.
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class MixinSuggestionsList {
   // Injected fields
   /** Outer class {@code this} instance, stored for convenience. */
   @Unique private @Nullable SmartCommandSuggestions smartcompletion$CommandSuggestions$this = null;
   /** Pre-highlighted suggestions, computed on {@link #onInit}. */
   @Unique private List<MeasuredHighlightedSuggestion> smartcompletion$highlightedSuggestions;
   /**
    * Whether the command these suggestions target has unparsed input.<br>
    * Controls whether the {@code <Enter>} key may be used to accept suggestions.
    */
   @Unique private boolean smartcompletion$hasUnparsedInput;
   /**
    * Used to detect from {@link #useSuggestion} which was the input that triggered the
    * suggestion.
    */
   @Unique private int smartcompletion$lastInputCode = -1;

   // Shadow accessors
   /** Rect with the drawing coordinates for the list. */
   @Shadow @Final private Rect2i rect;
   /** Vanilla's list of suggestions. */
   @Shadow @Final private List<Suggestion> suggestionList;
   /** Scroll offset within the suggestions list. */
   @Shadow private int offset;
   /**
    * Index of the currently selected suggestion.
    */
   @Shadow private int current;
   /**
    * Last known position of the mouse, or {@link Vec2#ZERO} on the first frame.
    */
   @Shadow private Vec2 lastMouse;
   /**
    * Whether pressing {@code <Tab>} or {@code <Shift>+<Tab>} should cycle beyond the
    * end of the list.
    */
   @Shadow boolean tabCycles;

   /**
    * Cycle through the suggestion list.
    */
   @Shadow public abstract void cycle(int step);
   /**
    * Select a given suggestion.
    */
   @Shadow public abstract void select(int index);
   /**
    * Insert the selected suggestion in the command bar.
    */
   @Shadow public abstract void useSuggestion();

   /**
    * Capture outer instance in the constructor, and recover smart suggestions
    * from the {@link ListWithAttachment} passed from {@link MixinCommandSuggestions}.<br>
    * <br>
    * The {@link #rect} is also patched to adjust to the highlighted suggestions,
    * in case their size differs from the unhighlighted ones.
    */
   @Inject(method = "<init>*", at = @At("RETURN"))
   private void onInit(
      CommandSuggestions commandSuggestions,
      int left, int anchor, int width,
      List<Suggestion> list,
      boolean narrateFirstEntry,
      CallbackInfo ci
   ) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enabled.get()
         || !settings.enable_suggestion_highlighting.get()
         || !(commandSuggestions instanceof SmartCommandSuggestions scs)
      ) return;
      if (!(list instanceof ListWithAttachment<?, ?>)) return;
      // Capture outer instance and injected parameter
      smartcompletion$CommandSuggestions$this = scs;
      ListWithAttachment<Suggestion, AggregatedSuggestions> lwa = (ListWithAttachment<Suggestion, AggregatedSuggestions>) list;
      AggregatedSuggestions matches = lwa.getAttachment();

      // Recover suggestions
      smartcompletion$hasUnparsedInput = scs.hasUnparsedInput();

      // Highlight suggestions
      int minStart = Integer.MAX_VALUE;
      int maxStart = 0;
      List<Suggestion> sortedSuggestions = matches.sortedSuggestions();
      List<MultiMatch> sortedSuggestionMatches = matches.sortedSuggestionMatches();
      smartcompletion$highlightedSuggestions = new ArrayList<>(sortedSuggestions.size());
      for (Suggestion suggestion : sortedSuggestions) {
         int start = suggestion.getRange().getStart();
         if (start < minStart) minStart = start;
         if (start > maxStart) maxStart = start;
      }

      // Patch positioning
      Font font = scs.getFont();
      EditBox input = scs.getInput();
      String argQuery = matches.argQuery();
      String command = input.getValue();
      String trimmedCommand = command.substring(min(command.length(), minStart));
      int diff = trimmedCommand.length() - (maxStart - minStart);
      if (diff > 0) trimmedCommand += " ".repeat(diff);

      int maxWidth = 0;
      for (int i = 0; i < sortedSuggestions.size(); i++) {
         Suggestion suggestion = sortedSuggestions.get(i);
         MultiMatch match = sortedSuggestionMatches.get(i);
         Component highlighted = highlightSuggestion(suggestion.getText(), match, argQuery);
         int relStart = suggestion.getRange().getStart() - minStart;
         int horizontalOffset = font.width(trimmedCommand.substring(0, relStart));
         smartcompletion$highlightedSuggestions.add(new MeasuredHighlightedSuggestion(
            highlighted, horizontalOffset));
         int effectiveWidth = font.width(highlighted) + horizontalOffset;
         if (effectiveWidth > maxWidth) maxWidth = effectiveWidth;
      }

      // Account for margin
      int w = maxWidth + 2;
      int boxStart = matches.range().getStart();
      int l = Mth.clamp(input.getScreenX(boxStart), 0, max(0, input.getScreenX(0) + input.getInnerWidth() - w));
      int h = min(suggestionList.size(), scs.getSuggestionLineLimit()) * 12;
      int y = scs.isAnchorToBottom() ? anchor - 3 - h : anchor;
      rect.setX(l);
      rect.setY(y);
      rect.setWidth(w);
      rect.setHeight(h);
      select(0);
   }

   /**
    * Override {@link SuggestionsList#render(GuiGraphics, int, int)} if
    * {@link SmartCompletionSettings#enabled} and
    * {@link SmartCompletionSettings#enable_suggestion_highlighting} are {@code true}.<br>
    * <br>
    * The code is mostly identical to that of the original method, but the colors and text
    * styles are loaded from the {@link SuggestionStyleSettings}, and we prevent the mouse
    * from selecting a suggestion from a suggestion list that has been just created,
    * without the mouse moving at all.
    */
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   public void onRender(
      GuiGraphics gg, int mouseX, int mouseY, CallbackInfo ci
   ) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (smartcompletion$CommandSuggestions$this == null
         || !settings.enabled.get()
         || !settings.enable_suggestion_highlighting.get()) return;
      Font font = Minecraft.getInstance().font;
      Screen screen = Minecraft.getInstance().screen;
      if (screen == null) return;
      ci.cancel();

      SuggestionStyleSettings style = settings.style;
      int maxSuggestionSize = smartcompletion$CommandSuggestions$this.getSuggestionLineLimit();
      int size = min(suggestionList.size(), maxSuggestionSize);
      int backgroundColor = style.background_color.get();
      int selectedBackgroundColor = style.background_selected_color.get();
      int ellipsisColor = style.ellipsis_color.get();

      int left = rect.getX();
      int right = rect.getX() + rect.getWidth();
      boolean reversed = settings.invert_suggestion_order.get() && smartcompletion$CommandSuggestions$this.isAnchorToBottom();
      boolean hasBefore = offset > 0;
      boolean hasAfter = suggestionList.size() > offset + size;
      boolean hasMore = hasBefore || hasAfter;
      boolean updatedMouse = lastMouse.x != (float) mouseX || lastMouse.y != (float) mouseY;
      if (updatedMouse) {
         // Suppress unintended mouse selection in the first frame
         if (lastMouse.x == 0 && lastMouse.y == 0) updatedMouse = false;
         lastMouse = new Vec2((float) mouseX, (float) mouseY);
      }

      // Draw ellipsis on top/below if there are suggestions not shown
      if (hasMore) {
         int k;

         // Top
         int y = rect.getY();
         gg.fill(left, y - 1, right, y, backgroundColor);
         if (reversed? hasAfter : hasBefore) for (k = 0; k < rect.getWidth(); k += 2) gg.fill(
            left + k, y - 1,
            left + k + 1, y, ellipsisColor);

         // Bottom
         y += rect.getHeight();
         gg.fill(left, y, right, y + 1, backgroundColor);
         if (reversed? hasBefore : hasAfter) for (k = 0; k < rect.getWidth(); k += 2) gg.fill(
            left + k, y,
            left + k + 1, y + 1, ellipsisColor);
      }

      // Draw suggestions
      boolean hovered = false;
      boolean mouseXInRange = mouseX > rect.getX() && mouseX < rect.getX() + rect.getWidth();
      int y = reversed? rect.getY() + rect.getHeight() : rect.getY() - 12;
      int yStep = reversed? -12 : 12;
      for (int i = 0; i < size; ++i) {
         boolean selected = i + offset == current;
         y += yStep;

         // Draw background
         gg.fill(left, y, right, y + 12, selected? selectedBackgroundColor : backgroundColor);

         // Check if hovered
         if (mouseXInRange && mouseY >= y && mouseY < y + 12) {
            if (updatedMouse) select(i + offset);
            hovered = true;
         }

         // Draw suggestion text
         MeasuredHighlightedSuggestion suggestion = smartcompletion$highlightedSuggestions.get(i + offset);
         Component text = suggestion.component();
         if (selected) text = text.copy().withStyle(style.selected.get());
         gg.drawString(font, text, left + 1 + suggestion.horizontalOffset(), y + 2, 0xFFAAAAAA);
      }

      if (hovered) {
         Message message = suggestionList.get(current).getTooltip();
         if (message != null)
            PolyFill.setTooltipForNextFrame(gg, font, ComponentUtils.fromMessage(message), mouseX, mouseY);
      }
   }

   /**
    * Enforce the suggestions to be updated when accepting the only available suggestion,
    * or when the used suggestion is a sub-word starting suggestion (e.g.: {@code [} or <code>{</code>).<br>
    * <br>
    * As an exception, we avoid this behavior when accepting the first suggestion and the second one
    * starts with the same character followed by a space, as this prevents cycling to the second
    * suggestion with {@code Tab}.
    */
   @Inject(
      method="useSuggestion",
      at=@At(
         value="FIELD",
         target="Lnet/minecraft/client/gui/components/CommandSuggestions;input:Lnet/minecraft/client/gui/components/EditBox;",
         ordinal=0)
   ) public void onUseSuggestion(CallbackInfo ci) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enabled.get() || smartcompletion$CommandSuggestions$this == null) return;
      Suggestion suggestion = suggestionList.get(current);
      String suggestionText = suggestion.getText();
      if (suggestionList.size() == 1 || SUGGESTION_STARTS_SUB_NODE.test(suggestionText)) {
         if (current == 0 && suggestionList.size() > 1) {
            Suggestion second = suggestionList.get(1);
            if (second.getRange().getStart() == suggestion.getRange().getStart()
               && second.getText().startsWith(suggestionText + " ")
            ) return;
         }
         smartcompletion$CommandSuggestions$this.setKeepSuggestions(false);
      }
   }

   /**
    * Erases the remainder of the input after the cursor depending on the input method
    * used to accept the suggestion.
    */
   @Inject(
      method="useSuggestion",
      at=@At("RETURN")
   ) public void afterUseSuggestion(CallbackInfo ci) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enabled.get() || !settings.enable_completion_keys.get() || smartcompletion$CommandSuggestions$this == null) return;
      if (switch (smartcompletion$lastInputCode) {
         case GLFW.GLFW_KEY_SPACE -> settings.erase_remainder_on_ctrl_space.get();
         case GLFW.GLFW_KEY_ENTER -> settings.erase_remainder_on_enter.get();
         case GLFW.GLFW_KEY_TAB -> settings.erase_remainder_on_tab.get();
         case   - 100 -> settings.erase_remainder_on_left_click.get();
         case 1 - 100 -> settings.erase_remainder_on_right_click.get();
         case 2 - 100 -> settings.erase_remainder_on_middle_click.get();
         default -> false;
      }) {
         EditBox input = smartcompletion$CommandSuggestions$this.getInput();
         int pos = input.getCursorPosition();
         String command = input.getValue();
         if (command.length() > pos) input.setValue(command.substring(0, pos));
      }
   }

   /**
    * Handle {@code <Ctrl>+<Space>} and {@code <Enter>} if {@link SmartCompletionSettings#enable_completion_keys}
    * and {@link SmartCompletionSettings#enable_completion_with_enter} are {@code true}.<br>
    * <br>
    * Additionally, invert the behavior of pressing {@code up} or {@code down} when the suggestion list
    * is inverted.
    */
   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   public void onKeyPressed(
      int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci
   ) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enable_completion_keys.get() || !(smartcompletion$CommandSuggestions$this instanceof CommandSuggestions cs)) return;
      if (current < 0 || current >= suggestionList.size()) return;
      smartcompletion$lastInputCode = keyCode;

      // Handle completion keys
      if (keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()
         || settings.enable_completion_with_enter.get() && smartcompletion$hasUnparsedInput && keyCode == GLFW.GLFW_KEY_ENTER) {
         // Accept suggestion
         useSuggestion();

         if (keyCode == GLFW.GLFW_KEY_ENTER) {
            // Hide suggestions (replicate what happens in onUpdateCommandInfo if keepSuggestions is false)
            smartcompletion$CommandSuggestions$this.getInput().setSuggestion(null);
            cs.hide();
         }
         // Mark the input event as handled
         ci.setReturnValue(true);
      }

      // Invert up-down keys when inverting suggestion order
      if (smartcompletion$shouldInvertSuggestionList()) {
         if (keyCode == GLFW.GLFW_KEY_DOWN) {
            cycle(-1); // Cycle up
            tabCycles = false;
            // Mark the input event as handled
            ci.setReturnValue(true);
         } else if (keyCode == GLFW.GLFW_KEY_UP) {
            cycle(1); // Cycle down
            tabCycles = false;
            // Mark the input event as handled
            ci.setReturnValue(true);
         }
      }
   }

   /**
    * Invert the behavior of the scroll wheel when inverting the suggestion order.
    */
   @Inject(method="mouseScrolled", at=@At("HEAD"), cancellable = true)
   public void onMouseScrolled(double amount, CallbackInfoReturnable<Boolean> cir) {
      if (!smartcompletion$shouldInvertSuggestionList()) return;
      assert smartcompletion$CommandSuggestions$this != null;

      // Same logic as SuggestionsList#mouseScrolled
      Minecraft minecraft = smartcompletion$CommandSuggestions$this.getMinecraft();
      Window window = minecraft.getWindow();
      MouseHandler mouseHandler = minecraft.mouseHandler;
      int x = (int) (mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth());
      int y = (int) (mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight());
      if (rect.contains(x, y)) {
         offset = Mth.clamp((int) ((double) offset + amount), 0, max(0, suggestionList.size() - smartcompletion$CommandSuggestions$this.getSuggestionLineLimit()));
         cir.setReturnValue(true);
      }
   }

   /**
    * Correct selected entry under the mouse when inverting the suggestion order.
    */
   @Inject(method="mouseClicked", at=@At("HEAD"), cancellable = true)
   public void onMouseClick(int mouseX, int mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (!settings.enable_completion_keys.get() || !(smartcompletion$CommandSuggestions$this instanceof CommandSuggestions)) return;
      smartcompletion$lastInputCode = button - 100;
      if (!smartcompletion$shouldInvertSuggestionList()) return;

      if (!rect.contains(mouseX, mouseY)) return;
      int i = offset + (rect.getY() + rect.getHeight() - mouseY - 1) / 12;
      if (i >= 0 && i < suggestionList.size()) {
         select(i);
         useSuggestion();
      }
      cir.setReturnValue(true);
   }

   @Unique private boolean smartcompletion$shouldInvertSuggestionList() {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      return smartcompletion$CommandSuggestions$this != null
         && settings.enabled.get()
         && settings.invert_suggestion_order.get()
         && settings.enable_suggestion_highlighting.get()
         && smartcompletion$CommandSuggestions$this.isAnchorToBottom();
   }
}
