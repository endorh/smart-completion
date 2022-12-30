package endorh.smartcompletion;

import com.google.gson.*;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SmartCompletionResourceReloadListener extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder()
	  .registerTypeAdapter(CommandSplittingSettings.class, CommandSplittingSettings.SERIALIZER)
	  .registerTypeAdapter(CommandCompletionStyle.class, CommandCompletionStyle.SERIALIZER)
	  .registerTypeAdapter(Style.class, new Style.Serializer())
	  .create();
	
	public SmartCompletionResourceReloadListener() {
		super(GSON, "smart-completion");
	}
	
	@Override protected void apply(
	  @NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager,
	  @NotNull ProfilerFiller profiler
	) {
		List<JsonElement> commandSplittingJsonList = map.entrySet().stream()
		  .filter(e -> e.getKey().getPath().equals("command_splitting"))
		  .map(Entry::getValue).collect(Collectors.toList());
		List<JsonElement> completionStyleJSONList = map.entrySet().stream()
		  .filter(e -> e.getKey().getPath().equals("completion_style"))
		  .map(Entry::getValue).collect(Collectors.toList());
		try {
			CommandSplittingSettings settings = new CommandSplittingSettings();
			for (JsonElement obj: commandSplittingJsonList) {
				CommandSplittingSettings next = GSON.fromJson(obj, CommandSplittingSettings.class);
				if (GsonHelper.getAsBoolean(obj.getAsJsonObject(), "replace", false)) {
					settings = next;
				} else settings = settings.merge(next);
			}
			SmartCommandCompletion.setSplittingSettings(settings);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to load command splitting settings", e);
		}
		try {
			CommandCompletionStyle style = new CommandCompletionStyle();
			for (JsonElement obj: completionStyleJSONList) {
				CommandCompletionStyle next = GSON.fromJson(obj, CommandCompletionStyle.class);
				if (GsonHelper.getAsBoolean(obj.getAsJsonObject(), "replace", false)) {
					style = next;
				} else style = next.applyTo(style);
			}
			SmartCommandCompletion.setCompletionStyle(style);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to load completion style settings", e);
		}
	}
	
	public static class CommandSplittingSettings {
		private final List<String> suffixes;
		private final List<String> words;
		
		public static final Pattern WORD_PATTERN = Pattern.compile("^\\p{Lower}++$", Pattern.UNICODE_CHARACTER_CLASS);
		
		public static CommandSplittingSettings of(List<String> suffixes, List<String> words) {
			Optional<String> opt = Stream.concat(suffixes.stream(), words.stream())
			  .filter(w -> !WORD_PATTERN.matcher(w).matches())
			  .findFirst();
			if (opt.isPresent()) throw new IllegalArgumentException(
			  "Words must only contain lowercase letter characters: \"" + opt.get() + "\"");
			// Make sure that the words and suffixes are sorted from longest to shortest
			suffixes = suffixes.stream()
			  .sorted(Comparator.comparingInt(String::length).reversed())
			  .collect(Collectors.toList());
			words = words.stream()
			  .sorted(Comparator.comparingInt(String::length).reversed())
			  .collect(Collectors.toList());
			return new CommandSplittingSettings(suffixes, words);
		}
		
		public CommandSplittingSettings(List<String> suffixes, List<String> words) {
			this.suffixes = suffixes;
			this.words = words;
		}
		
		public CommandSplittingSettings() {
			this(Collections.emptyList(), Collections.emptyList());
		}
		
		public CommandSplittingSettings merge(CommandSplittingSettings other) {
			return CommandSplittingSettings.of(
			  Stream.concat(suffixes.stream(), other.suffixes.stream()).collect(Collectors.toList()),
			  Stream.concat(words.stream(), other.words.stream()).collect(Collectors.toList())
			);
		}
		
		public static Serializer SERIALIZER = new Serializer();
		public static class Serializer implements JsonDeserializer<CommandSplittingSettings> {
			@Override public CommandSplittingSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				JsonObject obj = json.getAsJsonObject();
				JsonArray suffixesArray = GsonHelper.getAsJsonArray(obj, "suffixes", new JsonArray());
				JsonArray wordsArray = GsonHelper.getAsJsonArray(obj, "words", new JsonArray());
				List<String> suffixes = StreamSupport.stream(suffixesArray.spliterator(), false)
					.map(JsonElement::getAsString)
					.collect(Collectors.toList());
				List<String> words = StreamSupport.stream(wordsArray.spliterator(), false)
					.map(JsonElement::getAsString)
					.collect(Collectors.toList());
				return CommandSplittingSettings.of(suffixes, words);
			}
		}
		
		public List<String> suffixes() {
			return suffixes;
		}
		
		public List<String> words() {
			return words;
		}
	}
	
	public static class CommandCompletionStyle {
		private final Style suggestion;
		private final Style match;
		private final Style dumbMatch;
		private final Style prefix;
		private final Style repeat;
		private final Style unexpected;
		
		public CommandCompletionStyle(
		  Style suggestion, Style match, Style dumbMatch, Style prefix, Style repeat, Style unexpected
		) {
			this.suggestion = suggestion;
			this.match = match;
			this.dumbMatch = dumbMatch;
			this.prefix = prefix;
			this.repeat = repeat;
			this.unexpected = unexpected;
		}
		
		public CommandCompletionStyle() {
			this(Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY);
		}
		
		public CommandCompletionStyle applyTo(CommandCompletionStyle other) {
			return new CommandCompletionStyle(
			  suggestion.applyTo(other.suggestion),
			  match.applyTo(other.match),
			  dumbMatch.applyTo(other.dumbMatch),
			  prefix.applyTo(other.prefix),
			  repeat.applyTo(other.repeat),
			  unexpected.applyTo(other.unexpected));
		}
		
		public static Serializer SERIALIZER = new Serializer();
		public static class Serializer implements JsonDeserializer<CommandCompletionStyle> {
			@Override public CommandCompletionStyle deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				JsonObject obj = json.getAsJsonObject();
				JsonObject o = GsonHelper.getAsJsonObject(obj, "suggestion", new JsonObject());
				Style suggestion = GSON.fromJson(o, Style.class);
				o = GsonHelper.getAsJsonObject(obj, "match", new JsonObject());
				Style match = GSON.fromJson(o, Style.class);
				o = GsonHelper.getAsJsonObject(obj, "dumb_match", new JsonObject());
				Style dumbMatch = GSON.fromJson(o, Style.class);
				o = GsonHelper.getAsJsonObject(obj, "prefix", new JsonObject());
				Style prefix = GSON.fromJson(o, Style.class);
				o = GsonHelper.getAsJsonObject(obj, "repeat", new JsonObject());
				Style repeat = GSON.fromJson(o, Style.class);
				o = GsonHelper.getAsJsonObject(obj, "unexpected", new JsonObject());
				Style unexpected = GSON.fromJson(o, Style.class);
				return new CommandCompletionStyle(suggestion, match, dumbMatch, prefix, repeat, unexpected);
			}
		}
		
		public Style suggestion() {
			return suggestion;
		}
		
		public Style match() {
			return match;
		}
		
		public Style dumbMatch() {
			return dumbMatch;
		}
		
		public Style prefix() {
			return prefix;
		}
		
		public Style repeat() {
			return repeat;
		}
		
		public Style unexpected() {
			return unexpected;
		}
	}
}
