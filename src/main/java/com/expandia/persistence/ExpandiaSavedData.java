package com.expandia.persistence;

import com.expandia.ExpandiaConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExpandiaSavedData extends SavedData {

    public static final Codec<ExpandiaSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("spiralIndices", List.of()).forGetter(data -> data.copyIndices()),
            // Legacy fallback: pre-1.0 saves only stored a contiguous island count.
            Codec.INT.optionalFieldOf("islandCount", 1).forGetter(data -> data.spiralIndices.size()),
            Codec.STRING.listOf().optionalFieldOf("countedAdvancements", List.of())
                    .forGetter(data -> List.copyOf(data.countedAdvancements))
    ).apply(instance, ExpandiaSavedData::fromCodec));

    public static final SavedDataType<ExpandiaSavedData> TYPE = new SavedDataType<>(
            ExpandiaConstants.id("progression"),
            ExpandiaSavedData::new,
            CODEC
    );

    /** Spiral slots holding an island; always contains {@code 0} (the spawn island). */
    private final List<Integer> spiralIndices = new ArrayList<>();
    private final Set<String> countedAdvancements = new HashSet<>();

    public ExpandiaSavedData() {
        this.spiralIndices.add(0);
    }

    private ExpandiaSavedData(List<Integer> spiralIndices, int legacyCount, List<String> countedAdvancements) {
        if (spiralIndices.isEmpty()) {
            // Rebuild contiguous slots [0, legacyCount) from a legacy save.
            int count = Math.max(1, legacyCount);
            for (int i = 0; i < count; i++) {
                this.spiralIndices.add(i);
            }
        } else {
            this.spiralIndices.addAll(spiralIndices);
        }
        if (!this.spiralIndices.contains(0)) {
            this.spiralIndices.add(0);
        }
        this.countedAdvancements.addAll(countedAdvancements);
    }

    private static ExpandiaSavedData fromCodec(List<Integer> spiralIndices, int legacyCount, List<String> countedAdvancements) {
        return new ExpandiaSavedData(spiralIndices, legacyCount, countedAdvancements);
    }

    public static ExpandiaSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Integer> copyIndices() {
        return List.copyOf(this.spiralIndices);
    }

    public int getIslandCount() {
        return this.spiralIndices.size();
    }

    /** Spiral slots currently holding an island, as a flat {@code int[]}. */
    public int[] getSpiralIndices() {
        int[] result = new int[this.spiralIndices.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = this.spiralIndices.get(i);
        }
        return result;
    }

    /** Highest spiral slot in use; new islands search outward from here. */
    public int getHighestSpiralIndex() {
        int max = 0;
        for (int index : this.spiralIndices) {
            max = Math.max(max, index);
        }
        return max;
    }

    public boolean hasCounted(String advancementId) {
        return this.countedAdvancements.contains(advancementId);
    }

    /**
     * Records a new island at {@code spiralIndex}, guarding against double counting the advancement.
     *
     * @return {@code true} if a new island was added, {@code false} if the advancement already counted.
     */
    public boolean addIsland(int spiralIndex, String advancementId) {
        if (advancementId != null && !this.countedAdvancements.add(advancementId)) {
            return false;
        }
        addSlot(spiralIndex);
        return true;
    }

    /** Adds an island not tied to an advancement (e.g. the {@code /expandia unlock} command). */
    public void addIsland(int spiralIndex) {
        addSlot(spiralIndex);
    }

    private void addSlot(int spiralIndex) {
        if (!this.spiralIndices.contains(spiralIndex)) {
            this.spiralIndices.add(spiralIndex);
        }
        setDirty();
    }

    public void reset() {
        this.spiralIndices.clear();
        this.spiralIndices.add(0);
        this.countedAdvancements.clear();
        setDirty();
    }
}
