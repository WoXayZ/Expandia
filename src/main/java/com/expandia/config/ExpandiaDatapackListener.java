package com.expandia.config;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Datapack-driven configuration. Reads every {@code data/&lt;ns&gt;/expandia/progression.json} on
 * datapack reload ({@code /reload}) and feeds it to {@link ExpandiaConfigManager} as an overlay.
 */
public final class ExpandiaDatapackListener extends SimplePreparableReloadListener<Map<Identifier, ExpandiaConfig>> {

    private static final FileToIdConverter LISTENER = FileToIdConverter.json(ExpandiaConstants.DATAPACK_DIRECTORY);

    @Override
    protected Map<Identifier, ExpandiaConfig> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, ExpandiaConfig> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : LISTENER.listMatchingResources(manager).entrySet()) {
            Identifier fileId = LISTENER.fileToId(entry.getKey());
            if (!fileId.getPath().equals(ExpandiaConstants.DATAPACK_FILE)) {
                continue;
            }
            Identifier configId = Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(), fileId.getPath());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                result.put(configId, ExpandiaConfigManager.fromJson(json));
            } catch (IOException | RuntimeException e) {
                Expandia.LOGGER.error("Expandia: failed to parse datapack progression {}", entry.getKey(), e);
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, ExpandiaConfig> resources, ResourceManager manager, ProfilerFiller profiler) {
        ExpandiaConfig overlay = null;
        for (ExpandiaConfig parsed : resources.values()) {
            overlay = (overlay == null) ? parsed : ExpandiaConfigManager.merge(overlay, parsed);
        }
        ExpandiaConfigManager.setDatapackOverlay(overlay);
    }
}
