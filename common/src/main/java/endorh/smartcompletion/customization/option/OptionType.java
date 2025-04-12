package endorh.smartcompletion.customization.option;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import endorh.smartcompletion.util.IncludeExcludeSet;
import endorh.smartcompletion.util.PolyFill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Function;

import static endorh.smartcompletion.customization.SmartCompletionResourceReloadListener.GSON;
import static endorh.smartcompletion.util.PolyFill.*;

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
   private final Codec<T> codec;

   protected OptionType(Codec<T> codec) {
      this.codec = codec;
   }

   public Codec<T> getCodec() {
      return codec;
   }

   public @Nullable T override(@Nullable T base, @Nullable T override, boolean replace) {
      if (replace) return override;
      return override != null? override : base;
   }

   public JsonElement serialize(@NotNull T value) {
      return getOrThrow(codec.encode(value, JsonOps.INSTANCE, JsonOps.INSTANCE.empty()));
   }
   public @Nullable T deserialize(JsonElement element) {
      if (element.isJsonNull()) return null;
      DataResult<T> result = codec.parse(JsonOps.INSTANCE, element);
      if (isSuccess(result)) return getOrThrow(result);
      return null;
   }

   protected static MutableComponent commandLink(
      String text, String command, Component tooltip,
      ChatFormatting... formats
   ) {
      return Component.literal(text).withStyle(formats).withStyle(s ->
         s.withClickEvent(suggestCommandClickEvent(command))
            .withHoverEvent(showTextHoverEvent(tooltip)));
   }
   protected static MutableComponent literal(String text, ChatFormatting... formats) {
      return Component.literal(text).withStyle(formats);
   }
   protected static <S extends SharedSuggestionProvider> void sendSuccess(CommandContext<S> c, Component message) {
      if (c.getSource() instanceof CommandSourceStack cs) {
         cs.sendSuccess(() -> message, false);
      } else if (c.getSource() instanceof ClientSuggestionProvider) {
         Minecraft client = Minecraft.getInstance();
         client.gui.getChat().addMessage(message);
         client.getNarrator().sayNow(message);
      }
   }
   protected static <S extends SharedSuggestionProvider> void sendFailure(CommandContext<S> c, Component message) {
      sendSuccess(c, Component.empty().append(message).withStyle(ChatFormatting.RED));
   }

   public abstract <S extends SharedSuggestionProvider, A extends ArgumentBuilder<S, A>> A registerCommand(
      A builder, CommandBuildContext context, String command, Option<T> option
   );

   public Option<T> create(String name, @NotNull T defaultValue) {
      return new Option<>(this, name, defaultValue);
   }

   public static class SimpleOptionType<T> extends OptionType<T> {
      private final Function<CommandBuildContext, ArgumentType<T>> argumentType;

      public SimpleOptionType(Codec<T> codec, Function<CommandBuildContext, ArgumentType<T>> argumentType) {
         super(codec);
         this.argumentType = argumentType;
      }

      public SimpleOptionType(Codec<T> codec, ArgumentType<T> argumentType) {
         this(codec, (CommandBuildContext c) -> argumentType);
      }

      public ArgumentType<T> getArgumentType(CommandBuildContext context) {
         return argumentType.apply(context);
      }

      @Override public <S extends SharedSuggestionProvider, A extends ArgumentBuilder<S, A>> A registerCommand(
         A builder, CommandBuildContext context, String command, Option<T> option
      ) {
         ArgumentType<T> argumentType = getArgumentType(context);
         return builder.then(
            RequiredArgumentBuilder.<S, T>argument("value", argumentType).executes(c -> {
               try {
                  //noinspection unchecked
                  T value = (T) c.getArgument("value", Object.class);
                  option.setUserValue(value);
                  String jsonValue = GSON.toJson(option.getType().serialize(value));
                  sendSuccess(c, Component.translatable(
                     "smartcompletion.command.set",
                     commandLink(option.getPath(), command,
                        Component.translatable("smartcompletion.command.hover.option"),
                        ChatFormatting.LIGHT_PURPLE),
                     literal(jsonValue, ChatFormatting.DARK_AQUA)));
               } catch (RuntimeException e) {
                  return 0;
               }
               return 1;
            })
         ).executes(c -> {
            String json = GSON.toJson(serialize(option.get()));
            sendSuccess(c, Component.translatable(
               "smartcompletion.command.get",
               commandLink(option.getPath(), command,
                  Component.translatable("smartcompletion.command.hover.option"),
                  ChatFormatting.LIGHT_PURPLE),
               literal(json, ChatFormatting.DARK_AQUA)));
            return 0;
         });
      }
   }

   public static SimpleOptionType<Boolean> bool() {
      return new SimpleOptionType<>(Codec.BOOL, BoolArgumentType.bool());
   }
   public static SimpleOptionType<Integer> integer(ArgumentType<Integer> argumentType) {
      return new SimpleOptionType<>(Codec.INT, argumentType);
   }
   public static SimpleOptionType<Integer> integer() {
      return integer(IntegerArgumentType.integer());
   }
   public static SimpleOptionType<Integer> color() {
      return new SimpleOptionType<>(Codec.STRING.xmap(
         s -> {
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
            }}, (Integer i) -> String.format("#%08X", i)),
         IntegerArgumentType.integer());
   }
   public static SimpleOptionType<Style> style() {
      return new SimpleOptionType<>(Style.Serializer.CODEC, PolyFill::styleArgument) {
         @Override public @Nullable Style override(@Nullable Style base, @Nullable Style override, boolean replace) {
            if (!replace && base != null && override != null)
               return override.applyTo(base);
            return super.override(base, override, replace);
         }
      };
   }
   public static SimpleOptionType<String> string(ArgumentType<String> argumentType) {
      return new SimpleOptionType<>(Codec.STRING, argumentType);
   }
   public static SimpleOptionType<String> string() {
      return string(StringArgumentType.string());
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
         super(IncludeExcludeSet.codec(subType.getCodec(), comparator));
         this.subType = subType;
      }

      @Override public <S extends SharedSuggestionProvider, A extends ArgumentBuilder<S, A>> A registerCommand(
         A builder, CommandBuildContext context, String command, Option<IncludeExcludeSet<T>> option
      ) {
         ArgumentType<T> subArgType = subType.getArgumentType(context);
         return builder.then(
            LiteralArgumentBuilder.<S>literal("add").then(
               RequiredArgumentBuilder.<S, T>argument("value", subArgType).executes(c -> {
                  //noinspection unchecked
                  T value = (T) c.getArgument("value", Object.class);
                  IncludeExcludeSet<T> override = new IncludeExcludeSet<>(option.getDefault().getComparator());
                  override.include(value);
                  option.overrideUserValue(override);
                  return 1;
               }))
         ).then(
            LiteralArgumentBuilder.<S>literal("remove").then(
               RequiredArgumentBuilder.<S, T>argument("value", subArgType).executes(c -> {
                  //noinspection unchecked
                  T value = (T) c.getArgument("value", Object.class);
                  IncludeExcludeSet<T> override = new IncludeExcludeSet<>(option.getDefault().getComparator());
                  override.exclude(value);
                  option.overrideUserValue(override);
                  return 1;
               }))
         ).then(
            LiteralArgumentBuilder.<S>literal("reset").then(
               RequiredArgumentBuilder.<S, T>argument("value", subArgType).executes(c -> {
                  //noinspection unchecked
                  T value = (T) c.getArgument("value", Object.class);
                  IncludeExcludeSet<T> userValue = option.getUserValue();
                  IncludeExcludeSet<T> copy = userValue != null? userValue.copy() : new IncludeExcludeSet<>(option.getDefault().getComparator());
                  copy.unInclude(value);
                  copy.unExclude(value);
                  option.overrideUserValue(copy, true);
                  return 1;
               })
            ).executes(c -> {
               option.overrideUserValue(null, true);
               return 1;
            })
         ).then(
            LiteralArgumentBuilder.<S>literal("test").then(
               RequiredArgumentBuilder.<S, T>argument("value", subArgType).suggests((ctx, b) -> {
                  for (T elem : option.get()) {
                     Pair<Integer, Component> test = membershipTest(elem, option);
                     b.suggest(GSON.toJson(subType.serialize(elem)), test.getSecond());
                  }
                  return b.buildFuture();
               }).executes(c -> {
                  //noinspection unchecked
                  T value = (T) c.getArgument("value", Object.class);
                  Pair<Integer, Component> test = membershipTest(value, option);
                  sendSuccess(c, test.getSecond());
                  return test.getFirst();
               }))
         ).executes(c -> {
            JsonArray arr = new JsonArray();
            option.get().stream()
               .map(subType::serialize)
               .forEach(arr::add);
            String json = GSON.toJson(arr);
            sendSuccess(c, Component.translatable(
               "smartcompletion.command.get",
               commandLink(option.getPath(), command,
                  Component.translatable("smartcompletion.command.hover.option"),
                  ChatFormatting.LIGHT_PURPLE),
               literal(json, ChatFormatting.DARK_AQUA)));
            return 0;
         });
      }

      private static @NotNull <T> Pair<Integer, Component> membershipTest(T value, Option<IncludeExcludeSet<T>> option) {
         IncludeExcludeSet<T> packValue = option.getPackValue();
         IncludeExcludeSet<T> userValue = option.getUserValue();
         boolean baked = option.get().contains(value);
         boolean pack = packValue != null && packValue.contains(value);
         boolean user = userValue != null && userValue.contains(value);
         return Pair.of(
            user ? 2 : baked ? 1 : pack ? -1 : 0, Component.translatable(
            user ? "smartcompletion.command.configure.set.added_by_user"
               : baked ? "smartcompletion.command.configure.set.added_by_pack"
               : pack ? "smartcompletion.command.configure.set.removed_by_user"
               : "smartcompletion.command.configure.set.not_added"));
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
