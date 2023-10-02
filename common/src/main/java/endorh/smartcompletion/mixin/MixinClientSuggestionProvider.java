package endorh.smartcompletion.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import endorh.smartcompletion.util.EvictingLinkedHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adds support for multiple pending completion requests (up to 20) in {@link ClientSuggestionProvider}.
 */
@Mixin(ClientSuggestionProvider.class)
public class MixinClientSuggestionProvider {
   @Unique private static final Logger smartcompletion$LOGGER = LogManager.getLogger();

   // The choice of 20 is arbitrary, we technically only need 2
   @Unique private final Map<Integer, CompletableFuture<Suggestions>>
      smartcompletion$pendingSuggestionFutures = new EvictingLinkedHashMap<>(
      20, (i, v) -> v.cancel(true));

   // Adding an int field is free, debugging why suggestions get reported for the wrong
   // request if Mojang decides to use `ClientSuggestionProvider#pendingSuggestionsId` in
   // the future is not
   @Unique private int smartcompletion$pendingSuggestionsId = -1;

   @Final @Shadow private ClientPacketListener connection;


   /**
    * Sends a {@link ServerboundCommandSuggestionPacket} and adds a pending future to the map.
    */
   @Inject(method="customSuggestion", at=@At("HEAD"), cancellable=true)
   public void onCustomSuggestion(
      CommandContext<SharedSuggestionProvider> context,
      #if PRE_MC_1_18
      SuggestionsBuilder builder,
      #endif
      CallbackInfoReturnable<CompletableFuture<Suggestions>> cir
   ) {
      CompletableFuture<Suggestions> future = new CompletableFuture<>();
      int i = ++smartcompletion$pendingSuggestionsId;
      smartcompletion$pendingSuggestionFutures.put(i, future);
      connection.send(new ServerboundCommandSuggestionPacket(i, context.getInput()));
      cir.setReturnValue(future);
   }

   /**
    * Completes a pending future with the result of the {@link ServerboundCommandSuggestionPacket}.
    */
   @Inject(method="completeCustomSuggestions", at=@At("HEAD"), cancellable=true)
   public void onCompleteCustomSuggestions(
      int transaction, Suggestions result, CallbackInfo ci
   ) {
      CompletableFuture<Suggestions> future = smartcompletion$pendingSuggestionFutures.remove(transaction);
      if (future != null) future.complete(result);
      else smartcompletion$LOGGER.warn(
         "Received response for already evicted completion request (with id {})!", transaction);
      ci.cancel();
   }
}
