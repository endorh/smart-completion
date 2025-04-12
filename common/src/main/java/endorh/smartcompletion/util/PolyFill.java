package endorh.smartcompletion.util;

import com.mojang.serialization.DataResult;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class PolyFill {
   private PolyFill() {}

   #if PRE_MC_1_20_6
      public static <D> boolean isError(DataResult<D> r) { return r.error().isPresent(); }
      public static <D> boolean isSuccess(DataResult<D> r) { return r.result().isPresent(); }
      public static <D> D getOrThrow(DataResult<D> r) { return r.getOrThrow(false, s -> {}); }
      public static StyleArgument styleArgument(CommandBuildContext ctx) { return StyleArgument.style(); }
   #else
      public static <D> boolean isError(DataResult<D> r) { return r.isError(); }
      public static <D> boolean isSuccess(DataResult<D> r) { return r.isSuccess(); }
      public static <D> D getOrThrow(DataResult<D> r) { return r.getOrThrow(); }
      public static StyleArgument styleArgument(CommandBuildContext ctx) { return StyleArgument.style(ctx); }
   #endif

   #if PRE_MC_1_21_5
      public static ClickEvent suggestCommandClickEvent(String command) { return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command); }
      public static HoverEvent showTextHoverEvent(Component tooltip) { return new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip); }
   #else
      public static ClickEvent suggestCommandClickEvent(String command) { return new ClickEvent.SuggestCommand(command); }
      public static HoverEvent showTextHoverEvent(Component tooltip) { return new HoverEvent.ShowText(tooltip); }
   #endif
}
