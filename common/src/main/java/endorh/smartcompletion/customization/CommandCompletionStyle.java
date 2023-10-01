package endorh.smartcompletion.customization;

import com.google.gson.*;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

import static endorh.smartcompletion.customization.SmartCompletionResourceReloadListener.GSON;

public record CommandCompletionStyle(
   Style suggestion, Style match, Style dumbMatch, Style prefix,
   Style repeat, Style unexpected, Style selected,
   int backgroundColor, int selectedBackgroundColor
) {
   public static final int DEFAULT_BACKGROUND_COLOR = 0xBD000000;
   public static final int DEFAULT_SELECTED_BACKGROUND_COLOR = 0xBD242424;

   public CommandCompletionStyle() {
      this(Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY,
         DEFAULT_BACKGROUND_COLOR, DEFAULT_SELECTED_BACKGROUND_COLOR);
   }

   public CommandCompletionStyle applyTo(CommandCompletionStyle other) {
      return new CommandCompletionStyle(
         suggestion.applyTo(other.suggestion),
         match.applyTo(other.match),
         dumbMatch.applyTo(other.dumbMatch),
         prefix.applyTo(other.prefix),
         repeat.applyTo(other.repeat),
         unexpected.applyTo(other.unexpected),
         selected.applyTo(other.selected),
         backgroundColor, selectedBackgroundColor);
   }

   public static Serializer SERIALIZER = new Serializer();

   public static class Serializer implements JsonDeserializer<CommandCompletionStyle> {
      @Override
      public CommandCompletionStyle deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
         JsonObject obj = json.getAsJsonObject();
         return new CommandCompletionStyle(
            getStyle(obj, "suggestion"),
            getStyle(obj, "match"),
            getStyle(obj, "dumb_match"),
            getStyle(obj, "prefix"),
            getStyle(obj, "repeat"),
            getStyle(obj, "unexpected"),
            getStyle(obj, "selected"),
            getColor(obj, "background", DEFAULT_BACKGROUND_COLOR),
            getColor(obj, "background_selected", DEFAULT_SELECTED_BACKGROUND_COLOR));
      }

      private Style getStyle(JsonObject object, String name) {
         JsonObject o = GsonHelper.getAsJsonObject(object, name, new JsonObject());
         return GSON.fromJson(o, Style.class);
      }

      private int getColor(JsonObject object, String name, int fallback) {
         String s = GsonHelper.getAsString(object, name, "");
         if (!s.isEmpty()) {
            if (s.startsWith("#")) { // TextColor uses `parseInt`, which fails for ARGB colors
               try {
                  return Integer.parseUnsignedInt(s.substring(1), 16);
               } catch (NumberFormatException e) {
                  return fallback;
               }
            }
            TextColor c = TextColor.parseColor(s);
            if (c != null) return c.getValue();
         }
         return fallback;
      }
   }
}
