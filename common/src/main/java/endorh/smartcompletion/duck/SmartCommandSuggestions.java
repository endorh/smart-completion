package endorh.smartcompletion.duck;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.MultiMatch;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SmartCommandSuggestions {
	default String getLastArgumentQuery() {
		return smartcompletion$getLastArgumentQuery();
	}
	String smartcompletion$getLastArgumentQuery();

	default int getSuggestionLineLimit() {
		return smartcompletion$getSuggestionLineLimit();
	}
	int smartcompletion$getSuggestionLineLimit();

	default boolean isAnchorToBottom() {
		return smartcompletion$isAnchorToBottom();
	}
	boolean smartcompletion$isAnchorToBottom();

	default boolean hasUnparsedInput() {
		return smartcompletion$hasUnparsedInput();
	}
	boolean smartcompletion$hasUnparsedInput();

	default List<Pair<Suggestion, MultiMatch>> getLastSuggestionMatches() {
		return smartcompletion$getLastSuggestionMatches();
	}
	@Nullable List<Pair<Suggestion, MultiMatch>> smartcompletion$getLastSuggestionMatches();

	default Suggestions getLastBlindSuggestions() {
		return smartcompletion$getLastBlindSuggestions();
	}
	@Nullable Suggestions smartcompletion$getLastBlindSuggestions();

	default Suggestions getLastSuggestions() {
		return smartcompletion$getLastSuggestions();
	}
	@Nullable Suggestions smartcompletion$getLastSuggestions();
}
