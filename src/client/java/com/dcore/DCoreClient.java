package com.dcore;

import net.fabricmc.api.ClientModInitializer;

public class DCoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DCore.LOGGER.info("D-Core 客户端初始化完成！");
	}
}

