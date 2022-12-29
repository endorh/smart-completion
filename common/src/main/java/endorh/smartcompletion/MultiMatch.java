package endorh.smartcompletion;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public record MultiMatch(
  WordSplit split, String[] parts, int[] indices, int[] subIndices, int[][] repeats, boolean isDumb)
  implements Comparable<MultiMatch> {
	private static final MultiMatch
	  EMPTY =
	  new MultiMatch(WordSplit.whole(""), new String[0], new int[0], new int[0], new int[0][0],
	                 true);
	
	public static MultiMatch of(
	  WordSplit split, Collection<String> matches, IntList indices,
	  IntList subIndices, List<IntList> repeats, boolean isDumb
	) {
		return new MultiMatch(
		  split, matches.toArray(String[]::new), indices.toIntArray(), subIndices.toIntArray(),
		  repeats.stream().map(IntCollection::toIntArray).toArray(int[][]::new), isDumb);
	}
	
	public static MultiMatch whole(String string) {
		return new MultiMatch(
		  WordSplit.whole(string), new String[]{string}, new int[]{0}, new int[]{0},
		  new int[][]{new int[0]}, true);
	}
	
	public static MultiMatch empty() {
		return EMPTY;
	}
	
	public MultiMatch {
		if (parts.length != indices.length)
			throw new IllegalArgumentException("parts.length != indices.length");
		if (parts.length != subIndices.length)
			throw new IllegalArgumentException("parts.length != subIndices.length");
		if (parts.length != repeats.length)
			throw new IllegalArgumentException("parts.length != repeats.length");
	}
	
	public String word() {
		return split.string();
	}
	
	public boolean isEmpty() {
		return parts.length == 0;
	}
	
	public int size() {
		return parts.length;
	}
	
	public int totalLength() {
		return Arrays.stream(parts).mapToInt(String::length).sum();
	}
	
	@Override public int compareTo(@NotNull MultiMatch other) {
		int prod = Arrays.stream(parts).mapToInt(String::length).reduce(1, (a, b) -> a * b);
		int otherProd = Arrays.stream(other.parts).mapToInt(String::length).reduce(1, (a, b) -> a * b);
		int totalRepeats = Arrays.stream(repeats).mapToInt(r -> r.length).sum();
		int otherTotalRepeats = Arrays.stream(other.repeats).mapToInt(r -> r.length).sum();
		return new CompareToBuilder()
		  // Give priority to non-dumb matches
		  .append(isDumb, other.isDumb)
		  // Give priority to matches with many small submatches, rather than a few large matches
		  .append(prod, otherProd)
		  .append(size(), other.size())
		  // Give priority to matches where the same query part could match multiple target parts
		  .append(-totalRepeats, -otherTotalRepeats)
		  // Give priority to matches for words with fewer parts
		  .append(split.size(), other.split.size())
		  // Give priority to matches starting earlier
		  .append(subIndices, other.subIndices)
		  // Give priority to matches for shorter words
		  .append(word().length(), other.word().length())
		  // Otherwise, the order depends on the source order
		  .toComparison();
	}
	
	@Override public String toString() {
		if (isEmpty())
			return "!empty!";
		String word = word();
		String pre = isDumb? "~" : "[";
		String pos = isDumb? "~" : "]";
		for (int i = size() - 1; i >= 0; i--)
			word =
			  word.substring(0, indices[i]) + pre
			  + word.substring(indices[i], indices[i] + parts[i].length())
			  + pos + word.substring(indices[i] + parts[i].length());
		return word;
	}
}
