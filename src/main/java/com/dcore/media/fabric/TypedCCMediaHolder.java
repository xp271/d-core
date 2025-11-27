package com.dcore.media.fabric;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents;
import at.petrak.hexcasting.fabric.cc.adimpl.CCMediaHolder;
import com.dcore.media.MediaType;
import com.dcore.media.TypedMediaHolder;
import com.dcore.media.TypedMediaHolderItem;
import dev.onyxstudios.cca.api.v3.item.ItemComponent;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Fabric 平台上的类型化媒体容器实现
 * 继承 CCMediaHolder 以获得所有基础功能，同时实现 TypedMediaHolder 接口
 * 使用 HexMod 的 MEDIA_HOLDER ComponentKey，这样原有的查找逻辑也能找到
 */
public abstract class TypedCCMediaHolder extends CCMediaHolder implements TypedMediaHolder {
    public TypedCCMediaHolder(ItemStack stack) {
        super(stack);
    }

    /**
     * 基于物品的类型化媒体容器（用于实现 MediaHolderItem 的物品）
     */
    public static class ItemBased extends TypedCCMediaHolder {
        private final TypedMediaHolderItem mediaHolder;

        public ItemBased(TypedMediaHolderItem mediaHolder, ItemStack stack) {
            super(stack);
            this.mediaHolder = mediaHolder;
        }

        @Override
        public MediaType getMediaType() {
            return this.mediaHolder.getMediaType(this.stack);
        }

        @Override
        public long getMedia() {
            return this.mediaHolder.getMedia(this.stack);
        }

        @Override
        public long getMaxMedia() {
            return this.mediaHolder.getMaxMedia(this.stack);
        }

        @Override
        public void setMedia(long media) {
            this.mediaHolder.setMedia(this.stack, media);
        }

        @Override
        public boolean canRecharge() {
            return this.mediaHolder.canRecharge(this.stack);
        }

        @Override
        public boolean canProvide() {
            return this.mediaHolder.canProvideMedia(this.stack);
        }

        @Override
        public int getConsumptionPriority() {
            return this.mediaHolder.getConsumptionPriority(this.stack);
        }

        @Override
        public boolean canConstructBattery() {
            return false;
        }

        @Override
        public long withdrawMedia(long cost, boolean simulate) {
            return this.mediaHolder.withdrawMedia(this.stack, cost, simulate);
        }

        @Override
        public long insertMedia(long amount, boolean simulate) {
            // 检查类型兼容性
            MediaType stackType = this.mediaHolder.getMediaType(this.stack);
            // 这里我们需要知道要插入的媒体类型，但 ADMediaHolder 接口没有类型信息
            // 所以我们需要通过其他方式验证，或者信任调用者
            return this.mediaHolder.insertMedia(this.stack, amount, simulate);
        }
    }

    /**
     * 静态类型化媒体容器（用于固定提供媒体量的消耗品）
     */
    public static class Static extends TypedCCMediaHolder {
        private final Supplier<Long> baseWorth;
        private final int consumptionPriority;
        private final MediaType mediaType;

        public Static(Supplier<Long> baseWorth, int consumptionPriority, MediaType mediaType, ItemStack stack) {
            super(stack);
            this.baseWorth = baseWorth;
            this.consumptionPriority = consumptionPriority;
            this.mediaType = mediaType;
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public long getMedia() {
            return baseWorth.get() * stack.getCount();
        }

        @Override
        public long getMaxMedia() {
            return getMedia();
        }

        @Override
        public void setMedia(long media) {
            // NO-OP - 静态媒体不能修改
        }

        @Override
        public boolean canRecharge() {
            return false;
        }

        @Override
        public boolean canProvide() {
            return true;
        }

        @Override
        public int getConsumptionPriority() {
            return consumptionPriority;
        }

        @Override
        public boolean canConstructBattery() {
            return true;
        }

        @Override
        public long withdrawMedia(long cost, boolean simulate) {
            long worth = baseWorth.get();
            if (cost < 0) {
                cost = worth * stack.getCount();
            }
            double itemsRequired = cost / (double) worth;
            int itemsUsed = Math.min((int) Math.ceil(itemsRequired), stack.getCount());
            if (!simulate) {
                stack.shrink(itemsUsed);
            }
            return itemsUsed * worth;
        }
    }
}

