package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Collection;

public record WordSplit(String string, String[] words, int[] indices) {
	public static WordSplit of(String string, Collection<String> words, IntList indices) {
		return new WordSplit(string, words.toArray(String[]::new), indices.toIntArray());
	}
	
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
}
