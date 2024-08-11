package endorh.smartcompletion.customization.option;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.serialization.Codec;
import endorh.smartcompletion.util.IncludeExcludeSet;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Comparator;

import static endorh.smartcompletion.customization.SmartCompletionResourceReloadListener.GSON;

/**
 * Represents a type of option that can be configured in the game.<br>
 * <br>
 * An option type is responsible for:
 * <ul>
 *    <li>Serializing and deserializing option values to and from JSON, by providing a {@link Codec}.</li>
 *    <li>Defining how higher-priority values can override lower ones, in the {@link #override} method.</li>
 *    <li>Registering commands to allow the user to edit the option value.</li>
 * </ul>
 * For simple types, the {@link SimpleOptionType}, which defers all logic to a {@link Codec}
 * and an {@link ArgumentType} should suffice.
 *
 * @param <T> The type of the option value.
 */
public abstract class OptionType<T> {
   private final JsonDeserializer<T> deserializer;
   private final TypeToken<T> token;

   protected OptionType(JsonDeserializer<T> deserializer, TypeToken<T> token) {
      this.deserializer = deserializer;
      this.token = token;
   }

   public Type getType() {
      return token.getType();
   }

   public JsonDeserializer<T> getDeserializer() {
      return deserializer;
   }

   public @Nullable T override(@Nullable T base, @Nullable T override, boolean replace) {
      if (replace) return override;
      return override != null? override : base;
   }

   public @Nullable T deserialize(JsonElement element) {
      if (element.isJsonNull()) return null;
      try {
         return deserializer.deserialize(element, token.getType(), GSON::fromJson);
      } catch (JsonParseException e) {
         return null;
      }
   }

   public Option<T> create(String name, @NotNull T defaultValue) {
      return new Option<>(this, name, defaultValue);
   }

   public static class SimpleOptionType<T> extends OptionType<T> {
      public SimpleOptionType(JsonDeserializer<T> deserializer, TypeToken<T> token) {
         super(deserializer, token);
      }
   }

   public static SimpleOptionType<Boolean> bool() {
      return new SimpleOptionType<>(
         (json, type, ctx) -> json.getAsBoolean(), TypeToken.get(Boolean.class));
   }
   public static SimpleOptionType<Integer> integer() {
      return new SimpleOptionType<>(
         (json, type, ctx) -> json.getAsInt(), TypeToken.get(Integer.class));
   }
   public static SimpleOptionType<Integer> color() {
      return new SimpleOptionType<>((json, type, ctx) -> {
         String s = json.getAsString();
         try {
            if (s.charAt(0) == '#') s = s.substring(1);
            if (s.length() < 3 || s.length() > 8) return 0;
            if (s.length() <= 4) {
               StringBuilder sb = new StringBuilder(s.length() * 2);
               s.chars().forEach(i -> sb.append((char) i).append((char) i));
               s = sb.toString();
            }
            return (int) Long.parseLong(s, 16);
         } catch (NumberFormatException e) {
            return 0;
         }
      }, TypeToken.get(Integer.class));
   }
   public static SimpleOptionType<Style> style() {
      return new SimpleOptionType<Style>(new Style.Serializer(), TypeToken.get(Style.class)) {
         @Override public @Nullable Style override(@Nullable Style base, @Nullable Style override, boolean replace) {
            if (!replace && base != null && override != null)
               return override.applyTo(base);
            return super.override(base, override, replace);
         }
      };
   }
   public static SimpleOptionType<String> string() {
      return new SimpleOptionType<>((json, type, ctx) -> json.getAsString(), TypeToken.get(String.class));
   }
   public static <T> IncludeExcludeSetOptionType<T> set(SimpleOptionType<T> type) {
      return new IncludeExcludeSetOptionType<>(type);
   }
   public static <T> IncludeExcludeSetOptionType<T> set(SimpleOptionType<T> type, Comparator<T> comparator) {
      return new IncludeExcludeSetOptionType<>(type, comparator);
   }

   public static class IncludeExcludeSetOptionType<T> extends OptionType<IncludeExcludeSet<T>> {
      private final SimpleOptionType<T> subType;

      public IncludeExcludeSetOptionType(SimpleOptionType<T> subType) {
         this(subType, null);
      }
      public IncludeExcludeSetOptionType(SimpleOptionType<T> subType, Comparator<T> comparator) {
         //noinspection unchecked
         super(IncludeExcludeSet.deserializer(subType.getDeserializer()),
            (TypeToken<IncludeExcludeSet<T>>) TypeToken.getParameterized(IncludeExcludeSet.class, subType.getType()));
         this.subType = subType;
      }

      @Override public @Nullable IncludeExcludeSet<T> override(
         @Nullable IncludeExcludeSet<T> base, @Nullable IncludeExcludeSet<T> override, boolean replace
      ) {
         if (replace) return override;
         if (override != null)
            return base != null? base.override(override) : override;
         return base;
      }
   }
}
