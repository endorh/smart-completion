package endorh.smartcompletion.customization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import endorh.smartcompletion.SmartCommandCompletion;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class SmartCompletionResourceReloadListener extends SimpleJsonResourceReloadListener {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(CommandSplittingSettings.class, CommandSplittingSettings.SERIALIZER)
      .registerTypeAdapter(CommandCompletionStyle.class, CommandCompletionStyle.SERIALIZER)
      .registerTypeAdapter(Style.class, new Style.Serializer())
      .create();

   public SmartCompletionResourceReloadListener() {
      super(GSON, "smart-completion");
   }

   @Override protected void apply(
      @NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager,
      @NotNull ProfilerFiller profiler
   ) {
      List<JsonElement> commandSplittingJsonList = map.entrySet().stream()
         .filter(e -> "command_splitting".equals(e.getKey().getPath()))
         .map(Entry::getValue).toList();
      List<JsonElement> completionStyleJSONList = map.entrySet().stream()
         .filter(e -> "completion_style".equals(e.getKey().getPath()))
         .map(Entry::getValue).toList();
      try {
         CommandSplittingSettings settings = new CommandSplittingSettings();
         for (JsonElement obj : commandSplittingJsonList) {
            CommandSplittingSettings next = GSON.fromJson(obj, CommandSplittingSettings.class);
            if (GsonHelper.getAsBoolean(obj.getAsJsonObject(), "replace", false)) {
               settings = next;
            } else settings = settings.merge(next);
         }
         SmartCommandCompletion.setSplittingSettings(settings);
      } catch (RuntimeException e) {
         LOGGER.error("Failed to load command splitting settings", e);
      }
      try {
         CommandCompletionStyle style = new CommandCompletionStyle();
         for (JsonElement obj : completionStyleJSONList) {
            CommandCompletionStyle next = GSON.fromJson(obj, CommandCompletionStyle.class);
            if (GsonHelper.getAsBoolean(obj.getAsJsonObject(), "replace", false)) {
               style = next;
            } else style = next.applyTo(style);
         }
         SmartCommandCompletion.setCompletionStyle(style);
      } catch (RuntimeException e) {
         LOGGER.error("Failed to load completion style settings", e);
      }
   }
}
