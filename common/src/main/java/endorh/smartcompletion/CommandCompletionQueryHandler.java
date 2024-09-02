package endorh.smartcompletion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.customization.SmartCompletionSettings;
import endorh.smartcompletion.duck.SmartCommandSuggestions;
import endorh.smartcompletion.util.EvictingLinkedHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static endorh.smartcompletion.SmartCommandCompletion.ARG_WORD_SPLITTER;
import static endorh.smartcompletion.SmartCompletionMod.getSmartCompletionSettings;
import static java.lang.Math.max;

public class CommandCompletionQueryHandler {
   private final Map<QueryType, Map<String, CachedSuggestions>> CACHE = Util.make(new EnumMap<>(QueryType.class), e -> {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      for (QueryType type : QueryType.values()) e.put(
         type, Util.make(
            new EvictingLinkedHashMap<>(
               max(0, settings.cache.query_cache_max_entries.get()), (s, c) -> c.suggestions().cancel(true)
            ), m -> settings.cache.query_cache_max_entries.addChangeListener(max -> m.setMaxSize(max(0, max)))));
      settings.cache.enable_query_cache.addChangeListener(enabled -> {
         if (!enabled) e.values().forEach(Map::clear);
      });
   });

   private final SmartCommandSuggestions commandSuggestions;
   private final ClientPacketListener connection;
   private SplitCommandQuery lastQuery;
   private AggregatedSuggestions lastAggregatedSuggestions;
   private @Nullable Suggestions informedSuggestions = null;
   private @Nullable Suggestions argBlindSuggestions = null;
   private @Nullable Suggestions wordBlindSuggestions = null;


   public CommandCompletionQueryHandler(SmartCommandSuggestions suggestions, Minecraft minecraft) {
      commandSuggestions = suggestions;
      LocalPlayer player = minecraft.player;
      if (player == null) throw new IllegalStateException("Command Suggestions without player connection.");
      connection = player.connection;
   }

   private boolean isCacheEnabled(QueryType type) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      return settings.cache.enable_query_cache.get() && (
         type == QueryType.INFORMED
            ? settings.cache.cache_informed_queries_on_type.get() || settings.cache.cache_informed_queries.get()
            : settings.cache.cache_blind_queries_on_type.get() || settings.cache.cache_blind_queries.get());
   }

   private boolean isCacheVolatile(QueryType type) {
      SmartCompletionSettings settings = getSmartCompletionSettings();
      return !settings.cache.enable_query_cache.get() || (
         type == QueryType.INFORMED
            ? !settings.cache.cache_informed_queries.get()
            : !settings.cache.cache_blind_queries.get());
   }

   private void resetVolatileCache() {
      for (QueryType type : QueryType.values()) if (isCacheVolatile(type)) CACHE.get(type).clear();
   }

   private @Nullable CompletableFuture<Suggestions> getCachedSuggestions(QueryType type, String command) {
      if (!isCacheEnabled(type)) return null;
      Map<String, CachedSuggestions> typeCache = CACHE.get(type);
      CachedSuggestions cached = typeCache.get(command);
      if (cached == null) return null;
      if (cached.isExpired()) {
         cached.suggestions().cancel(true);
         typeCache.remove(command);
         return null;
      }
      return cached.suggestions();
   }

   private void cacheSuggestions(QueryType type, String command, CompletableFuture<Suggestions> suggestions) {
      Map<String, CachedSuggestions> typeCache = CACHE.get(type);
      CachedSuggestions prev = typeCache.get(command);
      if (prev == null) {
         typeCache.put(command, new CachedSuggestions(suggestions, System.currentTimeMillis()));
      } else if (prev.suggestions() != suggestions) {
         prev.suggestions().cancel(true);
         typeCache.put(command, new CachedSuggestions(suggestions, System.currentTimeMillis()));
      }
   }

   private StringReader reader(String command) {
      StringReader reader = new StringReader(command);
      if (reader.canRead() && reader.peek() == '/') reader.skip();
      return reader;
   }
   private void requestSuggestions(
      QueryType type, String command, int contextPos, String query,
      Executor updateExecutor
   ) {
      CompletableFuture<Suggestions> cached = getCachedSuggestions(type, command);
      if (cached != null) {
         if (cached.isDone()) {
            cached.thenAccept(s -> receiveSuggestions(s, type, command, contextPos, query, updateExecutor));
         } // else we don't add a second listener
      } else {
         CommandDispatcher<SharedSuggestionProvider> dispatcher = connection.getCommands();
         ParseResults<SharedSuggestionProvider> parse = dispatcher.parse(reader(command), connection.getSuggestionsProvider());
         CompletableFuture<Suggestions> suggestions = dispatcher.getCompletionSuggestions(parse, contextPos);
         suggestions.thenAccept(s -> receiveSuggestions(s, type, command, contextPos, query, updateExecutor));
         cacheSuggestions(type, command, suggestions);
      }
   }

   public int findLastWordPosition(String argQuery) {
      Matcher m = ARG_WORD_SPLITTER.matcher(argQuery);
      int pos = -1;
      while (m.find()) pos = m.end();
      return pos;
   }

   public void updateQuery(
      @NotNull String command, int cursor, ParseResults<SharedSuggestionProvider> parseResults
   ) {
      if (command.length() <= 1) resetVolatileCache();
      SuggestionContext<SharedSuggestionProvider> suggestionContext = parseResults.getContext().findSuggestionContext(cursor);

      // Blind Query
      int startPos = suggestionContext.startPos;
      String argBlindCommand = command.substring(0, startPos);
      String argQuery = command.substring(startPos, cursor);

      int lastWordPos = findLastWordPosition(argQuery);
      boolean hasWordBlind = lastWordPos > 0;
      lastWordPos += startPos;
      String wordBlindCommand = null;
      String wordQuery = null;
      if (hasWordBlind) {
         wordBlindCommand = command.substring(0, lastWordPos);
         wordQuery = command.substring(lastWordPos, cursor);
      }

      updateQuery(new SplitCommandQuery(
         command, cursor, startPos,
         argBlindCommand, argQuery,
         hasWordBlind? lastWordPos : -1, wordBlindCommand, wordQuery));
   }

   private void updateQuery(SplitCommandQuery query) {
      SplitCommandQuery lastQuery = this.lastQuery;
      this.lastQuery = query;
      lastAggregatedSuggestions = null;

      boolean informedChange = lastQuery == null || !lastQuery.informedEquivalent(query);
      boolean argBlindChange = lastQuery == null || !lastQuery.argBlindEquivalent(query);
      boolean wordBlindChange = query.wordStartPos() != -1 && (lastQuery == null || !lastQuery.wordBlindEquivalent(query));

      if (informedChange) informedSuggestions = null;
      if (argBlindChange) argBlindSuggestions = null;
      if (wordBlindChange) wordBlindSuggestions = null;

      ConflatingUpdateBarrier.withConflatingLock(executor -> {
         if (informedChange) requestSuggestions(QueryType.INFORMED, query.trimmedCommand(), query.cursor(), null, executor);
         if (argBlindChange) requestSuggestions(QueryType.ARG_BLIND, query.argBlindCommand(), query.argStartPos(), query.argQuery(), executor);
         if (wordBlindChange) requestSuggestions(QueryType.WORD_BLIND, query.wordBlindCommand(), query.wordStartPos(), query.wordQuery(), executor);
      });

      // If there is no change at all, there's a chance that we have retyped the '/' empty command,
      // and we need to show the suggestion list nonetheless
      if (!informedChange && !argBlindChange && !wordBlindChange)
         updateSuggestions();
   }

   private void receiveSuggestions(
      Suggestions suggestions, QueryType type,
      String command, int pos, String query,
      Executor updateExecutor
   ) {
      SplitCommandQuery lastQuery = this.lastQuery;
      if (lastQuery == null || lastQuery.hasExpired(type, pos, command, query)) return;
      switch (type) {
         case INFORMED -> informedSuggestions = suggestions;
         case ARG_BLIND -> argBlindSuggestions = suggestions;
         case WORD_BLIND -> wordBlindSuggestions = suggestions;
      }
      updateExecutor.execute(this::updateSuggestions);
   }

   private void updateSuggestions() {
      SplitCommandQuery query = lastQuery;
      boolean hasWordQuery = query.wordStartPos() != -1;
      AggregatedSuggestions sorted = SmartCommandCompletion.filterAndSort(
         argBlindSuggestions, wordBlindSuggestions, informedSuggestions,
         StringRange.between(query.argStartPos(), query.cursor()),
         query.argQuery(),
         hasWordQuery ? StringRange.between(query.wordStartPos(), query.cursor()) : null,
         hasWordQuery ? query.wordQuery() : null);
      AggregatedSuggestions last = lastAggregatedSuggestions;
      lastAggregatedSuggestions = sorted;
      if (sorted.isEquivalent(last)) return;
      commandSuggestions.updateAggregatedSuggestions(sorted);
   }

   public record SplitCommandQuery(
      String command, int cursor, int argStartPos,
      String argBlindCommand, String argQuery,
      int wordStartPos, @Nullable String wordBlindCommand, @Nullable String wordQuery
   ) {
      public SplitCommandQuery {
         if (wordStartPos > -1) Objects.requireNonNull(wordBlindCommand, wordQuery);
      }

      public String trimmedCommand() {
         return command.substring(0, cursor);
      }

      public boolean informedEquivalent(SplitCommandQuery other) {
         return cursor == other.cursor() && command.equals(other.command());
      }
      public boolean hasExpired(QueryType type, int pos, String command, String query) {
         return switch (type) {
            case INFORMED -> cursor != pos || !command.equals(trimmedCommand());
            case ARG_BLIND -> argStartPos != pos || !argBlindCommand.equals(command) || !argQuery.equals(query);
            case WORD_BLIND -> wordStartPos != pos || !Objects.equals(wordBlindCommand, command) || !Objects.equals(wordQuery, query);
         };
      }
      public boolean argBlindEquivalent(SplitCommandQuery other) {
         return argStartPos == other.argStartPos() && argBlindCommand.equals(other.argBlindCommand()) && argQuery.equals(other.argQuery());
      }
      public boolean wordBlindEquivalent(SplitCommandQuery other) {
         return wordStartPos == other.wordStartPos()
            && Objects.equals(wordBlindCommand, other.wordBlindCommand())
            && Objects.equals(wordQuery, other.wordQuery());
      }
   }

   public record CachedSuggestions(
      @NotNull CompletableFuture<Suggestions> suggestions,
      long timeStamp
   ) {
      public boolean isExpired() {
         return System.currentTimeMillis() - timeStamp
            > getSmartCompletionSettings().cache.query_cache_expiration_seconds.get() * 1000L || suggestions.isCancelled();
      }
   }

   public enum QueryType {
      INFORMED,
      ARG_BLIND,
      WORD_BLIND
   }

   /**
    * Conflates submitted tasks while locked, so, once released,
    * only the last submitted task is executed.<br>
    * <br>
    * While released, submitted tasks are executed immediately.
    */
   public static class ConflatingUpdateBarrier implements Executor {
      private final AtomicReference<Runnable> conflated = new AtomicReference<>();
      private final AtomicBoolean lock;
      public ConflatingUpdateBarrier(boolean locked) {
         lock = new AtomicBoolean(locked);
      }

      @Override public synchronized void execute(@NotNull Runnable action) {
         if (lock.get())
            conflated.set(action);
         else action.run();
      }

      public synchronized void lock() {
         lock.set(true);
      }

      public synchronized void release() {
         lock.set(false);
         Runnable action = conflated.getAndSet(null);
         if (action != null) action.run();
      }

      public static void withConflatingLock(Consumer<Executor> action) {
         ConflatingUpdateBarrier lock = new ConflatingUpdateBarrier(true);
         action.accept(lock);
         lock.release();
      }
   }
}
