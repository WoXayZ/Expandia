package com.expandia.config;

import com.expandia.ExpandiaConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Registers the datapack progression listener so modpacks can ship/override progression data and
 * have it picked up on {@code /reload} without a restart.
 */
@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaReloadEvents {

    private ExpandiaReloadEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(ExpandiaConstants.id("progression"), new ExpandiaDatapackListener());
    }
}
