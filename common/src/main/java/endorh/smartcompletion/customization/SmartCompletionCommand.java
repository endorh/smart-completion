package endorh.smartcompletion.customization;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.option.OptionCategory;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.CharUtils;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Registers option commands to edit user values of the {@link SmartCompletionSettings}.
 * @see OptionCategory
 */
public class SmartCompletionCommand {
   public static <S extends SharedSuggestionProvider> void registerCommands(
      CommandDispatcher<S> dispatcher, CommandBuildContext context
   ) {
      dispatcher.register(
         SmartCompletionMod.getSmartCompletionSettings().registerCommand(
            LiteralArgumentBuilder.literal("smartcompletion"), context, "/smartcompletion"));
   }

   public static class LowerCaseWordArgumentType implements ArgumentType<String> {
      public static LowerCaseWordArgumentType word() {
         return new LowerCaseWordArgumentType();
      }
      private static final SimpleCommandExceptionType INVALID_LOWERCASE_WORD = new SimpleCommandExceptionType(
         Component.translatableEscape("parsing.word.lowercase.expected"));

      @Override public String parse(StringReader reader) throws CommandSyntaxException {
         if (reader.canRead() && reader.peek() == '"') {
            String s = reader.readQuotedString();
            if (s.chars().anyMatch(c -> !CharUtils.isAsciiAlphaLower((char) c)))
               throw INVALID_LOWERCASE_WORD.createWithContext(reader);
            return s;
         }
         StringBuilder sb = new StringBuilder();
         while (reader.canRead()) {
            if (reader.peek() == ' ') break;
            if (!CharUtils.isAsciiAlphaLower(reader.peek()))
               throw INVALID_LOWERCASE_WORD.createWithContext(reader);
            sb.append(reader.read());
         }
         String word = sb.toString();
         if (word.isEmpty()) throw INVALID_LOWERCASE_WORD.createWithContext(reader);
         return word;
      }

      @Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
         return ArgumentType.super.listSuggestions(context, builder);
      }

      @Override public Collection<String> getExamples() {
         return ArgumentType.super.getExamples();
      }

      public static <S extends SharedSuggestionProvider> String getWord(CommandContext<S> context, String name) {
         return context.getArgument(name, String.class);
      }
   }
}
