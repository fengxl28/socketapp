package com.mwee.android.alp;


import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * 客户端
 * Created by virgil on 2016/12/11.
 */
class Client {
    private static final Object lock = new Object();
    public boolean isDisconnected = false;
    /**
     * 客户端持有的Socket
     */
    private Socket socket = null;
    /**
     * 手动终止
     */
    private boolean callFinish = false;
    private InputStream in;
    private OutputStream out;
    /**
     * 客户端的监听
     */
    private IMoniter receiver;
    /**
     * 当前客户端的名称
     */
    private String name;

    /**
     * 回执缓存的最大数
     */
    private static final int ACK_MAX_SIZE = 40;
    /**
     * 消息id和回执回调的map
     */
    private LinkedHashMap<String, Pair<Long, Ack>> ackMap = new LinkedHashMap<String, Pair<Long, Ack>>(0, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, Pair<Long, Ack>> eldest) {
            // 超过设置的缓存 ACK 最大长度，移除最旧的
            if (size() > ACK_MAX_SIZE) {
                String uniq = eldest.getKey();
                Ack ack = eldest.getValue().second;
                AlpLog.i("超出 ACK 缓存的最大长度，消息[" + uniq + "]不再等待回执");
                if (ack != null) {
                    ack.callback(uniq, AckStatus.UnKnow);
                }
                return true;
            }
            return false;
        }
    };
    /**
     * 消息回执的轮询
     */
    private Thread ackThread = new Thread("AlpAckLoop") {
        @Override
        public void run() {
            super.run();
            while (!isFinish()) {
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    AlpLog.e(e);
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
                        AlpLog.i("loop for ack, message [" + uniq + "] timeout");
                        ack.callback(uniq, AckStatus.Timeout);
                        ackMap.remove(uniq);
                    }
                }
            }
        }
    };

    protected Client() {
        ackThread.start();
    }

    /**
     * 启动客户端的连接
     *
     * @param address  String
     * @param port     int
     * @param receiver IMoniter
     */
    public void startConnect(String address, int port, IMoniter receiver) {
        callFinish = false;
        this.receiver = receiver;
        socket = new Socket();
        try {
            AlpLog.i("Client startClient()  " + address + ":" + port);

            socket.connect(new InetSocketAddress(address, port), 3000);
            socket.setKeepAlive(true);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            if (receiver != null) {
                receiver.connected();
            }
            byte[] header = new byte[4];
            pushMsgToServer(buildHeartBeating());
            while (!callFinish) {
                if (in == null) {
                    AlpLog.i("Client " + getName() + " 输入流已为null" + Thread.currentThread().getName());
                    break;
                }
                if (socket.isInputShutdown() || socket.isOutputShutdown() || socket.isClosed() || !socket.isConnected()) {
                    break;
                }
                String clientMsg = Util.socketReader(in, header);
                if (TextUtils.isEmpty(clientMsg)) {
                    AlpLog.i("Client " + getName() + " 链路已断开_" + Thread.currentThread().getName());
                    break;
                }

                AlpLog.i("Client " + getName() + " receive:" + clientMsg);
                int indexMsgType = clientMsg.indexOf(Configure.SYMBOL_SPLIT);
                String msgType = clientMsg.substring(0, indexMsgType);
                String value = clientMsg.substring(indexMsgType + Configure.SYMBOL_SPLIT.length());
                processMsg(msgType, value);
            }
        } catch (Throwable e) {
            AlpLog.e(e);
        } finally {
            close();

            if (receiver != null) {
                receiver.disconnected(callFinish);
            }
        }

    }

    private void close() {
        if (!callFinish) {
            isDisconnected = true;
        }
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            AlpLog.e(e);
        }
        in = null;
        out = null;
        socket = null;
    }

    /**
     * 接收到Server推送的消息
     *
     * @param key   String
     * @param value String
     */
    private void processMsg(String key, String value) {
        switch (key) {
            case Configure.MSG_TYPE_BIZ:
                if (receiver != null) {
                    receiver.receiveMsg(null, value);
                }
                break;
            case Configure.MSG_TYPE_INNER: {
                int indexValueType = value.indexOf(Configure.SYMBOL_SPLIT);
                String valueType = value.substring(0, indexValueType);
                String msgValue = value.substring(indexValueType + Configure.SYMBOL_SPLIT.length());
                switch (valueType) {
                    case Configure.KEY_HEART:
                        break;
                    case Configure.KEY_REGIST:
                        break;
                    case Configure.KEY_ACK:
                        // 回执消息
                        synchronized (ackMap) {
                            AlpLog.i("Receive ack for message [" + msgValue + "]");
                            if (ackMap.containsKey(msgValue)) {
                                ackMap.get(msgValue).second.callback(msgValue, AckStatus.Success);
                            }
                            ackMap.remove(msgValue);
                        }
                        break;
                    case Configure.KEY_UNREGISTERED: {
                        pushMsgToServer(Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_REGIST + Configure.SYMBOL_SPLIT + name);
                    }
                    break;
                    default:
                        break;
                }
            }
            break;
            case Configure.MSG_TYPE_BIZ_NEED_ACK: {
                // 分割消息标识、消息体
                int indexValueType = value.indexOf(Configure.SYMBOL_SPLIT);
                String uniq = value.substring(0, indexValueType);
                String message = value.substring(indexValueType + Configure.SYMBOL_SPLIT.length());

                // 发送回执
                pushMsgToServer(buildAck(uniq));

                // 回调业务层
                if (receiver != null) {
                    receiver.receiveMsg(uniq, message);
                }
            }
            break;
            default:
                break;
        }
    }

    private String buildHeartBeating() {
        return Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_HEART + Configure.SYMBOL_SPLIT;
    }

    /**
     * 回执消息
     *
     * @param messageId 消息id
     * @return
     */
    public String buildAck(String messageId) {
        return Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_ACK + Configure.SYMBOL_SPLIT + messageId;
    }

    public boolean pushBizToServer(String msg, Ack ack) {
        return pushBizToServer(UUID.randomUUID().toString(), msg, ack);
    }

    /**
     * 推送消息到服务器
     *
     * @param uniq
     * @param msg
     * @param ack
     * @return
     */
    public boolean pushBizToServer(String uniq, String msg, Ack ack) {
        if (ack != null) {
            synchronized (ackMap) {
                ackMap.put(uniq, new Pair<>(SystemClock.elapsedRealtime(), ack));
            }
        }
        return pushMsgToServer(Configure.MSG_TYPE_BIZ_NEED_ACK + Configure.SYMBOL_SPLIT + uniq + Configure.SYMBOL_SPLIT + msg);
    }

    /**
     * 推送消息到服务器
     *
     * @param msg String
     */
    public boolean pushMsgToServer(String msg) {
        try {
            if (isFinish()) {
                AlpLog.i("Client is finished " + Thread.currentThread().getName());
                return false;
            }
            if (out != null) {
                byte[] infoByte = msg.getBytes();
                byte[] header = Util.integerToBytes(infoByte.length, 4);
                synchronized (lock) {
                    out.write(header);
                    out.write(infoByte);
                    out.flush();
                }
                AlpLog.i("Client pushMsgToServer [" + msg + "]_" + Thread.currentThread().getName());

                return true;
            } else {
                AlpLog.i("Client out stream is null" + Thread.currentThread().getName());
                return false;
            }
        } catch (Exception e) {
            AlpLog.e(e);
        }
        return false;
    }

    /**
     * 检测链路是否连接正常
     *
     * @return boolean | true： 链路正常；false：链路已断开
     */
    private boolean checkAlive() {
        synchronized (lock) {
            try {


                //实际测试下来，在连接数很大的情况下，tcp的接收端并不会忽略这个消息
//                socket.sendUrgentData(1);

            } catch (Exception ex) {
                AlpLog.e(ex);
                return false;
            }
        }
        return true;
    }

    /**
     * 发送心跳报文
     */
    private boolean sendHeartBeating() {
        return pushMsgToServer(Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_HEART + Configure.SYMBOL_SPLIT);
    }

    /**
     * 一次心跳的结果，检测：socket的状态、连接状态、是否关闭，并调用{@link #checkAlive()}来检测链路
     * 最后，发送心跳的报文{@link #sendHeartBeating()}
     *
     * @return boolean
     */
    protected boolean heartBeating() {
        if (!(socket != null && !socket.isClosed() && socket.isConnected())) {
            AlpLog.i("Client 链路异常");
            return false;
        }
        if (!checkAlive()) {
            return false;
        }
        return sendHeartBeating();
    }

    /**
     * 终止Socket
     */
    public void callFinish() {
        callFinish = true;
        close();
    }

    /**
     * 链路是否被手动终止
     *
     * @return boolean | true: 已被手动终止；false：没有被手动终止
     */
    public boolean isFinish() {
        return callFinish;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
