package endorh.smartcompletion.customization.option;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Style;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static endorh.smartcompletion.customization.SmartCompletionResourceReloadListener.GSON;

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
   private final @Nullable Path configDirectory;
   private final @NotNull String name;
   private final @NotNull String file;
   private @Nullable OptionCategory<?> parent;
   private final List<Pair<Boolean, Consumer<C>>> changeListeners = new ArrayList<>();
   private boolean suppressUpdates = false;

   protected OptionCategory(@Nullable Path configDirectory, @NotNull String name) {
      this(configDirectory, name, name + ".json");
   }

   protected OptionCategory(@Nullable Path configDirectory, @NotNull String name, @NotNull String file) {
      this.configDirectory = configDirectory;
      this.name = name;
      this.file = file;
   }

   @SuppressWarnings("unchecked") protected C getSelf() {
      return (C) this;
   }

   private void setParent(@Nullable OptionCategory<?> parent) {
      this.parent = parent;
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

   public @Nullable File getSettingsFile() {
      return configDirectory != null ? configDirectory.resolve(name + ".json").toFile() : null;
   }

   public void reloadPackSettings(Collection<JsonElement> settings) {
      batchChange(false, () -> {
         options.forEach(o -> o.setPackValue(null));
         for (JsonElement setting : settings) {
            if (!(setting instanceof JsonObject json)) continue;
            boolean replace = GsonHelper.getAsBoolean(json, "replace", false);
            for (Option<?> option : options)
               overridePackValueIfFound(json, option, replace);
         }
      });
   }
   private <T> void overridePackValueIfFound(JsonObject json, Option<T> option, boolean replace) {
      if (json.has(option.getName()))
         option.overridePackValue(option.getType().deserialize(json.get(option.getName())), replace);
   }

   public void loadUserSettings() {
      batchChange(false, () -> {
         File jsonFile = getSettingsFile();
         options.forEach(o -> o.setUserValue(null));
         if (jsonFile == null || !jsonFile.isFile()) return;
         try (InputStreamReader reader = new InputStreamReader(new FileInputStream(jsonFile))) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            boolean isReplace = GsonHelper.getAsBoolean(json, "replace", false);
            for (Option<?> option : options)
               readUserValueOrDefaultIfReplace(json, option, isReplace);
         } catch (IOException | JsonSyntaxException | JsonIOException e) {
            LOGGER.warn("Error loading Smart Completion settings:", e);
         }
      });
   }
   private <T> void readUserValueOrDefaultIfReplace(JsonObject json, Option<T> option, boolean isReplace) {
      if (json.has(option.getName()))
         option.setUserValue(option.getType().deserialize(json.get(option.getName())));
      else if (isReplace)
         option.setUserValue(option.getDefault());
   }

   public void saveUserSettings() {
      File jsonFile = getSettingsFile();
      if (jsonFile == null) return;
      JsonObject json = new JsonObject();
      for (Option<?> option : options) addUserValueIfNotNull(json, option);
      String jsonString = GSON.toJson(json);
      try {
         Files.createDirectories(jsonFile.getParentFile().toPath());
         try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(jsonFile))) {
            writer.write(jsonString);
         }
      } catch (IOException e) {
         LOGGER.warn("Error saving Smart Completion settings:", e);
      }
   }
   private <T> void addUserValueIfNotNull(JsonObject json, Option<T> option) {
      T userValue = option.getUserValue();
      if (userValue != null)
         json.add(option.getName(), option.getType().serialize(userValue));
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
      loadUserSettings();
   }

   public <S extends SharedSuggestionProvider, A extends ArgumentBuilder<S, A>> A registerCommand(
      A builder, CommandBuildContext context, String command
   ) {
      for (Option<?> option : options) capture(option, o ->
         builder.then(o.getType().registerCommand(
         LiteralArgumentBuilder.literal(o.getName()), context, command + " " + o.getName(), o)));
      for (OptionCategory<?> category : categories) builder.then(
         category.registerCommand(
            LiteralArgumentBuilder.literal(category.getName()),
            context, command + " " + category.getName()));
      return builder;
   }

   // REPORT: Using this within a `batchChange` lambda causes a type inference compile error that shouldn't happen
   //         It also causes the compiler to crash when generating the error report if the `Manifold` plugin is applied,
   //         though that happens with any compile error
   private static <T> void capture(Option<T> o, Consumer<Option<T>> action) {
      action.accept(o);
   }

   public @Nullable Path getConfigDirectory() {
      return configDirectory;
   }

   public @NotNull String getName() {
      return name;
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

   protected <CC extends OptionCategory<?>> CC subCategory(Function<Path, CC> constructor) {
      CC category = constructor.apply(configDirectory);
      ((OptionCategory<?>) category).setParent(this);
      category.addChangeListener(c -> triggerChange(true));
      categories.add(category);
      return category;
   }

   protected <T> Option<T> option(
      OptionType<T> type, @NotNull T defaultValue
   ) {
      Option<T> instance = new Option<>(type, null, defaultValue);
      instance.setParent(this);
      instance.addChangeListener(v -> triggerChange(false));
      instance.addUserValueChangeListener(v -> {
         if (!suppressUpdates) saveUserSettings();
      });
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
