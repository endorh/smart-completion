package endorh.smartcompletion.util;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A {@link LinkedHashMap} that evicts the eldest entry when the size exceeds a maximum size,
 * since even in 2023 this isn't a standard feature of the JDK, and I couldn't find it in Guava either.<br>
 * <br>
 * Accepts an optional {@link #onEvict} listener that can be used to perform some action on eviction
 * (e.g. cancel an evicted {@link CompletableFuture}).
 */
public class EvictingLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
   private int maxSize;
   private @Nullable BiConsumer<K, V> onEvict = null;

   public EvictingLinkedHashMap(int maxSize) {
      super(maxSize);
      this.maxSize = maxSize;
   }

   public EvictingLinkedHashMap(int maxSize, @Nullable BiConsumer<K, V> onEvict) {
      super(maxSize);
      this.maxSize = maxSize;
      this.onEvict = onEvict;
   }

   public EvictingLinkedHashMap(int initialCapacity, float loadFactor, int maxSize) {
      super(initialCapacity, loadFactor);
      this.maxSize = maxSize;
   }

   public EvictingLinkedHashMap(
      int initialCapacity, float loadFactor,
      int maxSize, @Nullable BiConsumer<K, V> onEvict
   ) {
      super(initialCapacity, loadFactor);
      this.maxSize = maxSize;
      this.onEvict = onEvict;
   }

   public EvictingLinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder, int maxSize) {
      super(initialCapacity, loadFactor, accessOrder);
      this.maxSize = maxSize;
   }

   public EvictingLinkedHashMap(
      int initialCapacity, float loadFactor, boolean accessOrder,
      int maxSize, @Nullable BiConsumer<K, V> onEvict
   ) {
      super(initialCapacity, loadFactor, accessOrder);
      this.maxSize = maxSize;
      this.onEvict = onEvict;
   }

   @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      boolean evict = size() > maxSize;
      if (evict && onEvict != null)
         onEvict.accept(eldest.getKey(), eldest.getValue());
      return evict;
   }

   public int getMaxSize() {
      return maxSize;
   }

   public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
   }

   public BiConsumer<K, V> getEvictionListener() {
      return onEvict;
   }

   public void setEvictionListener(BiConsumer<K, V> onEvict) {
      this.onEvict = onEvict;
   }
}
