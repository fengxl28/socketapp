package com.mwee.android.alp;
/**
 * @Description:
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
public interface IMoniter {
    void connected();

    void disconnected(boolean manaualStop);

    void receiveMsg(String uniq, String msg);
}
