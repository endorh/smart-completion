package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionCommand;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
#if PRE_MC_1_20_6
   import net.neoforged.fml.common.Mod.EventBusSubscriber;
   import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
#else
   import net.neoforged.fml.common.EventBusSubscriber;
   import net.neoforged.fml.common.EventBusSubscriber.Bus;
#endif
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@Mod(SmartCompletionMod.MOD_ID)
@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD, modid = SmartCompletionMod.MOD_ID)
public class SmartCompletionModForge {
   public SmartCompletionModForge() {
      if (FMLEnvironment.dist == Dist.CLIENT)
         SmartCompletionMod.init();
   }

   @SubscribeEvent
   public static void registerReloadListener(RegisterParticleProvidersEvent event) {
      ReloadableResourceManager manager =
         (ReloadableResourceManager) Minecraft.getInstance().getResourceManager();
      manager.registerReloadListener(new SmartCompletionResourceReloadListener(
         SmartCompletionMod.getSmartCompletionSettings()));
   }

   #if PRE_MC_1_20_6
   @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE, modid = SmartCompletionMod.MOD_ID)
   #else
   @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.GAME, modid = SmartCompletionMod.MOD_ID)
   #endif
   public static class Registrar {
      @SubscribeEvent
      public static void registerClientCommands(RegisterClientCommandsEvent event) {
         SmartCompletionCommand.registerCommands(event.getDispatcher(), event.getBuildContext());
      }
   }
}
