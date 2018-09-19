package com.mwee.android.alp;

/**
 * @Description: 回执实体
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
public abstract class Ack {

    /**
     * 超时时长, 默认 5 秒
     *
     * @return
     */
    public long timeout() {
        return 5 * 1000;
    }

    /**
     * 回调
     *
     * @param uniq   消息id
     * @param status 回执状态
     */
    public abstract void callback(String uniq, @AckStatus int status);
}
