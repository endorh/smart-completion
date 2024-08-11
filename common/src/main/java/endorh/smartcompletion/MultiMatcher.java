package endorh.smartcompletion;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;
import java.util.Stack;

import static endorh.smartcompletion.SmartCommandCompletion.split;
import static java.lang.Math.min;

/**
 * Matches queries against targets by splitting each of them into parts
 * ({@link SmartCommandCompletion#split}), and further dividing each query
 * part into subparts that are each matched at the start of each target
 * part.<br>
 * <br>
 * If a match could've occurred at multiple target parts, these are recorded.<br>
 * <br>
 * Once {@link #weakCheckDepth} is exceeded, a check is performed to discard
 * the match if all query characters are not contained in order within the target.
 */
public class MultiMatcher {
   private String target;
   private String query;
   private WordSplit targetSplit;
   private WordSplit querySplit;
   private final List<String> matches = Lists.newArrayList();
   private final IntList indices = new IntArrayList();
   private final IntList partIndices = new IntArrayList();
   private final IntList qIndices = new IntArrayList();
   private final List<IntList> repeats = Lists.newArrayList();
   private IntList repeat;
   private int queryPartIdx;
   private String queryPartRem;
   private int targetPartIdx;
   private int targetPartTargetIdx;
   private String targetPart;
   private String lastMatch;
   private final Stack<MatchState> stack = new Stack<>();
   private boolean done;
   private int depth;
   private int weakCheckDepth = 10;
   private int weakMatchLengthThreshold = 2;
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
      queryPartIdx = -1;
      querySplit = split(this.query, false);
      targetSplit = split(this.target, true);
      if (querySplit.size() > targetSplit.size()) {
         result = weakMatch();
         done = true;
         return;
      }
      matches.clear();
      indices.clear();
      partIndices.clear();
      qIndices.clear();
      repeats.clear();
      repeat = null;
      targetPartIdx = -1;
      lastMatch = null;
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
      queryPartIdx++;
      if (queryPartIdx < querySplit.size()) {
         queryPartRem = querySplit.words()[queryPartIdx].toLowerCase();
      } else {
         done = true;
         result = MultiMatch.of(targetSplit, matches, indices, partIndices, repeats, 0);
      }
   }

   private boolean skipTargetPart() {
      if (targetSplit.size() - ++targetPartIdx >= querySplit.size() - queryPartIdx) {
         targetPartTargetIdx = targetSplit.indices()[targetPartIdx];
         targetPart = targetSplit.words()[targetPartIdx];
         return false;
      } else {
         backTrack();
         return true;
      }
   }

   private void matchQueryPart() {
      if (skipTargetPart()) return;
      while (!queryPartRem.isEmpty()) {
         int j = 0;
         int m = min(queryPartRem.length(), targetPart.length());
         // swallow common parts
         while (j < m && queryPartRem.charAt(j) == Character.toLowerCase(targetPart.charAt(j))) j++;
         if (j == 0) { // skip target part
            if (lastMatch != null && targetPart.toLowerCase().startsWith(lastMatch))
               repeat.add(targetPartTargetIdx);
            if (skipTargetPart()) return;
            continue;
         }
         if (j > 1) stack.push(new MatchState(
            matches.size(), queryPartIdx, queryPartRem.substring(1), targetPart.substring(1, j)));
         j = 1;
         matches.add(lastMatch = targetPart.substring(0, j));
         lastMatch = lastMatch.toLowerCase();
         indices.add(targetPartTargetIdx);
         partIndices.add(targetPartIdx);
         qIndices.add(queryPartIdx);
         repeats.add(repeat = new IntArrayList());

         queryPartRem = queryPartRem.substring(j);
         if (queryPartRem.isEmpty()) break;
         if (skipTargetPart()) return;
      }
      skipQueryPart();
   }

   protected void backTrack() {
      if (stack.isEmpty()) {
         partBackTrack();
         return;
      }
      if (depth++ == weakCheckDepth && !weakMatchCheck()) {
         done = true;
         result = weakMatch();
         return;
      }
      MatchState s = stack.pop();
      int n = matches.size();
      if (s.matchCount() + 1 < n) {
         matches.subList(s.matchCount() + 1, n).clear();
         indices.subList(s.matchCount() + 1, n).clear();
         partIndices.subList(s.matchCount() + 1, n).clear();
         repeats.subList(s.matchCount() + 1, n).clear();
      }
      repeat = repeats.get(s.matchCount());
      repeat.clear();
      matches.set(s.matchCount(), lastMatch = matches.get(s.matchCount()) + s.lastMatch().charAt(0));
      lastMatch = lastMatch.toLowerCase();
      queryPartRem = s.queryPart().substring(1);
      queryPartIdx = s.queryPartIdx();
      targetPartIdx = partIndices.getInt(s.matchCount());
      if (s.lastMatch().length() > 1) stack.push(new MatchState(
         s.matchCount(), s.queryPartIdx(), queryPartRem, s.lastMatch().substring(1)));
      if (queryPartRem.isEmpty())
         skipQueryPart();
   }

   protected void partBackTrack() {
      int n = matches.size();
      if (n == 0) {
         done = true;
         result = weakMatch();
         return;
      }
      if (depth++ == weakCheckDepth && !weakMatchCheck()) {
         done = true;
         result = weakMatch();
         return;
      }
      queryPartIdx = qIndices.getInt(n - 1) - 1;
      skipQueryPart();
      int nn = n - 1;
      for (int i = n - 2; i >= 0; i--)
         if (qIndices.getInt(i) == queryPartIdx)
            nn = i;
      targetPartIdx = partIndices.getInt(nn);
      matches.subList(nn, n).clear();
      indices.subList(nn, n).clear();
      partIndices.subList(nn, n).clear();
      repeats.subList(nn, n).clear();
      if (nn > 0) {
         lastMatch = matches.get(nn - 1);
         repeat = repeats.get(nn - 1);
      } else {
         lastMatch = null;
         repeat = null;
      }
   }

   /**
    * Check if all the query characters are contained in order
    */
   protected boolean weakMatchCheck() {
      int i = 0;
      String qs = String.join("", querySplit.words());
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

   private void initWeak() {
      matches.clear();
      indices.clear();
      partIndices.clear();
      repeats.clear();
      repeat = new IntArrayList();
   }

   /**
    * Find each query part anywhere in the target, in order
    */
   private MultiMatch weakMatch() {
      initWeak();
      int start = 0;
      String target = this.target.toLowerCase();
      boolean significant = false;
      for (String qq : querySplit.words()) {
         int i = target.indexOf(qq.toLowerCase(), start);
         if (i == -1) return MultiMatch.empty();
         matches.add(qq);
         indices.add(i);
         partIndices.add(partIndices.size());
         repeats.add(repeat);
         start = i + qq.length();
         significant |= qq.length() >= weakMatchLengthThreshold;
      }
      return significant ? MultiMatch.of(
         targetSplit, matches, indices, partIndices, repeats, 1
      ) : MultiMatch.empty();
   }

   public void setWeakCheckDepth(int depth) {
      weakCheckDepth = depth;
   }

   public void setWeakMatchLengthThreshold(int threshold) {
      weakMatchLengthThreshold = threshold;
   }

   private record MatchState(int matchCount, int queryPartIdx, String queryPart, String lastMatch) {}
}
