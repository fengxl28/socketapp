package com.mwee.android.alp;
/**
 * @Description:
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
class Configure {
    /*
     * =============================消息类型=============================
     */
    /**
     * 业务消息
     */
    public final static String MSG_TYPE_BIZ = "1";
    /**
     * 内部链路消息
     */
    public final static String MSG_TYPE_INNER = "2";

    /**
     * 需要回执的业务消息
     */
    public final static String MSG_TYPE_BIZ_NEED_ACK = "3";

    /*
     * =============================内部链路消息分类=============================
     */
    /**
     * 注册名称
     */
    public final static String KEY_REGIST = "3";
    /**
     * 心跳
     */
    public final static String KEY_HEART = "4";
    /**
     * 回执消息
     */
    public final static String KEY_ACK = "5";
    /**
     * 连接未注册，需要注册
     */
    public final static String KEY_UNREGISTERED = "6";

    protected final static String SYMBOL_SPLIT="#@%";


}
