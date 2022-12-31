package endorh.smartcompletion.duck;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.MultiMatch;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SmartCommandSuggestions {
	String getLastArgumentQuery();
	int getSuggestionLineLimit();
	boolean isAnchorToBottom();
	boolean hasUnparsedInput();
	@Nullable List<Pair<Suggestion, MultiMatch>> getLastSuggestionMatches();
	@Nullable Suggestions getLastBlindSuggestions();
	@Nullable Suggestions getLastSuggestions();
}
