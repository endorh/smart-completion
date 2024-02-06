package endorh.smartcompletion.duck;

import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.SortedMatchedSuggestions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

public interface SmartCommandSuggestions {
   default String getLastArgumentQuery() {
      return smartcompletion$getLastArgumentQuery();
   }
   String smartcompletion$getLastArgumentQuery();

   default EditBox getInput() {
      return smartcompletion$getInput();
   }
   EditBox smartcompletion$getInput();

   default Font getFont() {
      return smartcompletion$getFont();
   }
   Font smartcompletion$getFont();

   default int getSuggestionLineLimit() {
      return smartcompletion$getSuggestionLineLimit();
   }
   int smartcompletion$getSuggestionLineLimit();

   default boolean isAnchorToBottom() {
      return smartcompletion$isAnchorToBottom();
   }
   boolean smartcompletion$isAnchorToBottom();

   default boolean hasUnparsedInput() {
      return smartcompletion$hasUnparsedInput();
   }
   boolean smartcompletion$hasUnparsedInput();

   default @Nullable SortedMatchedSuggestions getLastSuggestionMatches() {
      return smartcompletion$getLastSuggestionMatches();
   }
   @Nullable SortedMatchedSuggestions smartcompletion$getLastSuggestionMatches();

   default Suggestions getLastBlindSuggestions() {
      return smartcompletion$getLastBlindSuggestions();
   }
   @Nullable Suggestions smartcompletion$getLastBlindSuggestions();

   default Suggestions getLastWordBlindSuggestions() {
      return smartcompletion$getLastWordBlindSuggestions();
   }
   @Nullable Suggestions smartcompletion$getLastWordBlindSuggestions();

   default Suggestions getLastSuggestions() {
      return smartcompletion$getLastSuggestions();
   }
   @Nullable Suggestions smartcompletion$getLastSuggestions();
}
