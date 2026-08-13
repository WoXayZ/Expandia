package com.expandia;

import com.expandia.client.ExpandiaClientBootstrap;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.network.ExpandiaNetwork;
import com.expandia.registry.ExpandiaRegistries;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(ExpandiaConstants.MOD_ID)
public final class Expandia {

    public static final Logger LOGGER = LogUtils.getLogger();

    public Expandia(IEventBus modEventBus, ModContainer modContainer) {
        ExpandiaRegistries.register(modEventBus);
        ExpandiaConfigManager.load();
        modEventBus.addListener(ExpandiaNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ExpandiaClientBootstrap.init(modEventBus);
        }
        LOGGER.info("Expandia initialised - players will start on a small island and expand the world through advancements.");
    }
}
