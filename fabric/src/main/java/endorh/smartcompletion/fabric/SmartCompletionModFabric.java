package endorh.smartcompletion.fabric;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.customization.SmartCompletionCommand;
import endorh.smartcompletion.customization.SmartCompletionResourceReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SmartCompletionModFabric implements ClientModInitializer {
   @Override public void onInitializeClient() {
      SmartCompletionMod.init();

      ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
         location("smart-completion"),
         new SmartCompletionResourceReloadListener(
            SmartCompletionMod.getSmartCompletionSettings()));

      ClientCommandRegistrationCallback.EVENT.register(SmartCompletionCommand::registerCommands);
   }

   private static Identifier location(@NotNull @NonNls String path) {
      return Identifier.fromNamespaceAndPath(SmartCompletionMod.MOD_ID, path);
   }
}
