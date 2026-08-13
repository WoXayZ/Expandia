package com.expandia.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExpandiaSavedData extends SavedData {

    public static final SavedData.Factory<ExpandiaSavedData> FACTORY = new SavedData.Factory<>(
            ExpandiaSavedData::new,
            ExpandiaSavedData::load
    );

    private final DimProgress overworld = new DimProgress();
    private final DimProgress nether = new DimProgress();

    private boolean netherSpawnRepaired;

    public ExpandiaSavedData() {
    }

    public static ExpandiaSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ExpandiaSavedData data = new ExpandiaSavedData();
        if (tag.contains("overworld", Tag.TAG_COMPOUND)) {
            data.overworld.read(tag.getCompound("overworld"));
        } else {
            data.overworld.read(tag);
        }
        if (tag.contains("nether", Tag.TAG_COMPOUND)) {
            data.nether.read(tag.getCompound("nether"));
        }
        data.netherSpawnRepaired = tag.getBoolean("netherSpawnRepaired");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("overworld", this.overworld.write());
        tag.put("nether", this.nether.write());
        tag.putBoolean("netherSpawnRepaired", this.netherSpawnRepaired);
        return tag;
    }

    public static ExpandiaSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "expandia_progression");
    }

    public DimProgress of(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) ? this.nether : this.overworld;
    }

    public DimProgress overworld() {
        return this.overworld;
    }

    public DimProgress nether() {
        return this.nether;
    }

    public void resetAll() {
        this.overworld.reset();
        this.nether.reset();
        this.netherSpawnRepaired = false;
        setDirty();
    }

    public boolean netherSpawnRepaired() {
        return this.netherSpawnRepaired;
    }

    public void setNetherSpawnRepaired(boolean value) {
        this.netherSpawnRepaired = value;
        setDirty();
    }

    public final class DimProgress {
        private final List<Integer> spiralIndices = new ArrayList<>();
        private final Set<String> countedAdvancements = new HashSet<>();

        private DimProgress() {
            this.spiralIndices.add(0);
        }

        private void read(CompoundTag tag) {
            this.spiralIndices.clear();
            this.countedAdvancements.clear();
            if (tag.contains("spiralIndices", Tag.TAG_INT_ARRAY)) {
                for (int value : tag.getIntArray("spiralIndices")) {
                    this.spiralIndices.add(value);
                }
            }
            if (this.spiralIndices.isEmpty()) {
                int legacyCount = tag.contains("islandCount", Tag.TAG_INT) ? Math.max(1, tag.getInt("islandCount")) : 1;
                for (int i = 0; i < legacyCount; i++) {
                    this.spiralIndices.add(i);
                }
            }
            if (!this.spiralIndices.contains(0)) {
                this.spiralIndices.add(0);
            }
            if (tag.contains("countedAdvancements", Tag.TAG_LIST)) {
                ListTag list = tag.getList("countedAdvancements", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    this.countedAdvancements.add(list.getString(i));
                }
            }
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("spiralIndices", this.spiralIndices.stream().mapToInt(Integer::intValue).toArray());
            tag.putInt("islandCount", this.spiralIndices.size());
            ListTag counted = new ListTag();
            for (String id : this.countedAdvancements) {
                counted.add(StringTag.valueOf(id));
            }
            tag.put("countedAdvancements", counted);
            return tag;
        }

        public int getIslandCount() {
            return this.spiralIndices.size();
        }

        public int[] getSpiralIndices() {
            int[] result = new int[this.spiralIndices.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = this.spiralIndices.get(i);
            }
            return result;
        }

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

        public boolean addIsland(int spiralIndex, String advancementId) {
            if (advancementId != null && !this.countedAdvancements.add(advancementId)) {
                return false;
            }
            addSlot(spiralIndex);
            return true;
        }

        public void markCounted(String advancementId) {
            if (advancementId != null) {
                this.countedAdvancements.add(advancementId);
                ExpandiaSavedData.this.setDirty();
            }
        }

        public void addIsland(int spiralIndex) {
            addSlot(spiralIndex);
        }

        private void addSlot(int spiralIndex) {
            if (!this.spiralIndices.contains(spiralIndex)) {
                this.spiralIndices.add(spiralIndex);
            }
            ExpandiaSavedData.this.setDirty();
        }

        public void reset() {
            this.spiralIndices.clear();
            this.spiralIndices.add(0);
            this.countedAdvancements.clear();
            ExpandiaSavedData.this.setDirty();
        }
    }
}
