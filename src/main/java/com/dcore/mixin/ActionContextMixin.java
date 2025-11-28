package com.dcore.mixin;

import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import com.dcore.media.ActionContext;
import com.dcore.DCore;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Mixin 到 PatternIota 的动作执行处，用于追踪当前动作 ID
 * 在动作执行前设置动作上下文，执行后清除
 */
@Mixin(PatternIota.class)
public class ActionContextMixin {
    
    /**
     * 在 precheckAction 之后设置当前动作 ID
     * 此时 lookup 已经确定，可以从 lookup 提取动作 ID
     */
    @Inject(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;precheckAction(Lat/petrak/hexcasting/api/casting/PatternShapeMatch;)V",
            remap = false,
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onAfterPrecheck(
        at.petrak.hexcasting.api.casting.eval.vm.CastingVM vm,
        net.minecraft.server.level.ServerLevel world,
        at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation continuation,
        CallbackInfoReturnable<?> cir,
        java.util.function.Supplier<?> castedName,
        PatternShapeMatch lookup
    ) {
        ResourceLocation actionId = extractActionId(lookup);
        if (actionId != null) {
            ActionContext.setCurrentAction(actionId);
        }
    }
    
    /**
     * 在方法返回时清除（延迟清除，给 extractMedia 更多时间）
     * 注意：不在 execute 方法中直接清除，而是在 TypedMediaExtractor 中处理完媒体后再清除
     * 这里只作为备用清除，防止 ActionContext 泄漏
     */
    @Inject(
        method = "execute",
        at = @At("RETURN")
    )
    private void onAfterActionExecute(CallbackInfoReturnable<?> cir) {
        // 在 PatternIota.execute() 返回时，清除取消标记，确保不影响下一个图案的执行
        // 这样即使当前图案被取消，也不会影响后续图案的执行
        com.dcore.media.SpellCancellationContext.clear();
        com.dcore.DCore.LOGGER.info("[ActionContextMixin] PatternIota.execute() 返回，清除取消标记");
    }
    
    /**
     * 从 PatternShapeMatch 提取动作 ID
     */
    private ResourceLocation extractActionId(PatternShapeMatch lookup) {
        if (lookup instanceof PatternShapeMatch.Normal normal) {
            return normal.key.location();
        } else if (lookup instanceof PatternShapeMatch.PerWorld perWorld) {
            return perWorld.key.location();
        } else if (lookup instanceof PatternShapeMatch.Special special) {
            // Special handler 使用其 key
            return special.key.location();
        }
        return null;
    }
}

