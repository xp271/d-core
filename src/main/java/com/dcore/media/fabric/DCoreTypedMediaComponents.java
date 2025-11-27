package com.dcore.media.fabric;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.fabric.cc.HexCardinalComponents;
import com.dcore.DCore;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.item.ItemComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.item.ItemComponentInitializer;
import net.minecraft.resources.ResourceLocation;

/**
 * 类型化媒体容器的 Cardinal Components 注册
 * 用于注册支持媒体类型的容器
 * 
 * 注意：类型化容器也注册到 HexMod 的 MEDIA_HOLDER，
 * 这样原有的查找逻辑也能找到，但会在 TypedMediaExtractor 中进行类型检查
 */
public class DCoreTypedMediaComponents implements ItemComponentInitializer {
    
    /**
     * 类型化媒体容器 Component Key
     * 延迟初始化，在 registerItemComponentFactories 中创建
     * 因为 Cardinal Components 需要在 entrypoint 加载后才能创建 ComponentKey
     */
    private static ComponentKey<TypedCCMediaHolder> TYPED_MEDIA_HOLDER;
    
    @Override
    public void registerItemComponentFactories(ItemComponentFactoryRegistry registry) {
        if (TYPED_MEDIA_HOLDER == null) {
            TYPED_MEDIA_HOLDER = ComponentRegistry.getOrCreate(
                new ResourceLocation(com.dcore.DCore.MOD_ID, "typed_media_holder"), 
                TypedCCMediaHolder.class
            );
        }
        
        registry.register(
            i -> i instanceof com.dcore.media.TypedMediaHolderItem,
            HexCardinalComponents.MEDIA_HOLDER,
            stack -> {
                com.dcore.media.TypedMediaHolderItem item = 
                    (com.dcore.media.TypedMediaHolderItem) stack.getItem();
                return new TypedCCMediaHolder.ItemBased(item, stack);
            }
        );
        
        if (TYPED_MEDIA_HOLDER != null) {
            registry.register(
                i -> i instanceof com.dcore.media.TypedMediaHolderItem,
                TYPED_MEDIA_HOLDER,
                stack -> {
                    com.dcore.media.TypedMediaHolderItem item = 
                        (com.dcore.media.TypedMediaHolderItem) stack.getItem();
                    return new TypedCCMediaHolder.ItemBased(item, stack);
                }
            );
        }
    }
}

