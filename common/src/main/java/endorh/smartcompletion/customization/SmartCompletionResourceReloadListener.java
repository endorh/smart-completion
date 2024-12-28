package endorh.smartcompletion.customization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import endorh.smartcompletion.customization.option.OptionCategory;
#if POST_MC_1_21_3
import endorh.smartcompletion.util.JsonElementCodec;
#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Reloads pack options from resource packs.
 * @see OptionCategory
 */
public class SmartCompletionResourceReloadListener extends
#if POST_MC_1_21_3
   SimpleJsonResourceReloadListener<JsonElement>
#else
   SimpleJsonResourceReloadListener
#endif
{
   private static final Logger LOGGER = LogManager.getLogger();
   public static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .create();

   protected final List<OptionCategory<?>> categories = new ArrayList<>();

   public SmartCompletionResourceReloadListener(OptionCategory<?>... options) {
      super(
         #if PRE_MC_1_21_3
         GSON,
         #else
         JsonElementCodec.INSTANCE,
         #endif
         "smart-completion");
      for (OptionCategory<?> cat : options) registerCategory(cat);
   }
   public final void registerCategory(OptionCategory<?> category) {
      categories.add(category);
      for (OptionCategory<?> sub : category.getCategories())
         registerCategory(sub);
   }

   @Override protected void apply(
      @NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager,
      @NotNull ProfilerFiller profiler
   ) {
      for (OptionCategory<?> category : categories) {
         profiler.push(category.getName());
         Set<String> names = category.getAllNames();
         try {
            category.reloadPackSettings(map.entrySet().stream()
               .filter(e -> names.contains(e.getKey().getPath()))
               .map(Entry::getValue).toList());
         } catch (RuntimeException e) {
            // Any exceptions thrown from here are silently swallowed and freeze the game
            LOGGER.error("Error loading Smart Completion settings from resources.", e);
         }
         profiler.pop();
      }
   }
}
