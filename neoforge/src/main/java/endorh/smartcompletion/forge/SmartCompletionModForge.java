package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionCommand;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

#if PRE_MC_1_20_6
   import net.neoforged.fml.common.Mod.EventBusSubscriber;
   import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
#else
   import net.neoforged.fml.common.EventBusSubscriber;
   #if PRE_MC_1_21_7
      import net.neoforged.fml.common.EventBusSubscriber.Bus;
   #endif
#endif

#if PRE_MC_1_21_5
   import net.minecraft.client.Minecraft;
   import net.minecraft.server.packs.resources.ReloadableResourceManager;
   import net.neoforged.fml.loading.FMLEnvironment;
   import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
#else
   import net.minecraft.resources.ResourceLocation;
   import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
#endif

@Mod(SmartCompletionMod.MOD_ID)
@EventBusSubscriber(value = Dist.CLIENT, #if PRE_MC_1_21_7 bus = Bus.MOD, #endif modid = SmartCompletionMod.MOD_ID)
public class SmartCompletionModForge {
   public SmartCompletionModForge() {
      #if PRE_MC_1_21_5
         if (FMLEnvironment.dist == Dist.CLIENT)
            SmartCompletionMod.init();
      #endif
   }

   #if PRE_MC_1_21_5
      @SubscribeEvent
      public static void registerReloadListener(RegisterParticleProvidersEvent event) {
         ReloadableResourceManager manager =
            (ReloadableResourceManager) Minecraft.getInstance().getResourceManager();
         manager.registerReloadListener(new SmartCompletionResourceReloadListener(
            SmartCompletionMod.getSmartCompletionSettings()));
      }
   #else
      @SubscribeEvent
      public static void registerClientReloadListener(AddClientReloadListenersEvent event) {
         SmartCompletionMod.init();
         event.addListener(
            ResourceLocation.fromNamespaceAndPath(SmartCompletionMod.MOD_ID, "settings"),
            new SmartCompletionResourceReloadListener(SmartCompletionMod.getSmartCompletionSettings()));
      }
   #endif

   #if PRE_MC_1_20_6
      @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE, modid = SmartCompletionMod.MOD_ID)
   #else
      @EventBusSubscriber(value = Dist.CLIENT, #if PRE_MC_1_21_7 bus = Bus.GAME, #endif modid = SmartCompletionMod.MOD_ID)
   #endif
   public static class Registrar {
      @SubscribeEvent
      public static void registerClientCommands(RegisterClientCommandsEvent event) {
         SmartCompletionCommand.registerCommands(event.getDispatcher(), event.getBuildContext());
      }
   }
}
