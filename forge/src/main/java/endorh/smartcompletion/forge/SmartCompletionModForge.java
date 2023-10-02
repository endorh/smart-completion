package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.api.distmarker.Dist;
#if POST_MC_1_19
    import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
#else
    import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
#endif
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
    #if POST_MC_1_19
        public static void registerReloadListener(RegisterParticleProvidersEvent event) {
    #else
        public static void registerReloadListener(ParticleFactoryRegisterEvent event) {
    #endif
        ReloadableResourceManager manager =
          (ReloadableResourceManager) Minecraft.getInstance().getResourceManager();
        manager.registerReloadListener(new SmartCompletionResourceReloadListener());
    }
}
