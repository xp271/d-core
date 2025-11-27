package com.dcore.media;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.dcore.DCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 类型化媒体提取组件
 * 使用 CastingEnvironmentComponent.ExtractMedia.Pre hook 来拦截媒体提取
 * 实现按类型提取媒体，如果特定类型不足则消耗血量
 */
public class TypedMediaExtractor implements CastingEnvironmentComponent.ExtractMedia.Pre {
    
    public static final CastingEnvironmentComponent.Key<TypedMediaExtractor> KEY = new CastingEnvironmentComponent.Key<TypedMediaExtractor>() {};
    
    private final CastingEnvironment environment;
    
    public TypedMediaExtractor(CastingEnvironment environment) {
        this.environment = environment;
    }
    
    @Override
    public @NotNull Key<TypedMediaExtractor> getKey() {
        return KEY;
    }
    
    @Override
    public long onExtractMedia(long cost, boolean simulate) {
        if (cost <= 0) {
            return cost; // 不需要媒体
        }
        
        ResourceLocation currentActionId = getCurrentActionId();
        if (currentActionId == null) {
            return cost;
        }
        
        MediaType requiredType = MediaTypeRegistry.getActionRequiredType(currentActionId);
        if (requiredType == null) {
            return cost;
        }
        
        if (requiredType == MediaType.SPECIAL) {
            ActionContext.markActionRequiringSpecialMedia(currentActionId);
        }
        
        long extracted = extractTypedMedia(cost, requiredType, simulate);
        long remaining = cost - extracted;
        
        if (remaining > 0) {
            if (!simulate) {
                extractHealthAsMedia(remaining);
            }
            remaining = 0;
        } else if (remaining < 0) {
            remaining = 0;
        }
        
        if (requiredType == MediaType.SPECIAL) {
            TypedMediaExtractorPost.recordPreHookCost(remaining);
        }
        
        if (remaining == 0 && !simulate) {
            ActionContext.clearSpecialMediaAction();
            ActionContext.clearCurrentAction();
        }
        
        return remaining;
    }
    
    /**
     * 提取指定类型的媒体
     */
    private long extractTypedMedia(long cost, MediaType requiredType, boolean simulate) {
        if (!(environment.getCastingEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        Inventory inventory = player.getInventory();
        List<TypedMediaSource> mediaSources = new ArrayList<>();
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            
            ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
            if (holder == null) continue;
            
            if (holder instanceof TypedMediaHolder typedHolder) {
                if (typedHolder.canProvideMediaType(requiredType)) {
                    mediaSources.add(new TypedMediaSource(
                        typedHolder,
                        i,
                        typedHolder.getConsumptionPriority()
                    ));
                }
            } else if (requiredType == MediaType.STANDARD) {
                TypedMediaHolder standardWrapper = new StandardMediaWrapper(holder);
                if (standardWrapper.canProvideMediaType(requiredType)) {
                    mediaSources.add(new TypedMediaSource(
                        standardWrapper,
                        i,
                        holder.getConsumptionPriority()
                    ));
                }
            }
        }
        
        if (mediaSources.isEmpty()) {
            return 0;
        }
        
        mediaSources.sort(Comparator.comparingInt((TypedMediaSource s) -> s.priority).reversed());
        
        long totalExtracted = 0;
        for (TypedMediaSource source : mediaSources) {
            if (totalExtracted >= cost) {
                break;
            }
            long remaining = cost - totalExtracted;
            totalExtracted += source.holder().withdrawMedia(remaining, simulate);
        }
        
        return totalExtracted;
    }
    
    /**
     * 从血量中提取媒体
     */
    private long extractHealthAsMedia(long amount) {
        if (!(environment.getCastingEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        // 获取当前动作需要的媒体类型，以确定使用哪个血量扣除比例
        ResourceLocation currentActionId = getCurrentActionId();
        MediaType requiredType = MediaType.STANDARD;
        if (currentActionId != null) {
            MediaType actionType = MediaTypeRegistry.getActionRequiredType(currentActionId);
            if (actionType != null) {
                requiredType = actionType;
            }
        }
        
        // 从配置获取该媒体类型的血量扣除比例
        double healthRate = MediaTypeRegistry.getMediaTypeHealthRate(requiredType);
        double healthCost = amount / healthRate;
        float currentHealth = player.getHealth();
        float newHealth = (float) Math.max(0, currentHealth - healthCost);
        
        player.setHealth(newHealth);
        
        return (long) (Math.min(healthCost, currentHealth) * healthRate);
    }
    
    private ResourceLocation getCurrentActionId() {
        return ActionContext.getCurrentAction();
    }
    
    /**
     * 媒体源信息
     */
    private record TypedMediaSource(
        TypedMediaHolder holder,
        int slot,
        int priority
    ) {}
    
    /**
     * 将普通 ADMediaHolder 包装为 STANDARD 类型的 TypedMediaHolder
     * 这样可以让普通容器在需要 STANDARD 类型时被使用
     */
    private static class StandardMediaWrapper implements TypedMediaHolder {
        private final ADMediaHolder delegate;
        
        public StandardMediaWrapper(ADMediaHolder delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public MediaType getMediaType() {
            return MediaType.STANDARD;
        }
        
        @Override
        public long getMedia() {
            return delegate.getMedia();
        }
        
        @Override
        public long getMaxMedia() {
            return delegate.getMaxMedia();
        }
        
        @Override
        public void setMedia(long media) {
            delegate.setMedia(media);
        }
        
        @Override
        public boolean canRecharge() {
            return delegate.canRecharge();
        }
        
        @Override
        public boolean canProvide() {
            return delegate.canProvide();
        }
        
        @Override
        public int getConsumptionPriority() {
            return delegate.getConsumptionPriority();
        }
        
        @Override
        public boolean canConstructBattery() {
            return delegate.canConstructBattery();
        }
        
        @Override
        public long withdrawMedia(long cost, boolean simulate) {
            return delegate.withdrawMedia(cost, simulate);
        }
        
        @Override
        public long insertMedia(long amount, boolean simulate) {
            return delegate.insertMedia(amount, simulate);
        }
    }
}

