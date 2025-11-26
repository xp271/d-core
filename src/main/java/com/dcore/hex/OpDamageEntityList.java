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
import at.petrak.hexcasting.api.casting.iota.ListIota;
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

import java.util.ArrayList;
import java.util.List;

public class OpDamageEntityList implements ConstMediaAction {
    public static final OpDamageEntityList INSTANCE = new OpDamageEntityList();
    
    private static final float BASE_DAMAGE = 100.0f;
    private static final long BASE_MEDIA_COST = MediaConstants.DUST_UNIT * 2; // 单体消耗：2 个粉尘单位
    
    @Override
    public int getArgc() {
        return 2; // 需要 2 个参数：实体列表和强度
    }
    
    @Override
    public long getMediaCost() {
        // 返回基础消耗，实际消耗会在 operate 中动态计算
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
        
        // 获取实体列表（第一个参数）
        Iota listIota = args.get(0);
        if (!(listIota instanceof ListIota)) {
            throw MishapInvalidIota.ofType(listIota, 0, "list");
        }
        
        List<Entity> entities = new ArrayList<>();
        
        // 遍历列表，提取所有实体
        for (Iota item : ((ListIota) listIota).getList()) {
            if (!(item instanceof EntityIota)) {
                throw MishapInvalidIota.ofType(item, 0, "entity");
            }
            Entity entity = ((EntityIota) item).getEntity();
            env.assertEntityInRange(entity);
            entities.add(entity);
        }
        
        if (entities.isEmpty()) {
            return List.of();
        }
        
        // 计算实际伤害：基础伤害 × 强度
        float actualDamage = (float) (BASE_DAMAGE * strength);
        
        // 对列表中的每个实体造成伤害
        Entity firstEntity = entities.get(0);
        DamageSources damageSources = firstEntity.damageSources();
        DamageSource damageSource = damageSources.source(
            ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
            env.getCastingEntity()
        );
        
        for (Entity target : entities) {
            target.hurt(damageSource, actualDamage);
            
            // 产生粒子效果
            double eyeHeight = target.getEyeHeight();
            env.produceParticles(
                ParticleSpray.burst(target.position().add(0.0, eyeHeight / 2.0, 0.0), 0.5, 20),
                env.getPigment()
            );
        }
        
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
        
        // 在计算消耗之前，先检查参数类型并计算实体数量和强度
        int entityCount = 0;
        double strength = 1.0;
        
        if (args.size() >= 2 && args.get(1) instanceof DoubleIota) {
            strength = ((DoubleIota) args.get(1)).getDouble();
            if (strength <= 0) {
                strength = 1.0; // 默认强度为 1
            }
        }
        
        if (args.size() > 0 && args.get(0) instanceof ListIota) {
            ListIota listIota = (ListIota) args.get(0);
            for (Iota item : listIota.getList()) {
                if (item instanceof EntityIota) {
                    entityCount++;
                }
            }
        }
        
        // 计算动态消耗：实体数量的平方 × 基础消耗 × 强度的平方
        long actualMediaCost = entityCount > 0 
            ? (long) (entityCount * entityCount * BASE_MEDIA_COST * strength * strength)
            : (long) (BASE_MEDIA_COST * strength * strength);
        
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

