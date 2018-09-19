package com.mwee.android.alp;



import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.mwee.android.alp.AckStatus.Disconnected;
import static com.mwee.android.alp.AckStatus.Success;
import static com.mwee.android.alp.AckStatus.Timeout;
import static com.mwee.android.alp.AckStatus.UnKnow;

/**
 * @ClassName: AckStatus
 * @Description:
 * @author: SugarT
 * @date: 2018/6/19 下午3:49
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@IntDef(value = {UnKnow, Success, Disconnected, Timeout})
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
