package endorh.smartcompletion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.MultiMatch;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.phys.Vec2;
import org.apache.commons.lang3.tuple.Pair;
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

@Mixin(SuggestionsList.class)
public abstract class MixinSuggestionsList {
	@Unique private String lastArgumentQuery;
	@Unique private List<Component> highlightedSuggestions;
	@Unique private boolean hasUnparsedInput;
	
	@Shadow private @Final Rect2i rect;
	@Shadow private @Final List<Suggestion> suggestionList;
	@Shadow private int offset;
	@Shadow private int current;
	@Shadow private Vec2 lastMouse;
	
	@Shadow public abstract void select(int index);
	@Shadow public abstract void useSuggestion();
	
	@Inject(method = "<init>", at = @At("RETURN"))
	private void onInit(
	  CommandSuggestions commandSuggestions, int left, int anchor, int width,
	  List<Suggestion> list, boolean bl, CallbackInfo ci
	) {
		if (!enableSmartCompletion || !(commandSuggestions instanceof SmartCommandSuggestions scs))
			return;
		lastArgumentQuery = scs.getLastArgumentQuery();
		Suggestions blindSuggestions = scs.getLastBlindSuggestions();
		Suggestions lastSuggestions = scs.getLastSuggestions();
		StringRange range = scs.getLastArgumentRange();
		hasUnparsedInput = scs.hasUnparsedInput();
		if (lastArgumentQuery == null || blindSuggestions == null
		    || lastSuggestions == null || range == null) return;
		List<Pair<Suggestion, MultiMatch>> sorted = sort(
		  blindSuggestions, lastSuggestions, range, lastArgumentQuery);
		suggestionList.clear();
		sorted.stream().map(Pair::getLeft).forEachOrdered(suggestionList::add);
		highlightedSuggestions = sorted.stream()
		  .map(p -> highlightSuggestion(p.getLeft().getText(), p.getRight(), lastArgumentQuery))
		  .collect(Collectors.toList());
		
		// Patch positioning
		Font font = Minecraft.getInstance().font;
		int h = Math.min(suggestionList.size(), scs.getSuggestionLineLimit()) * 12;
		int w = highlightedSuggestions.stream().mapToInt(font::width).max().orElse(0) + 1;
		int y = scs.isAnchorToBottom()? anchor - 3 - h : anchor;
		rect.setY(y);
		rect.setWidth(w);
		rect.setHeight(h);
	}
	
	@Inject(method="render", at=@At("HEAD"), cancellable=true)
	public void onRender(
	  PoseStack mStack, int mouseX, int mouseY, CallbackInfo ci
	) {
		if (!enableSmartCompletion || lastArgumentQuery == null) return;
		Font font = Minecraft.getInstance().font;
		Screen screen = Minecraft.getInstance().screen;
		if (screen == null) return;
		ci.cancel();
		
		int maxSuggestionSize = 10;
		int size = Math.min(suggestionList.size(), maxSuggestionSize);
		int backgroundColor = 0xBD000000;
		int selectedBackgroundColor = 0xDB242424;
		
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
			  selected? selectedBackgroundColor : backgroundColor);
			if (mouseX > rect.getX() && mouseX < rect.getX() + rect.getWidth() &&
			    mouseY > rect.getY() + 12 * i && mouseY < rect.getY() + 12 * i + 12) {
				if (updatedMouse) select(i + offset);
				hovered = true;
			}
			font.drawShadow(
			  mStack, highlightedSuggestions.get(i + offset),
			  (float) (rect.getX() + 1), (float) (rect.getY() + 2 + 12 * i),
			  selected? 0xFFFFFF00 : 0xFFAAAAAA);
		}
		
		if (hovered) {
			Message message = suggestionList.get(current).getTooltip();
			if (message != null)
				screen.renderTooltip(mStack, ComponentUtils.fromMessage(message), mouseX, mouseY);
		}
	}
	
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci) {
		if (current < 0 || current >= suggestionList.size()) return;
		if (keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()
		    || completeWithEnter && hasUnparsedInput && keyCode == GLFW.GLFW_KEY_ENTER) {
			useSuggestion();
			ci.cancel();
			ci.setReturnValue(true);
		}
	}
}
