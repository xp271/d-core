package com.dcore.media.config;

import com.dcore.DCore;
import com.dcore.media.MediaType;
import com.dcore.media.MediaTypeRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 媒体类型配置加载器
 * 从 JSON 配置文件加载物品和动作的媒体类型配置
 */
public class MediaTypeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;
    
    /**
     * 获取 Gson 实例（供其他类使用）
     */
    public static Gson getGson() {
        return GSON;
    }
    
    
    /**
     * 加载配置文件
     */
    public static void load(Path configDir) {
        configPath = configDir.resolve("d-core").resolve("media_types.json");
        
        try {
            if (!Files.exists(configPath)) {
                createDefaultConfig(configPath);
            }
            
            String content = Files.readString(configPath);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json == null) {
                DCore.LOGGER.error("[MediaTypeConfig] 配置文件解析失败，JSON 为 null");
                return;
            }
            
            loadConfig(json);
            
        } catch (IOException e) {
            DCore.LOGGER.error("[MediaTypeConfig] 加载配置文件失败: {}", configPath, e);
        } catch (Exception e) {
            DCore.LOGGER.error("[MediaTypeConfig] 处理配置文件时发生错误: {}", configPath, e);
        }
    }
    
    /**
     * 创建默认配置文件
     */
    private static void createDefaultConfig(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        
        JsonObject config = new JsonObject();
        
        // 媒体物品配置
        // 支持的媒体类型：standard, special（可以在 MediaType 枚举中添加更多类型）
        JsonObject items = new JsonObject();
        items.addProperty("comment", "媒体物品配置 - type 字段支持: standard, special");
        JsonObject exampleItem = new JsonObject();
        exampleItem.addProperty("amount", 80000);
        exampleItem.addProperty("type", "special");
        items.add("spectrum:amethyst_powder", exampleItem);
        config.add("special_media_items", items);
        
        // 动作媒体类型配置
        // 值可以是字符串（媒体类型名称），如 "special", "standard"
        JsonObject actions = new JsonObject();
        actions.addProperty("comment", "动作媒体类型配置 - 值支持: standard, special");
        actions.addProperty("d-core:damage_entity", "special");
        config.add("special_media_actions", actions);
        
        // 媒体类型血量扣除比例配置
        // 值表示：多少媒体量 = 1 血量，例如 100.0 表示 100 媒体 = 1 血量
        JsonObject healthRates = new JsonObject();
        healthRates.addProperty("comment", "媒体类型血量扣除比例 - 值表示：多少媒体量 = 1 血量");
        healthRates.addProperty("standard", 10000.0);
        healthRates.addProperty("special", 100.0);
        config.add("media_type_health_rates", healthRates);
        
        Files.writeString(path, GSON.toJson(config));
    }
    
    /**
     * 加载配置数据
     */
    private static void loadConfig(JsonObject json) {
        int itemsLoaded = 0;
        int actionsLoaded = 0;
        
        // 加载媒体类型的血量扣除比例配置
        if (json.has("media_type_health_rates")) {
            JsonObject healthRates = json.getAsJsonObject("media_type_health_rates");
            for (Map.Entry<String, JsonElement> entry : healthRates.entrySet()) {
                String key = entry.getKey();
                if (key.equals("comment") || key.startsWith("comment_")) {
                    continue;
                }
                
                try {
                    MediaType mediaType = MediaType.fromString(key);
                    double rate = entry.getValue().getAsDouble();
                    MediaTypeRegistry.registerMediaTypeHealthRate(mediaType, rate);
                } catch (Exception e) {
                    DCore.LOGGER.warn("[MediaTypeConfig] 无效的血量扣除比例配置: {}", key, e);
                }
            }
        }
        
        if (json.has("special_media_items")) {
            JsonObject items = json.getAsJsonObject("special_media_items");
            
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                String key = entry.getKey();
                if (key.equals("comment") || key.startsWith("comment_") || key.equals("example")) {
                    continue;
                }
                
                try {
                    ResourceLocation itemId = new ResourceLocation(key);
                    JsonObject itemConfig = entry.getValue().getAsJsonObject();
                    
                    String typeStr = itemConfig.has("type") ? itemConfig.get("type").getAsString() : "standard";
                    MediaType mediaType = MediaType.fromString(typeStr);
                    
                    MediaTypeRegistry.registerItemMediaType(itemId, mediaType);
                    itemsLoaded++;
                } catch (Exception e) {
                    DCore.LOGGER.warn("[MediaTypeConfig] 无效的物品配置: {}", key, e);
                }
            }
        }
        
        if (json.has("special_media_actions")) {
            JsonElement actionsElement = json.get("special_media_actions");
            if (actionsElement.isJsonObject()) {
                JsonObject actions = actionsElement.getAsJsonObject();
                
                for (Map.Entry<String, JsonElement> entry : actions.entrySet()) {
                    String key = entry.getKey();
                    if (key.equals("comment") || key.startsWith("comment_") || key.equals("example")) {
                        continue;
                    }
                    
                    try {
                        ResourceLocation actionId = new ResourceLocation(key);
                        MediaType requiredType = MediaType.SPECIAL; // 默认值
                        
                        // 如果值是字符串，解析媒体类型
                        JsonElement value = entry.getValue();
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            String typeStr = value.getAsString();
                            requiredType = MediaType.fromString(typeStr);
                        }
                        // 如果是对象，可以扩展支持更多配置（目前默认 SPECIAL）
                        
                        MediaTypeRegistry.registerActionRequiredType(actionId, requiredType);
                        actionsLoaded++;
                    } catch (Exception e) {
                        DCore.LOGGER.warn("[MediaTypeConfig] 无效的动作配置: {}", key, e);
                    }
                }
            }
        }
    }
    
    /**
     * 重新加载配置
     */
    public static void reload() {
        if (configPath != null) {
            load(configPath.getParent().getParent());
        }
    }
}

