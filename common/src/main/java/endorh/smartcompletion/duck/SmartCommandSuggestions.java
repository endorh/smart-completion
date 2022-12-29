package endorh.smartcompletion.duck;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestions;
import org.jetbrains.annotations.Nullable;

public interface SmartCommandSuggestions {
	String getLastArgumentQuery();
	int getSuggestionLineLimit();
	boolean isAnchorToBottom();
	boolean hasUnparsedInput();
	@Nullable StringRange getLastArgumentRange();
	@Nullable Suggestions getLastBlindSuggestions();
	@Nullable Suggestions getLastSuggestions();
}
