package endorh.smartcompletion.duck;

import endorh.smartcompletion.AggregatedSuggestions;
import endorh.smartcompletion.mixin.MixinCommandSuggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface injected to the {@link CommandSuggestions} class
 * by the {@link MixinCommandSuggestions} mixin.<br>
 * <br>
 * If the mixin was applied, it's possible to cast at runtime any {@link CommandSuggestions}
 * instance to this interface.
 * <pre><code>
 *    CommandSuggestions commandSuggestions;
 *    if (commandSuggestions instanceof SmartCommandSuggestions scs)
 *        scs.enforceShowSuggestions();
 * </code></pre>
 */
public interface SmartCommandSuggestions {
   /**
    * Update the aggregated suggestions for the last command.
    */
   default void updateAggregatedSuggestions(AggregatedSuggestions suggestions) {
      smartcompletion$updateAggregatedSuggestions(suggestions, false);
   }
   /** @see #updateAggregatedSuggestions */
   void smartcompletion$updateAggregatedSuggestions(AggregatedSuggestions suggestions, boolean narrateFirstEntry);

   /**
    * Force the suggestion list to show.
    */
   default void enforceShowSuggestions() {
      smartcompletion$enforceShowSuggestions();
   }
   /** @see #enforceShowSuggestions() */
   void smartcompletion$enforceShowSuggestions();

   /**
    * Whether edits to the command bar are prevented from updating the list of suggestions.
    */
   default boolean isKeepSuggestions() {
      return smartcompletion$isKeepSuggestions();
   }
   /** @see #isKeepSuggestions() */
   boolean smartcompletion$isKeepSuggestions();

   /** @see #isKeepSuggestions() */
   default void setKeepSuggestions(boolean keepSuggestions) {
      smartcompletion$setKeepSuggestions(keepSuggestions);
   }
   /** @see #setKeepSuggestions(boolean) */
   void smartcompletion$setKeepSuggestions(boolean keepSuggestions);

   /**
    * Minecraft instance.
    */
   default Minecraft getMinecraft() {
      return smartcompletion$getMinecraft();
   }
   /** @see #getMinecraft() */
   Minecraft smartcompletion$getMinecraft();

   /**
    * Command input bar.
    */
   default EditBox getInput() {
      return smartcompletion$getInput();
   }
   /** @see #getInput() */
   EditBox smartcompletion$getInput();

   /**
    * Font used by the command input bar.
    */
   default Font getFont() {
      return smartcompletion$getFont();
   }
   /** @see #getFont() */
   Font smartcompletion$getFont();

   /**
    * Line limit of the vanilla suggestion list.
    */
   default int getSuggestionLineLimit() {
      return smartcompletion$getSuggestionLineLimit();
   }
   /** @see #getSuggestionLineLimit() */
   int smartcompletion$getSuggestionLineLimit();

   /**
    * Whether the vanilla suggestion list is anchored to the bottom of the screen.
    */
   default boolean isAnchorToBottom() {
      return smartcompletion$isAnchorToBottom();
   }
   /** @see #isAnchorToBottom() */
   boolean smartcompletion$isAnchorToBottom();

   /**
    * Whether the current command has unparsed input and is thus known to be invalid.
    */
   default boolean hasUnparsedInput() {
      return smartcompletion$hasUnparsedInput();
   }
   /** @see #hasUnparsedInput() */
   boolean smartcompletion$hasUnparsedInput();

   /**
    * Aggregated suggestions for the latest parsed command.
    */
   default @Nullable AggregatedSuggestions getLastAggregatedSuggestions() {
      return smartcompletion$getLastAggregatedSuggestions();
   }
   /** @see #getLastAggregatedSuggestions() */
   @Nullable AggregatedSuggestions smartcompletion$getLastAggregatedSuggestions();
}
