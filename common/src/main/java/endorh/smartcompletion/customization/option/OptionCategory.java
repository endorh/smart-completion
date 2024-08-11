package endorh.smartcompletion.customization.option;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Style;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Container for {@link Option} instances.<br>
 * <br>
 * Subclasses <b>must be final</b> and call {@link #initializeCategory()} at the end of their
 * constructors.<br>
 * <br>
 * Provides helper {@link #option} methods to create and register {@link Option} instances.
 *
 * @param <C> Self type.
 */
public abstract class OptionCategory<C extends OptionCategory<C>> {
   private static final Logger LOGGER = LogManager.getLogger();
   private final List<Option<?>> options = new ArrayList<>();
   private final List<OptionCategory<?>> categories = new ArrayList<>();
   private final @NotNull String name;
   private final @NotNull String file;
   private String @Nullable[] aliases;
   private @Nullable OptionCategory<?> parent;
   private final List<Pair<Boolean, Consumer<C>>> changeListeners = new ArrayList<>();
   private boolean suppressUpdates = false;

   protected OptionCategory(@NotNull String name) {
      this(name, name + ".json");
   }

   protected OptionCategory(@NotNull String name, @NotNull String file) {
      this.name = name;
      this.file = file;
   }

   @SuppressWarnings("unchecked") protected C getSelf() {
      return (C) this;
   }

   private void setParent(@Nullable OptionCategory<?> parent) {
      this.parent = parent;
   }

   protected void defineAlias(String... aliases) {
      if (this.aliases == null) this.aliases = aliases;
      else this.aliases = ArrayUtils.addAll(this.aliases, aliases);
   }

   protected String @Nullable[] getAliases() {
      return aliases;
   }

   public String getPath() {
      if (parent != null) return parent.getPath() + "." + name;
      return name;
   }

   private void batchChange(boolean recursive, Runnable action) {
      boolean outer = suppressUpdates;
      suppressUpdates = true;
      try {
         action.run();
      } finally {
         suppressUpdates = outer;
      }
      triggerChange(recursive);
   }

   private void triggerChange(boolean recursive) {
      if (suppressUpdates) return;
      for (Pair<Boolean, Consumer<C>> pair : changeListeners)
         if (!recursive || pair.getFirst()) pair.getSecond().accept(getSelf());
   }

   public void addChangeListener(Consumer<C> listener) {
      addChangeListener(true, listener);
   }
   public void addChangeListener(boolean recursive, Consumer<C> listener) {
      changeListeners.add(Pair.of(recursive, listener));
   }
   public void removeChangeListener(Consumer<C> listener) {
      changeListeners.removeIf(p -> p.getSecond().equals(listener));
   }

   public void reloadPackSettings(Collection<JsonElement> settings) {
      batchChange(false, () -> {
         options.forEach(o -> o.setPackValue(null));
         for (JsonElement setting : settings) {
            if (!(setting instanceof JsonObject)) continue;
            JsonObject json = (JsonObject) setting;
            boolean replace = GsonHelper.getAsBoolean(json, "replace", false);
            for (Option<?> option : options)
               overridePackValueIfFound(json, option, replace);
         }
      });
   }
   private <T> void overridePackValueIfFound(JsonObject json, Option<T> option, boolean replace) {
      for (String name : option.getAllNames())
         if (json.has(name)) option.overridePackValue(
            option.getType().deserialize(json.get(name)), replace);
   }


   /**
    * Must be called at the end of subclass initialization.
    */
   protected final void initializeCategory() {
      Arrays.stream(getClass().getDeclaredFields())
         .filter(f -> f.getType() == Option.class && Modifier.isPublic(f.getModifiers()))
         .forEach(f -> {
            try {
               Option<?> value = (Option<?>) f.get(this);
               int i = options.indexOf(value);
               if (i == -1)
                  throw new IllegalStateException("Unlisted option instance found in class " + getClass().getCanonicalName());
               value.setName(f.getName());
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         });
      // Run the non-nullity check for all registered options
      for (Option<?> option : options) option.getName();
   }

   private static <T> void capture(Option<T> o, Consumer<Option<T>> action) {
      action.accept(o);
   }

   public @NotNull String getName() {
      return name;
   }

   public @NotNull Set<String> getAllNames() {
      Set<String> set = new HashSet<>();
      set.add(getName());
      String[] aliases = getAliases();
      if (aliases != null) Collections.addAll(set, aliases);
      return set;
   }

   public @NotNull String getFile() {
      return file;
   }

   public List<Option<?>> getOptions() {
      return options;
   }

   public List<OptionCategory<?>> getCategories() {
      return categories;
   }

   protected <CC extends OptionCategory<?>> CC subCategory(Supplier<CC> constructor) {
      CC category = constructor.get();
      ((OptionCategory<?>) category).setParent(this);
      category.addChangeListener(c -> triggerChange(true));
      categories.add(category);
      return category;
   }

   protected <T> Option<T> alias(Option<T> option, String... aliases) {
      option.defineAlias(aliases);
      return option;
   }

   protected <T> Option<T> option(
      OptionType<T> type, @NotNull T defaultValue
   ) {
      Option<T> instance = new Option<>(type, null, defaultValue);
      instance.setParent(this);
      instance.addChangeListener(v -> triggerChange(false));
      options.add(instance);
      return instance;
   }

   protected Option<Boolean> option(boolean defaultValue) {
      return option(OptionType.bool(), defaultValue);
   }

   protected Option<Integer> option(int defaultValue) {
      return option(OptionType.integer(), defaultValue);
   }

   protected Option<Integer> color(int defaultValue) {
      return option(OptionType.color(), defaultValue);
   }

   protected Option<Style> option(Style defaultValue) {
      return option(OptionType.style(), defaultValue);
   }
}
