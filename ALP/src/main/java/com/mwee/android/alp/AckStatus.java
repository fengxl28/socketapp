package com.mwee.android.alp;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Description: 回执状态
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface AckStatus {

    /**
     * 未知，不再等待回执结果
     */
    int UnKnow = -1;

    /**
     * 成功
     */
    int Success = 0;

    /**
     * 未连接
     */
    int Disconnected = 1;

    /**
     * 超时
     */
    int Timeout = 2;
}
