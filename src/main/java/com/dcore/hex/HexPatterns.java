package com.dcore.hex;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class HexPatterns {
    public static void register() {
        var registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        
        // 创建一个简单的图案：从东北方向开始，画一个简单的三角形
        // 图案签名: "waq" 表示向前、向左、向前
        HexPattern pattern = HexPattern.fromAngles("waq", HexDir.NORTH_EAST);
        
        // 创建动作注册条目
        ActionRegistryEntry entry = new ActionRegistryEntry(pattern, OpDamageEntity.INSTANCE);
        
        // 注册图案
        ResourceLocation id = HexAPI.modLoc("damage_entity");
        ResourceKey<ActionRegistryEntry> key = ResourceKey.create(registry.key(), id);
        Registry.register(registry, key, entry);
        
        HexAPI.LOGGER.info("已注册伤害图案: " + id);
    }
}

