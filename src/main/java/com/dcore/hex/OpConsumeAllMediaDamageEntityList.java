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
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.dcore.media.MediaType;
import com.dcore.media.MediaTypeRegistry;
import com.dcore.media.TypedMediaHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消耗所有媒体伤害实体列表
 * 输入：实体列表
 * 消耗：背包中所有可用的媒体
 * 伤害：根据每种媒体类型的血量扣除比例计算总伤害，平均分配给所有实体
 */
public class OpConsumeAllMediaDamageEntityList implements ConstMediaAction {
    public static final OpConsumeAllMediaDamageEntityList INSTANCE = new OpConsumeAllMediaDamageEntityList();
    
    @Override
    public int getArgc() {
        return 1; // 只需要实体列表参数
    }
    
    @Override
    public long getMediaCost() {
        return 0; // 消耗所有媒体，不在这里计算
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
        
        // 获取实体列表
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
        
        // 获取并消耗所有媒体，计算总伤害
        float totalDamage = consumeAllMediaAndCalculateDamage(env);
        
        if (totalDamage > 0) {
            // 伤害除以10，然后平均分配给所有实体
            float damagePerEntity = (totalDamage / 10.0f) / entities.size();
            
            // 对列表中的每个实体造成伤害
            Entity firstEntity = entities.get(0);
            DamageSources damageSources = firstEntity.damageSources();
            DamageSource damageSource = damageSources.source(
                ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()),
                env.getCastingEntity()
            );
            
            for (Entity target : entities) {
                target.hurt(damageSource, damagePerEntity);
                
                // 产生粒子效果
                double eyeHeight = target.getEyeHeight();
                env.produceParticles(
                    ParticleSpray.burst(target.position().add(0.0, eyeHeight / 2.0, 0.0), 0.5, 20),
                    env.getPigment()
                );
            }
        }
        
        return List.of();
    }
    
    /**
     * 消耗所有媒体并计算总伤害
     * @param env 施法环境
     * @return 总伤害值
     */
    private float consumeAllMediaAndCalculateDamage(CastingEnvironment env) {
        if (!(env.getCastingEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        Inventory inventory = player.getInventory();
        Map<MediaType, Long> mediaByType = new HashMap<>();
        
        // 遍历背包，收集所有媒体
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            
            ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
            if (holder == null) continue;
            
            MediaType mediaType = MediaType.STANDARD;
            if (holder instanceof TypedMediaHolder typedHolder) {
                mediaType = typedHolder.getMediaType();
            }
            
            long mediaAmount = holder.getMedia();
            if (mediaAmount > 0) {
                mediaByType.put(mediaType, mediaByType.getOrDefault(mediaType, 0L) + mediaAmount);
            }
        }
        
        // 消耗所有媒体并计算总伤害
        long totalMediaConsumed = 0;
        float totalDamage = 0;
        
        for (Map.Entry<MediaType, Long> entry : mediaByType.entrySet()) {
            MediaType mediaType = entry.getKey();
            long mediaAmount = entry.getValue();
            
            // 消耗媒体
            long consumed = consumeMediaOfType(inventory, mediaType, mediaAmount);
            totalMediaConsumed += consumed;
            
            // 根据该媒体类型的血量扣除比例计算伤害
            double healthRate = MediaTypeRegistry.getMediaTypeHealthRate(mediaType);
            totalDamage += (float) (consumed / healthRate);
        }
        
        return totalDamage;
    }
    
    /**
     * 消耗指定类型的所有媒体
     */
    private long consumeMediaOfType(Inventory inventory, MediaType mediaType, long maxAmount) {
        long totalConsumed = 0;
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (totalConsumed >= maxAmount) {
                break;
            }
            
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            
            ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
            if (holder == null) continue;
            
            MediaType holderType = MediaType.STANDARD;
            if (holder instanceof TypedMediaHolder typedHolder) {
                holderType = typedHolder.getMediaType();
            }
            
            if (holderType == mediaType) {
                long remaining = maxAmount - totalConsumed;
                long consumed = holder.withdrawMedia(remaining, false);
                totalConsumed += consumed;
            }
        }
        
        return totalConsumed;
    }
    
    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        List<Iota> stack = new ArrayList<>(image.getStack());
        
        if (getArgc() > stack.size()) {
            throw new MishapNotEnoughArgs(getArgc(), stack.size());
        }
        List<Iota> args = new ArrayList<>(stack.subList(stack.size() - getArgc(), stack.size()));
        stack.subList(stack.size() - getArgc(), stack.size()).clear();
        
        ConstMediaAction.CostMediaActionResult result = executeWithOpCount(args, env);
        stack.addAll(result.getResultStack());
        
        // 计算实际消耗的媒体量（用于显示）
        long totalMediaConsumed = calculateTotalMediaConsumed(env);
        
        List<OperatorSideEffect> sideEffects = new ArrayList<>();
        if (totalMediaConsumed > 0) {
            sideEffects.add(new OperatorSideEffect.ConsumeMedia(totalMediaConsumed));
        }
        
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
     * 计算总媒体消耗量（用于显示）
     */
    private long calculateTotalMediaConsumed(CastingEnvironment env) {
        if (!(env.getCastingEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        
        Inventory inventory = player.getInventory();
        long total = 0;
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            
            ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
            if (holder == null) continue;
            
            total += holder.getMedia();
        }
        
        return total;
    }
}

