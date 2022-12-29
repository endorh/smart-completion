package endorh.smartcompletion;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.FormatString;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmartCommandCompletionTest {
	@Test public void basicTest() {
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
	
	@Test public void backTrack() {
		assertMatch("spreadPlayers", "spl", "[s]pread[Pl]ayers");
		assertMatch("spreadPlayers", "sppl", "[sp]read[Pl]ayers");
	}
	
	@Test public void partBackTrack() {
		assertMatch("doDaylightCycle", "dac", "do[Da]ylight[C]ycle");
		assertMatch("daDaylightCycle", "dayc", "da[Day]light[C]ycle");
		assertMatch("ddDdylightCycle", "ddyc", "dd[Ddy]light[C]ycle");
	}
	
	@Test public void sortOrder() {
		assertSorted("g", "give", "gameMode", "defaultGameMode");
		assertSorted("dc", "doDaylightCycle", "doWeatherCycle");
		assertSorted("di", "prefixDoInsomnia", "disableRaids");
		assertSorted("light", "prefixLight", "Daylight");
	}
	
	public void assertMatch(String target, String query, String expected) {
		assertEquals(expected, multiMatch(target, query).toString());
	}
	
	public void assertNoMatch(String target, String query) {
		assertEquals("!empty!", multiMatch(target, query).toString());
	}
	
	public void assertSorted(String query, String... target) {
		List<MultiMatch> matches = Arrays.stream(target)
		  .map(t -> SmartCommandCompletion.multiMatch(t, query))
		  .toList();
		List<String> sortedStrings = Lists.reverse(matches).stream()
		  .sorted()
		  .map(MultiMatch::toString)
		  .toList();
		List<String> strings = matches.stream()
		  .map(MultiMatch::toString)
		  .toList();
		info("Sorted: " + sortedStrings);
		assertEquals(strings, sortedStrings);
	}
	
	InstrumentedMultiMatcher MATCHER = new InstrumentedMultiMatcher();
	public MultiMatch multiMatch(String target, String query) {
		MultiMatch match = MATCHER.match(target, query);
		info(
		  "  multiMatch(\"%s\", \"%s\") (backtrack: %d, part backtrack: %d) %s%n    \"%s\"",
		  target, query, MATCHER.getBackTrackCount(), MATCHER.getPartBackTrackCount(),
		  MATCHER.didAbortWithDumbCheck() ? "[dumb check abort]" : "", match);
		return match;
	}
	
	public void info(@FormatString String s, Object... args) {
		System.out.printf(s + "%n", args);
	}
}
