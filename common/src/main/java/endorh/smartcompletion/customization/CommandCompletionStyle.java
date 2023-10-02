package endorh.smartcompletion.customization;

import com.google.gson.*;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

public class CommandCompletionStyle {
   public static final int DEFAULT_BACKGROUND_COLOR = 0xBD000000;
   public static final int DEFAULT_SELECTED_BACKGROUND_COLOR = 0xBD242424;

   private final Style suggestion;
   private final Style match;
   private final Style dumbMatch;
   private final Style prefix;
   private final Style repeat;
   private final Style unexpected;
   private final Style selected;
   private final Integer backgroundColor;
   private final Integer selectedBackgroundColor;

   public CommandCompletionStyle(
      Style suggestion, Style match, Style dumbMatch, Style prefix, Style repeat, Style unexpected,
      Style selected, @Nullable Integer backgroundColor, @Nullable Integer selectedBackgroundColor
   ) {
      this.suggestion = suggestion;
      this.match = match;
      this.dumbMatch = dumbMatch;
      this.prefix = prefix;
      this.repeat = repeat;
      this.unexpected = unexpected;
      this.selected = selected;
      this.backgroundColor = backgroundColor;
      this.selectedBackgroundColor = selectedBackgroundColor;
   }

   public CommandCompletionStyle() {
      this(Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY, Style.EMPTY,
         null, null);
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
         backgroundColor != null? backgroundColor : other.backgroundColor,
         selectedBackgroundColor != null? selectedBackgroundColor : other.selectedBackgroundColor);
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
            getColor(obj, "background"),
            getColor(obj, "background_selected"));
      }

      private static Style getStyle(JsonObject object, String name) {
         JsonObject o = GsonHelper.getAsJsonObject(object, name, new JsonObject());
         return SmartCompletionResourceReloadListener.GSON.fromJson(o, Style.class);
      }

      private Integer getColor(JsonObject object, String name) {
         String s = GsonHelper.getAsString(object, name, "");
         if (!s.isEmpty()) {
            if (s.startsWith("#")) { // TextColor uses `parseInt`, which fails for ARGB colors
               try {
                  return Integer.parseUnsignedInt(s.substring(1), 16);
               } catch (NumberFormatException e) {
                  return null;
               }
            }
            TextColor c = TextColor.parseColor(s);
            if (c != null) return c.getValue();
         }
         return null;
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

   public Style selected() {
      return selected;
   }

   public int backgroundColor() {
      return backgroundColor != null? backgroundColor : DEFAULT_BACKGROUND_COLOR;
   }

   public int selectedBackgroundColor() {
      return selectedBackgroundColor != null? selectedBackgroundColor : DEFAULT_SELECTED_BACKGROUND_COLOR;
   }
}
