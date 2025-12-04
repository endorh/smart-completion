package endorh.smartcompletion.duck;

import endorh.smartcompletion.mixin.MixinSuggestionsList;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;

/**
 * Duck interface injected to the {@link SuggestionsList} class
 * by the {@link MixinSuggestionsList} mixin.<br>
 * <br>
 * If the mixin was applied, it's possible to cast at runtime any {@link SuggestionsList}
 * instance to this interface.
 * <pre><code>
 *    SuggestionsList suggestions;
 *    if (suggestions instanceof SmartSuggestionsList smartSuggestions)
 *        smartSuggestions.setLastInputCode(-100);
 * </code></pre>
 */
public interface SmartSuggestionsList {
   /**
    * Retrieves the last input code. Used to determine the behavior of suggestion insertion.
    */
   default int getLastInputCode() {
      return smartcompletion$getLastInputCode();
   }

   /** @see #getLastInputCode() */
   int smartcompletion$getLastInputCode();

   /**
    * Set the last input code. Used to determine the behavior of suggestion insertion.
    */
   default void setLastInputCode(int keyCode) {
      smartcompletion$setLastInputCode(keyCode);
   }

   /** @see #setLastInputCode(int) */
   void smartcompletion$setLastInputCode(int keyCode);
}
