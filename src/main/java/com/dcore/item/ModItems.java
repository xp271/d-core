package com.dcore.item;

import com.dcore.DCore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ModItems {
	
	// 绿色钥匙
	public static final Item GREEN_KEY = new Item(new Item.Properties());

	/**
	 * 注册所有物品
	 */
	public static void registerItems() {
		DCore.LOGGER.info("正在注册物品...");
		
		// 注册核心碎片
		Registry.register(BuiltInRegistries.ITEM, 
			new ResourceLocation(DCore.MOD_ID, "green_key"), 
			GREEN_KEY);
		
		
		DCore.LOGGER.info("物品注册完成！");
	}
}

