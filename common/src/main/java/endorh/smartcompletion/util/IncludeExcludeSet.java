package endorh.smartcompletion.util;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A set that allows including and excluding elements.
 * An element belongs to the set if it is included and not excluded.
 *
 * @param <T> the type of elements in the set
 */
public class IncludeExcludeSet<T> implements Set<T> {
   private static class IncludeExcludeSetEncoder<T> implements Encoder<IncludeExcludeSet<T>> {
      private final Codec<T> elemCodec;

      private IncludeExcludeSetEncoder(Codec<T> elemCodec) {
         this.elemCodec = elemCodec;
      }
      private <D> Optional<DataResult<D>> collectErrors(Stream<DataResult<D>> subResults) {
         List<DataResult<D>> errors = subResults
            .filter(DataResult::isError)
            .toList();
         if (!errors.isEmpty()) return Optional.of(DataResult.error(() ->
            "Failed to encode elements: " + errors.stream()
               .map(dr -> "\"" + dr.error().get().message() + "\"")
               .collect(Collectors.joining(", "))));
         return Optional.empty();
      }
      @Override public <D> DataResult<D> encode(IncludeExcludeSet<T> input, DynamicOps<D> ops, D prefix) {
         if (input.isReplace()) {
            List<DataResult<D>> encoded = input.stream().map(e -> elemCodec.encode(e, ops, ops.empty())).toList();
            Optional<DataResult<D>> error = collectErrors(encoded.stream());
            return error.orElseGet(() -> DataResult.success(ops.createMap(Map.of(
               ops.createString("replace"),
               ops.createList(encoded.stream().map(DataResult::getOrThrow))
            ))));
         }
         List<DataResult<D>> included = input.getIncludeSet().stream().map(e -> elemCodec.encode(e, ops, ops.empty())).toList();
         List<DataResult<D>> excluded = input.getExcludeSet().stream().map(e -> elemCodec.encode(e, ops, ops.empty())).toList();
         Optional<DataResult<D>> error = collectErrors(Stream.concat(included.stream(), excluded.stream()));
         if (error.isPresent()) return error.get();
         if (excluded.isEmpty()) return DataResult.success(
            ops.createList(included.stream().map(DataResult::getOrThrow)));
         return DataResult.success(ops.createMap(Util.make(new HashMap<>(), m -> {
            if (!included.isEmpty()) m.put(
               ops.createString("include"), ops.createList(included.stream().map(DataResult::getOrThrow)));
            m.put(ops.createString("exclude"), ops.createList(excluded.stream().map(DataResult::getOrThrow)));
         })));
      }
   }
   private static class IncludeExcludeSetDecoder<T> implements Decoder<IncludeExcludeSet<T>> {
      private final Codec<T> elemCodec;
      private final Comparator<T> comparator;

      private IncludeExcludeSetDecoder(Codec<T> elemCodec, Comparator<T> comparator) {
         this.elemCodec = elemCodec;
         this.comparator = comparator;
      }
      private <D> DataResult<List<T>> readList(DynamicOps<D> ops, D list) {
         return readList(ops, ops.getList(list));
      }
      private <D> DataResult<List<T>> readList(DynamicOps<D> ops, DataResult<Consumer<Consumer<D>>> list) {
         List<T> r = new ArrayList<>();
         List<DataResult<T>> errors = new ArrayList<>();
         if (list.isError()) return DataResult.error(list.error().get()::message);
         list.getOrThrow().accept(e -> {
            DataResult<T> edr = elemCodec.decode(ops, e).map(Pair::getFirst);
            if (edr.isSuccess())
               r.add(edr.getOrThrow());
            else errors.add(edr);
         });
         if (!errors.isEmpty()) return DataResult.error(() ->
            "Failed to decode list element(s): " +
               errors.stream().map(edr -> "\"" + edr.error().get().message() + "\"")
                  .collect(Collectors.joining(", ")));
         return DataResult.success(r);
      }
      @Override public <D> DataResult<Pair<IncludeExcludeSet<T>, D>> decode(DynamicOps<D> ops, D input) {
         IncludeExcludeSet<T> set = new IncludeExcludeSet<>(comparator);
         DataResult<Consumer<Consumer<D>>> list = ops.getList(input);
         if (list.isSuccess()) {
            DataResult<List<T>> include = readList(ops, list);
            if (include.isError()) return DataResult.error(include.error().get()::message);
            set.setIncludeSet(include.getOrThrow());
            return DataResult.success(Pair.of(set, input));
         }
         DataResult<Consumer<BiConsumer<D, D>>> map = ops.getMapEntries(input);
         if (map.isError()) return DataResult.error(
            () -> "Expected a map with optional keys [replace, include, exclude]");
         AtomicReference<D> replaceRef = new AtomicReference<>();
         AtomicReference<D> includeRef = new AtomicReference<>();
         AtomicReference<D> excludeRef = new AtomicReference<>();
         map.getOrThrow().accept((k, v) -> {
            DataResult<String> key = ops.getStringValue(k);
            if (!key.isError()) switch (key.getOrThrow()) {
               case "replace" -> replaceRef.set(v);
               case "include" -> includeRef.set(v);
               case "exclude" -> excludeRef.set(v);
            }
         });
         if (replaceRef.get() != null) {
            DataResult<List<T>> replace = readList(ops, replaceRef.get());
            if (replace.isError()) return DataResult.error(() -> replace.error().get().message());
            set.setIncludeSet(replace.getOrThrow());
            set.setReplace(true);
            return DataResult.success(Pair.of(set, input));
         }
         if (includeRef.get() != null) {
            DataResult<List<T>> include = readList(ops, includeRef.get());
            if (include.isError()) return DataResult.error(() -> include.error().get().message());
            set.setIncludeSet(include.getOrThrow());
         }
         if (excludeRef.get() != null) {
            DataResult<List<T>> exclude = readList(ops, excludeRef.get());
            if (exclude.isError()) return DataResult.error(() -> exclude.error().get().message());
            set.setExcludeSet(exclude.getOrThrow());
         }
         return DataResult.success(Pair.of(set, input));
      }
   }

   public static <T> Codec<IncludeExcludeSet<T>> codec(Codec<T> elementCodec) {
      return codec(elementCodec, null);
   }
   public static <T> Codec<IncludeExcludeSet<T>> codec(Codec<T> elementCodec, Comparator<T> comparator) {
      return Codec.of(
         new IncludeExcludeSetEncoder<>(elementCodec),
         new IncludeExcludeSetDecoder<>(elementCodec, comparator));
   }
   private Set<T> bakedSet = Set.of();
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
      bakedSet = Set.of();
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
