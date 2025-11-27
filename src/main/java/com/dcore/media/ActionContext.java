package com.dcore.media;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作上下文
 * 用于追踪当前正在执行的动作
 */
public class ActionContext {
    private static final ThreadLocal<ResourceLocation> currentActionId = new ThreadLocal<>();
    
    // 记录需要特殊类型媒体的动作（用于在 simulate=false 时也能识别）
    // Key: Thread ID + 时间戳（简化），Value: 动作 ID
    // 实际上我们使用 ThreadLocal 的扩展来存储最近的动作
    private static final ThreadLocal<ResourceLocation> lastActionRequiringSpecialMedia = new ThreadLocal<>();
    
    /**
     * 设置当前执行的动作 ID
     */
    public static void setCurrentAction(ResourceLocation actionId) {
        currentActionId.set(actionId);
    }
    
    /**
     * 获取当前执行的动作 ID
     * @return 动作 ID，如果没有则返回 null
     */
    public static ResourceLocation getCurrentAction() {
        ResourceLocation action = currentActionId.get();
        // 如果当前动作为空，尝试使用最近需要特殊媒体的动作
        if (action == null) {
            action = lastActionRequiringSpecialMedia.get();
        }
        return action;
    }
    
    /**
     * 记录需要特殊类型媒体的动作（用于 simulate=false 时也能识别）
     */
    public static void markActionRequiringSpecialMedia(ResourceLocation actionId) {
        lastActionRequiringSpecialMedia.set(actionId);
    }
    
    /**
     * 清除当前动作（应该在动作执行完成后调用）
     */
    public static void clearCurrentAction() {
        currentActionId.remove();
        // 延迟清除 lastActionRequiringSpecialMedia，给 simulate=false 更多时间
        // 在实际消耗媒体后再清除
    }
    
    /**
     * 清除需要特殊媒体的动作记录
     */
    public static void clearSpecialMediaAction() {
        lastActionRequiringSpecialMedia.remove();
    }
}
