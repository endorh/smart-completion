package endorh.smartcompletion.util;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class JsonElementCodec implements Codec<JsonElement> {
   public static JsonElementCodec INSTANCE = new JsonElementCodec();

   @Override public <T> DataResult<Pair<JsonElement, T>> decode(DynamicOps<T> ops, T input) {
      HashMap<String, JsonElement> decodedMap = new HashMap<>();
      DataResult<MapLike<T>> map = ops.getMap(input);
      if (map.isSuccess()) {
         AtomicReference<DataResult<Pair<JsonElement, T>>> error = new AtomicReference<>(null);
         map.getOrThrow().entries().forEach(pair -> {
            DataResult<Pair<String, T>> key = STRING.decode(ops, pair.getFirst());
            if (key.isError()) {
               key.ifError(err -> error.set(DataResult.error(err.messageSupplier())));
               return;
            }
            DataResult<Pair<JsonElement, T>> elemResult = decode(ops, pair.getSecond());
            elemResult.ifError(err -> error.set(DataResult.error(err.messageSupplier())));
            elemResult.ifSuccess(entry -> decodedMap.put(key.getOrThrow().getFirst(), entry.getFirst()));
         });
         DataResult<Pair<JsonElement, T>> foundError = error.get();
         if (foundError != null) return foundError;
         JsonObject obj = new JsonObject();
         decodedMap.forEach(obj::add);
         return DataResult.success(Pair.of(obj, input));
      }
      DataResult<Consumer<Consumer<T>>> list = ops.getList(input);
      if (list.isSuccess()) {
         List<JsonElement> decodedList = new ArrayList<>();
         AtomicReference<DataResult.Error<Pair<JsonElement, T>>> error = new AtomicReference<>(null);
         list.getOrThrow().accept(elem -> {
            DataResult<Pair<JsonElement, T>> elemResult = decode(ops, elem);
            elemResult.error().ifPresent(error::set);
            elemResult.ifSuccess(pair -> decodedList.add(pair.getFirst()));
         });
         DataResult.Error<Pair<JsonElement, T>> foundError = error.get();
         if (foundError != null) return foundError;
         JsonArray array = new JsonArray(decodedList.size());
         for (JsonElement elem : decodedList)
            array.add(elem);
         return DataResult.success(Pair.of(array, input));
      }
      DataResult<Boolean> bool = ops.getBooleanValue(input);
      if (bool.isSuccess()) {
         return DataResult.success(Pair.of(new JsonPrimitive(bool.getOrThrow()), input));
      }
      DataResult<Number> number = ops.getNumberValue(input);
      if (number.isSuccess()) {
         return DataResult.success(Pair.of(new JsonPrimitive(number.getOrThrow()), input));
      }
      DataResult<String> string = ops.getStringValue(input);
      if (string.isSuccess()) {
         return DataResult.success(Pair.of(new JsonPrimitive(string.getOrThrow()), input));
      }
      return DataResult.success(Pair.of(JsonNull.INSTANCE, input));
   }

   @Override public <T> DataResult<T> encode(JsonElement input, DynamicOps<T> ops, T prefix) {
      if (input.isJsonNull()) {
         DataResult.success(ops.empty());
      } else if (input instanceof JsonPrimitive p) {
         if (p.isBoolean()) {
            return BOOL.encode(p.getAsBoolean(), ops, prefix);
         } else if (p.isNumber()) {
            Number number = p.getAsNumber();
            return switch (number) {
               case null -> DataResult.success(ops.empty());
               case Float v -> FLOAT.encode(number.floatValue(), ops, prefix);
               case Integer i -> INT.encode(number.intValue(), ops, prefix);
               case Double v -> DOUBLE.encode(number.doubleValue(), ops, prefix);
               default -> LONG.encode(number.longValue(), ops, prefix);
            };
         } else if (p.isString()) {
            return STRING.encode(p.getAsString(), ops, prefix);
         }
      } else if (input instanceof JsonArray a) {
         return Codec.list(this).encode(a.asList(), ops, prefix);
      } else if (input instanceof JsonObject o) {
         return Codec.unboundedMap(STRING, this).encode(o.asMap(), ops, prefix);
      }
      throw new IllegalArgumentException("Unknown JsonElement type!");
   }
}
