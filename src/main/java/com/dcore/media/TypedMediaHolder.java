package com.dcore.media;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;

/**
 * 支持媒体类型的媒体容器接口
 * 扩展了 ADMediaHolder，增加了媒体类型支持
 */
public interface TypedMediaHolder extends ADMediaHolder {
    
    /**
     * 获取此容器存储的媒体类型
     * @return 媒体类型
     */
    MediaType getMediaType();
    
    /**
     * 检查此容器是否可以存储指定类型的媒体
     * @param type 要存储的媒体类型
     * @return 是否可以存储
     */
    default boolean canStoreMediaType(MediaType type) {
        return getMediaType() == type;
    }
    
    /**
     * 检查此容器是否可以提供指定类型的媒体
     * @param type 需要的媒体类型
     * @return 是否可以提供
     */
    default boolean canProvideMediaType(MediaType type) {
        return getMediaType() == type && canProvide();
    }
}

