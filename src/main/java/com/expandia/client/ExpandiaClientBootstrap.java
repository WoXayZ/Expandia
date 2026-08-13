package com.expandia.client;

import com.expandia.ExpandiaConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ExpandiaClientBootstrap {

    public static final KeyMapping OPEN_SCREEN = new KeyMapping(
            ExpandiaConstants.Lang.KEY_OPEN_SCREEN,
            GLFW.GLFW_KEY_K,
            ExpandiaConstants.Lang.KEY_CATEGORY);

    private ExpandiaClientBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ExpandiaClientBootstrap::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ExpandiaClientBootstrap::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SCREEN);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_SCREEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new ExpandiaIslandsScreen());
            }
        }
    }
}
