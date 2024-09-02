package endorh.smartcompletion.customization;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.option.Option;
import endorh.smartcompletion.customization.option.OptionCategory;
import endorh.smartcompletion.customization.option.OptionType;
import endorh.smartcompletion.util.IncludeExcludeSet;
import net.minecraft.network.chat.Style;

import java.util.Comparator;

/**
 * Smart completion settings.<br>
 * Get the settings instance by calling
 * {@link SmartCompletionMod#getSmartCompletionSettings() getSmartCompletionSettings()}.
 */
public final class SmartCompletionSettings extends OptionCategory<SmartCompletionSettings> {
   /** Enable smart completion logic (feature toggle for the mod). */
   public final Option<Boolean> enabled = option(true);
   /** Enable completion keys ({@code <Ctrl>+<Space>}). */
   public final Option<Boolean> enable_completion_keys = option(true);
   /** Enable completion with {@code <Enter>} if current command is known to be incomplete. */
   public final Option<Boolean> enable_completion_with_enter = option(true);
   /**
    * Force suggestions to show when typing a slash ({@code /}).<br>
    * Makes it harder to use the command history.
    */
   public final Option<Boolean> enforce_show_suggestions_on_slash = option(false);
   /**
    * Enable suggestion highlighting, which requires replacing vanilla rendering of
    * the suggestion list, and may conflict with other mods that do the same.
    */
   public final Option<Boolean> enable_suggestion_highlighting = option(true);
   /**
    * Invert the order of suggestions, so the most relevant are closer to the
    * input bar.<br>
    * This setting is only effective when {@link #enable_suggestion_highlighting} is
    * {@code true} and the vanilla suggestions are anchored to the bottom of the screen.
    */
   public final Option<Boolean> invert_suggestion_order = option(true);
   /**
    * Include smart matches, which match segments of the query with initials of
    * parts of the suggestions.
    */
   public final Option<Boolean> include_smart_matches = option(true);
   /**
    * Include weak matches, which search for each query part anywhere on the
    * suggestions, in order.
    */
   public final Option<Boolean> include_weak_matches = option(true);
   /** Include vanilla suggestions not already matched by other means. */
   public final Option<Boolean> include_unexpected_suggestions = option(true);
   /** Minimum size of a weak match to be considered. */
   public final Option<Integer> minimum_weak_match_length = option(2);
   /** Delete input after cursor when accepting a suggestion with {@code <Ctrl>+<Space>}. */
   public final Option<Boolean> erase_remainder_on_ctrl_space = option(true);
   /** Delete input after cursor when accepting a suggestion with {@code <Enter>}. */
   public final Option<Boolean> erase_remainder_on_enter = option(false);
   /** Delete input after cursor when accepting a suggestion with {@code <Tab>}. */
   public final Option<Boolean> erase_remainder_on_tab = option(false);
   /** Delete input after cursor when accepting a suggestion by clicking on the suggestion list. */
   public final Option<Boolean> erase_remainder_on_left_click = option(false);
   /** Delete input after cursor when accepting a suggestion by right-clicking on the suggestion list. */
   public final Option<Boolean> erase_remainder_on_right_click = option(false);
   /** Delete input after cursor when accepting a suggestion by middle-clicking on the suggestion list. */
   public final Option<Boolean> erase_remainder_on_middle_click = option(true);

   /**
    * Settings regarding the splitting of flatcase commands.
    */
   public final FlatcaseSplittingSettings flatcase_splitting = subCategory(FlatcaseSplittingSettings::new);
   /**
    * Style settings.<br>
    * {@code null} in test environment.
    */
   public final SuggestionStyleSettings style;
   /**
    * Settings related to the caching of queries.
    */
   public final CacheSettings cache = subCategory(CacheSettings::new);

   public SmartCompletionSettings(boolean testing) {
      super("settings");
      style = !testing? subCategory(SuggestionStyleSettings::new) : null;
      initializeCategory();
   }

   /**
    * Settings regarding the splitting of flatcase commands.
    */
   public static final class FlatcaseSplittingSettings extends OptionCategory<FlatcaseSplittingSettings> {
      private static Comparator<String> reverseLength() {
         return Comparator.comparingInt(String::length).reversed();
      }
      /**
       * Suffixes that are recognized after a known word if found remaining between known words.
       */
      public final Option<IncludeExcludeSet<String>> suffixes = option(
         OptionType.set(OptionType.string(), reverseLength()),
         new IncludeExcludeSet<>(reverseLength()));
      /**
       * Known words used to split flatcase commands.
       */
      public final Option<IncludeExcludeSet<String>> words = option(
         OptionType.set(OptionType.string(), reverseLength()),
         new IncludeExcludeSet<>(reverseLength()));

      public FlatcaseSplittingSettings() {
         super("flatcase_splitting");
         defineAlias("command_splitting");
         initializeCategory();
      }
   }

   /**
    * Style settings.
    */
   public static final class SuggestionStyleSettings extends OptionCategory<SuggestionStyleSettings> {
      /** Style used for suggestion text. */
      public final Option<Style> suggestion = option(Style.EMPTY);
      /** Style used for matched text in smart suggestions. */
      public final Option<Style> match = option(Style.EMPTY);
      /** Style used for matched text in weak suggestions. */
      public final Option<Style> weak_match = option(Style.EMPTY);
      /** Style used for shadowed prefixes in suggestions. */
      public final Option<Style> prefix = option(Style.EMPTY);
      /** Style used for alternative matches for a query part that could've matched more than one suggestion part. */
      public final Option<Style> repeat = option(Style.EMPTY);
      /** Style used for unexpected suggestions. */
      public final Option<Style> unexpected = option(Style.EMPTY);
      /** Style used for the selected suggestion text. */
      public final Option<Style> selected = option(Style.EMPTY);
      /** Background color of the suggestion list, in {@code AARRGGBB} format. */
      public final Option<Integer> background_color = alias(color(0xBD000000), "background");
      /** Background color of the row of the selected suggestion, in {@code AARRGGBB} format. */
      public final Option<Integer> background_selected_color = alias(color(0xBD242424), "background_selected");
      /**
       * Color of the ellipsis dots above or below the suggestions list when scrolling is possible,
       * in {@code #AARRGGBB} format.
       */
      public final Option<Integer> ellipsis_color = color(0x80FFFFFF);

      public SuggestionStyleSettings() {
         super("style");
         defineAlias("completion_style");
         initializeCategory();
      }
   }

   /**
    * Settings related to the caching of suggestion queries.
    */
   public static final class CacheSettings extends OptionCategory<CacheSettings> {
      /**
       * Enable cache features.<br>
       * Convenience option to disable all other cache features at once.
       */
      public final Option<Boolean> enable_query_cache = option(true);
      /**
       * Enable caching of uninformed queries while you type the same argument,
       * as they should always return the same suggestions under normal circumstances.<br>
       * <br>
       * Only effective if {@link #enable_query_cache} is {@code true}.
       */
      public final Option<Boolean> cache_blind_queries_on_type = option(true);
      /**
       * Enable caching of informed queries while you type the same argument, as they
       * should always return the same suggestions under normal circumstances.<br>
       * <br>
       * The benefit of caching informed queries is significantly smaller than that of
       * caching informed queries.<br>
       * <br>
       * Only effective if {@link #enable_query_cache} is {@code true}.
       */
      public final Option<Boolean> cache_informed_queries_on_type = option(true);
      /**
       * Enable caching of blind queries between different commands,
       * according to the limits set by {@link #query_cache_max_entries}
       * and {@link #query_cache_expiration_seconds}.<br>
       * <br>
       * Caching queries may lead to unexpected nuisances when the commands
       * you're able to execute change.<br>
       * For example, if you've just become an operator, you may need to wait
       * {@link #query_cache_expiration_seconds} to get smart completions
       * for operator commands.<br>
       * <br>
       * Only effective if {@link #enable_query_cache} is {@code true}.
       */
      public final Option<Boolean> cache_blind_queries = option(false);
      /**
       * Enable caching of informed queries between different commands, according to the
       * limits set by {@link #query_cache_max_entries} and {@link #query_cache_expiration_seconds}.<br>
       * <br>
       * Caching queries may lead to unexpected nuisances when the commands you're able
       * to execute change.<br>
       * For example, if you've just become an operator, you may need to wait
       * {@link #query_cache_expiration_seconds} to get smart completions for operator commands.<br>
       * <br>
       * The benefit of caching informed queries is significantly smaller than that of
       * caching informed queries.<br>
       * <br>
       * Only effective if {@link #enable_query_cache} is {@code true}.
       */
      public final Option<Boolean> cache_informed_queries = option(false);
      /**
       * Maximum number of entries to keep cached. This limit is separate for each
       * query type.<br>
       * <br>
       * Each entry may contain an arbitrary number of results, so the memory used
       * may vary greatly.
       */
      public final Option<Integer> query_cache_max_entries = option(40);
      /**
       * Maximum number of seconds to preserve query cache entries.<br>
       * <br>
       * Setting this too high will only aggravate the nuisances caused by
       * commands that change frequently, if {@link #cache_blind_queries} or
       * {@link #cache_informed_queries} change frequently.
       */
      public final Option<Integer> query_cache_expiration_seconds = option(60);

      /**
       * Enable caching of splits of flatcase commands.<br>
       * May marginally improve speed of completions for the base commands.
       */
      public final Option<Boolean> enable_split_cache = option(true);
      /**
       * Maximum number of split commands to keep in the split cache.
       */
      public final Option<Integer> split_cache_max_entries = option(2048);

      public CacheSettings() {
         super("cache");
         initializeCategory();
      }
   }
}