package com.mwee.android.alp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import java.util.UUID;

/**
 * Created by virgil on 2016/12/11.
 */
@SuppressWarnings("unused")
public class PushClient {
    /**
     * 单例的实例
     */
    private static PushClient instance = new PushClient();
    /**
     * 客户端的实例
     */
    private Client client = null;
    /**
     * 服务器地址
     */
    private volatile String serverAddress;
    /**
     * 服务器端口
     */
    private int serverPort;
    /**
     * 本地工作线程的Handler
     */
    private Handler msgHandler = null;
    private volatile int heartInterval = INTERVAL_LONG;
    /**
     * 心跳间隔，1分钟
     */
    private final static int INTERVAL_LONG = 1000 * 60 ;
    /**
     * 轮询间隔20秒
     */
    private final static int INTERVAL_SHORT = 1000 * 20;

    /**
     * 最短的心跳轮询
     */
    private final static int INTERVAL_MIN = 1000 * 5;

    private Context context;
    private String name = "";
    private final Object lock = new Object();
    /**
     * 维持心跳的线程
     */
    private Thread heartThread = new Thread("PushClientHeart") {
        @Override
        public void run() {
            super.run();

            startWork();
        }

        private void startWork() {
            try {
                doWork();
            } catch (InterruptedException e) {
                 AlpLog.e(e);
                startWork();
            }
        }

        private void doWork() throws InterruptedException {
            while (true) {
                boolean heartOK = false;

                try {
                    synchronized (lock) {
                        lock.wait(heartInterval);
                    }
                    AlpLog.i("PushClient 执行心跳");

                    if (client == null) {
                        AlpLog.i("PushClient client尚未初始化，wait()_" + Thread.currentThread().getName());
                        synchronized (lock) {
                            lock.wait(INTERVAL_SHORT);
                        }
                        if (client == null) {
                            AlpLog.i("PushClient client尚未初始化，尝试重连" + Thread.currentThread().getName());
                            msgHandler.sendEmptyMessage(6666);
                            continue;
                        }
                    }
                } catch (Exception e) {
                     AlpLog.e(e);
                }
                if (client != null && !client.isFinish()) {
                    try {
                        heartOK = client.heartBeating();
                    } catch (Exception e) {
                         AlpLog.e(e);
                    }
                }
                if (!heartOK) {
//                    AlpLog.i("PushClient 链路异常，20秒后尝试重连_" + Thread.currentThread().getName());
                    AlpLog.i("PushClient 链路异常，心跳间隔调整为5秒，5秒后尝试重连_" + Thread.currentThread().getName());
                    heartInterval = INTERVAL_MIN;
                    try {
                        Thread.sleep(INTERVAL_MIN);
                    } catch (InterruptedException e) {
                         AlpLog.e(e);
                    }
                    msgHandler.sendEmptyMessage(6666);
                } else {
                    AlpLog.i("PushClient 心跳正常_" + Thread.currentThread().getName());
                }
            }
        }
    };

    /**
     * 用户传入的回调
     */
    private IMsgReceiver userReceiver;
    /**
     * 连接状态的监听
     */
    private IMoniter innerReceiver = new IMoniter() {
        @Override
        public void connected() {
            AlpLog.i("PushClient 连接成功，每1分钟心跳一次_" + Thread.currentThread().getName());

            /**
             * 如果连接成功，则启动心跳，1分钟心跳一次
             */
            heartInterval = INTERVAL_LONG;
            try {
                synchronized (lock) {
                    lock.notify();
                }
            } catch (Exception e) {
                 AlpLog.e(e);
            }
            if (userReceiver != null) {
                userReceiver.connected();
            }
        }

        @Override
        public void disconnected(boolean manaualStop) {

            /**
             * 如果没有网络，则心跳调整为5秒，并通过心跳进行重试。
             */
            if (Util.isNetworkAvailable(context)) {
                heartInterval = INTERVAL_MIN;
                AlpLog.i("PushClient 连接断开，心跳改为5秒_" + Thread.currentThread().getName());
            } else {
                heartInterval = INTERVAL_LONG;
                AlpLog.i("PushClient 连接断开，心跳改为1分钟_" + Thread.currentThread().getName());
            }
//            try {
//                Thread.sleep(INTERVAL_MIN);
//            } catch (InterruptedException e) {
//                 AlpLog.e(e);
//            }
            if (!manaualStop) {
                try {
                    synchronized (lock) {
                        lock.notify();
                    }
                } catch (Exception e) {
                     AlpLog.e(e);
                }
            }
            try {
                if (!manaualStop && userReceiver != null) {
                    userReceiver.disconnected();
                }
            } catch (Exception e) {
                 AlpLog.e(e);
            }
        }

        @Override
        public void receiveMsg(final String uniq, final String msg) {
//            if (userReceiver != null) {
////                final String msgStr = (String) msg.obj;
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            userReceiver.receive(msg);
//                        } catch (Exception e) {
//                             AlpLog.e(e);
//                        }
//                    }
//                }).start();
//
//            }

            if (msgHandler != null) {
                com.mwee.android.alp.Message message = new com.mwee.android.alp.Message();
                message.uniq = uniq;
                message.message = msg;
                msgHandler.sendMessage(msgHandler.obtainMessage(9999, message));
            }
        }
    };
    /**
     * 监听网络状态变化的回调
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (msgHandler == null) {
                return;
            }
            if (context == null) {
                return;
            }
            ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (conn == null) {
                return;
            }
            NetworkInfo networkInfo = conn.getActiveNetworkInfo();
            msgHandler.sendEmptyMessage(6666);

            if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                AlpLog.i("PushClient 网络Receiver 连接成功，立即尝试重试，并恢复每3分钟心跳一次 threadName=" + Thread.currentThread().getName());

            } else if (networkInfo != null) {
                NetworkInfo.DetailedState state = networkInfo.getDetailedState();
                AlpLog.i("NetWork Changed" + state.name());
            } else {
                AlpLog.i("PushClient 网络Receiver 断开，主动断开连接");
            }
        }
    };

    private PushClient() {
        heartThread.start();
        /**
         * 处理消息的线程
         */
        HandlerThread msgThread = new HandlerThread("PushClientMsg") {
            @Override
            protected void onLooperPrepared() {
                super.onLooperPrepared();

                msgHandler = new Handler(Looper.myLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
//                        final String msgStr = (String) msg.obj;
                        Object obj = msg.obj;

                        switch (msg.what) {
                            case 9999: {
                                if (userReceiver != null) {
                                    try {
                                        com.mwee.android.alp.Message message = (com.mwee.android.alp.Message) obj;
                                        userReceiver.receive(message.uniq, message.message);
                                    } catch (Exception e) {
                                        AlpLog.e(e);
                                    }
                                }
                            }
                            break;
                            case 8888:
                                if (client == null) {
                                    return;
                                }
//                                client.pushMsgToServer(Configure.MSG_TYPE_BIZ + Configure.SYMBOL_SPLIT + UUID.randomUUID().toString() + Configure.SYMBOL_SPLIT + msgStr);
                                com.mwee.android.alp.Message message1 = (com.mwee.android.alp.Message) obj;
                                client.pushMsgToServer(Configure.MSG_TYPE_BIZ + Configure.SYMBOL_SPLIT + message1.message);
                                break;
                            case 7777: {
                                if (client == null) {
                                    return;
                                }
                                String msgStr = (String) obj;
                                client.pushMsgToServer(Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_REGIST + Configure.SYMBOL_SPLIT + msgStr);

                            }
                            break;
                            case 6666:
                                if (client == null || client.isDisconnected || client.isFinish()) {
                                    reTryConnect();
                                }
                                break;
                            case 5555:
                                disConnect();
                                break;
                            case 4444: {
                                if (client == null) {
                                    return;
                                }
                                com.mwee.android.alp.Message message = (com.mwee.android.alp.Message) obj;
                                client.pushBizToServer(message.uniq, message.message, message.ack);
                            }
                            break;
                            default:
                                break;
                        }
                    }
                };

            }
        };
        msgThread.start();

    }

    /**
     * 初始化的方法，需要：
     * 1，启动网络状态变化的广播监听
     * 2，启动轮询线程
     *
     * @param context Context
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        context.registerReceiver(receiver, filter);
    }

    public static PushClient getInstance() {
        return instance;
    }

    public void pushMsg(final String msg) {
//        msgHandler.sendMessage(msgHandler.obtainMessage(8888, msg));
        com.mwee.android.alp.Message message = new com.mwee.android.alp.Message();
        message.message = msg;
        msgHandler.sendMessage(msgHandler.obtainMessage(8888, message));
    }

    public void pushMsgNeedAck(String msg, Ack ack) {
        pushMsgNeedAck(UUID.randomUUID().toString(), msg, ack);
    }

    /**
     * 推送业务消息到服务器
     * @param uniq String | 消息唯一标识
     * @param msg String | 消息体
     * @param ack Ack | 回执回调
     */
    public void pushMsgNeedAck(String uniq, String msg, Ack ack) {
        com.mwee.android.alp.Message message = new com.mwee.android.alp.Message();
        message.uniq = uniq;
        message.message = msg;
        message.ack = ack;
        msgHandler.sendMessage(msgHandler.obtainMessage(4444, message));
    }

    /**
     * 将当前客户端注册到服务器
     *
     * @param name String | 客户端名称
     */
    public void registerToServer(final String name) {
        this.name = name;
        client.setName(name);
        msgHandler.sendMessage(msgHandler.obtainMessage(7777, name));
    }

    /**
     * 启动客户端，包括：
     * 1，构建{@link Client}
     * 2，连接到服务器
     *
     * @param address  String
     * @param port     int
     * @param receiver IMsgReceiver
     */
    public void startClient(String address, int port, IMsgReceiver receiver) {
        this.serverAddress = address;
        this.serverPort = port;
        this.userReceiver = receiver;
        connect();
    }

    /**
     * 重置IP服务器
     *
     * @param address String
     */
    public void resetAddress(String address) {
        if (TextUtils.equals(address, serverAddress)) {
            return;
        }
        this.serverAddress = address;
        connect();
    }

    /**
     * 尝试重连
     */
    private void reTryConnect() {
        connect();
    }

//    private synchronized void doConnect() {
//        if (TextUtils.isEmpty(serverAddress) || serverPort < 3000) {
//            AlpLog.e("PushClient connect() 参数没有设置 " + serverAddress + ":" + serverPort);
//            return;
//        }
//        client.startConnect(serverAddress, serverPort, innerReceiver);
//
//    }

    /**
     * 进行连接
     */
    private synchronized void connect() {
        disConnect();
        if (TextUtils.isEmpty(serverAddress) || serverPort < 3000) {
            AlpLog.e("PushClient connect() 参数没有设置 " + serverAddress + ":" + serverPort);
            return;
        }
        client = new Client();
        client.setName(name);

        Thread workingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(serverAddress) || serverPort < 3000) {
                    AlpLog.e("PushClient connect() 参数没有设置 " + serverAddress + ":" + serverPort);
                    return;
                }
                client.startConnect(serverAddress, serverPort, innerReceiver);
            }
        });
        workingThread.setName("PushClientWorking_" + System.currentTimeMillis());
        workingThread.start();
        AlpLog.e("PushClient startDo connect()  " + serverAddress + ":" + serverPort);

    }

    /**
     * 断开本地的Socket连接
     */
    private synchronized void disConnect() {
        if (client != null) {
            client.callFinish();
        }
    }
}
