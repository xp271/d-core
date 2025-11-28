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
        DCore.LOGGER.info("[TypedMediaExtractor] onExtractMedia: cost={}, simulate={}", cost, simulate);
        
        if (cost <= 0) {
            return cost; // 不需要媒体
        }
        
        ResourceLocation currentActionId = getCurrentActionId();
        if (currentActionId == null) {
            DCore.LOGGER.info("[TypedMediaExtractor] currentActionId is null, returning cost");
            return cost;
        }
        
        MediaType requiredType = MediaTypeRegistry.getActionRequiredType(currentActionId);
        if (requiredType == null) {
            DCore.LOGGER.info("[TypedMediaExtractor] requiredType is null, returning cost");
            return cost;
        }
        
        DCore.LOGGER.info("[TypedMediaExtractor] currentActionId={}, requiredType={}", currentActionId, requiredType);
        
        if (requiredType == MediaType.SPECIAL) {
            ActionContext.markActionRequiringSpecialMedia(currentActionId);
        }
        
        long extracted = extractTypedMedia(cost, requiredType, simulate);
        long remaining = cost - extracted;
        
        DCore.LOGGER.info("[TypedMediaExtractor] extracted={}, remaining={}, simulate={}", extracted, remaining, simulate);
        
        if (remaining > 0) {
            if (simulate) {
                // 在 simulate 模式下，检查血量是否足够，以便提前设置取消标记
                checkAndMarkCancellationIfNeeded(remaining, requiredType);
                // simulate 模式下返回 0，让 HexMod 认为媒体足够（真实扣血在非 simulate 时执行）
                // 注意：即使设置了取消标记，也要返回 0，这样 HexMod 才会继续执行真实操作
                remaining = 0;
            } else {
                // 真实执行时，必须扣除血量（无论是否设置了取消标记）
                DCore.LOGGER.info("[TypedMediaExtractor] 真实执行模式，调用 extractHealthAsMedia: remaining={}", remaining);
                long extractedFromHealth = extractHealthAsMedia(remaining);
                DCore.LOGGER.info("[TypedMediaExtractor] extractHealthAsMedia 返回: extractedFromHealth={}", extractedFromHealth);
                // 无论血量是否足够，我们都返回 0（表示已提取所有可用媒体）
                // 如果血量不足，extractHealthAsMedia 会设置或保持取消标记
                remaining = 0;
            }
        } else if (remaining < 0) {
            remaining = 0;
        }
        
        if (requiredType == MediaType.SPECIAL) {
            TypedMediaExtractorPost.recordPreHookCost(remaining);
        }
        
        if (remaining == 0 && !simulate) {
            ActionContext.clearSpecialMediaAction();
            ActionContext.clearCurrentAction();
            // 注意：取消标记不应该在这里清除，因为可能有后续的 Action 需要使用这个标记
            // 取消标记应该在 Action 的 operate() 方法中，或者 SpellCancellationMixin 中清除
        }
        
        DCore.LOGGER.info("[TypedMediaExtractor] onExtractMedia 返回: remaining={}, simulate={}, shouldCancel={}", 
            remaining, simulate, SpellCancellationContext.shouldCancel());
        return remaining;
    }
    
    /**
     * 检查血量是否足够，如果不够则设置取消标记（用于 simulate 模式）
     */
    private void checkAndMarkCancellationIfNeeded(long mediaAmount, MediaType requiredType) {
        if (!(environment.getCastingEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // 从配置获取该媒体类型的血量扣除比例
        double healthRate = MediaTypeRegistry.getMediaTypeHealthRate(requiredType);
        double healthCost = mediaAmount / healthRate;
        float maxHealth = player.getMaxHealth();
        
        // 检查血量扣除是否超过最大生命值
        if (healthCost > maxHealth) {
            // 提前设置取消标记和媒体消耗量，这样在 operate() 调用之前就能检查到
            SpellCancellationContext.markForCancellation(mediaAmount);
            // 注意：在 simulate 阶段不发送消息，只在真实执行阶段发送，避免重复
            DCore.LOGGER.info("[TypedMediaExtractor] simulate模式: 检测到血量不足: healthCost={}, maxHealth={}, 已设置取消标记, mediaAmount={}", 
                healthCost, maxHealth, mediaAmount);
        }
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
     * 如果血量扣除超过玩家最大生命值，将扣除生命值但标记法术需要被取消
     */
    private long extractHealthAsMedia(long amount) {
        if (!(environment.getCastingEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        // 如果 simulate 阶段已经检查过并设置了取消标记，直接使用结果，避免重复计算
        if (SpellCancellationContext.shouldCancel()) {
            long existingCost = SpellCancellationContext.getMediaCost();
            // 如果已有取消标记且媒体消耗量匹配，说明 simulate 阶段已经检查过
            if (existingCost == amount) {
                // 从配置获取该媒体类型的血量扣除比例（只获取一次）
                ResourceLocation currentActionId = getCurrentActionId();
                MediaType requiredType = MediaType.STANDARD;
                if (currentActionId != null) {
                    MediaType actionType = MediaTypeRegistry.getActionRequiredType(currentActionId);
                    if (actionType != null) {
                        requiredType = actionType;
                    }
                }
                double healthRate = MediaTypeRegistry.getMediaTypeHealthRate(requiredType);
                float currentHealth = player.getHealth();
                
                DCore.LOGGER.info("[TypedMediaExtractor] extractHealthAsMedia: 使用 simulate 阶段的结果，直接扣除血量并发送消息, amount={}", amount);
                
                // 发送消息（如果还没发送过）
                if (!SpellCancellationContext.isMessageSent()) {
                    net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.translatable("d-core.spell.cancelled.insufficient_health");
                    player.sendSystemMessage(message);
                    SpellCancellationContext.markMessageSent();
                    DCore.LOGGER.info("[TypedMediaExtractor] 已向玩家发送取消消息: {}", player.getName().getString());
                }
                
                // 扣除所有生命值（设置为0）
                player.setHealth(0.0f);
                // 返回实际扣除的血量对应的媒体量
                long extracted = (long) (currentHealth * healthRate);
                DCore.LOGGER.info("[TypedMediaExtractor] 返回提取的媒体量: {}", extracted);
                return extracted;
            }
        }
        
        // 如果 simulate 阶段没有检查过，或者检查结果不一致，执行完整的检查流程
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
        float maxHealth = player.getMaxHealth();
        
        DCore.LOGGER.info("[TypedMediaExtractor] extractHealthAsMedia: amount={}, healthRate={}, healthCost={}, currentHealth={}, maxHealth={}", 
            amount, healthRate, healthCost, currentHealth, maxHealth);
        
        // 检查血量扣除是否超过最大生命值
        if (healthCost > maxHealth) {
            DCore.LOGGER.info("[TypedMediaExtractor] 血量扣除超过最大生命值: healthCost={}, maxHealth={}, 扣除所有生命值并标记取消, amount={}", 
                healthCost, maxHealth, amount);
            
            // 如果还没有发送过消息，立即发送消息给玩家（在扣除血量之前发送）
            if (!SpellCancellationContext.isMessageSent()) {
                net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.translatable("d-core.spell.cancelled.insufficient_health");
                player.sendSystemMessage(message);
                SpellCancellationContext.markMessageSent();
                DCore.LOGGER.info("[TypedMediaExtractor] 已向玩家发送取消消息: {}", player.getName().getString());
            } else {
                DCore.LOGGER.info("[TypedMediaExtractor] 消息已发送过，跳过重复发送");
            }
            
            // 扣除所有生命值（设置为0）
            player.setHealth(0.0f);
            // 标记法术需要被取消，并记录媒体消耗量
            SpellCancellationContext.markForCancellation(amount);
            DCore.LOGGER.info("[TypedMediaExtractor] 已标记取消标记: shouldCancel={}, mediaCost={}", 
                SpellCancellationContext.shouldCancel(), SpellCancellationContext.getMediaCost());
            // 返回实际扣除的血量对应的媒体量
            long extracted = (long) (currentHealth * healthRate);
            DCore.LOGGER.info("[TypedMediaExtractor] 返回提取的媒体量: {}", extracted);
            return extracted;
        }
        
        // 正常扣除血量
        float newHealth = (float) Math.max(0, currentHealth - healthCost);
        player.setHealth(newHealth);
        long extracted = (long) (Math.min(healthCost, currentHealth) * healthRate);
        
        DCore.LOGGER.info("[TypedMediaExtractor] 正常扣除血量: newHealth={}, extracted={}", newHealth, extracted);
        return extracted;
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

