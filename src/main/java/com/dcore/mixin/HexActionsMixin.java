package com.dcore.mixin;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 使用 Mixin 修改 HexActions 的图案定义
 * - READ (Scribe's Reflection): "aqqqqq" + EAST → "ed" + NORTH_EAST
 * - WRITE (Scribe's Gambit): "deeeee" + EAST → "aq" + SOUTH_EAST
 * - EVAL (Hermes' Gambit): "deaqq" + SOUTH_EAST → "aq" + EAST
 */
@Mixin(targets = "at.petrak.hexcasting.common.lib.hex.HexActions")
public class HexActionsMixin {
    
    /**
     * 重定向 HexPattern.fromAngles 的调用
     * 修改 READ、WRITE 和 EVAL 的图案定义
     */
    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/math/HexPattern;fromAngles(Ljava/lang/String;Lat/petrak/hexcasting/api/casting/math/HexDir;)Lat/petrak/hexcasting/api/casting/math/HexPattern;",
            remap = false
        )
    )
    private static HexPattern redirectPattern(String angles, HexDir startDir) {
        // 修改 READ (Scribe's Reflection): "aqqqqq" + EAST → "ed" + NORTH_EAST
        if ("aqqqqq".equals(angles) && startDir == HexDir.EAST) {
            return HexPattern.fromAngles("ed", HexDir.NORTH_EAST);
        }
        
        // 修改 WRITE (Scribe's Gambit): "deeeee" + EAST → "aq" + SOUTH_EAST
        if ("deeeee".equals(angles) && startDir == HexDir.EAST) {
            return HexPattern.fromAngles("aq", HexDir.SOUTH_WEST);
        }
        
        // 修改 EVAL (Hermes' Gambit): "deaqq" + SOUTH_EAST → "aq" + EAST
        if ("deaqq".equals(angles) && startDir == HexDir.SOUTH_EAST) {
            return HexPattern.fromAngles("qa", HexDir.EAST);
        }
        
        // 其他调用保持原样
        return HexPattern.fromAngles(angles, startDir);
    }
}
