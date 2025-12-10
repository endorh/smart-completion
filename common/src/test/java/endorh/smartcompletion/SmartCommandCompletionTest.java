package endorh.smartcompletion;

import com.google.common.collect.Lists;
import endorh.smartcompletion.util.IncludeExcludeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

#if PRE_MC_1_21_11
   import net.minecraft.Util;
#else
   import net.minecraft.util.Util;
#endif

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartCommandCompletionTest {
   @BeforeAll static void prepareSplittingSettings() {
      SmartCompletionMod.init(true);
      SmartCompletionMod.getSmartCompletionSettings().flatcase_splitting.words.setPackValue(
         Util.make(new IncludeExcludeSet<>(), s -> s.addAll(List.of(
            "game",
            "rule"
         ))));
   }

   @Test void split() {
      assertSplit("word", "word");
      assertSplit("word split", "word", "split");
      assertSplit("smart-completion:word_split",
         "smart", "-", "completion", ":", "word", "_", "split");
      assertSplit(
         "aa bb,cc.dd:ee_ff/gg",
         "aa", "bb", ",", "cc", ".", "dd", ":", "ee", "_", "ff", "/", "gg");
      assertSplit("@p", "@", "p");
      assertSplit("", "");
      assertSplit("camelCase24", "camel", "Case", "24");
      assertSplit("hyphen-24", "hyphen", "-", "24");
      assertSplit("minus -24", "minus", "-", "24");
      assertSplit("minus - 24", "minus", "-", "24");
      assertSplit("-62 70 -34", "-", "62", "70", "-", "34");
   }

   @Test void splitFlatcase() {
      assertSplitFlatcase("word", "word");
      assertSplitFlatcase("doDaylightCycle", "do", "Daylight", "Cycle");
      assertSplitFlatcase("gamerule", "game", "rule");
   }

   @Test void basicTest() {
      assertMatch("gameRule", "gr", "[g]ame[R]ule");
      assertMatch("give", "g", "[g]ive");
      assertMatch("gameRule", "garu", "[ga]me[Ru]le");
      assertMatch("gameRule", "gamerule", "[game][Rule]");
      assertMatch("gameRule", "game", "[game]Rule");
      assertMatch("gameRule", "rule", "game[Rule]");

      assertNoMatch("gameRule", "");
      assertNoMatch("gameRule", "gamo");

      assertMatch("gameRule", "mer", "ga~meR~ule");
   }

   @Test void backTrack() {
      assertMatch("spreadPlayers", "spl", "[s]pread[Pl]ayers");
      assertMatch("spreadPlayers", "sppl", "[sp]read[Pl]ayers");
   }

   @Test void partBackTrack() {
      assertMatch("doDaylightCycle", "dac", "do[Da]ylight[C]ycle");
      assertMatch("daDaylightCycle", "dayc", "da[Day]light[C]ycle");
      assertMatch("ddDdylightCycle", "ddyc", "dd[Ddy]light[C]ycle");
   }

   // If changing the sorting order, please ensure this test either passes, or the change is justified by new test cases
   @Test void sortOrder() {
      assertSorted("g", "give", "gameMode", "defaultGameMode");
      assertSorted("dc", "doDaylightCycle", "doWeatherCycle");
      assertSorted("di", "prefixDoInsomnia", "disableRaids");
      assertSorted("light", "prefixLight", "Daylight");
      assertSorted("app", "apple", "acacia_pressure_plate");
      assertSorted("app", "minecraft:apple", "minecraft:acacia_pressure_plate");
      assertSorted("p", "@p", "Player123");
   }

   @Test void multiNumberMatch() {
      assertMatch("62 70 34", "62 70 34", "[62] [70] [34]");
      assertMatch("62 70 -34", "62 70 -34", "[62] [70] [-][34]");
      assertMatch("-62 70 -34", "-62 70 -34", "[-][62] [70] [-][34]");
      assertMatch("-62 70 -34", "-62 70 -3", "[-][62] [70] [-][3]4");
   }

   // Test Utils
   void assertSplit(String query, String... expectedSplits) {
      WordSplit split = SmartCommandCompletion.split(query, false);
      info("Split: %s -> %s", query, split);
      assertArrayEquals(expectedSplits, split.words());
   }

   void assertSplitFlatcase(String query, String... expectedSplits) {
      WordSplit split = SmartCommandCompletion.split(query, true);
      info("Split words: %s -> %s", query, split);
      assertArrayEquals(expectedSplits, split.words());
   }

   void assertMatch(String target, String query, String expected) {
      assertEquals(expected, multiMatch(target, query).toString());
   }

   void assertNoMatch(String target, String query) {
      assertEquals("!empty!", multiMatch(target, query).toString());
   }

   void assertSorted(String query, String... target) {
      List<MultiMatch> matches = Arrays.stream(target)
         .map(t -> SmartCommandCompletion.multiMatch(t, query))
         .collect(Collectors.toList());
      List<String> sortedStrings = Lists.reverse(matches).stream()
         .sorted()
         .map(MultiMatch::toString)
         .collect(Collectors.toList());
      List<String> strings = matches.stream()
         .map(MultiMatch::toString)
         .collect(Collectors.toList());
      info("Sorted: " + sortedStrings);
      assertEquals(strings, sortedStrings);
   }

   private InstrumentedMultiMatcher MATCHER = new InstrumentedMultiMatcher();
   MultiMatch multiMatch(String target, String query) {
      MultiMatch match = MATCHER.match(target, query);
      info(
         "  multiMatch(\"%s\", \"%s\") (backtrack: %d, part backtrack: %d) %s%n    \"%s\"",
         target, query, MATCHER.getBackTrackCount(), MATCHER.getPartBackTrackCount(),
         MATCHER.didAbortWithWeakCheck() ? "[weak check abort]" : "", match);
      return match;
   }

   public void info(String s, Object... args) {
      System.out.printf(s + "%n", args);
   }
}
