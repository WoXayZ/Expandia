package com.expandia.config;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Loads, merges and reloads the Expandia configuration from disk and datapack overlays.
 */
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
        Expandia.LOGGER.info("Expandia: config loaded (baseRadius={}, transitionWidth={}, {} island advancements)",
                current.baseRadius(), current.transitionWidth(), current.advancements().size());
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
        Set<Identifier> advancements = new LinkedHashSet<>(base.advancements());
        advancements.addAll(override.advancements());

        Set<Identifier> dimensions = new LinkedHashSet<>(override.enabledDimensions());

        return new ExpandiaConfig(
                override.baseRadius(),
                override.transitionWidth(),
                override.generateStructures(),
                dimensions,
                advancements
        );
    }

    public static ExpandiaConfig fromJson(JsonObject json) {
        ExpandiaConfig defaults = ExpandiaConfig.defaults();

        int baseRadius = getInt(json, "baseRadius", defaults.baseRadius());
        int transitionWidth = getInt(json, "transitionWidth", defaults.transitionWidth());
        boolean generateStructures = json.has("generateStructures")
                ? json.get("generateStructures").getAsBoolean()
                : defaults.generateStructures();

        Set<Identifier> dimensions = new LinkedHashSet<>();
        if (json.has("dimensions") && json.get("dimensions").isJsonArray()) {
            json.getAsJsonArray("dimensions").forEach(e -> {
                Identifier id = parseId(e.getAsString());
                if (id != null) {
                    dimensions.add(id);
                }
            });
        }
        if (dimensions.isEmpty()) {
            dimensions.addAll(defaults.enabledDimensions());
        }

        Set<Identifier> advancements = new LinkedHashSet<>();
        if (json.has("advancements") && json.get("advancements").isJsonArray()) {
            json.getAsJsonArray("advancements").forEach(e -> {
                Identifier id = parseId(e.getAsString());
                if (id == null) {
                    Expandia.LOGGER.warn("Expandia: skipping invalid advancement id '{}'", e);
                } else {
                    advancements.add(id);
                }
            });
        }

        return new ExpandiaConfig(baseRadius, transitionWidth, generateStructures, dimensions, advancements);
    }

    public static JsonObject toJson(ExpandiaConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("baseRadius", config.baseRadius());
        json.addProperty("transitionWidth", config.transitionWidth());
        json.addProperty("generateStructures", config.generateStructures());

        com.google.gson.JsonArray dimensions = new com.google.gson.JsonArray();
        config.enabledDimensions().forEach(id -> dimensions.add(id.toString()));
        json.add("dimensions", dimensions);

        com.google.gson.JsonArray advancements = new com.google.gson.JsonArray();
        config.advancements().forEach(id -> advancements.add(id.toString()));
        json.add("advancements", advancements);
        return json;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    @Nullable
    private static Identifier parseId(String value) {
        return Identifier.tryParse(value);
    }
}
