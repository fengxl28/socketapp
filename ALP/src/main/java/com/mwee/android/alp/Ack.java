package com.mwee.android.alp;

/**
 * @ClassName: Ack
 * @Description:
 * @author: SugarT
 * @date: 2018/6/19 上午11:50
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
     * @param uniq 消息id
     * @param status 回执状态
     */
    public abstract void callback(String uniq, @AckStatus int status);
}
