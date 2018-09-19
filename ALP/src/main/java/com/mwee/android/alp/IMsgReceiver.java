package com.mwee.android.alp;

/**
 * @Description:
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
public interface IMsgReceiver {

    /**
     * 消息接受
     *
     * @param uniq  消息标识
     * @param param 消息内容
     */
    void receive(String uniq, String param);

    void connected();

    void disconnected();
}
