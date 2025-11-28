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
import com.dcore.DCore;
import com.dcore.media.SpellCancellationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class OpDamageEntity implements ConstMediaAction {
    public static final OpDamageEntity INSTANCE = new OpDamageEntity();
    
    private static final float BASE_DAMAGE = 100.0f;
    private static final long BASE_MEDIA_COST = MediaConstants.DUST_UNIT * 2; // 基础消耗：2 个粉尘单位
    
    @Override
    public int getArgc() {
        return 2; // 需要 2 个参数：实体和强度
    }
    
    @Override
    public long getMediaCost() {
        // 返回基础消耗，实际消耗会在 operate 中根据强度动态计算
        return BASE_MEDIA_COST;
    }
    
    @Override
    public ConstMediaAction.CostMediaActionResult executeWithOpCount(List<? extends Iota> args, CastingEnvironment env) {
        List<Iota> resultStack = execute(args, env);
        return new ConstMediaAction.CostMediaActionResult(resultStack, 1);
    }
    
    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        if (args.size() < 2) {
            throw new MishapNotEnoughArgs(2, args.size());
        }
        
        // 获取强度（第二个参数，栈顶）
        Iota strengthIota = args.get(1);
        if (!(strengthIota instanceof DoubleIota)) {
            throw MishapInvalidIota.ofType(strengthIota, 1, "number");
        }
        double strength = ((DoubleIota) strengthIota).getDouble();
        if (strength <= 0) {
            throw MishapInvalidIota.ofType(strengthIota, 1, "positive number");
        }
        
        // 获取实体（第一个参数）
        Iota entityIota = args.get(0);
        if (!(entityIota instanceof EntityIota)) {
            throw MishapInvalidIota.ofType(entityIota, 0, "entity");
        }
        Entity target = ((EntityIota) entityIota).getEntity();
        env.assertEntityInRange(target);
        
        // 计算实际伤害：基础伤害 × 强度
        float actualDamage = (float) (BASE_DAMAGE * strength);
        
        // 在造成伤害之前检查是否需要取消
        if (com.dcore.media.SpellCancellationContext.shouldCancel()) {
            com.dcore.DCore.LOGGER.info("[OpDamageEntity] execute: 检测到取消标记，阻止造成伤害，actualDamage={}", actualDamage);
            return List.of(); // 直接返回，不造成伤害
        }
        
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] execute: 准备造成伤害，actualDamage={}", actualDamage);
        
        // 对实体造成伤害
        DamageSources damageSources = target.damageSources();
        DamageSource damageSource = damageSources.source(
            ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
            env.getCastingEntity()
        );
        
        target.hurt(damageSource, actualDamage);
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] execute: 伤害已造成");
        
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
        
        // ========== 第一步：提前计算媒体消耗 ==========
        // 获取强度参数（用于计算实际媒体消耗）
        double strength = 1.0;
        if (args.size() >= 2 && args.get(1) instanceof DoubleIota) {
            strength = ((DoubleIota) args.get(1)).getDouble();
            if (strength <= 0) {
                strength = 1.0; // 默认强度为 1
            }
        }
        
        // 计算动态消耗：基础消耗 × 强度的平方
        long actualMediaCost = (long) (BASE_MEDIA_COST * strength * strength);
        
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 第一步 - 计算媒体消耗完成，actualMediaCost={}", actualMediaCost);
        
        // ========== 第二步：提前检查媒体（在 execute() 之前） ==========
        // 先模拟检查媒体是否足够
        long remainingAfterSimulate = env.extractMedia(actualMediaCost, true);
        
        // 检查是否在 simulate 阶段设置了取消标记（即使返回 0 也可能设置了取消标记）
        boolean shouldCancelBeforeExecute = com.dcore.media.SpellCancellationContext.shouldCancel();
        long mediaCostBeforeExecute = com.dcore.media.SpellCancellationContext.getMediaCost();
        
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 第二步 - simulate 检查完成，remainingAfterSimulate={}, shouldCancel={}, mediaCost={}", 
            remainingAfterSimulate, shouldCancelBeforeExecute, mediaCostBeforeExecute);
        
        if (remainingAfterSimulate > 0 || shouldCancelBeforeExecute) {
            // 媒体不足或已设置取消标记，需要进行血量扣除
            if (remainingAfterSimulate > 0) {
                com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 媒体不足，剩余={}，准备检查血量扣除", remainingAfterSimulate);
            } else {
                com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 检测到取消标记（simulate阶段设置），准备检查血量扣除");
            }
            
            // 真实执行媒体提取（这会扣除血量并可能设置取消标记）
            env.extractMedia(actualMediaCost, false);
            
            // 再次检查是否设置了取消标记（血量不足）
            if (com.dcore.media.SpellCancellationContext.shouldCancel()) {
                com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 第三步 - 检测到取消标记（血量不足），阻止 execute() 调用");
                
                // 注意：这里不清除取消标记，因为取消标记会在 PatternIota.execute() 返回时清除
                // 这样可以确保当前图案的取消不影响其他图案的执行
                
                // 恢复栈状态：参数没有被消耗，需要放回去
                // 注意：这里不修改 stack，因为参数已经被弹出，我们需要保持栈的状态
                // 实际上，HexMod 的机制是：如果返回 OperationResult，栈会被替换为新的栈
                // 所以我们需要保持原栈（参数已被弹出），这样下次执行时栈就是正确的
                
                // 直接返回，不执行 execute()，但保留媒体消耗副作用
                // 栈保持当前状态（参数已弹出），这样不会影响后续执行
                List<Iota> emptyStack = new ArrayList<>(); // 空栈，因为参数已被弹出且 execute() 没执行
                ConstMediaAction.CostMediaActionResult emptyResult = new ConstMediaAction.CostMediaActionResult(emptyStack, 1);
                
                List<OperatorSideEffect> sideEffects = new ArrayList<>();
                sideEffects.add(new OperatorSideEffect.ConsumeMedia(actualMediaCost));
                
                CastingImage image2 = image.copy(
                    stack, // 使用当前栈（参数已弹出）
                    image.getParenCount(),
                    image.getParenthesized(),
                    image.getEscapeNext(),
                    image.getOpsConsumed() + emptyResult.getOpCount(),
                    image.getUserData()
                );
                
                com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 已返回空结果，execute() 不会被调用，栈状态已恢复");
                // 注意：取消标记已经在上面清除了，这里不需要再次清除
                return new OperationResult(image2, sideEffects, continuation, HexEvalSounds.NORMAL_EXECUTE);
            }
            
            // 如果血量足够，但媒体还是不足，抛出异常
            if (remainingAfterSimulate > 0) {
                throw new MishapNotEnoughMedia(actualMediaCost);
            }
        }
        
        // ========== 第三步：媒体检查通过，执行法术效果 ==========
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 第二步 - 媒体检查通过，开始执行 execute()");
        
        // 现在才执行 execute()，此时媒体已经检查完成，取消标记也已设置（如果需要）
        ConstMediaAction.CostMediaActionResult result = executeWithOpCount(args, env);
        stack.addAll(result.getResultStack());
        
        // ========== 第四步：创建副作用 ==========
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
        
        // 确保在正常执行完成后清除取消标记（如果有残留）
        if (com.dcore.media.SpellCancellationContext.shouldCancel()) {
            com.dcore.DCore.LOGGER.warn("[OpDamageEntity] operate: 正常执行完成，但检测到残留的取消标记，清除它");
            com.dcore.media.SpellCancellationContext.clear();
        }
        
        com.dcore.DCore.LOGGER.info("[OpDamageEntity] operate: 完成，返回结果");
        return new OperationResult(image2, sideEffects, continuation, HexEvalSounds.NORMAL_EXECUTE);
    }
}

