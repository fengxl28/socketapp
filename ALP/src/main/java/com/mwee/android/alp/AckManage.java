package com.mwee.android.alp;

import android.os.SystemClock;
import android.util.Pair;

import java.util.LinkedHashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * @Description: 回执消息管理
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
public class AckManage {

    /**
     * 回执缓存的最大数
     */
    public static final int ACK_MAX_SIZE = 40;
    private boolean isFinish = false;


    public AckManage() {
        ackThread.start();
    }

    public void setFinish(boolean finish) {
        isFinish = finish;
    }

    /**
     * 消息id和回执回调的map
     */
    private LinkedHashMap<String, Pair<Long, Ack>> ackMap = new LinkedHashMap<String, Pair<Long, Ack>>(0, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pair<Long, Ack>> eldest) {
            // 超过设置的缓存 ACK 最大长度，移除最旧的
            if (size() > ACK_MAX_SIZE) {
                String uniq = eldest.getKey();
                Ack ack = eldest.getValue().second;
                Timber.i("超出 ACK 缓存的最大长度，消息[" + uniq + "]不再等待回执");
                if (ack != null) {
                    ack.callback(uniq, AckStatus.UnKnow);
                }
                return true;
            }
            return false;
        }
    };
    /**
     * 消息回执的轮询, 5秒一次删除过期的
     */
    private Thread ackThread = new Thread("AlpAckLoop") {
        @Override
        public void run() {
            super.run();
            while (!isFinish) {
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    Timber.e(e);
                }
                if (ackMap.size() == 0) {
                    continue;
                }
                synchronized (ackMap) {
                    for (String uniq : ackMap.keySet()) {
                        long timestamp = ackMap.get(uniq).first;
                        Ack ack = ackMap.get(uniq).second;
                        if (SystemClock.elapsedRealtime() - timestamp < ack.timeout()) {
                            continue;
                        }
                        Timber.i("loop for ack, message [" + uniq + "] timeout");
                        ack.callback(uniq, AckStatus.Timeout);
                        ackMap.remove(uniq);
                    }
                }
            }
        }
    };

    /**
     * 移除回执
     * @param msgValue
     */
    public void removeAck(String msgValue) {
        synchronized (ackMap) {
            Timber.i("Receive ack for message [" + msgValue + "]");
            if (ackMap.containsKey(msgValue)) {
                ackMap.get(msgValue).second.callback(msgValue, AckStatus.Success);
            }
            ackMap.remove(msgValue);
        }
    }

    /**
     * add ack
     * @param uniq
     * @param ack
     */
    public void addAck(String uniq, Ack ack) {
        if (ack != null) {
            synchronized (ackMap) {
                ackMap.put(uniq, new Pair<>(SystemClock.elapsedRealtime(), ack));
            }
        }
    }
}
