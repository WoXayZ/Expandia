package com.expandia.config;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ExpandiaConfigManager {

    private ExpandiaConfigManager() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile ExpandiaConfig current = ExpandiaConfig.defaults();
    @Nullable
    private static volatile ExpandiaConfig datapackOverlay = null;

    public static ExpandiaConfig get() {
        return current;
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(ExpandiaConstants.MOD_ID).resolve(ExpandiaConstants.CONFIG_FILE);
    }

    public static synchronized void load() {
        Path path = configPath();
        ExpandiaConfig fileConfig;
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(toJson(ExpandiaConfig.defaults())));
                Expandia.LOGGER.info("Expandia: created default config at {}", path);
                fileConfig = ExpandiaConfig.defaults();
            } else {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                fileConfig = fromJson(json);
            }
        } catch (Exception e) {
            Expandia.LOGGER.error("Expandia: failed to read {}, keeping defaults", path, e);
            fileConfig = ExpandiaConfig.defaults();
        }
        current = merge(datapackOverlay, fileConfig);
        Expandia.LOGGER.info(
                "Expandia: config loaded (radius={}, overworld {} advancements, nether {} advancements; biomes auto)",
                current.baseRadius(),
                current.overworld().advancements().size(),
                current.nether().advancements().size());
    }

    public static synchronized void reload() {
        load();
    }

    public static synchronized void setDatapackOverlay(@Nullable ExpandiaConfig overlay) {
        datapackOverlay = overlay;
        load();
    }

    public static ExpandiaConfig merge(@Nullable ExpandiaConfig base, ExpandiaConfig override) {
        if (base == null) {
            return override;
        }
        return new ExpandiaConfig(
                override.baseRadius(),
                override.transitionWidth(),
                mergePack(base.overworld(), override.overworld()),
                mergePack(base.nether(), override.nether())
        );
    }

    private static ExpandiaConfig.DimensionPack mergePack(
            ExpandiaConfig.DimensionPack base, ExpandiaConfig.DimensionPack override) {
        Set<ResourceLocation> advancements = new LinkedHashSet<>(base.advancements());
        advancements.addAll(override.advancements());
        return new ExpandiaConfig.DimensionPack(new ArrayList<>(advancements));
    }

    public static ExpandiaConfig fromJson(JsonObject json) {
        ExpandiaConfig defaults = ExpandiaConfig.defaults();
        int baseRadius = getInt(json, "baseRadius", defaults.baseRadius());
        int transitionWidth = getInt(json, "transitionWidth", defaults.transitionWidth());
        if (json.has("overworld") && json.get("overworld").isJsonObject()) {
            ExpandiaConfig.DimensionPack overworld = packFromJson(json.getAsJsonObject("overworld"), defaults.overworld());
            ExpandiaConfig.DimensionPack nether = json.has("nether") && json.get("nether").isJsonObject()
                    ? packFromJson(json.getAsJsonObject("nether"), defaults.nether())
                    : defaults.nether();
            return new ExpandiaConfig(baseRadius, transitionWidth, overworld, nether);
        }
        return fromLegacyJson(json, defaults, baseRadius, transitionWidth);
    }

    private static ExpandiaConfig fromLegacyJson(
            JsonObject json, ExpandiaConfig defaults, int baseRadius, int transitionWidth) {
        if (!json.has("advancements")) {
            return new ExpandiaConfig(baseRadius, transitionWidth, defaults.overworld(), defaults.nether());
        }
        List<ResourceLocation> overworld = new ArrayList<>();
        List<ResourceLocation> nether = new ArrayList<>();
        for (ResourceLocation id : readIds(json.get("advancements"))) {
            if (id.getPath().startsWith("nether/")) {
                nether.add(id);
            } else {
                overworld.add(id);
            }
        }
        ExpandiaConfig.DimensionPack overworldPack = overworld.isEmpty()
                ? defaults.overworld()
                : new ExpandiaConfig.DimensionPack(overworld);
        ExpandiaConfig.DimensionPack netherPack = nether.isEmpty()
                ? defaults.nether()
                : new ExpandiaConfig.DimensionPack(nether);
        return new ExpandiaConfig(baseRadius, transitionWidth, overworldPack, netherPack);
    }

    private static ExpandiaConfig.DimensionPack packFromJson(JsonObject json, ExpandiaConfig.DimensionPack defaults) {
        List<ResourceLocation> advancements = json.has("advancements")
                ? readIds(json.get("advancements")) : defaults.advancements();
        if (advancements.isEmpty()) {
            advancements = defaults.advancements();
        }
        return new ExpandiaConfig.DimensionPack(advancements);
    }

    private static List<ResourceLocation> readIds(JsonElement element) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return ids;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            ResourceLocation id = parseId(entry.getAsString());
            if (id == null) {
                Expandia.LOGGER.warn("Expandia: skipping invalid id '{}'", entry);
            } else {
                ids.add(id);
            }
        }
        return ids;
    }

    public static JsonObject toJson(ExpandiaConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("baseRadius", config.baseRadius());
        json.addProperty("transitionWidth", config.transitionWidth());
        json.add("overworld", packToJson(config.overworld()));
        json.add("nether", packToJson(config.nether()));
        return json;
    }

    private static JsonObject packToJson(ExpandiaConfig.DimensionPack pack) {
        JsonObject json = new JsonObject();
        json.add("advancements", toArray(pack.advancements()));
        return json;
    }

    private static JsonArray toArray(List<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.toString()));
        return array;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    @Nullable
    private static ResourceLocation parseId(String value) {
        return ResourceLocation.tryParse(value);
    }
}
