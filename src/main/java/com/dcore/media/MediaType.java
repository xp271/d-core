package com.dcore.media;

import net.minecraft.resources.ResourceLocation;
import com.dcore.DCore;

/**
 * 媒体类型枚举
 * 定义不同的媒体类型，例如普通媒体和特殊媒体
 */
public enum MediaType {
    /**
     * 默认/普通媒体类型（HexMod 原有的媒体类型）
     */
    STANDARD(createId("standard")),
    
    /**
     * 特殊媒体类型（新的媒体类型）
     * 只能由特定的物品提供，不能存储在普通容器中
     */
    SPECIAL(createId("special"));

    private final ResourceLocation id;

    MediaType(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTranslationKey() {
        return "media_type." + DCore.MOD_ID + "." + id.getPath();
    }

    private static ResourceLocation createId(String path) {
        return new ResourceLocation(DCore.MOD_ID, path);
    }

    /**
     * 根据 ID 查找媒体类型
     */
    public static MediaType fromId(ResourceLocation id) {
        for (MediaType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return STANDARD; // 默认返回标准类型
    }
    
    /**
     * 根据字符串名称查找媒体类型（不区分大小写）
     * 用于从配置文件解析
     */
    public static MediaType fromString(String name) {
        if (name == null) {
            return STANDARD;
        }
        String lowerName = name.toLowerCase();
        for (MediaType type : values()) {
            if (type.name().equalsIgnoreCase(lowerName) || 
                type.id.getPath().equalsIgnoreCase(lowerName)) {
                return type;
            }
        }
        return STANDARD; // 默认返回标准类型
    }
}

