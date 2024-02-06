package endorh.smartcompletion;

import com.mojang.brigadier.suggestion.Suggestion;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class SortedMatchedSuggestions {
   private final List<Pair<Suggestion, MultiMatch>> sortedSuggestions;
   private final boolean hasBlindMatches;
   private final boolean hasWordBlindMatches;
   private final boolean hasDumbMatches;

   public SortedMatchedSuggestions(List<Pair<Suggestion, MultiMatch>> sortedSuggestions, boolean hasBlindMatches, boolean hasWordBlindMatches, boolean hasDumbMatches) {
      this.sortedSuggestions = sortedSuggestions;
      this.hasBlindMatches = hasBlindMatches;
      this.hasWordBlindMatches = hasWordBlindMatches;
      this.hasDumbMatches = hasDumbMatches;
   }

   public List<Pair<Suggestion, MultiMatch>> sortedSuggestions() {
      return sortedSuggestions;
   }

   public boolean hasBlindMatches() {
      return hasBlindMatches;
   }

   public boolean hasWordBlindMatches() {
      return hasWordBlindMatches;
   }

   public boolean hasDumbMatches() {
      return hasDumbMatches;
   }

   public boolean isEmpty() {
      return sortedSuggestions.isEmpty();
   }
}
