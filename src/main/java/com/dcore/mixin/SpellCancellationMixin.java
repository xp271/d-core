package com.dcore.mixin;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.dcore.media.SpellCancellationContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin 到 PatternIota.execute 方法，用于在血量扣除超过最大生命值时取消法术执行
 * 直接拦截 action.operate() 调用，如果检测到需要取消，阻止执行并手动创建结果
 */
@Mixin(PatternIota.class)
public class SpellCancellationMixin {
    
    /**
     * 在 action.operate() 调用之后拦截
     * 如果检测到需要取消，修改返回结果，清空栈并发送消息
     */
    @Inject(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/castables/Action;operate(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;Lat/petrak/hexcasting/api/casting/eval/vm/CastingImage;Lat/petrak/hexcasting/api/casting/eval/vm/SpellContinuation;)Lat/petrak/hexcasting/api/casting/eval/OperationResult;",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void onAfterOperate(
        at.petrak.hexcasting.api.casting.eval.vm.CastingVM vm,
        net.minecraft.server.level.ServerLevel world,
        at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation continuation,
        CallbackInfoReturnable<CastResult> cir
    ) {
        // 在 operate() 调用之后检查是否需要取消法术执行（血量扣除超过最大生命值）
        boolean shouldCancel = SpellCancellationContext.shouldCancel();
        long mediaCost = SpellCancellationContext.getMediaCost();
        
        // 获取当前动作 ID 用于调试
        net.minecraft.resources.ResourceLocation currentActionId = com.dcore.media.ActionContext.getCurrentAction();
        com.dcore.DCore.LOGGER.info("[SpellCancellationMixin] onAfterOperate: shouldCancel={}, mediaCost={}, currentActionId={}, thread={}", 
            shouldCancel, mediaCost, currentActionId, Thread.currentThread().getId());
        
        if (shouldCancel && mediaCost > 0) {
            com.dcore.DCore.LOGGER.info("[SpellCancellationMixin] operate() 执行后检测到需要取消法术，修改返回结果");
            
            // 获取 operate() 的原始返回结果
            CastResult originalResult = cir.getReturnValue();
            if (originalResult == null) {
                com.dcore.DCore.LOGGER.warn("[SpellCancellationMixin] 原始结果为 null，无法修改");
                return;
            }
            
            // 获取原始的副作用列表，保留 ConsumeMedia 副作用（如果存在）
            List<OperatorSideEffect> sideEffects = new ArrayList<>(originalResult.getSideEffects());
            
            // 获取原始结果的新图像，如果为 null 则使用当前 vm 的图像
            at.petrak.hexcasting.api.casting.eval.vm.CastingImage originalImage = originalResult.getNewData();
            if (originalImage == null) {
                originalImage = vm.getImage();
            }
            
            // 创建一个空的 CastingImage，清空所有状态（栈、括号计数等）
            at.petrak.hexcasting.api.casting.eval.vm.CastingImage emptyImage = 
                originalImage.copy(
                    java.util.List.of(), // 空栈
                    0, // parenCount = 0
                    java.util.List.of(), // 空 parenthesized
                    false, // escapeNext = false
                    originalImage.getOpsConsumed(), // 保留 opsConsumed
                    new net.minecraft.nbt.CompoundTag() // 空的 userData
                );
            
            CastResult cancelledResult = new CastResult(
                originalResult.getCast(),
                originalResult.getContinuation(),
                emptyImage, // 设置为空的 CastingImage，清空所有状态
                sideEffects, // 保留原始副作用（包含 ConsumeMedia）
                at.petrak.hexcasting.api.casting.eval.ResolvedPatternType.ERRORED, // 设置为 ERRORED 以触发 earlyExit
                originalResult.getSound() // 保留原始音效
            );
            
            com.dcore.DCore.LOGGER.info("[SpellCancellationMixin] 已修改返回结果，清空栈并设置 ERRORED，副作用数量: {}", 
                sideEffects.size());
            
            cir.setReturnValue(cancelledResult);
            return; // 直接返回，不再执行后续代码
        } else if (shouldCancel && mediaCost == 0) {
            // 如果 mediaCost 为 0，清除标记但不取消执行（可能是异常情况）
            com.dcore.DCore.LOGGER.warn("[SpellCancellationMixin] 检测到取消标记但 mediaCost=0，清除标记并允许继续执行");
            SpellCancellationContext.clear();
        }
    }
}

