package endorh.smartcompletion.fabric;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionCommand;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

#if PRE_MC_1_21_3
   import net.minecraft.util.profiling.ProfilerFiller;
#endif

#if PRE_MC_1_21_9
   import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
   import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
   import net.minecraft.server.packs.resources.PreparableReloadListener;
   import net.minecraft.server.packs.resources.ResourceManager;

   import java.util.concurrent.CompletableFuture;
   import java.util.concurrent.Executor;
#else
   import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
#endif

public class SmartCompletionModFabric implements ClientModInitializer {
   @Override public void onInitializeClient() {
      SmartCompletionMod.init();

      #if PRE_MC_1_21_9
         ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new FabricResourceReloadListener(
               location("smart-completion"),
               new SmartCompletionResourceReloadListener(
                  SmartCompletionMod.getSmartCompletionSettings())));
      #else
         ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
            location("smart-completion"),
            new SmartCompletionResourceReloadListener(
               SmartCompletionMod.getSmartCompletionSettings()));
      #endif

      ClientCommandRegistrationCallback.EVENT.register(SmartCompletionCommand::registerCommands);
   }

   #if PRE_MC_1_21_9
      public static class FabricResourceReloadListener implements IdentifiableResourceReloadListener {
         private final ResourceLocation id;
         private final PreparableReloadListener listener;

         public FabricResourceReloadListener(ResourceLocation id, PreparableReloadListener listener) {
            this.id = id;
            this.listener = listener;
         }

         @Override public ResourceLocation getFabricId() {
            return id;
         }

         @Override public @NotNull CompletableFuture<Void> reload(
            @NotNull PreparationBarrier preparationBarrier, @NotNull ResourceManager resourceManager,
            #if PRE_MC_1_21_3
            @NotNull ProfilerFiller preparationProfiler, @NotNull ProfilerFiller applicationProfiler,
            #endif
            @NotNull Executor preparationExecutor, @NotNull Executor applicationExecutor
         ) {
            return listener.reload(
               preparationBarrier, resourceManager,
               #if PRE_MC_1_21_3
               preparationProfiler, applicationProfiler,
               #endif
               preparationExecutor, applicationExecutor);
         }

         @Override public @NotNull String getName() {
            return id.toString();
         }
      }
   #endif

   private static ResourceLocation location(@NotNull @NonNls String path) {
      #if POS_MC_1_21
         return ResourceLocation.fromNamespaceAndPath(SmartCompletionMod.MOD_ID, path);
      #else
         return new ResourceLocation(SmartCompletionMod.MOD_ID, path);
      #endif
   }
}
