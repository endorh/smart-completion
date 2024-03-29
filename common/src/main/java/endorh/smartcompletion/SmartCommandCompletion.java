package endorh.smartcompletion;

import com.google.common.collect.Lists;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.customization.CommandCompletionStyle;
import endorh.smartcompletion.customization.CommandSplittingSettings;
import endorh.smartcompletion.util.EvictingLinkedHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.minecraft.network.chat.Component.literal;

public class SmartCommandCompletion {
   // Preferences
   /**
    * Enable smart completion logic (feature toggle for the mod).
    */
   public static boolean enableSmartCompletion = true;
   /**
    * Enable completion keys ({@code <Ctrl>+<Space>} and {@code <Enter>}
    * (if {@link #completeWithEnter} is true and current command is known to be incomplete))
    */
   public static boolean enableCompletionKeys = true;
   /**
    * Enable completion with {@code <Enter>} if current command is known to be incomplete.
    */
   public static boolean completeWithEnter = true;
   /**
    * Force suggestions to show when typing a slash ({@code /}).
    */
   public static boolean showSuggestionsOnSlash = true;

   public static CommandCompletionStyle STYLE = new CommandCompletionStyle();
   private static CommandSplittingSettings SPLITTING_SETTINGS = new CommandSplittingSettings();
   private static final Map<String, WordSplit> SPLIT_CACHE = new EvictingLinkedHashMap<>(2048);

   public static final Pattern FULL_NON_WORD = Pattern.compile("^[\\W\\d]++$");
   private static final Pattern LOWER = Pattern.compile("^\\p{Lower}++$", Pattern.UNICODE_CHARACTER_CLASS);
   public static Pattern WORD_SPLITTER = Pattern.compile(
      "[\\s_:./\\\\-]++|(?<=[a-z])(?=[A-Z])|" +
         "(?<=[a-zA-Z])(?=[^a-zA-Z\\s_:./\\\\-])|(?<=[^a-zA-Z\\s_:./\\\\-])(?=[a-zA-Z])");
   private static final MultiMatcher MATCHER = new MultiMatcher();

   public static void setSplittingSettings(CommandSplittingSettings settings) {
      SPLIT_CACHE.clear();
      SPLITTING_SETTINGS = settings;
   }

   public static void setCompletionStyle(CommandCompletionStyle style) {
      STYLE = style;
   }

   public static MultiMatch multiMatch(String target, String query) {
      return MATCHER.match(target, query);
   }

   public static WordSplit split(String string, boolean splitWords) {
      Matcher m = WORD_SPLITTER.matcher(string);
      int start = 0;
      List<String> parts = Lists.newArrayList();
      IntList indices = new IntArrayList();
      while (m.find(start + 1)) {
         parts.add(string.substring(start, m.start()));
         indices.add(start);
         start = m.end();
      }
      parts.add(string.substring(start));
      indices.add(start);
      if (parts.size() == 1 && splitWords)
         return splitWord(string);
      return WordSplit.of(string, parts, indices);
   }

   public static SortedMatchedSuggestions sort(
      Suggestions blindSuggestions, Suggestions suggestions,
      StringRange range, String query
   ) {
      return sort(blindSuggestions, null, suggestions, range, query, null, null);
   }

   public static SortedMatchedSuggestions sort(
      Suggestions blindSuggestions, @Nullable Suggestions wordBlindSuggestions, Suggestions suggestions,
      StringRange range, String query, @Nullable StringRange wordRange, @Nullable String wordQuery
   ) {
      if (wordQuery == null || wordRange == null) {
         wordBlindSuggestions = null;
      }
      // If query is empty, return all blind suggestions
      if (query.isEmpty()) return new SortedMatchedSuggestions(suggestions.getList().stream()
         .map(s -> Pair.of(s, MultiMatch.whole(s.getText())))
         .collect(Collectors.toList()), false, false, true);
      // Perform smart matching on blind suggestions
      LinkedHashMap<String, Pair<Suggestion, MultiMatch>> smartSuggestions = blindSuggestions.getList().stream()
         .map(s -> Pair.of(
            new Suggestion(range, s.getText(), s.getTooltip()),
            multiMatch(s.getText(), query))
         ).filter(t -> !t.getRight().isEmpty() && !t.getRight().isMatched())
         .collect(Collectors.toMap(p -> p.getLeft().getText(), p -> p, (a, b) -> a, LinkedHashMap::new));
      LinkedHashMap<String, Pair<Suggestion, MultiMatch>> smartWordSuggestions = new LinkedHashMap<>();
      // Perform smart matching on word blind suggestions
      if (wordBlindSuggestions != null) wordBlindSuggestions.getList().stream()
         .map(s -> Pair.of(
            new Suggestion(wordRange, s.getText(), s.getTooltip()),
            multiMatch(s.getText(), wordQuery))
         ).filter(t -> !t.getRight().isEmpty() && !t.getRight().isMatched())
         .forEach(t -> smartWordSuggestions.putIfAbsent(t.getLeft().getText(), t));
      List<Pair<Suggestion, MultiMatch>> dumbSuggestions = Lists.newArrayList();
      // Also perform smart matching on any potential unexpected suggestions to the informed query
      suggestions.getList().stream()
         .filter(s -> !smartSuggestions.containsKey(s.getText()) && !smartWordSuggestions.containsKey(s.getText()))
         .map(s -> Triple.of(
            new Suggestion(s.getRange(), s.getText(), s.getTooltip()),
            multiMatch(s.getText(), query),
            multiMatch(s.getText(), wordQuery))
         ).forEach(t -> {
            MultiMatch m = t.getRight();
            if (m.isEmpty()) m = t.getMiddle();
            if (!m.isEmpty()) {
               if (!m.isMatched())
                  smartSuggestions.put(t.getLeft().getText(), Pair.of(t.getLeft(), m));
            } else dumbSuggestions.add(Pair.of(t.getLeft(), MultiMatch.whole(t.getLeft().getText())));
         });
      List<Pair<Suggestion, MultiMatch>> sorted = Lists.newArrayList(smartSuggestions.values());
      sorted.addAll(smartWordSuggestions.values());
      // Sort first by start index, then by match fitness
      sorted.sort(Comparator.comparingInt((Pair<Suggestion, MultiMatch> p) ->
         -p.getLeft().getRange().getStart()
      ).thenComparing(Pair::getRight));
      sorted.addAll(dumbSuggestions);
      return new SortedMatchedSuggestions(sorted, !smartSuggestions.isEmpty(), !smartWordSuggestions.isEmpty(), !dumbSuggestions.isEmpty());
   }

   public static WordSplit splitWord(String word) {
      if (SPLIT_CACHE.containsKey(word)) return SPLIT_CACHE.get(word);
      if (FULL_NON_WORD.matcher(word).matches()) {
         WordSplit whole = WordSplit.whole(word);
         SPLIT_CACHE.put(word, whole);
         return whole;
      }
      TreeMap<Integer, String> map = new TreeMap<>();
      String remaining = word;
      for (String w : SPLITTING_SETTINGS.words()) {
         int i = remaining.indexOf(w);
         if (i != -1) {
            map.put(i, w);
            remaining = new StringBuilder(remaining).replace(i, i + w.length(), " ".repeat(w.length())).toString();
         }
      }
      if (map.isEmpty() || map.size() == 1 && map.keySet().iterator().next() == 0 && map.values().iterator().next().length() == word.length()) {
         WordSplit whole = WordSplit.whole(word);
         SPLIT_CACHE.put(word, whole);
         return whole;
      }
      int i = 0;
      List<String> parts = Lists.newArrayList();
      IntList indices = new IntArrayList();
      Iterator<Entry<Integer, String>> iter = map.entrySet().iterator();
      Entry<Integer, String> e;
      int j;
      while (i < word.length()) {
         if (iter.hasNext()) {
            e = iter.next();
            j = e.getKey();
         } else {
            e = null;
            j = word.length();
         }
         if (j > i) {
            // Swallow suffixes
            if (!parts.isEmpty()) {
               String unmatched = word.substring(i, j);
               for (String suffix : SPLITTING_SETTINGS.suffixes()) {
                  if (unmatched.startsWith(suffix)) {
                     parts.set(parts.size() - 1, parts.get(parts.size() - 1) + suffix);
                     i += suffix.length();
                     break;
                  }
               }
            }
            // Add missing parts
            if (j > i) {
               parts.add(word.substring(i, j));
               indices.add(i);
               i += j - i;
            }
         }
         // Add word
         if (e != null) {
            parts.add(e.getValue());
            indices.add(j);
            i += e.getValue().length();
         }
      }
      WordSplit split = WordSplit.of(word, parts, indices);
      SPLIT_CACHE.put(word, split);
      return split;
   }

   public static MutableComponent highlightSuggestion(String suggestion, MultiMatch matches, String query) {
      if (query.isEmpty() || matches.isEmpty())
         return literal(suggestion).withStyle(STYLE.suggestion());
      if (matches.totalLength() > query.length())
         return literal(suggestion).withStyle(STYLE.unexpected());
      MutableComponent t = Component.empty();
      int prefixIndex = suggestion.indexOf(":") + 1;
      // Match prefixes if they only consist of letters
      if (prefixIndex > 1 && !LOWER.matcher(suggestion.substring(0, prefixIndex - 1)).matches())
         prefixIndex = 0;
      int prev = 0;
      int @Nullable [] repeats = null;
      int repeatLength = 0;
      for (int i = 0; i < matches.size(); i++) {
         String m = matches.parts()[i];
         int idx = matches.indices()[i];
         highlightGap(t, suggestion, prev, idx, prefixIndex, repeats, repeatLength);
         t.append(literal(suggestion.substring(idx, idx + m.length())).withStyle(matches.isDumb() ? STYLE.dumbMatch() : STYLE.match()));
         repeats = matches.repeats()[i];
         repeatLength = m.length();
         prev = idx + repeatLength;
      }
      highlightGap(t, suggestion, prev, suggestion.length(), prefixIndex, repeats, repeatLength);
      return t;
   }

   private static void highlightGap(
      MutableComponent t, String suggestion, int prev, int idx, int prefixIdx,
      int @Nullable [] repeats, int repeatLength
   ) {
      if (prev >= idx) return;
      if (repeats != null && repeats.length > 0) {
         int p = prev;
         for (int r : repeats) {
            highlightGap(t, suggestion, p, r, prefixIdx, null, 0);
            p = r + repeatLength;
            t.append(literal(suggestion.substring(r, p)).withStyle(STYLE.repeat()));
         }
         highlightGap(t, suggestion, p, idx, prefixIdx, null, 0);
      } else if (prefixIdx > prev && prefixIdx < idx) {
         t.append(literal(suggestion.substring(prev, prefixIdx)).withStyle(STYLE.prefix()));
         t.append(literal(suggestion.substring(prefixIdx, idx)).withStyle(STYLE.suggestion()));
      } else t.append(literal(suggestion.substring(prev, idx)).withStyle(prefixIdx > prev ? STYLE.prefix() : STYLE.suggestion()));
   }
}
