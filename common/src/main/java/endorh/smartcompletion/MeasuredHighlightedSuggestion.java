package endorh.smartcompletion;

import net.minecraft.network.chat.Component;

/**
 * A suggestion with measures needed for rendering.
 */
public class MeasuredHighlightedSuggestion {
   private final Component component;
   private final int horizontalOffset;

   public MeasuredHighlightedSuggestion(Component component, int horizontalOffset) {
      this.component = component;
      this.horizontalOffset = horizontalOffset;
   }

   public Component component() { return component; }
   public int horizontalOffset() { return horizontalOffset; }
}
