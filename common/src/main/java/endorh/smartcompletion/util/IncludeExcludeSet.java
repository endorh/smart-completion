package endorh.smartcompletion.util;

import com.google.common.collect.ImmutableSet;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A set that allows including and excluding elements.
 * An element belongs to the set if it is included and not excluded.
 *
 * @param <T> the type of elements in the set
 */
public class IncludeExcludeSet<T> implements Set<T> {
   private static class IncludeExcludeSetDecoder<T> implements JsonDeserializer<IncludeExcludeSet<T>> {
      private final JsonDeserializer<T> elemDeserializer;
      private final Comparator<T> comparator;

      private IncludeExcludeSetDecoder(JsonDeserializer<T> elemDeserializer, Comparator<T> comparator) {
         this.elemDeserializer = elemDeserializer;
         this.comparator = comparator;
      }

      private Stream<T> elemStream(JsonArray array, Type type, JsonDeserializationContext ctx) {
         return StreamSupport.stream(array.spliterator(), false)
            .map(e -> elemDeserializer.deserialize(e, type, ctx));
      }
      @Override public IncludeExcludeSet<T> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext ctx) throws JsonParseException {
         if (jsonElement.isJsonArray()) {
            IncludeExcludeSet<T> set = new IncludeExcludeSet<>(comparator);
            elemStream(jsonElement.getAsJsonArray(), type, ctx).forEach(set::include);
            return set;
         } else if (jsonElement.isJsonObject()) {
            JsonObject o = jsonElement.getAsJsonObject();
            IncludeExcludeSet<T> set = new IncludeExcludeSet<>();
            if (o.has("replace")) {
               set.setReplace(true);
               elemStream(o.getAsJsonArray("replace"), type, ctx)
                  .forEach(set::include);
               return set;
            } else {
               if (o.has("include"))
                  elemStream(o.getAsJsonArray("include"), type, ctx).forEach(set::include);
               if (o.has("exclude"))
                  elemStream(o.getAsJsonArray("exclude"), type, ctx).forEach(set::exclude);
               return set;
            }
         }
         throw new JsonParseException("Expected a list or an object");
      }
   }

   public static <T> JsonDeserializer<IncludeExcludeSet<T>> deserializer(JsonDeserializer<T> elemDeserializer) {
      return deserializer(elemDeserializer, null);
   }
   public static <T> JsonDeserializer<IncludeExcludeSet<T>> deserializer(JsonDeserializer<T> elemDeserializer, Comparator<T> comparator) {
      return new IncludeExcludeSetDecoder<>(elemDeserializer, comparator);
   }

   private Set<T> bakedSet = Collections.emptySet();
   private boolean replace = false;
   private final Set<T> includeSet = new HashSet<>();
   private final Set<T> excludeSet = new HashSet<>();
   private @Nullable Comparator<T> comparator;

   public IncludeExcludeSet() {
      this(null);
   }

   public IncludeExcludeSet(@Nullable Comparator<T> order) {
      comparator = order;
   }

   public IncludeExcludeSet<T> copy() {
      IncludeExcludeSet<T> copy = new IncludeExcludeSet<>(comparator);
      copy.includeSet.addAll(includeSet);
      copy.excludeSet.addAll(excludeSet);
      copy.bakedSet = ImmutableSet.copyOf(bakedSet);
      return copy;
   }

   private void simplify() {
      includeSet.removeAll(excludeSet);
   }

   private boolean bake(boolean r) {
      bake();
      return r;
   }
   private void bake() {
      simplify();
      Stream<T> stream = includeSet.stream();
      if (comparator != null) stream = stream.sorted(comparator);
      bakedSet = stream.collect(Collectors.toCollection(LinkedHashSet::new));
   }

   public @Nullable Comparator<T> getComparator() {
      return comparator;
   }

   public void setComparator(@Nullable Comparator<T> comparator) {
      this.comparator = comparator;
      bake();
   }

   public boolean isReplace() {
      return replace;
   }
   public void setReplace(boolean replace) {
      this.replace = replace;
   }

   /**
    * Elements in the excluded set will be ignored.
    */
   public void setIncludeSet(@NotNull Collection<T> includeSet) {
      this.includeSet.clear();
      this.includeSet.addAll(includeSet);
      bake();
   }

   public @NotNull Set<T> getIncludeSet() {
      return ImmutableSet.copyOf(includeSet);
   }

   public void setExcludeSet(@NotNull Collection<T> excludeSet) {
      this.excludeSet.clear();
      this.excludeSet.addAll(excludeSet);
      bake();
   }

   public @NotNull Set<T> getExcludeSet() {
      return ImmutableSet.copyOf(excludeSet);
   }

   public IncludeExcludeSet<T> override(IncludeExcludeSet<T> override) {
      if (override.isReplace()) return override;
      IncludeExcludeSet<T> copy = new IncludeExcludeSet<>(override.comparator);
      Set<T> overrideIncludeSet = override.getIncludeSet();
      Set<T> includeSet = new LinkedHashSet<>(getIncludeSet());
      includeSet.addAll(overrideIncludeSet);
      Set<T> excludeSet = new LinkedHashSet<>(getExcludeSet());
      excludeSet.removeAll(overrideIncludeSet);
      excludeSet.addAll(override.getExcludeSet());
      // Set exclude set first
      copy.setExcludeSet(excludeSet);
      copy.setIncludeSet(includeSet);
      return copy;
   }

   public boolean include(T item) {
      boolean r = excludeSet.remove(item);
      if (!includeSet.contains(item))
         r |= includeSet.add(item);
      return bake(r);
   }
   public boolean unInclude(Object item) {
      //noinspection SuspiciousMethodCalls
      return bake(includeSet.remove(item));
   }

   public boolean exclude(T item) {
      boolean r = includeSet.remove(item);
      if (!excludeSet.contains(item))
         r |= excludeSet.add(item);
      return bake(r);
   }

   public boolean unExclude(T item) {
      return bake(excludeSet.remove(item));
   }

   @Override public boolean add(T t) {
      return include(t);
   }
   @Override public boolean remove(Object o) {
      return unInclude(o);
   }

   @Override public void clear() {
      bakedSet = Collections.emptySet();
      includeSet.clear();
      excludeSet.clear();
   }

   // Bulk modify
   @Override public boolean addAll(@NotNull Collection<? extends T> c) {
      for (T elem : c) add(elem);
      return true;
   }
   @Override public boolean removeAll(@NotNull Collection<?> c) {
      boolean any = false;
      for (Object elem : c) any |= remove(elem);
      return any;
   }
   @Override public boolean retainAll(@NotNull Collection<?> c) {
      boolean any = false;
      for (T elem : bakedSet) if (!c.contains(elem)) any |= remove(elem);
      return any;
   }

   // Read operations delegated to `baked`
   @Override public int size() {
      return bakedSet.size();
   }
   @Override public boolean isEmpty() {
      return bakedSet.isEmpty();
   }
   @Override public boolean contains(Object o) {
      return bakedSet.contains(o);
   }
   @Override public boolean containsAll(@NotNull Collection<?> c) {
      return bakedSet.containsAll(c);
   }
   @NotNull @Override public Iterator<T> iterator() {
      return bakedSet.iterator();
   }
   @NotNull @Override public Object @NotNull [] toArray() {
      return bakedSet.toArray();
   }
   @NotNull @Override public <A> A @NotNull [] toArray(@NotNull A @NotNull [] a) {
      return bakedSet.toArray(a);
   }
}
