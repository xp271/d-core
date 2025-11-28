package com.dcore.hex;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.dcore.media.SpellCancellationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 根据实体身上负面效果的数量造成伤害
 * 输入：实体
 * 伤害 = 1.2^求和(负面效果等级)
 */
public class OpNegativeEffectDamageEntity implements ConstMediaAction {
    public static final OpNegativeEffectDamageEntity INSTANCE = new OpNegativeEffectDamageEntity();
    
    private static final long BASE_MEDIA_COST = MediaConstants.DUST_UNIT * 2; // 基础消耗：2 个粉尘单位
    
    /**
     * 使用 ThreadLocal 存储计算结果，避免在 operate 和 execute 中重复计算
     */
    private static final ThreadLocal<DamageAndCost> cachedResult = new ThreadLocal<>();
    
    @Override
    public int getArgc() {
        return 1; // 只需要 1 个参数：实体
    }
    
    @Override
    public long getMediaCost() {
        return BASE_MEDIA_COST;
    }
    
    @Override
    public ConstMediaAction.CostMediaActionResult executeWithOpCount(List<? extends Iota> args, CastingEnvironment env) {
        List<Iota> resultStack = execute(args, env);
        return new ConstMediaAction.CostMediaActionResult(resultStack, 1);
    }
    
    /**
     * 计算结果：包含伤害和消耗
     */
    private static class DamageAndCost {
        final float damage;
        final long cost;
        
        DamageAndCost(float damage, long cost) {
            this.damage = damage;
            this.cost = cost;
        }
    }
    
    /**
     * 计算伤害和消耗
     */
    private DamageAndCost calculateDamageAndCost(LivingEntity livingTarget) {
        // 获取实体身上所有效果
        Collection<MobEffectInstance> effects = livingTarget.getActiveEffects();
        
        // 计算所有负面效果的等级总和（用于伤害）和连乘（用于消耗）
        int totalNegativeLevel = 0;
        long negativeLevelProduct = 1;
        boolean hasNegativeEffect = false;
        
        for (MobEffectInstance effect : effects) {
            // 检查是否为负面效果（不是有益效果）
            if (!effect.getEffect().isBeneficial()) {
                // 获取效果等级（amplifier + 1，因为等级从0开始）
                int level = effect.getAmplifier() + 1;
                totalNegativeLevel += level;
                negativeLevelProduct *= level;
                hasNegativeEffect = true;
            }
        }
        
        // 计算伤害：1.2^负面效果等级总和
        double damageValue;
        if (totalNegativeLevel > 0) {
            damageValue = Math.pow(1.2, totalNegativeLevel);
        } else {
            // 如果没有负面效果，造成最小伤害（1.2^0 = 1.0）
            damageValue = 1.0;
        }
        
        float actualDamage = (float) Math.max(0, damageValue);
        
        // 计算消耗：负面效果等级的连乘
        long mediaCost;
        if (hasNegativeEffect) {
            mediaCost = BASE_MEDIA_COST * negativeLevelProduct;
        } else {
            mediaCost = BASE_MEDIA_COST;
        }
        
        return new DamageAndCost(actualDamage, mediaCost);
    }
    
    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        if (args.size() < 1) {
            throw new MishapNotEnoughArgs(1, args.size());
        }
        
        // 获取实体（唯一参数）
        Iota entityIota = args.get(0);
        if (!(entityIota instanceof EntityIota)) {
            throw MishapInvalidIota.ofType(entityIota, 0, "entity");
        }
        Entity target = ((EntityIota) entityIota).getEntity();
        env.assertEntityInRange(target);
        
        // 检查是否为生物实体
        if (!(target instanceof LivingEntity livingTarget)) {
            // 如果不是生物实体，无法获取效果，直接返回
            cachedResult.remove(); // 清理缓存
            return List.of();
        }
        
        // 尝试使用缓存的结果，如果没有则计算
        DamageAndCost result = cachedResult.get();
        if (result == null) {
            // 如果缓存中没有结果，则计算（这种情况不应该发生，但为了安全还是处理一下）
            result = calculateDamageAndCost(livingTarget);
        }
        
        // 对实体造成伤害
        DamageSources damageSources = target.damageSources();
        DamageSource damageSource = damageSources.source(
            ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
            env.getCastingEntity()
        );
        
        livingTarget.hurt(damageSource, result.damage);
        
        // 产生粒子效果
        double eyeHeight = target.getEyeHeight();
        env.produceParticles(
            ParticleSpray.burst(target.position().add(0.0, eyeHeight / 2.0, 0.0), 0.5, 20),
            env.getPigment()
        );
        
        // 清理缓存
        cachedResult.remove();
        
        return List.of();
    }
    
    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        List<Iota> stack = new ArrayList<>(image.getStack());
        
        if (getArgc() > stack.size()) {
            throw new MishapNotEnoughArgs(getArgc(), stack.size());
        }
        List<Iota> args = new ArrayList<>(stack.subList(stack.size() - getArgc(), stack.size()));
        stack.subList(stack.size() - getArgc(), stack.size()).clear();
        
        // 先计算伤害和消耗，并将结果缓存到 ThreadLocal
        DamageAndCost damageAndCost = calculateDamageAndCostForArgs(args, env);
        long mediaCost;
        if (damageAndCost != null) {
            cachedResult.set(damageAndCost); // 缓存结果供 execute 使用
            mediaCost = damageAndCost.cost;
        } else {
            mediaCost = BASE_MEDIA_COST;
        }
        
        // 先检查媒体是否足够（simulate=true），如果不够则提前返回
        // 这样可以避免在 execute() 中造成伤害后再发现媒体不足
        long remainingAfterSimulate = env.extractMedia(mediaCost, true);
        if (remainingAfterSimulate > 0) {
            cachedResult.remove(); // 清理缓存
            throw new MishapNotEnoughMedia(mediaCost);
        }
        
        // 检查是否需要取消（血量扣除超过最大生命值的情况）
        // 注意：这个检查需要在 extractMedia(simulate=true) 之后，因为取消标记是在 extractMedia 中设置的
        if (SpellCancellationContext.shouldCancel()) {
            com.dcore.DCore.LOGGER.info("[OpNegativeEffectDamageEntity] operate: 检测到取消标记，跳过 execute() 调用");
            cachedResult.remove(); // 清理缓存
            
            // 实际消耗媒体（血量已在 extractMedia 中扣除）
            env.extractMedia(mediaCost, false);
            
            // 返回一个空结果，不执行任何效果
            List<OperatorSideEffect> sideEffects = new ArrayList<>();
            sideEffects.add(new OperatorSideEffect.ConsumeMedia(mediaCost));
            
            CastingImage image2 = image.copy(
                stack,
                image.getParenCount(),
                image.getParenthesized(),
                image.getEscapeNext(),
                image.getOpsConsumed() + 1,
                image.getUserData()
            );
            return new OperationResult(image2, sideEffects, continuation, HexEvalSounds.NOTHING);
        }
        
        ConstMediaAction.CostMediaActionResult result = executeWithOpCount(args, env);
        stack.addAll(result.getResultStack());
        
        // 实际消耗媒体
        env.extractMedia(mediaCost, false);
        
        List<OperatorSideEffect> sideEffects = new ArrayList<>();
        sideEffects.add(new OperatorSideEffect.ConsumeMedia(mediaCost));
        
        CastingImage image2 = image.copy(
            stack,
            image.getParenCount(),
            image.getParenthesized(),
            image.getEscapeNext(),
            image.getOpsConsumed() + result.getOpCount(),
            image.getUserData()
        );
        return new OperationResult(image2, sideEffects, continuation, HexEvalSounds.NORMAL_EXECUTE);
    }
    
    /**
     * 根据参数计算伤害和消耗，用于 operate 方法
     */
    private DamageAndCost calculateDamageAndCostForArgs(List<? extends Iota> args, CastingEnvironment env) {
        if (args.size() < 1) {
            return null;
        }
        
        Iota entityIota = args.get(0);
        if (!(entityIota instanceof EntityIota)) {
            return null;
        }
        
        Entity target = ((EntityIota) entityIota).getEntity();
        if (!(target instanceof LivingEntity livingTarget)) {
            return null;
        }
        
        // 使用共享方法计算（同时计算伤害和消耗）
        return calculateDamageAndCost(livingTarget);
    }
}

