package com.expandia.client;

import com.expandia.ExpandiaConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = ExpandiaConstants.MOD_ID, dist = Dist.CLIENT)
public final class ExpandiaClient {

    public ExpandiaClient(IEventBus modEventBus) {
        ExpandiaClientBootstrap.init(modEventBus);
    }
}
