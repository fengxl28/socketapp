package com.mwee.android.alp;


import android.text.TextUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import timber.log.Timber;

/**
 * @Description: 客户端
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
class Client {
    private static final Object lock = new Object();
    public boolean isDisconnected = false;
    private Socket socket = null;
    private InputStream in;
    private OutputStream out;
    private AckManage ackManage;
    /**
     * 手动终止
     */
    private boolean callFinish = false;
    /**
     * 客户端的监听
     */
    private IMoniter receiver;
    /**
     * 当前客户端的名称
     */
    private String name;


    protected Client() {
        ackManage = new AckManage();
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
            Timber.i("Client startClient()  " + address + ":" + port);

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
                    Timber.i("Client " + getName() + " 输入流已为null" + Thread.currentThread().getName());
                    break;
                }
                if (socket.isInputShutdown() || socket.isOutputShutdown() || socket.isClosed() || !socket.isConnected()) {
                    break;
                }
                String clientMsg = Util.socketReader(in, header);
                if (TextUtils.isEmpty(clientMsg)) {
                    Timber.i("Client " + getName() + " 链路已断开_" + Thread.currentThread().getName());
                    break;
                }

                Timber.i("Client " + getName() + " receive:" + clientMsg);
                int indexMsgType = clientMsg.indexOf(Configure.SYMBOL_SPLIT);
                String msgType = clientMsg.substring(0, indexMsgType);
                String value = clientMsg.substring(indexMsgType + Configure.SYMBOL_SPLIT.length());
                processMsg(msgType, value);
            }
        } catch (Throwable e) {
            Timber.e(e);
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
            Timber.e(e);
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
                        ackManage.removeAck(msgValue);
                        break;
                    case Configure.KEY_UNREGISTERED:
                        pushMsgToServer(Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_REGIST + Configure.SYMBOL_SPLIT + name);
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
        ackManage.addAck(uniq, ack);
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
                Timber.i("Client is finished " + Thread.currentThread().getName());
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
                Timber.i("Client pushMsgToServer [" + msg + "]_" + Thread.currentThread().getName());

                return true;
            } else {
                Timber.i("Client out stream is null" + Thread.currentThread().getName());
                return false;
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }


    /**
     * 发送心跳报文
     */
    private boolean sendHeartBeating() {
        return pushMsgToServer(Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_HEART + Configure.SYMBOL_SPLIT);
    }

    /**
     * 一次心跳的结果，检测：socket的状态、连接状态、是否关闭，
     *
     * @return boolean
     */
    protected boolean heartBeating() {
        if (!(socket != null && !socket.isClosed() && socket.isConnected())) {
            Timber.i("Client 链路异常");
            return false;
        }
        return sendHeartBeating();
    }

    /**
     * 终止Socket
     */
    public void callFinish() {
        callFinish = true;
        ackManage.setFinish(callFinish);
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
