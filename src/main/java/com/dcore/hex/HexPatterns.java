package com.dcore.hex;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.dcore.DCore;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class HexPatterns {
    private static final Map<ResourceLocation, ActionRegistryEntry> ACTIONS = new LinkedHashMap<>();
    
    // 参考 HexMod 的注册方式：使用 make 方法将图案放入 Map，然后通过 register 方法批量注册
    public static final ActionRegistryEntry DAMAGE_ENTITY = make("damage_entity",
        new ActionRegistryEntry(HexPattern.fromAngles("wdwdw", HexDir.EAST), OpDamageEntity.INSTANCE));
    
    // 伤害实体列表：输入一个实体列表，对列表中所有实体造成伤害，消耗为实体数量的平方 * 单体消耗
    public static final ActionRegistryEntry DAMAGE_ENTITY_LIST = make("damage_entity_list",
        new ActionRegistryEntry(HexPattern.fromAngles("wdwdww", HexDir.EAST), OpDamageEntityList.INSTANCE));
    
    // 消耗所有媒体伤害单个实体：输入单个实体，消耗背包中所有媒体，伤害 = 总媒体量 / 媒体血量扣除比例
    public static final ActionRegistryEntry CONSUME_ALL_MEDIA_DAMAGE_ENTITY = make("consume_all_media_damage_entity",
        new ActionRegistryEntry(HexPattern.fromAngles("ddaadaada", HexDir.EAST), OpConsumeAllMediaDamageEntity.INSTANCE));
    
    // 消耗所有媒体伤害实体列表：输入实体列表，消耗背包中所有媒体，伤害平均分配
    public static final ActionRegistryEntry CONSUME_ALL_MEDIA_DAMAGE_ENTITY_LIST = make("consume_all_media_damage_entity_list",
        new ActionRegistryEntry(HexPattern.fromAngles("ddawaaddaaddaaw", HexDir.EAST), OpConsumeAllMediaDamageEntityList.INSTANCE));
    
    // 速度伤害：根据实体当前血量和速度造成伤害
    public static final ActionRegistryEntry VELOCITY_DAMAGE_ENTITY = make("velocity_damage_entity",
        new ActionRegistryEntry(HexPattern.fromAngles("dadaqwadadaqw", HexDir.NORTH_EAST), OpVelocityDamageEntity.INSTANCE));
    
    // 负面效果伤害：根据实体身上负面效果的数量造成伤害
    public static final ActionRegistryEntry NEGATIVE_EFFECT_DAMAGE_ENTITY = make("negative_effect_damage_entity",
        new ActionRegistryEntry(HexPattern.fromAngles("waqawawaq", HexDir.SOUTH_EAST), OpNegativeEffectDamageEntity.INSTANCE));
    
    private static ActionRegistryEntry make(String name, ActionRegistryEntry are) {
        // 使用我们自己的 MOD_ID，而不是 HexAPI.modLoc（它使用的是 hexcasting）
        ResourceLocation id = new ResourceLocation(DCore.MOD_ID, name);
        var old = ACTIONS.put(id, are);
        if (old != null) {
            throw new IllegalArgumentException("Typo? Duplicate id " + name);
        }
        return are;
    }
    
    public static void register(BiConsumer<ActionRegistryEntry, ResourceLocation> r) {
        for (var e : ACTIONS.entrySet()) {
            r.accept(e.getValue(), e.getKey());
        }
    }
    
}

