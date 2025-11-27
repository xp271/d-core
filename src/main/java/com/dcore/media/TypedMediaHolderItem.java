package com.dcore.media;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import net.minecraft.world.item.ItemStack;

/**
 * 支持媒体类型的物品接口
 * 类似于 MediaHolderItem，但支持类型
 */
public interface TypedMediaHolderItem {
    /**
     * 获取此物品堆栈的媒体类型
     */
    MediaType getMediaType(ItemStack stack);
    
    /**
     * 获取当前媒体量
     */
    long getMedia(ItemStack stack);

    /**
     * 获取最大媒体量
     */
    long getMaxMedia(ItemStack stack);

    /**
     * 设置媒体量
     */
    void setMedia(ItemStack stack, long media);

    /**
     * 是否可以提供媒体
     */
    boolean canProvideMedia(ItemStack stack);

    /**
     * 是否可以充入媒体
     */
    boolean canRecharge(ItemStack stack);

    /**
     * 获取消费优先级
     */
    default int getConsumptionPriority(ItemStack stack) {
        return ADMediaHolder.BATTERY_PRIORITY;
    }

    /**
     * 提取媒体
     */
    default long withdrawMedia(ItemStack stack, long cost, boolean simulate) {
        var mediaHere = getMedia(stack);
        if (cost < 0) {
            cost = mediaHere;
        }
        if (!simulate) {
            var mediaLeft = mediaHere - cost;
            setMedia(stack, mediaLeft);
        }
        return Math.min(cost, mediaHere);
    }

    /**
     * 插入媒体（会检查类型兼容性）
     */
    default long insertMedia(ItemStack stack, long amount, boolean simulate) {
        var mediaHere = getMedia(stack);
        long emptySpace = getMaxMedia(stack) - mediaHere;
        if (emptySpace <= 0) {
            return 0;
        }
        if (amount < 0) {
            amount = emptySpace;
        }

        long inserting = Math.min(amount, emptySpace);

        if (!simulate) {
            var newMedia = mediaHere + inserting;
            setMedia(stack, newMedia);
        }
        return inserting;
    }
    
    /**
     * 获取媒体填充度
     */
    default float getMediaFullness(ItemStack stack) {
        long max = getMaxMedia(stack);
        if (max == 0) {
            return 0;
        }
        return (float) getMedia(stack) / (float) max;
    }
}

