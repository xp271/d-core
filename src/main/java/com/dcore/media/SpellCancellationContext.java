package com.dcore.media;

/**
 * 法术取消上下文
 * 用于存储需要取消法术执行的标记和媒体消耗量
 */
public class SpellCancellationContext {
    /**
     * 使用 ThreadLocal 存储当前线程是否需要取消法术执行
     */
    private static final ThreadLocal<Boolean> shouldCancelSpell = new ThreadLocal<>();
    
    /**
     * 使用 ThreadLocal 存储需要消耗的媒体量（用于创建 ConsumeMedia 副作用）
     */
    private static final ThreadLocal<Long> mediaCost = new ThreadLocal<>();
    
    /**
     * 使用 ThreadLocal 标记是否已经发送过取消消息（避免重复发送）
     */
    private static final ThreadLocal<Boolean> messageSent = new ThreadLocal<>();
    
    /**
     * 标记当前线程的法术应该被取消，并记录媒体消耗量
     */
    public static void markForCancellation(long mediaAmount) {
        shouldCancelSpell.set(true);
        mediaCost.set(mediaAmount);
    }
    
    /**
     * 标记当前线程的法术应该被取消（不记录媒体消耗量）
     */
    public static void markForCancellation() {
        shouldCancelSpell.set(true);
    }
    
    /**
     * 检查当前线程的法术是否应该被取消
     */
    public static boolean shouldCancel() {
        Boolean cancel = shouldCancelSpell.get();
        return cancel != null && cancel;
    }
    
    /**
     * 获取记录的媒体消耗量
     */
    public static long getMediaCost() {
        Long cost = mediaCost.get();
        return cost != null ? cost : 0L;
    }
    
    /**
     * 标记已经发送过取消消息
     */
    public static void markMessageSent() {
        messageSent.set(true);
    }
    
    /**
     * 检查是否已经发送过取消消息
     */
    public static boolean isMessageSent() {
        Boolean sent = messageSent.get();
        return sent != null && sent;
    }
    
    /**
     * 清除取消标记和媒体消耗量
     */
    public static void clear() {
        shouldCancelSpell.remove();
        mediaCost.remove();
        messageSent.remove();
    }
}

