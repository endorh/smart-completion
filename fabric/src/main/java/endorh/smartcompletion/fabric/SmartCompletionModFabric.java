package endorh.smartcompletion.fabric;

import endorh.smartcompletion.SmartCompletionMod;
import endorh.smartcompletion.SmartCompletionResourceReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class SmartCompletionModFabric implements ClientModInitializer {
    @Override public void onInitializeClient() {
        SmartCompletionMod.init();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
          new FabricResourceReloadListener(
            new ResourceLocation(SmartCompletionMod.MOD_ID, "smart-completion"),
            new SmartCompletionResourceReloadListener()));
    }
    
    public static class FabricResourceReloadListener implements IdentifiableResourceReloadListener {
        private final ResourceLocation id;
        private final PreparableReloadListener listener;
    
        public FabricResourceReloadListener(ResourceLocation id, PreparableReloadListener listener) {
            this.id = id;
            this.listener = listener;
        }
    
        @Override public ResourceLocation getFabricId() {
            return id;
        }
    
        @Override public CompletableFuture<Void> reload(
          @NotNull PreparationBarrier preparationBarrier, @NotNull ResourceManager resourceManager,
          @NotNull ProfilerFiller preparationProfiler, @NotNull ProfilerFiller applicationProfiler,
          @NotNull Executor preparationExecutor, @NotNull Executor applicationExecutor
        ) {
            return listener.reload(
               preparationBarrier, resourceManager, preparationProfiler, applicationProfiler,
               preparationExecutor, applicationExecutor);
        }
    
        @Override public String getName() {
            return id.toString();
        }
    }
}
