package endorh.smartcompletion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.MultiMatch;
import endorh.smartcompletion.SmartCommandCompletion;
import endorh.smartcompletion.SortedMatchedSuggestions;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
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
import java.util.stream.Collectors;

import static endorh.smartcompletion.SmartCommandCompletion.*;
import static net.minecraft.client.gui.GuiComponent.fill;

/**
 * Mixin for {@link CommandSuggestions.SuggestionsList}, an inner class of
 * {@link CommandSuggestions} which we can't instance directly.<br>
 * <br>
 * This mixin recovers the smart suggestions computed by the
 * {@link MixinCommandSuggestions} mixin, and renders them with smart highlighting.
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class MixinSuggestionsList {
   // Injected fields
   @Unique private @Nullable SmartCommandSuggestions smartcompletion$CommandSuggestions$this = null;
   @Unique private List<Component> smartcompletion$highlightedSuggestions;
   @Unique private boolean smartcompletion$hasUnparsedInput;

   // Shadow accessors
   @Shadow @Final private Rect2i rect;
   @Shadow @Final private List<Suggestion> suggestionList;
   @Shadow private int offset;
   @Shadow private int current;
   @Shadow private Vec2 lastMouse;

   @Shadow public abstract void select(int index);
   @Shadow public abstract void useSuggestion();

   /**
    * Capture outer instance in the constructor, and recover smart suggestions
    * if its mixin duck, {@link SmartCommandSuggestions}, is available.
    */
   @Inject(method = "<init>*", at = @At("RETURN"))
   private void smartcompletion$init(
      CommandSuggestions commandSuggestions, int left, int anchor, int width,
      List<Suggestion> list, boolean bl, CallbackInfo ci
   ) {
      if (!enableSmartCompletion || !(commandSuggestions instanceof SmartCommandSuggestions))
         return;
      // Capture outer instance
      SmartCommandSuggestions scs = (SmartCommandSuggestions) commandSuggestions;
      smartcompletion$CommandSuggestions$this = scs;

      // Recover suggestions
      String lastQuery = scs.getLastArgumentQuery();
      Suggestions blindSuggestions = scs.getLastBlindSuggestions();
      Suggestions wordBlindSuggestions = scs.getLastWordBlindSuggestions();
      Suggestions lastSuggestions = scs.getLastSuggestions();
      smartcompletion$hasUnparsedInput = scs.hasUnparsedInput();
      SortedMatchedSuggestions matches = scs.getLastSuggestionMatches();
      List<Pair<Suggestion, MultiMatch>> sorted = matches != null ? matches.sortedSuggestions() : null;
      if (lastQuery == null || blindSuggestions == null
         || lastSuggestions == null || matches == null || matches.isEmpty()) {
         smartcompletion$CommandSuggestions$this = null;
         return;
      }
      suggestionList.clear();
      sorted.stream().map(Pair::getLeft).forEachOrdered(suggestionList::add);

      // Highlight suggestions
      smartcompletion$highlightedSuggestions = sorted.stream()
         .map(p -> highlightSuggestion(p.getLeft().getText(), p.getRight(), lastQuery))
         .collect(Collectors.toList());

      // Patch positioning
      Font font = scs.getFont();
      EditBox input = scs.getInput();
      int w = smartcompletion$highlightedSuggestions.stream().mapToInt(font::width).max().orElse(0) + 1;
      int minI = Integer.MAX_VALUE;
      if (matches.hasBlindMatches() && blindSuggestions.getRange().getStart() < minI)
         minI = blindSuggestions.getRange().getStart();
      if (wordBlindSuggestions != null && matches.hasWordBlindMatches() && wordBlindSuggestions.getRange().getStart() < minI)
         minI = wordBlindSuggestions.getRange().getStart();
      if (matches.hasDumbMatches() && lastSuggestions.getRange().getStart() < minI)
         minI = lastSuggestions.getRange().getStart();
      int l = Mth.clamp(input.getScreenX(minI), 0, Math.max(0, input.getScreenX(0) + input.getInnerWidth() - w));
      int h = Math.min(suggestionList.size(), scs.getSuggestionLineLimit()) * 12;
      int y = scs.isAnchorToBottom() ? anchor - 3 - h : anchor;
      #if POST_MC_1_17_1
         rect.setX(l);
         rect.setY(y);
         rect.setWidth(w);
         rect.setHeight(h);
      #else
         rect.xPos = l;
         rect.yPos = y;
         rect.width = w;
         rect.height = h;
      #endif
      select(0);
   }

   /**
    * Override {@code render} if smart completion is enabled to support
    * custom highlighting.
    */
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   public void smartcompletion$render(
      PoseStack mStack, int mouseX, int mouseY, CallbackInfo ci
   ) {
      if (!enableSmartCompletion || smartcompletion$CommandSuggestions$this == null) return;
      Font font = Minecraft.getInstance().font;
      Screen screen = Minecraft.getInstance().screen;
      if (screen == null) return;
      ci.cancel();

      int maxSuggestionSize = 10;
      int size = Math.min(suggestionList.size(), maxSuggestionSize);
      int backgroundColor = STYLE.backgroundColor();
      int selectedBackgroundColor = STYLE.selectedBackgroundColor();

      boolean hasBefore = offset > 0;
      boolean hasAfter = suggestionList.size() > offset + size;
      boolean hasMore = hasBefore || hasAfter;
      boolean updatedMouse = lastMouse.x != (float) mouseX || lastMouse.y != (float) mouseY;
      if (updatedMouse) lastMouse = new Vec2((float) mouseX, (float) mouseY);

      if (hasMore) {
         fill(
            mStack, rect.getX(), rect.getY() - 1,
            rect.getX() + rect.getWidth(), rect.getY(), backgroundColor);
         fill(
            mStack, rect.getX(), rect.getY() + rect.getHeight(),
            rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight() + 1, backgroundColor);
         int k;
         if (hasBefore) for (k = 0; k < rect.getWidth(); ++k) {
            if (k % 2 == 0) fill(
               mStack, rect.getX() + k, rect.getY() - 1,
               rect.getX() + k + 1, rect.getY(), 0xFFFFFFFF);
         }

         if (hasAfter) for (k = 0; k < rect.getWidth(); ++k) {
            if (k % 2 == 0) fill(
               mStack, rect.getX() + k, rect.getY() + rect.getHeight(),
               rect.getX() + k + 1, rect.getY() + rect.getHeight() + 1, 0xFFFFFFFF);
         }
      }

      boolean hovered = false;
      for (int i = 0; i < size; ++i) {
         boolean selected = i + offset == current;
         fill(
            mStack, rect.getX(), rect.getY() + 12 * i,
            rect.getX() + rect.getWidth(), rect.getY() + 12 * i + 12,
            selected ? selectedBackgroundColor : backgroundColor);
         if (mouseX > rect.getX() && mouseX < rect.getX() + rect.getWidth() &&
            mouseY > rect.getY() + 12 * i && mouseY < rect.getY() + 12 * i + 12) {
            if (updatedMouse) select(i + offset);
            hovered = true;
         }
         Component text = smartcompletion$highlightedSuggestions.get(i + offset);
         if (selected) text = text.copy().withStyle(STYLE.selected());
         font.drawShadow(
            mStack, text,
            (float) (rect.getX() + 1), (float) (rect.getY() + 2 + 12 * i),
            0xFFAAAAAA);
      }

      if (hovered) {
         Message message = suggestionList.get(current).getTooltip();
         if (message != null)
            screen.renderTooltip(mStack, ComponentUtils.fromMessage(message), mouseX, mouseY);
      }
   }

   /**
    * Handle {@code <Ctrl>+<Space>} and {@code <Enter>} if {@link SmartCommandCompletion#enableCompletionKeys}
    * and {@link SmartCommandCompletion#completeWithEnter} are {@code true}.
    */
   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   public void smartcompletion$keyPressed(
      int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci
   ) {
      if (!enableCompletionKeys || !(smartcompletion$CommandSuggestions$this instanceof CommandSuggestions scs)) return;
      if (current < 0 || current >= suggestionList.size()) return;
      if (keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()
         || completeWithEnter && smartcompletion$hasUnparsedInput && keyCode == GLFW.GLFW_KEY_ENTER) {
         // Accept suggestion
         useSuggestion();
         if (keyCode == GLFW.GLFW_KEY_ENTER) {
            // Hide suggestions (replicate what happens in onUpdateCommandInfo if keepSuggestions is false)
            smartcompletion$CommandSuggestions$this.getInput().setSuggestion(null);
            scs.hide();
         }
         // Mark the input event as handled, and cancel the original method
         ci.cancel();
         ci.setReturnValue(true);
      }
   }
}
