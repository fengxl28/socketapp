package com.mwee.android.alp;

import android.util.Log;


/**
 * 本地日志工具类
 * Created by virgil on 2016/12/12.
 */
public class AlpLog {
    /**
     * 是否是生产包
     */
    private static boolean isRelase = false;
    /**
     * 是否需要打印日志
     */
    protected static boolean showLog = true;

    /**
     * 设置当前环境
     *
     * @param release boolean | true:生产包;false:非生产包
     */
    public static void setRelease(boolean release) {
        isRelase = release;
        showLog = !isRelase;
    }

    protected static void e(String msg) {
        if (showLog) {
            Log.e("AlpLog", msg);
        }
    }

    protected static void e(Throwable e) {
        Log.e("AlpLog", "", e);
    }

    protected static void i(String msg) {
        if (showLog) {
            Log.i("AlpLog", msg);
        }
    }
}
