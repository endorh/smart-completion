package endorh.smartcompletion.util;

import com.mojang.serialization.DataResult;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
#if POST_MC_1_19
import net.minecraft.network.chat.Component;
#else
import net.minecraft.network.chat.TextComponent;
#endif

@ApiStatus.Internal
public abstract class PolyFill {
   private PolyFill() {}


   public static <D> boolean isError(DataResult<D> r) { return r.error().isPresent(); }
   public static <D> boolean isSuccess(DataResult<D> r) { return r.result().isPresent(); }
   public static <D> D getOrThrow(DataResult<D> r) { return r.getOrThrow(false, s -> {}); }

   public static int compareArrays(int @NotNull[] a1, int @NotNull[] a2) {
      #if JAVA_16_OR_LATER
      return java.util.Arrays.compare(a1, a2);
      #else
      int cmp, len = Math.min(a1.length, a2.length);
      for (int i = 0; i < len; i++)
         if ((cmp = Integer.compare(a1[i], a2[i])) != 0)
            return cmp;
      return Integer.compare(a1.length, a2.length);
      #endif
   }

   public static void setRect(Rect2i rect, int x, int y, int width, int height) {
      #if POST_MC_1_17_1
         rect.setX(x);
         rect.setY(y);
         rect.setWidth(width);
         rect.setHeight(height);
      #else
         rect.xPos = x;
         rect.yPos = y;
         rect.width = width;
         rect.height = height;
      #endif
   }

   public static MutableComponent literal(String s) {
      #if POST_MC_1_19
         return Component.literal(s);
      #else
         return new TextComponent(s);
      #endif
   }

   public static MutableComponent empty() {
      #if POST_MC_1_19
         return Component.empty();
      #else
         return TextComponent.EMPTY.copy();
      #endif
   }
}
