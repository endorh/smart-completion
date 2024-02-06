package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@Mod(SmartCompletionMod.MOD_ID)
@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD, modid = SmartCompletionMod.MOD_ID)
public class SmartCompletionModForge {
   public SmartCompletionModForge() {
      SmartCompletionMod.init();
   }

   @SubscribeEvent
   public static void registerReloadListener(RegisterParticleProvidersEvent event) {
      ReloadableResourceManager manager =
         (ReloadableResourceManager) Minecraft.getInstance().getResourceManager();
      manager.registerReloadListener(new SmartCompletionResourceReloadListener());
   }
}
