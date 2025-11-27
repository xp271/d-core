package com.dcore.media;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents;
import at.petrak.hexcasting.fabric.cc.adimpl.CCMediaHolder;
import com.dcore.media.fabric.TypedCCMediaHolder;
import com.dcore.DCore;
import com.dcore.media.config.MediaTypeConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onyxstudios.cca.api.v3.item.ItemComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.item.ItemComponentInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 注册媒体消耗品的组件初始化器
 * Cardinal Components 会自动扫描并加载实现 ItemComponentInitializer 的类
 * 
 * 支持两种注册方式：
 * 1. 普通媒体消耗品（注册到 HexMod 的 MEDIA_HOLDER）
 * 2. 类型化媒体消耗品（注册到类型化媒体系统）
 */
public class DCoreMediaComponents implements ItemComponentInitializer {

    @Override
    public void registerItemComponentFactories(ItemComponentFactoryRegistry registry) {
        // 从配置文件加载物品注册
        loadItemsFromConfig(registry);
    }
    
    /**
     * 注册单个物品为媒体消耗品
     */
    private void registerItem(ItemComponentFactoryRegistry registry, ResourceLocation itemId, 
                             long mediaAmount, int priority, MediaType mediaType) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            return;
        }
        
        try {
            if (mediaType == MediaType.STANDARD) {
                registry.register(
                    item,
                    HexCardinalComponents.MEDIA_HOLDER,
                    stack -> new CCMediaHolder.Static(
                        () -> mediaAmount,
                        priority,
                        stack
                    )
                );
            } else {
                registry.register(
                    item,
                    HexCardinalComponents.MEDIA_HOLDER,
                    stack -> new TypedCCMediaHolder.Static(
                        () -> mediaAmount,
                        priority,
                        mediaType,
                        stack
                    )
                );
            }
            MediaTypeRegistry.registerItemMediaType(itemId, mediaType);
        } catch (Exception e) {
            DCore.LOGGER.error("[DCoreMediaComponents] 注册物品 {} 时发生错误", itemId, e);
        }
    }
    
    /**
     * 从配置文件加载物品注册
     */
    private void loadItemsFromConfig(ItemComponentFactoryRegistry registry) {
        try {
            // 尝试从配置目录加载
            Path configPath = getConfigPath();
            if (configPath == null || !Files.exists(configPath)) {
                return; // 配置文件不存在，跳过
            }
            
            String content = Files.readString(configPath);
            JsonObject json = MediaTypeConfig.getGson().fromJson(content, JsonObject.class);
            if (json == null || !json.has("special_media_items")) {
                return;
            }
            
            JsonObject items = json.getAsJsonObject("special_media_items");
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                String key = entry.getKey();
                if (key.equals("comment") || key.startsWith("comment_") || key.equals("example")) {
                    continue;
                }
                
                try {
                    ResourceLocation itemId = new ResourceLocation(key);
                    JsonObject itemConfig = entry.getValue().getAsJsonObject();
                    
                    long amount = itemConfig.has("amount") ? itemConfig.get("amount").getAsLong() : 80000L;
                    String typeStr = itemConfig.has("type") ? itemConfig.get("type").getAsString() : "standard";
                    MediaType mediaType = MediaType.fromString(typeStr);
                    int priority = itemConfig.has("priority") ? itemConfig.get("priority").getAsInt() : ADMediaHolder.AMETHYST_DUST_PRIORITY;
                    
                    registerItem(registry, itemId, amount, priority, mediaType);
                } catch (Exception e) {
                    DCore.LOGGER.warn("[DCoreMediaComponents] 从配置文件注册物品 {} 失败", key, e);
                }
            }
        } catch (Exception e) {
            DCore.LOGGER.error("[DCoreMediaComponents] 从配置文件加载物品注册失败", e);
        }
    }
    
    /**
     * 获取配置文件路径
     */
    private Path getConfigPath() {
        try {
            // 尝试多个可能的配置路径
            String[] possiblePaths = {
                "config/d-core/media_types.json",
                System.getProperty("user.dir") + "/config/d-core/media_types.json",
                "../config/d-core/media_types.json"
            };
            
            for (String pathStr : possiblePaths) {
                Path path = java.nio.file.Paths.get(pathStr);
                if (Files.exists(path)) {
                    return path;
                }
            }
            
            // 如果都不存在，返回默认路径（可能文件还不存在，但路径是有效的）
            return java.nio.file.Paths.get("config/d-core/media_types.json");
        } catch (Exception e) {
            return null;
        }
    }
    
}

