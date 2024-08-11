package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * Represents a split of a string into words for the purpose of query matching.<br>
 * There may be words between known words that are unknown.
 */
public class WordSplit {
   private final String string;
   private final String[] words;
   private final int[] indices;

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

   /**
    * @param string Original string.
    * @param words Words within the original string.
    * @param indices Indices of the {@code words} within the original string.
    *                Its length <b>must</b> be the same as that of {@code words}.
    */
   public WordSplit(String string, String[] words, int[] indices) {
      if (words.length != indices.length)
         throw new IllegalArgumentException("words.length != indices.length");
      this.string = string;
      this.words = words;
      this.indices = indices;
   }

   /** Original string. */
   public String string() { return string; }
   /** Words within the original string. */
   public String[] words() { return words; }
   /** Indices of the {@code words} within the original string. */
   public int[] indices() { return indices; }

   public int size() {
      return words.length;
   }

   @Override public String toString() {
      return StringUtils.join(words, "¦");
   }
}
