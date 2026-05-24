package endorh.smartcompletion.forge;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionCommand;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@Mod(SmartCompletionMod.MOD_ID)
@EventBusSubscriber(value = Dist.CLIENT, modid = SmartCompletionMod.MOD_ID)
public class SmartCompletionModForge {
   @SubscribeEvent
   public static void registerClientReloadListener(AddClientReloadListenersEvent event) {
      SmartCompletionMod.init();
      event.addListener(
         Identifier.fromNamespaceAndPath(SmartCompletionMod.MOD_ID, "settings"),
         new SmartCompletionResourceReloadListener(SmartCompletionMod.getSmartCompletionSettings()));
   }

   @SubscribeEvent
   public static void registerClientCommands(RegisterClientCommandsEvent event) {
      SmartCompletionCommand.registerCommands(event.getDispatcher(), event.getBuildContext());
   }
}
