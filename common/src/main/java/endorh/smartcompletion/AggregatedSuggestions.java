package endorh.smartcompletion;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AggregatedSuggestions {
   private final String argQuery;
   private final StringRange range;
   private final List<Suggestion> sortedSuggestions;
   private final List<MultiMatch> sortedSuggestionMatches;
   private final @Nullable Suggestions argBlindSuggestions;
   private final @Nullable Suggestions wordBlindSuggestions;
   private final @Nullable Suggestions informedSuggestions;
   private final boolean hasArgBlindMatches;
   private final boolean hasWordBlindMatches;
   private final boolean hasWeakMatches;

   public AggregatedSuggestions(String argQuery, StringRange range, List<Suggestion> sortedSuggestions, List<MultiMatch> sortedSuggestionMatches, @Nullable Suggestions argBlindSuggestions, @Nullable Suggestions wordBlindSuggestions, @Nullable Suggestions informedSuggestions, boolean hasArgBlindMatches, boolean hasWordBlindMatches, boolean hasWeakMatches) {
      this.argQuery = argQuery;
      this.range = range;
      this.sortedSuggestions = sortedSuggestions;
      this.sortedSuggestionMatches = sortedSuggestionMatches;
      this.argBlindSuggestions = argBlindSuggestions;
      this.wordBlindSuggestions = wordBlindSuggestions;
      this.informedSuggestions = informedSuggestions;
      this.hasArgBlindMatches = hasArgBlindMatches;
      this.hasWordBlindMatches = hasWordBlindMatches;
      this.hasWeakMatches = hasWeakMatches;
   }

   public String argQuery() { return argQuery; }
   public StringRange range() { return range; }
   public List<Suggestion> sortedSuggestions() { return sortedSuggestions; }
   public List<MultiMatch> sortedSuggestionMatches() { return sortedSuggestionMatches; }
   public @Nullable Suggestions argBlindSuggestions() { return argBlindSuggestions; }
   public @Nullable Suggestions wordBlindSuggestions() { return wordBlindSuggestions; }
   public @Nullable Suggestions informedSuggestions() { return informedSuggestions; }
   public boolean hasArgBlindMatches() { return hasArgBlindMatches; }
   public boolean hasWordBlindMatches() { return hasWordBlindMatches; }
   public boolean hasWeakMatches() { return hasWeakMatches; }

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
