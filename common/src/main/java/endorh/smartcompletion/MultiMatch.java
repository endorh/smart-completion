package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static endorh.smartcompletion.SmartCommandCompletion.findNamespaceUnprefixedIndex;
import static java.lang.Integer.compare;
import static java.util.Arrays.compare;

/**
 * Represents a match of multiple parts of a query in a potential completion suggestion.<br>
 * <br>
 * @param split Word split of the suggestion.
 * @param parts Split of the query into matched segments.
 * @param indices Indices within the suggestion where each of the {@code parts} matched.
 * @param partIndices Indices within the split of the suggestion where each of the {@code parts} matched.
 * @param repeats Arrays of extra indices where each of the {@code parts} could've
 *                alternatively been matched.
 * @param priority Priority of the match. Matches with higher priority are preferred.
 * <br>
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public record MultiMatch(
   WordSplit split,
   String[] parts,
   int[] indices,
   int[] partIndices,
   int[][] repeats,
   int priority
) implements Comparable<MultiMatch> {
   public MultiMatch {
      if (parts.length != indices.length)
         throw new IllegalArgumentException("parts.length != indices.length");
      if (parts.length != partIndices.length)
         throw new IllegalArgumentException("parts.length != partIndices.length");
      if (parts.length != repeats.length)
         throw new IllegalArgumentException("parts.length != repeats.length");
   }

   private static final MultiMatch EMPTY = new MultiMatch(WordSplit.whole(""), new String[0], new int[0], new int[0], new int[0][0], Integer.MAX_VALUE);

   public static MultiMatch of(
      WordSplit split, Collection<String> matches, IntList indices,
      IntList partIndices, List<IntList> repeats, int priority
   ) {
      return new MultiMatch(
         split, matches.toArray(new String[0]), indices.toIntArray(), partIndices.toIntArray(),
         repeats.stream().map(IntCollection::toIntArray).toArray(int[][]::new), priority);
   }

   public static MultiMatch whole(String string) {
      return whole(string, 1);
   }

   public static MultiMatch whole(String string, int priority) {
      return new MultiMatch(
         WordSplit.whole(string), new String[]{string}, new int[]{0}, new int[]{0},
         new int[][]{new int[0]}, priority);
   }

   public static MultiMatch empty() {
      return EMPTY;
   }

   public String word() {
      return split.string();
   }

   public String unprefixedWord() {
      String word = word();
      int index = findNamespaceUnprefixedIndex(word);
      if (index != -1) return word.substring(index);
      return word;
   }

   public boolean isEmpty() {
      return parts.length == 0;
   }

   /**
    * Returns {@code true} if the match is complete, i.e., the query
    * is equal to the suggestion up to splitting.
    */
   public boolean isCompletelyMatched() {
      WordSplit split = split();
      if (split.size() != parts.length) return false;
      for (int i = 0; i < parts.length; i++)
         if (!split.words()[i].equals(parts[i]))
            return false;
      return true;
   }

   public int size() {
      return parts.length;
   }

   public int totalLength() {
      return Arrays.stream(parts).mapToInt(String::length).sum();
   }

   public int hyperVolume() {
      return Arrays.stream(parts).mapToInt(String::length).reduce((a, b) -> a * b).orElse(0);
   }

   public int totalRepeats() {
      return Arrays.stream(repeats).mapToInt(a -> a.length).sum();
   }

   public float matchRatio() {
      return (float) totalLength() / (float) word().length();
   }

   public float unprefixedMatchRatio() {
      String word = word();
      int idx = findNamespaceUnprefixedIndex(word);
      if (idx == -1 || Arrays.stream(indices).anyMatch(i -> i < idx)) return matchRatio();
      return (float) totalLength() / (float) (word.length() - idx);
   }

   public int letterLength() {
      return Arrays.stream(parts).mapToInt(p -> (int) p.chars().filter(Character::isAlphabetic).count()).sum();
   }

   public float letterMatchRatio() {
      int letterLength = letterLength();
      int wordAsciiLength = (int) word().chars().filter(Character::isAlphabetic).count();
      return (float) letterLength / (float) wordAsciiLength;
   }

   /**
    * Rather than comparing ratios directly, which would almost completely
    * invalidate any lower priority criteria, we slot them into wide buckets.
    */
   public static int scoreRatio(float portion) {
      if (portion <= 0.5F) return 0;
      if (portion < 0.9F) return 1;
      return 2;
   }

   public int compareRatio(float a, float b) {
      return compare(scoreRatio(a), scoreRatio(b));
   }

   @Override public int compareTo(@NotNull MultiMatch other) {
      int r;
      // Sort according to priority first (non-weak matches have priority 0 (highest))
      if ((r = compare(priority, other.priority)) != 0) return r;

      // Give priority to significant matches
      if ((r = -compareRatio(unprefixedMatchRatio(), other.unprefixedMatchRatio())) != 0) return r;
      if ((r = -compareRatio(letterMatchRatio(), other.letterMatchRatio())) != 0) return r;

      // Give priority to matches with many small sub-matches rather than a few large matches
      if ((r = compare(hyperVolume(), other.hyperVolume())) != 0) return r;
      if ((r = compare(size(), other.size())) != 0) return r;

      // Give priority to matches where the same query part could match multiple target parts
      if ((r = -compare(totalRepeats(), other.totalRepeats())) != 0) return r;

      // Give priority to suggestions with fewer parts
      if ((r = compare(split.size(), other.split.size())) != 0) return r;

      // Give priority to suggestions starting earlier
      if ((r = compare(partIndices, other.partIndices)) != 0) return r;

      // Give priority to shorter suggestions
      if ((r = compare(word().length(), other.word().length())) != 0) return r;

      // Otherwise, the order depends on the source order
      return 0;
   }

   @Override public String toString() {
      if (isEmpty())
         return "!empty!";
      String word = word();
      String pre = isWeak() ? "~" : "[";
      String pos = isWeak() ? "~" : "]";
      for (int i = size() - 1; i >= 0; i--)
         word = word.substring(0, indices[i]) + pre
            + word.substring(indices[i], indices[i] + parts[i].length())
            + pos + word.substring(indices[i] + parts[i].length());
      return word;
   }

   public boolean isWeak() {
      return priority > 0;
   }

   public MultiMatch withPriority(int priority) {
      return new MultiMatch(split, parts, indices, partIndices, repeats, priority);
   }
}
