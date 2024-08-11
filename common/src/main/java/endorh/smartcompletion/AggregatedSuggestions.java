package endorh.smartcompletion;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record AggregatedSuggestions(
   String argQuery, StringRange range,
   List<Suggestion> sortedSuggestions,
   List<MultiMatch> sortedSuggestionMatches,
   @Nullable Suggestions argBlindSuggestions,
   @Nullable Suggestions wordBlindSuggestions,
   @Nullable Suggestions informedSuggestions,
   boolean hasArgBlindMatches,
   boolean hasWordBlindMatches,
   boolean hasWeakMatches
) {
   public boolean isEmpty() {
      return sortedSuggestions.isEmpty();
   }
   public boolean isEquivalent(@Nullable AggregatedSuggestions other) {
      return other != null
         && hasArgBlindMatches == other.hasArgBlindMatches()
         && hasWordBlindMatches == other.hasWordBlindMatches()
         && hasWeakMatches == other.hasWeakMatches()
         && argQuery.equals(other.argQuery())
         && range.equals(other.range())
         && sortedSuggestions.equals(other.sortedSuggestions());
   }

   public CompletableFuture<Suggestions> dummyEmptyEquivalentSuggestions() {
      Suggestions dummy = argBlindSuggestions();
      if (dummy == null || dummy.isEmpty() && wordBlindSuggestions() != null)
         dummy = wordBlindSuggestions();
      if (dummy == null || dummy.isEmpty() && informedSuggestions() != null)
         dummy = informedSuggestions();
      if (dummy == null) return Suggestions.empty();
      else return CompletableFuture.completedFuture(dummy);
   }
}
