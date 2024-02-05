package endorh.smartcompletion.customization;

import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

import static endorh.smartcompletion.customization.SmartCompletionResourceReloadListener.GSON;

public record CommandCompletionStyle(
   Style suggestion, Style match, Style dumbMatch, Style prefix,
   Style repeat, Style unexpected, Style selected,
   @Nullable Integer backgroundColor, @Nullable Integer selectedBackgroundColor
) {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final int DEFAULT_BACKGROUND_COLOR = 0xBD000000;
   public static final int DEFAULT_SELECTED_BACKGROUND_COLOR = 0xBD242424;

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
            getARGBColor(obj, "background"),
            getARGBColor(obj, "background_selected"));
      }

      private Style getStyle(JsonObject object, String name) {
         JsonObject o = GsonHelper.getAsJsonObject(object, name, new JsonObject());
         #if POST_MC_1_20_3
            DataResult<Style> style = Style.Serializer.CODEC.parse(JsonOps.INSTANCE, o);
            return style.resultOrPartial(LOGGER::warn).orElse(Style.EMPTY);
         #else
            return GSON.fromJson(o, Style.class);
         #endif
      }

      private Integer getARGBColor(JsonObject object, String name) {
         String s = GsonHelper.getAsString(object, name, "");
         if (!s.isEmpty()) {
            if (s.startsWith("#")) { // TextColor uses `parseInt`, which fails for ARGB colors
               try {
                  return Integer.parseUnsignedInt(s.substring(1), 16);
               } catch (NumberFormatException e) {
                  return null;
               }
            }
            return parseColor(s);
         }
         return null;
      }
   }

   public int getBackgroundColor() {
      return backgroundColor != null? backgroundColor : DEFAULT_BACKGROUND_COLOR;
   }

   public int getSelectedBackgroundColor() {
      return selectedBackgroundColor != null? selectedBackgroundColor : DEFAULT_SELECTED_BACKGROUND_COLOR;
   }

   public static Integer parseColor(String s) {
      #if POST_MC_1_20_3
         return TextColor.parseColor(s).resultOrPartial(LOGGER::warn).map(TextColor::getValue).orElse(null);
      #else
         TextColor c = TextColor.parseColor(s);
         return c != null? c.getValue() : null;
      #endif
   }
}
