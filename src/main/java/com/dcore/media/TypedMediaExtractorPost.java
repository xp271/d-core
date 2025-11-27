package com.dcore.media;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import com.dcore.DCore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类型化媒体提取 Post 组件
 * 在 extractMediaEnvironment 之后检查，如果动作需要特殊类型媒体，
 * 但 extractMediaEnvironment 仍然提取了 STANDARD 类型媒体，记录错误并阻止
 */
public class TypedMediaExtractorPost implements CastingEnvironmentComponent.ExtractMedia.Post {
    
    public static final CastingEnvironmentComponent.Key<TypedMediaExtractorPost> KEY = new CastingEnvironmentComponent.Key<TypedMediaExtractorPost>() {};
    
    private final CastingEnvironment environment;
    
    // 记录 Pre hook 传递给 extractMediaEnvironment 的 cost
    // Key: Thread ID (简化，实际应该用更精确的标识)
    // Value: Pre hook 传递给 extractMediaEnvironment 的 cost
    private static final ThreadLocal<Long> preHookCost = new ThreadLocal<>();
    
    public TypedMediaExtractorPost(CastingEnvironment environment) {
        this.environment = environment;
    }
    
    @Override
    public @NotNull Key<TypedMediaExtractorPost> getKey() {
        return KEY;
    }
    
    /**
     * 记录 Pre hook 传递给 extractMediaEnvironment 的 cost
     * 由 TypedMediaExtractor 调用
     */
    public static void recordPreHookCost(long cost) {
        preHookCost.set(cost);
    }
    
    @Override
    public long onExtractMedia(long cost, boolean simulate) {
        // Post hook 接收的是 extractMediaEnvironment 之后的剩余 cost
        // 我们需要检查：如果 Pre hook 传递了 0，但 extractMediaEnvironment 仍然提取了媒体（cost < 0），这是错误的
        
        ResourceLocation currentActionId = ActionContext.getCurrentAction();
        if (currentActionId == null) {
            preHookCost.remove(); // 清除记录
            return cost; // 无法确定动作，不处理
        }
        
        MediaType requiredType = MediaTypeRegistry.getActionRequiredType(currentActionId);
        if (requiredType == null || requiredType == MediaType.STANDARD) {
            preHookCost.remove(); // 清除记录
            return cost; // 不需要特殊类型，不处理
        }
        
        // 获取 Pre hook 传递给 extractMediaEnvironment 的 cost
        Long preCost = preHookCost.get();
        if (preCost == null) {
            return cost;
        }
        
        if (preCost == 0 && cost < 0) {
            DCore.LOGGER.error("[TypedMediaExtractorPost] 错误：动作 {} 需要 {} 类型媒体，Pre hook 传递了 0，但 extractMediaEnvironment 仍然提取了 {} STANDARD 类型媒体", 
                currentActionId, requiredType, -cost);
            preHookCost.remove();
            return 0;
        }
        
        preHookCost.remove(); // 清除记录
        return cost;
    }
}

