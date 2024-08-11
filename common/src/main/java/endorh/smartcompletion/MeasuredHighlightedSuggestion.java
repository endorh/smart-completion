package endorh.smartcompletion;

import net.minecraft.network.chat.Component;

/**
 * A suggestion with measures needed for rendering.
 */
public record MeasuredHighlightedSuggestion(
   Component component,
   int horizontalOffset
) {}
