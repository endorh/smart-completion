package endorh.smartcompletion.customization.option;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a customizable option instance.<br>
 * <br>
 * An option has a default value, a pack value, and a user value.
 * The pack value is loaded from resource packs, while the user value is loaded
 * from a config directory and may be modified with commands.<br>
 * <br>
 * The final value is determined by the {@link OptionType#override} method of the
 * option's type, which combines the default, pack and user values accordingly.
 */
public class Option<T> {
   private final @NotNull OptionType<T> type;
   private String name;
   private String @Nullable[] aliases = null;
   private @Nullable OptionCategory<?> parent;
   private final @NotNull T defaultValue;
   private @Nullable T packValue;
   private @NotNull T bakedValue;
   private final List<Consumer<T>> changeListeners = new ArrayList<>();
   private final List<Consumer<T>> userValueChangeListener = new ArrayList<>();

   public Option(@NotNull OptionType<T> type, String name, @NotNull T defaultValue) {
      this.type = type;
      this.name = name;
      this.defaultValue = defaultValue;
      bake(false);
   }

   public @NotNull OptionType<T> getType() {
      return type;
   }

   public @NotNull String getName() {
      if (name == null)
         throw new IllegalStateException("Option instance name has not yet been initialized!");
      return name;
   }

   protected void setName(String name) {
      this.name = name;
   }
   protected void setParent(@Nullable OptionCategory<?> parent) {
      this.parent = parent;
   }

   protected void defineAlias(String... aliases) {
      if (this.aliases == null) this.aliases = aliases;
      else this.aliases = ArrayUtils.addAll(this.aliases, aliases);
   }

   protected String @Nullable[] getAliases() {
      return aliases;
   }

   protected List<String> getAllNames() {
      List<String> names = new ArrayList<>();
      names.add(name);
      String[] aliases = getAliases();
      if (aliases != null) Collections.addAll(names, aliases);
      Collections.reverse(names);
      return names;
   }

   public String getPath() {
      if (parent != null) return parent.getPath() + "." + getName();
      return getName();
   }

   private void bake(boolean userValueChange) {
      T value = defaultValue;
      OptionType<T> type = getType();
      value = type.override(value, packValue, false);
      if (value == null) value = defaultValue;
      bakedValue = value;
      triggerChange(userValueChange);
   }

   private void triggerChange(boolean userValueChange) {
      T value = get();
      if (userValueChange) userValueChangeListener.forEach(l -> l.accept(value));
      changeListeners.forEach(l -> l.accept(value));
   }

   public void addChangeListener(Consumer<T> listener) {
      changeListeners.add(listener);
   }
   public void removeChangeListener(Consumer<T> listener) {
      changeListeners.remove(listener);
   }
   public void addUserValueChangeListener(Consumer<T> listener) {
      userValueChangeListener.add(listener);
   }
   public void removeUserValueChangeListener(Consumer<T> listener) {
      userValueChangeListener.remove(listener);
   }

   public @NotNull T get() {
      return bakedValue;
   }

   public @Nullable T getPackValue() {
      return packValue;
   }

   public void setPackValue(@Nullable T value) {
      this.packValue = value;
      bake(false);
   }

   public void overridePackValue(@Nullable T value) {
      overridePackValue(value, false);
   }
   public void overridePackValue(@Nullable T value, boolean replace) {
      setPackValue(getType().override(getPackValue(), value, replace));
   }

   public @NotNull T getDefault() {
      return defaultValue;
   }
}
