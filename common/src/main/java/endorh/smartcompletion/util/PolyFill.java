package endorh.smartcompletion.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

public class PolyFill {
   public static Screen getScreen(Minecraft client) {
      #if POS_MC_26_2
         return client.gui.screen();
      #else
         return client.screen;
      #endif
   }

   public static ChatComponent getChat(Minecraft client) {
      #if POS_MC_26_2
         return client.gui.hud.getChat();
      #else
         return client.gui.getChat();
      #endif
   }
}
