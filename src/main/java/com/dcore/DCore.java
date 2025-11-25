package com.dcore;

import com.dcore.hex.HexPatterns;
import com.dcore.item.ModItems;
import net.fabricmc.api.ModInitializer;
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
		
		// 注册 Hex 图案
		HexPatterns.register();
		
		LOGGER.info("D-Core 模组初始化完成！");
	}
}

