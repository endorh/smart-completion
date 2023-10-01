package endorh.smartcompletion.customization;

import com.google.gson.*;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public record CommandSplittingSettings(List<String> suffixes, List<String> words) {
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

   public CommandSplittingSettings() {
      this(Collections.emptyList(), Collections.emptyList());
   }

   public CommandSplittingSettings merge(CommandSplittingSettings other) {
      return of(
         Stream.concat(suffixes.stream(), other.suffixes.stream()).collect(Collectors.toList()),
         Stream.concat(words.stream(), other.words.stream()).collect(Collectors.toList())
      );
   }

   public static Serializer SERIALIZER = new Serializer();

   public static class Serializer implements JsonDeserializer<CommandSplittingSettings> {
      @Override
      public CommandSplittingSettings deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
         JsonObject obj = json.getAsJsonObject();
         JsonArray suffixesArray = GsonHelper.getAsJsonArray(obj, "suffixes", new JsonArray());
         JsonArray wordsArray = GsonHelper.getAsJsonArray(obj, "words", new JsonArray());
         List<String> suffixes = StreamSupport.stream(suffixesArray.spliterator(), false)
            .map(JsonElement::getAsString)
            .collect(Collectors.toList());
         List<String> words = StreamSupport.stream(wordsArray.spliterator(), false)
            .map(JsonElement::getAsString)
            .collect(Collectors.toList());
         return of(suffixes, words);
      }
   }
}
