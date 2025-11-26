package com.dcore;

import com.dcore.hex.HexPatterns;
import com.dcore.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DCore implements ModInitializer {
	public static final String MOD_ID = "d-core";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("D-Core 模组正在初始化...");
		
		// 注册物品
		ModItems.registerItems();
		
		// 注册 Hex 图案（参考 HexMod 的方式）
		// HexMod 在 initRegistries() 中调用 HexActions.register(bind(registry))
		// 我们也在 onInitialize 中注册，确保在 PatternRegistryManifest.processRegistry() 之前注册
		var registry = at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.getActionRegistry();
		HexPatterns.register((entry, id) -> net.minecraft.core.Registry.register(registry, id, entry));
		
		LOGGER.info("D-Core 模组初始化完成！");
	}
}

