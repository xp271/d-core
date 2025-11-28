package com.dcore.hex;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据实体当前血量和速度造成伤害
 * 输入：实体
 * 伤害 = 当前血量 × 速度
 */
public class OpVelocityDamageEntity implements ConstMediaAction {
    public static final OpVelocityDamageEntity INSTANCE = new OpVelocityDamageEntity();
    
    private static final long BASE_MEDIA_COST = MediaConstants.DUST_UNIT * 3; // 基础消耗：3 个粉尘单位
    
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
            // 如果不是生物实体，无法获取血量，直接返回
            return List.of();
        }
        
        // 获取实体当前血量和最大血量
        float currentHealth = livingTarget.getHealth();
        float maxHealth = livingTarget.getMaxHealth();
        
        // 获取实体速度向量并计算速度大小
        Vec3 velocity = target.getDeltaMovement();
        double speed = velocity.length(); // 速度的大小（m/s）
        
        // 伤害 = 血量 × (速度 / 10)
        double damageValue = currentHealth * (speed / 10.0);
        
        // 伤害上限：最多为血量上限的10倍
        double maxDamage = maxHealth * 10.0;
        damageValue = Math.min(damageValue, maxDamage);
        
        float actualDamage = (float) Math.max(0, damageValue);
        
        // 对实体造成伤害
        DamageSources damageSources = target.damageSources();
        DamageSource damageSource = damageSources.source(
            ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
            env.getCastingEntity()
        );
        
        livingTarget.hurt(damageSource, actualDamage);
        
        // 产生粒子效果
        double eyeHeight = target.getEyeHeight();
        env.produceParticles(
            ParticleSpray.burst(target.position().add(0.0, eyeHeight / 2.0, 0.0), 0.5, 20),
            env.getPigment()
        );
        
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
        
        // 基础消耗
        long actualMediaCost = BASE_MEDIA_COST;
        
        ConstMediaAction.CostMediaActionResult result = executeWithOpCount(args, env);
        stack.addAll(result.getResultStack());
        
        // 检查并消耗媒体
        if (env.extractMedia(actualMediaCost, true) > 0) {
            throw new MishapNotEnoughMedia(actualMediaCost);
        }
        
        List<OperatorSideEffect> sideEffects = new ArrayList<>();
        sideEffects.add(new OperatorSideEffect.ConsumeMedia(actualMediaCost));
        
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
}

