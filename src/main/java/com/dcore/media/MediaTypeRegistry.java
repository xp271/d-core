package com.dcore.media;

import com.dcore.DCore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 媒体类型注册表
 * 用于注册和管理媒体类型与物品的映射关系
 */
public class MediaTypeRegistry {
    private static final Map<ResourceLocation, MediaType> itemToMediaType = new HashMap<>();
    private static final Map<ResourceLocation, MediaType> actionToRequiredType = new HashMap<>();
    
    // 每种媒体类型的血量扣除比例（媒体量/血量）
    // 例如：100.0 表示 100 媒体 = 1 血量
    private static final Map<MediaType, Double> mediaTypeToHealthRate = new HashMap<>();
    
    /**
     * 注册物品的媒体类型
     * @param itemId 物品 ID
     * @param mediaType 媒体类型
     */
    public static void registerItemMediaType(ResourceLocation itemId, MediaType mediaType) {
        itemToMediaType.put(itemId, mediaType);
    }
    
    public static void registerActionRequiredType(ResourceLocation actionId, @Nullable MediaType requiredType) {
        actionToRequiredType.put(actionId, requiredType);
    }
    
    public static MediaType getItemMediaType(ResourceLocation itemId) {
        return itemToMediaType.getOrDefault(itemId, MediaType.STANDARD);
    }
    
    @Nullable
    public static MediaType getActionRequiredType(ResourceLocation actionId) {
        return actionToRequiredType.get(actionId);
    }
    
    /**
     * 检查动作是否需要特定类型的媒体
     * @param actionId 动作 ID
     * @return true 如果需要特定类型，false 如果可以使用任何类型或消耗血量
     */
    public static boolean requiresSpecificMediaType(ResourceLocation actionId) {
        return actionToRequiredType.containsKey(actionId) && actionToRequiredType.get(actionId) != null;
    }
    
    /**
     * 注册媒体类型的血量扣除比例
     * @param mediaType 媒体类型
     * @param healthRate 血量扣除比例（媒体量/血量），例如 100.0 表示 100 媒体 = 1 血量
     */
    public static void registerMediaTypeHealthRate(MediaType mediaType, double healthRate) {
        mediaTypeToHealthRate.put(mediaType, healthRate);
    }
    
    /**
     * 获取媒体类型的血量扣除比例
     * @param mediaType 媒体类型
     * @return 血量扣除比例，默认 10000.0（10000 媒体 = 1 血量）
     */
    public static double getMediaTypeHealthRate(MediaType mediaType) {
        return mediaTypeToHealthRate.getOrDefault(mediaType, 10000.0);
    }
    
    /**
     * 清除所有注册（主要用于测试）
     */
    public static void clear() {
        itemToMediaType.clear();
        actionToRequiredType.clear();
        mediaTypeToHealthRate.clear();
    }
}

