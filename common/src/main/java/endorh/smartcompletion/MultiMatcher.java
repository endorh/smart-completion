package endorh.smartcompletion;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;
import java.util.Stack;

import static java.lang.Math.min;

public class MultiMatcher {
	private String target;
	private String query;
	private WordSplit t;
	private WordSplit q;
	private final List<String> matches = Lists.newArrayList();
	private final IntList indices = new IntArrayList();
	private final IntList subIndices = new IntArrayList();
	private final IntList qIndices = new IntArrayList();
	private final List<IntList> repeats = Lists.newArrayList();
	private IntList repeat;
	private int qi;
	private String qq;
	private int r;
	private int remIdx;
	private String rem;
	private String last;
	private final Stack<MatchState> stack = new Stack<>();
	private boolean done;
	private int depth;
	private int dumbCheckDepth = 10;
	private int dumbMatchLengthThreshold = 2;
	private MultiMatch result;
	
	private void init(String target, String query) {
		this.query = query.trim();
		this.target = target.trim();
		done = false;
		result = MultiMatch.empty();
		if (this.query.isEmpty() || this.query.length() > this.target.length()) {
			done = true;
			return;
		}
		qi = -1;
		q = SmartCommandCompletion.split(this.query, false);
		t = SmartCommandCompletion.split(this.target, true);
		if (q.size() > t.size()) {
			result = dumbMatch();
			done = true;
			return;
		}
		matches.clear();
		indices.clear();
		subIndices.clear();
		qIndices.clear();
		repeats.clear();
		repeat = null;
		r = -1;
		last = null;
		stack.clear();
		depth = 1;
	}
	
	public synchronized MultiMatch match(String target, String query) {
		init(target, query);
		if (!done) {
			skipQueryPart();
			while (!done)
				matchQueryPart();
		}
		return result;
	}
	
	private void skipQueryPart() {
		qi++;
		if (qi < q.size()) {
			qq = q.words()[qi].toLowerCase();
		} else {
			done = true;
			result = MultiMatch.of(t, matches, indices, subIndices, repeats, 0);
		}
	}
	
	private boolean skipTargetPart() {
		if (t.size() - ++r >= q.size() - qi) {
			remIdx = t.indices()[r];
			rem = t.words()[r];
			return false;
		} else {
			backTrack();
			return true;
		}
	}
	
	private void matchQueryPart() {
		if (skipTargetPart()) return;
		while (!qq.isEmpty()) {
			int j = 0;
			int m = min(qq.length(), rem.length());
			// swallow common parts
			while (j < m && qq.charAt(j) == Character.toLowerCase(rem.charAt(j))) j++;
			if (j == 0) { // skip target part
				if (last != null && rem.toLowerCase().startsWith(last))
					repeat.add(remIdx);
				if (skipTargetPart()) return;
				continue;
			}
			if (j > 1) stack.push(new MatchState(
			  matches.size(), qi, qq.substring(1), rem.substring(1, j)));
			j = 1;
			matches.add(last = rem.substring(0, j));
			last = last.toLowerCase();
			indices.add(remIdx);
			subIndices.add(r);
			qIndices.add(qi);
			repeats.add(repeat = new IntArrayList());
			qq = qq.substring(j);
			if (qq.isEmpty()) break;
			if (skipTargetPart()) return;
		}
		skipQueryPart();
	}
	
	protected void backTrack() {
		if (stack.isEmpty()) {
			partBackTrack();
			return;
		}
		if (depth++ == dumbCheckDepth && !dumbMatchCheck()) {
			done = true;
			result = dumbMatch();
			return;
		}
		MatchState s = stack.pop();
		int n = matches.size();
		if (s.i + 1 < n) {
			matches.subList(s.i + 1, n).clear();
			indices.subList(s.i + 1, n).clear();
			subIndices.subList(s.i + 1, n).clear();
			repeats.subList(s.i + 1, n).clear();
		}
		repeat = repeats.get(s.i);
		repeat.clear();
		matches.set(s.i, last = matches.get(s.i) + s.m.charAt(0));
		last = last.toLowerCase();
		qq = s.qq.substring(1);
		qi = s.qi;
		r = subIndices.getInt(s.i);
		if (s.m.length() > 1) stack.push(new MatchState(
		  s.i, s.qi, qq, s.m.substring(1)));
		if (qq.isEmpty())
			skipQueryPart();
	}
	
	protected void partBackTrack() {
		int n = matches.size();
		if (n == 0) {
			done = true;
			result = dumbMatch();
			return;
		}
		if (depth++ == dumbCheckDepth && !dumbMatchCheck()) {
			done = true;
			result = dumbMatch();
			return;
		}
		qi = qIndices.getInt(n - 1) - 1;
		skipQueryPart();
		int nn = n - 1;
		for (int i = n - 2; i >= 0; i--)
			if (qIndices.getInt(i) == qi)
				nn = i;
		r = subIndices.getInt(nn);
		matches.subList(nn, n).clear();
		indices.subList(nn, n).clear();
		subIndices.subList(nn, n).clear();
		repeats.subList(nn, n).clear();
		if (nn > 0) {
			last = matches.get(nn - 1);
			repeat = repeats.get(nn - 1);
		} else {
			last = null;
			repeat = null;
		}
	}
	
	/**
	 * Check if all the query characters are contained in order
	 */
	protected boolean dumbMatchCheck() {
		int i = 0;
		String qs = String.join("", q.words());
		int tl = target.length();
		int ql = qs.length();
		for (int qi = 0; qi < ql; qi++) {
			if (tl - i <= ql - qi)
				return false;
			char c = qs.charAt(qi);
			while (target.charAt(i++) != c)
				if (tl - i <= ql - qi)
					return false;
		}
		return true;
	}
	
	private void initDumb() {
		matches.clear();
		indices.clear();
		subIndices.clear();
		repeats.clear();
		repeat = new IntArrayList();
	}
	
	/**
	 * Find each query part anywhere in the target, in order
	 */
	private MultiMatch dumbMatch() {
		initDumb();
		int start = 0;
		String target = this.target.toLowerCase();
		boolean significant = false;
		for (String qq: q.words()) {
			int i = target.indexOf(qq.toLowerCase(), start);
			if (i == -1) return MultiMatch.empty();
			matches.add(qq);
			indices.add(i);
			subIndices.add(subIndices.size());
			repeats.add(repeat);
			start = i + qq.length();
			significant |= qq.length() >= dumbMatchLengthThreshold;
		}
		return significant? MultiMatch.of(
		  t, matches, indices, subIndices, repeats, 1
		) : MultiMatch.empty();
	}
	
	public void setDumbCheckDepth(int depth) {
		dumbCheckDepth = depth;
	}
	
	public void setDumbMatchLengthThreshold(int threshold) {
		dumbMatchLengthThreshold = threshold;
	}
	
	private static class MatchState {
		private final int i;
		private final int qi;
		private final String qq;
		private final String m;
		
		private MatchState(int i, int qi, String qq, String m) {
			this.i = i;
			this.qi = qi;
			this.qq = qq;
			this.m = m;
		}
	}
}
