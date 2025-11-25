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
    
    private static final float DAMAGE_AMOUNT = 10.0f;
    
    @Override
    public int getArgc() {
        return 1;
    }
    
    @Override
    public long getMediaCost() {
        return MediaConstants.DUST_UNIT * 2; // 消耗 2 个粉尘单位的媒体
    }
    
    @Override
    public ConstMediaAction.CostMediaActionResult executeWithOpCount(List<? extends Iota> args, CastingEnvironment env) {
        List<Iota> resultStack = execute(args, env);
        return new ConstMediaAction.CostMediaActionResult(resultStack, 1);
    }
    
    @Override
    public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
        Iota iota = args.size() > 0 ? args.get(0) : null;
        if (iota == null) {
            throw new MishapNotEnoughArgs(1, args.size());
        }
        if (!(iota instanceof EntityIota)) {
            throw MishapInvalidIota.ofType(iota, getArgc() - 1, "entity");
        }
        Entity target = ((EntityIota) iota).getEntity();
        env.assertEntityInRange(target);
        
        // 对实体造成 10 点伤害
        DamageSources damageSources = target.damageSources();
        DamageSource damageSource = damageSources.source(
            ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
            env.getCastingEntity()
        );
        
        target.hurt(damageSource, DAMAGE_AMOUNT);
        
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
        // ConstMediaAction 接口已经提供了 operate 的默认实现
        // 但由于是 Kotlin 接口，在 Java 中需要显式实现
        // 这里直接调用父接口的默认实现逻辑
        List<Iota> stack = new ArrayList<>(image.getStack());
        
        if (getArgc() > stack.size()) {
            throw new MishapNotEnoughArgs(getArgc(), stack.size());
        }
        List<Iota> args = new ArrayList<>(stack.subList(stack.size() - getArgc(), stack.size()));
        stack.subList(stack.size() - getArgc(), stack.size()).clear();
        
        ConstMediaAction.CostMediaActionResult result = executeWithOpCount(args, env);
        stack.addAll(result.getResultStack());
        
        if (env.extractMedia(getMediaCost(), true) > 0) {
            throw new MishapNotEnoughMedia(getMediaCost());
        }
        
        List<OperatorSideEffect> sideEffects = new ArrayList<>();
        sideEffects.add(new OperatorSideEffect.ConsumeMedia(getMediaCost()));
        
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

