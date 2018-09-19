package com.mwee.android.alp;

/**
 * Created by virgil on 2016/12/12.
 */

public interface IMoniter {
    void connected();

    void disconnected(boolean manaualStop);

    void receiveMsg(String uniq, String msg);
}
