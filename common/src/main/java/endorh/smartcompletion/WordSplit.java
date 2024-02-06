package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Collection;

public class WordSplit {
   private final String string;
   private final String[] words;
   private final int[] indices;

   public static WordSplit of(String string, Collection<String> words, IntList indices) {
      return new WordSplit(string, words.toArray(new String[0]), indices.toIntArray());
   }

   public static WordSplit whole(String string) {
      return new WordSplit(string, new String[]{string}, new int[]{0});
   }

   public WordSplit(String string, String[] words, int[] indices) {
      this.string = string;
      this.words = words;
      this.indices = indices;

      if (words.length != indices.length)
         throw new IllegalArgumentException("words.length != indices.length");
   }

   public int size() {
      return words.length;
   }

   public String string() {
      return string;
   }

   public String[] words() {
      return words;
   }

   public int[] indices() {
      return indices;
   }
}
