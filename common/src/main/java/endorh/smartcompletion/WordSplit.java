package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * Represents a split of a string into words for the purpose of query matching.<br>
 * There may be words between known words that are unknown.
 *
 * @param string Original string.
 * @param words Words within the original string.
 * @param indices Indices of the {@code words} within the original string.
 *                Its length <b>must</b> be the same as that of {@code words}.
 */
public record WordSplit(String string, String[] words, int[] indices) {
   public static WordSplit of(String string, String[] words, int[] indices) {
      return new WordSplit(string, words, indices);
   }
   public static WordSplit of(String string, Collection<String> words, IntList indices) {
      return new WordSplit(string, words.toArray(new String[0]), indices.toIntArray());
   }

   /** A word split for words that can't be split. */
   public static WordSplit whole(String string) {
      return new WordSplit(string, new String[]{string}, new int[]{0});
   }

   public WordSplit {
      if (words.length != indices.length)
         throw new IllegalArgumentException("words.length != indices.length");
   }

   public int size() {
      return words.length;
   }

   @Override public String toString() {
      return StringUtils.join(words, "¦");
   }
}
