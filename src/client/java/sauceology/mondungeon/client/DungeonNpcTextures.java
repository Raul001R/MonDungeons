package sauceology.mondungeon.client;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** RCT can be installed on the server alone. Never request skins from an external service. */
public final class DungeonNpcTextures implements SimpleSynchronousResourceReloadListener {
    private static final Map<ResourceLocation, ResourceLocation> FALLBACKS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> AVAILABLE = new HashMap<>();
    private static ResourceLocation defaultSkin = ResourceLocation.parse("minecraft:textures/entity/player/wide/kai.png");

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new DungeonNpcTextures());
    }

    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.parse("mondungeons:npc_skin_fallbacks");
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        AVAILABLE.clear();
        FALLBACKS.clear();
        manager.getResource(ResourceLocation.parse("mondungeons:npc_skin_fallbacks.json")).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                defaultSkin = ResourceLocation.parse(data.get("default").getAsString());
                data.getAsJsonObject("appearances").entrySet().forEach(entry ->
                    FALLBACKS.put(ResourceLocation.parse(entry.getKey()), ResourceLocation.parse(entry.getValue().getAsString())));
            } catch (Exception exception) {
                LoggerFactory.getLogger("mondungeons/skins").warn("Could not load dungeon NPC skin fallbacks", exception);
            }
        });
    }

    public static ResourceLocation resolve(NPCEntity npc, ResourceLocation selected) {
        var appearance = npc.getForcedResourceIdentifier();
        if (appearance == null || !appearance.getNamespace().equals("mondungeons")) return selected;
        var resources = Minecraft.getInstance().getResourceManager();
        if (AVAILABLE.computeIfAbsent(selected, id -> resources.getResource(id).isPresent())) return selected;
        var fallback = FALLBACKS.getOrDefault(appearance, defaultSkin);
        return AVAILABLE.computeIfAbsent(fallback, id -> resources.getResource(id).isPresent()) ? fallback : defaultSkin;
    }
}
