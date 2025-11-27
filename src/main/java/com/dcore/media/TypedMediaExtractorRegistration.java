package com.dcore.media;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.dcore.DCore;
import net.minecraft.nbt.CompoundTag;

import java.util.function.BiConsumer;

/**
 * 注册类型化媒体提取器到 CastingEnvironment
 * 使用 CastingEnvironment.addCreateEventListener 在所有环境创建时添加我们的组件
 */
public class TypedMediaExtractorRegistration {
    
    private static boolean registered = false;
    
    /**
     * 注册类型化媒体提取器
     * 应该在模组初始化时调用
     */
    public static void register() {
        if (registered) {
            return;
        }
        
        CastingEnvironment.addCreateEventListener(new EnvironmentCreator());
        registered = true;
    }
    
    /**
     * 环境创建监听器
     * 在每个 CastingEnvironment 创建时，添加我们的 TypedMediaExtractor 组件
     */
    private static class EnvironmentCreator implements BiConsumer<CastingEnvironment, CompoundTag> {
        @Override
        public void accept(CastingEnvironment environment, CompoundTag userData) {
            if (environment.getExtension(TypedMediaExtractor.KEY) != null) {
                return;
            }
            
            TypedMediaExtractor extractor = new TypedMediaExtractor(environment);
            environment.addExtension(extractor);
            
            TypedMediaExtractorPost extractorPost = new TypedMediaExtractorPost(environment);
            environment.addExtension(extractorPost);
        }
    }
}

