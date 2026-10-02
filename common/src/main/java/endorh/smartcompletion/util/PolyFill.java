package endorh.smartcompletion.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

#if PRE_MC_26_3
   import org.lwjgl.glfw.GLFW;
#else
   import org.lwjgl.sdl.SDLMouse;
   import org.lwjgl.sdl.SDLScancode;
#endif

public class PolyFill {
   public static final class Keys {
      private Keys() {}

      public static final int SPACE =  #if PRE_MC_26_3 GLFW.GLFW_KEY_SPACE #else SDLScancode.SDL_SCANCODE_SPACE  #endif;
      public static final int RETURN = #if PRE_MC_26_3 GLFW.GLFW_KEY_ENTER #else SDLScancode.SDL_SCANCODE_RETURN #endif;
      public static final int TAB =    #if PRE_MC_26_3 GLFW.GLFW_KEY_TAB   #else SDLScancode.SDL_SCANCODE_TAB    #endif;
      public static final int UP =     #if PRE_MC_26_3 GLFW.GLFW_KEY_UP    #else SDLScancode.SDL_SCANCODE_UP     #endif;
      public static final int DOWN =   #if PRE_MC_26_3 GLFW.GLFW_KEY_DOWN  #else SDLScancode.SDL_SCANCODE_DOWN   #endif;
      public static final int MOUSE_LEFT =    #if PRE_MC_26_3   - 100 #else SDLMouse.SDL_BUTTON_LEFT   #endif;
      public static final int MOUSE_RIGHT =   #if PRE_MC_26_3 1 - 100 #else SDLMouse.SDL_BUTTON_RIGHT  #endif;
      public static final int MOUSE_MIDDLE =  #if PRE_MC_26_3 2 - 100 #else SDLMouse.SDL_BUTTON_MIDDLE #endif;
   }

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
