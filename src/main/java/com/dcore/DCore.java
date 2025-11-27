package com.dcore;

import com.dcore.hex.HexPatterns;
import com.dcore.item.ModItems;
import com.dcore.media.TypedMediaExtractorRegistration;
import com.dcore.media.config.MediaTypeConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class DCore implements ModInitializer {
	public static final String MOD_ID = "d-core";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.registerItems();
		
		var registry = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.getActionRegistry();
		HexPatterns.register((entry, id) -> net.minecraft.core.Registry.register(registry, id, entry));
		
		TypedMediaExtractorRegistration.register();
		
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Path configDir = server.getServerDirectory().toPath().resolve("config");
			MediaTypeConfig.load(configDir);
		});
	}
}

