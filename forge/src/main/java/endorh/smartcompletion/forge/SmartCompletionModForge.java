package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.SmartCompletionResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@Mod(SmartCompletionMod.MOD_ID)
@EventBusSubscriber(value = Dist.CLIENT, bus=Bus.MOD, modid = SmartCompletionMod.MOD_ID)
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
