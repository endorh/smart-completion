package endorh.smartcompletion;

import com.google.common.collect.Lists;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.customization.SmartCompletionSettings;
import endorh.smartcompletion.customization.SmartCompletionSettings.FlatcaseSplittingSettings;
import endorh.smartcompletion.customization.SmartCompletionSettings.SuggestionStyleSettings;
import endorh.smartcompletion.util.EvictingLinkedHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static endorh.smartcompletion.SmartCompletionMod.getSmartCompletionSettings;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.network.chat.Component.literal;

/**
 * Command completion utils.
 */
public class SmartCommandCompletion {
   private static final Map<String, WordSplit> SPLIT_CACHE;
   private static final MultiMatcher MATCHER = new MultiMatcher();

   public static final Pattern FULL_NON_WORD = Pattern.compile("^[\\W\\d]++$");
   public static final Pattern LOWER_ALPHA = Pattern.compile("^\\p{Lower}++$", Pattern.UNICODE_CHARACTER_CLASS);
   public static Pattern WORD_SPLITTER = Pattern.compile(
      "\\s++" +
      "|(?<=[_:./\\\\-])(?=[^\\s_:./\\\\-])" +
      "|(?<=[a-z])(?=[A-Z])" +
      "|(?<=[a-zA-Z])(?=[^a-zA-Z\\s])" +
      "|(?<=[^a-zA-Z\\s])(?=[a-zA-Z])");
   public static Pattern ARG_WORD_SPLITTER = Pattern.compile("\\s++|[\\[{(,;=]");
   public static Predicate<String> SUGGESTION_STARTS_SUB_NODE =
      s -> s.length() == 1 && !Character.isAlphabetic(s.charAt(0)) && !Character.isDigit(s.charAt(0));

   static {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      EvictingLinkedHashMap<String, WordSplit> cache = new EvictingLinkedHashMap<>(max(0, settings.cache.split_cache_max_entries.get()));
      settings.cache.split_cache_max_entries.addChangeListener(max -> cache.setMaxSize(max(0, max)));
      SPLIT_CACHE = cache;
      settings.cache.enable_split_cache.addChangeListener(enabled -> {
         if (!enabled) SPLIT_CACHE.clear();
      });
      settings.flatcase_splitting.addChangeListener(fs -> SPLIT_CACHE.clear());
      settings.minimum_weak_match_length.addChangeListener(MATCHER::setWeakMatchLengthThreshold);
      MATCHER.setWeakMatchLengthThreshold(settings.minimum_weak_match_length.get());
   }

   /**
    * Match a query against a target suggestion.
    * @see MultiMatch
    */
   public static MultiMatch multiMatch(String target, String query) {
      return MATCHER.match(target, query);
   }

   /**
    * Split a suggestion into different words for the purpose of matching.
    * @param splitFlatcase If {@code true} and the {@code string} consists of a single
    *                      word, attempt to split it as a flatcase word.
    */
   public static WordSplit split(String string, boolean splitFlatcase) {
      Matcher m = WORD_SPLITTER.matcher(string);
      int start = 0;
      List<String> parts = Lists.newArrayList();
      IntList indices = new IntArrayList();
      while (start < string.length() && m.find(start + 1)) {
         parts.add(string.substring(start, m.start()));
         indices.add(start);
         start = m.end();
      }
      parts.add(string.substring(start));
      indices.add(start);
      if (splitFlatcase) {
         if (parts.size() == 1) return splitFlatcase(string);
         String first = parts.get(0);
         if (parts.size() == 2 && "/".equals(first)) {
            WordSplit wordSplit = splitFlatcase(parts.get(1));
            if (wordSplit.words().length > 1) {
               parts.remove(parts.size() - 1);
               indices.removeInt(indices.size() - 1);
               Collections.addAll(parts, wordSplit.words());
               for (int index : wordSplit.indices()) indices.add(index + first.length());
            }
         }
      }
      return WordSplit.of(string, parts, indices);
   }

   public static AggregatedSuggestions filterAndSort(
      Suggestions blindSuggestions, Suggestions suggestions,
      StringRange range, String query
   ) {
      return filterAndSort(blindSuggestions, null, suggestions, range, query, null, null);
   }

   public static AggregatedSuggestions filterAndSort(
      @Nullable Suggestions argBlindSuggestions,
      @Nullable Suggestions wordBlindSuggestions,
      @Nullable Suggestions informedSuggestions,
      StringRange argRange, String argQuery,
      @Nullable StringRange wordRange, @Nullable String wordQuery
   ) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (wordQuery == null || wordRange == null) wordBlindSuggestions = null;

      String nonWordQuery = wordQuery != null ? argQuery.substring(0, argQuery.length() - wordQuery.length()) : "";

      // If query is empty, return all blind suggestions
      if (argQuery.isEmpty()) {
         List<Suggestion> suggestions = argBlindSuggestions != null? argBlindSuggestions.getList()
            : informedSuggestions != null? informedSuggestions.getList()
            : Collections.emptyList();
         List<MultiMatch> matches = suggestions.stream().map(s -> MultiMatch.whole(s.getText())).toList();
         return new AggregatedSuggestions(
            argQuery, argRange, suggestions, matches,
            argBlindSuggestions,
            wordBlindSuggestions,
            informedSuggestions,
            false,
            false,
            true);
      }

      boolean includeSmartMatches = settings.include_smart_matches.get();
      boolean includeWeakMatches = settings.include_weak_matches.get();
      boolean includeUnexpectedSuggestions = settings.include_unexpected_suggestions.get();
      Map<String, Pair<Suggestion, MultiMatch>> smartSuggestions = new LinkedHashMap<>();
      Map<String, Pair<Suggestion, MultiMatch>> smartWordSuggestions = new LinkedHashMap<>();
      List<Pair<Suggestion, MultiMatch>> unexpectedSuggestions = new ArrayList<>();

      // Perform matching on word blind suggestions
      if ((includeSmartMatches || includeWeakMatches)
         && wordBlindSuggestions != null
      ) for (Suggestion s : wordBlindSuggestions.getList()) {
         String sText = s.getText();
         StringRange sRange = s.getRange();
         if (sRange.getStart() == sRange.getEnd() && sRange.getStart() == wordRange.getStart())
            sRange = wordRange;
         // Discard if redundant
         if (wordQuery.startsWith(sText) && !wordQuery.equals(sText)) continue;
         MultiMatch mm = multiMatch(sText, wordQuery);
         // Discard if unmatched and the query is non-empty
         if (mm.isEmpty() && !wordQuery.isEmpty()) continue;
         // Discard if ignored
         if (!(mm.priority() == 0 ? includeSmartMatches : includeWeakMatches)) continue;
         smartWordSuggestions.putIfAbsent(sText, Pair.of(
            new Suggestion(sRange, sText, s.getTooltip()), mm));
      }

      // Perform matching on blind suggestions
      if (argBlindSuggestions != null
         && (includeSmartMatches || includeWeakMatches)
      ) for (Suggestion s : argBlindSuggestions.getList()) {
         String sText = s.getText();
         StringRange sRange = s.getRange();
         if (sRange.getStart() == sRange.getEnd() && sRange.getStart() == argRange.getStart())
            sRange = argRange;

         // Discard if already matched
         if (smartWordSuggestions.containsKey(sText)) continue;
         // Discard if redundant
         if (argQuery.startsWith(sText) && !argQuery.equals(sText)) continue;
         MultiMatch mm = multiMatch(s.getText(), argQuery);
         // Discard if unmatched
         if (mm.isEmpty() /*&& !argQuery.isEmpty()*/) continue;
         // Discard if ignored
         if (!(mm.priority() == 0 ? includeSmartMatches : includeWeakMatches)) continue;
         smartSuggestions.putIfAbsent(sText, Pair.of(
            new Suggestion(sRange, s.getText(), s.getTooltip()), mm));
      }

      // Perform matching on any unseen suggestions from the informed query
      if (informedSuggestions != null
         && (includeSmartMatches || includeWeakMatches || includeUnexpectedSuggestions)
      ) for (Suggestion s : informedSuggestions.getList()) {
         String sText = s.getText();

         // Discard if already matched
         if (smartSuggestions.containsKey(sText) || smartWordSuggestions.containsKey(sText)) continue;
         StringRange sRange = s.getRange();

         // Trim non-word prefix
         if (!nonWordQuery.isEmpty() && sRange.getStart() == argRange.getStart() && sText.startsWith(nonWordQuery)) {
            sText = sText.substring(nonWordQuery.length());
            sRange = new StringRange(sRange.getStart() + nonWordQuery.length(), sRange.getEnd());
         }

         Suggestion suggestion = new Suggestion(sRange, sText, s.getTooltip());
         MultiMatch mm = multiMatch(sText, argQuery);
         MultiMatch wmm = wordQuery != null ? multiMatch(sText, wordQuery) : MultiMatch.empty();

         // Use word match if possible
         if (!wmm.isEmpty()) mm = wmm;
         if (!mm.isEmpty() && (mm.priority() == 0 ? includeSmartMatches : includeWeakMatches)) {
            // If there's a match, treat as a smart match
            (!wmm.isEmpty() ? smartWordSuggestions : smartSuggestions).put(sText, Pair.of(suggestion, mm));
         } else if (includeUnexpectedSuggestions) {
            // Else report as an unexpected vanilla suggestion
            unexpectedSuggestions.add(Pair.of(suggestion, MultiMatch.empty()));
         }
      }

      // Sort suggestions
      List<Suggestion> suggestions = new ArrayList<>();
      List<MultiMatch> matches = new ArrayList<>();
      Stream.concat(
         Stream.concat(smartSuggestions.values().stream(), smartWordSuggestions.values().stream()),
         unexpectedSuggestions.stream()
      ).sorted(
         // Compare first by start position (closer to the cursor first), then by match fitness
         Comparator.<Pair<Suggestion, MultiMatch>>comparingInt(p -> -p.getLeft().getRange().getStart())
            .thenComparing(Pair::getRight)
      ).forEach(p -> {
         suggestions.add(p.getLeft());
         matches.add(p.getRight());
      });

      int minI = argRange.getEnd();
      if (!smartSuggestions.isEmpty() && argBlindSuggestions != null)
         minI = min(minI, argBlindSuggestions.getRange().getStart());
      if (!smartWordSuggestions.isEmpty() && wordBlindSuggestions != null)
         minI = min(minI, wordBlindSuggestions.getRange().getStart());
      if (!unexpectedSuggestions.isEmpty() && informedSuggestions != null)
         minI = min(minI, informedSuggestions.getRange().getStart());
      StringRange range = StringRange.between(minI, argRange.getEnd());

      return new AggregatedSuggestions(
         argQuery, range,
         suggestions, matches,
         argBlindSuggestions,
         wordBlindSuggestions,
         informedSuggestions,
         !smartSuggestions.isEmpty(),
         !smartWordSuggestions.isEmpty(),
         !unexpectedSuggestions.isEmpty());
   }

   /**
    * Split a flatcase word into different words according to
    * the {@link FlatcaseSplittingSettings}.
    */
   public static WordSplit splitFlatcase(String word) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      if (settings.cache.enable_split_cache.get() && SPLIT_CACHE.containsKey(word)) return SPLIT_CACHE.get(word);

      // Split full non-words into individual symbols
      if (FULL_NON_WORD.matcher(word).matches()) {
         WordSplit split = WordSplit.of(word,
            word.chars().mapToObj(i -> String.valueOf((char) i)).toArray(String[]::new),
            IntStream.range(0, word.length()).toArray());
         if (settings.cache.enable_split_cache.get()) SPLIT_CACHE.put(word, split);
         return split;
      }

      // Search for known words in word
      TreeMap<Integer, String> foundWords = new TreeMap<>();
      int read = 0;
      search:while (read < word.length()) {
         for (String w : settings.flatcase_splitting.words.get()) {
            if (word.startsWith(w, read)) {
               foundWords.put(read, w);
               read += w.length();
               continue search;
            }
         }
         read++;
      }

      // If not found any words, or the whole word is known return the unsplit word
      if (foundWords.isEmpty() || foundWords.size() == 1 && foundWords.keySet().iterator().next() == 0 && foundWords.values().iterator().next().length() == word.length()) {
         WordSplit whole = WordSplit.whole(word);
         if (settings.cache.enable_split_cache.get()) SPLIT_CACHE.put(word, whole);
         return whole;
      }

      // Sort the found words and fill the gaps
      int i = 0;
      List<String> parts = Lists.newArrayList();
      IntList indices = new IntArrayList();
      Iterator<Entry<Integer, String>> iter = foundWords.entrySet().iterator();
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
               for (String suffix : settings.flatcase_splitting.suffixes.get()) {
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
      if (settings.cache.enable_split_cache.get()) SPLIT_CACHE.put(word, split);
      return split;
   }

   public static int findNamespaceUnprefixedIndex(String suggestion) {
      int prefixIndex = suggestion.indexOf(":") + 1;
      // We only recognize full lowercase alphabetic namespaces
      if (prefixIndex > 1 && !LOWER_ALPHA.matcher(suggestion.substring(0, prefixIndex - 1)).matches())
         prefixIndex = 0;
      return prefixIndex;
   }

   /**
    * Highlight a matched suggestion to display where the query matched or could've
    * alternatively matched each of the suggestion parts, according to the
    * {@link SuggestionStyleSettings}.
    */
   public static MutableComponent highlightSuggestion(String suggestion, MultiMatch matches, String query) {
      SuggestionStyleSettings style = getSmartCompletionSettings().style;
      // Return plain suggestion if there's no match
      if (query.isEmpty() || matches.isEmpty())
         return literal(suggestion).withStyle(style.suggestion.get());
      // Highlight as unexpected if inconsistent
      if (matches.totalLength() > query.length())
         return literal(suggestion).withStyle(style.unexpected.get());

      MutableComponent builder = Component.empty();

      // Highlight namespace prefix
      int prefixIndex = findNamespaceUnprefixedIndex(suggestion);

      // Highlight matches
      int prev = 0;
      int @Nullable [] repeats = null;
      int repeatLength = 0;
      for (int i = 0; i < matches.size(); i++) {
         String m = matches.parts()[i];
         int idx = matches.indices()[i];
         // Highlight gap before suggestion
         highlightGap(builder, suggestion, prev, idx, prefixIndex, repeats, repeatLength);
         // Highlight suggestion
         builder.append(literal(suggestion.substring(idx, idx + m.length())).withStyle(matches.isWeak() ? style.weak_match.get() : style.match.get()));
         repeats = matches.repeats()[i];
         repeatLength = m.length();
         prev = idx + repeatLength;
      }
      // Highlight remaining gap
      highlightGap(builder, suggestion, prev, suggestion.length(), prefixIndex, repeats, repeatLength);
      return builder;
   }

   /**
    * Highlight the gap between suggestion matches that encompasses
    * the characters from {@code suggestion} between {@code start} and {@code end}.<br>
    * @param builder Component builder
    * @param suggestion Highlighted suggestion
    * @param start First character of the gap
    * @param end First character out of the gap
    * @param prefixIdx First character not highlighted as a namespace prefix
    * @param repeats Indices of alternative matches for the last highlighted match,
    *                or {@code null} if already handled.
    * @param repeatLength Length of the last highlighted match.
    */
   private static void highlightGap(
      MutableComponent builder, String suggestion, int start, int end, int prefixIdx,
      int @Nullable [] repeats, int repeatLength
   ) {
      if (start >= end) return;
      SuggestionStyleSettings style = getSmartCompletionSettings().style;
      // Highlight repeats, i.e., alternative matches for the last match, and their gaps
      if (repeats != null && repeats.length > 0) {
         int s = start;
         for (int r : repeats) {
            // Highlight gap before repeat without considering repeats
            highlightGap(builder, suggestion, s, r, prefixIdx, null, 0);
            // Highlight repeat
            s = r + repeatLength;
            builder.append(literal(suggestion.substring(r, s)).withStyle(style.repeat.get()));
         }
         // Highlight remaining gap
         highlightGap(builder, suggestion, s, end, prefixIdx, null, 0);
      } else if (prefixIdx > start && prefixIdx < end) {
         // If the prefix ends within this gap, highlight the two parts
         builder.append(literal(suggestion.substring(start, prefixIdx)).withStyle(style.prefix.get()));
         builder.append(literal(suggestion.substring(prefixIdx, end)).withStyle(style.suggestion.get()));
      } else {
         // Highlight the gap as either prefix or base text
         builder.append(literal(suggestion.substring(start, end)).withStyle(prefixIdx > start ? style.prefix.get() : style.suggestion.get()));
      }
   }
}
