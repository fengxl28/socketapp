package com.mwee.android.alp;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import timber.log.Timber;

/**
 * @Description: 服务端管理
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
@SuppressWarnings("unused")
public class PushServer {
    /**
     * 单例
     */
    private static PushServer instance = new PushServer();
    /**
     * 服务器的实例
     */
    private Server server = null;
    /**
     * 用户的监听
     */
    private IMsgReceiver userReceiver;
    private Handler threadHanlder = null;
    /**
     * 内部和Server的监听
     */
    private IMsgReceiver receiver = new IMsgReceiver() {

        @Override
        public void receive(String uniq, final String param) {
            if (userReceiver != null) {
                threadHanlder.post(() -> userReceiver.receive(uniq, param));
            }
        }

        @Override
        public void connected() {

        }

        @Override
        public void disconnected() {

        }
    };
    private Thread workingThread = null;

    private PushServer() {
        init();
    }

    public static PushServer getInstance() {
        if (instance == null) {
            synchronized (PushServer.class) {
                if (instance == null) {
                    instance = new PushServer();
                }
            }
        }
        return instance;
    }

    private void init() {
        HandlerThread thread = new HandlerThread("ClientHeartBeatingThread_" + SystemClock.elapsedRealtime()) {
            @Override
            protected void onLooperPrepared() {
                super.onLooperPrepared();
                threadHanlder = new Handler(Looper.myLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                    }
                };
            }
        };
        thread.start();
    }

    /**
     * 推送消息到所有站点
     *
     * @param msg String | 消息体
     */
    public void pushMsg(final String msg) {
        threadHanlder.post(() -> {
            if (server == null) {
                return;
            }
            server.pushMsgToAll(msg);
        });
    }

    /**
     * 推送消息到指定的接收者
     *
     * @param targetName String | 对应{@link PushClient#registerToServer(String)} 里的名称
     * @param msg        String
     */
    public void pushMsgTo(final String targetName, final String msg) {
        threadHanlder.post(() -> {
            if (server == null) {
                return;
            }
            server.pushMsgToTarget(targetName, msg);
        });
    }

    public void pushMsgNeedAck(final String targetName, final String msg, Ack ack) {
        threadHanlder.post(() -> {
            if (server == null) {
                return;
            }
            server.pushMsgToTargetNeedAck(targetName, msg, ack);
        });
    }

    public void pushMsgNeedAck(final String targetName, String uniq, final String msg, Ack ack) {
        threadHanlder.post(() -> {
            if (server == null) {
                return;
            }
            server.pushMsgToTargetNeedAck(targetName, msg, uniq, ack);
        });
    }

    /**
     * 启动服务器
     *
     * @param port         int
     * @param userReceiver IMsgReceiver
     */
    public void startServer(final int port, IMsgReceiver userReceiver) {
        if (port < 3000) {
            Timber.e("PushServer startServer() 非法端口 " + port);
            return;
        }
        if (server != null) {
            server.finish();
        }
        this.userReceiver = userReceiver;
        workingThread = new Thread(() -> {
            server = new Server();
            server.init(port, receiver);
        });
        workingThread.setName("PushServerWorking_" + SystemClock.elapsedRealtime());
        workingThread.start();

    }

    public void finishServer() {
        synchronized (PushServer.class) {
            Timber.i("destroy");
            if (server != null) {
                server.finish();
                server = null;
            }
            try {
                if (workingThread != null && !workingThread.isInterrupted() && workingThread.isAlive()) {
                    workingThread.interrupt();
                }
                workingThread = null;
            } catch (Exception e) {
                Timber.e(e);
            }
            if (threadHanlder != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    threadHanlder.getLooper().quitSafely();
                } else {
                    threadHanlder.getLooper().quit();
                }
            }
            threadHanlder = null;
            instance = null;
        }
    }

    /**
     * 仅测试使用
     */
    public void removeFistConn() {
        if (server == null) {
            return;
        }
        server.removeFirst();
    }
}
