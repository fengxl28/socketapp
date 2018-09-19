package com.mwee.android.alp;

/**
 * Created by virgil on 2016/12/11.
 */
public interface IMsgReceiver {

//    void receive(String param);

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
