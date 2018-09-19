package com.mwee.android.alp;


import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

/**
 * @Description: 服务端
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
class Server {
    /**
     * 客户端和名称的mapping
     */
    private ArrayMap<String, ClientHandler> keyClient = new ArrayMap<>();
    /**
     * 客户端列表
     */
    private List<ClientHandler> clientList = new ArrayList<>();
    /**
     * 手动终止
     */
    private volatile boolean callFinish = false;
    /**
     * 服务器的监听
     */
    private IMsgReceiver receiver;
    private Thread handlerThread = null;

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
                Timber.i(("ALP" + "超出 ACK 缓存的最大长度，消息[" + uniq + "]不再等待回执"));
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
            Timber.i("Ack loop thread check finish: " + checkFinish());
            while (!checkFinish()) {
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    Timber.e(e);
                }
                if (ackMap.size() == 0) {
                    continue;
                }
                synchronized (ackMap) {
                    Iterator<String> it = ackMap.keySet().iterator();
                    while (it.hasNext()) {
                        String uniq = it.next();
                        long timestamp = ackMap.get(uniq).first;
                        Ack ack = ackMap.get(uniq).second;
                        if (SystemClock.elapsedRealtime() - timestamp < ack.timeout()) {
                            continue;
                        }
                        Timber.i("loop for ack, message [" + uniq + "] timeout");
                        ack.callback(uniq, AckStatus.Timeout);
                        it.remove();
                    }
                }
            }
        }
    };

    protected Server() {
        Timber.e("server created " + Thread.currentThread().getName());
    }

    /**
     * 链路管理器，每6分钟检测所有链路，如果链路的心跳间隔超过了6分钟，则主动断掉链路
     */
    private void initClientChecker() {
        if (handlerThread == null) {
            handlerThread = new Thread("ALPServerLoop") {
                @Override
                public void run() {
                    super.run();
                    while (!checkFinish()) {
                        try {
                            Thread.sleep(6 * 60 * 1000);
                        } catch (InterruptedException e) {
                            Timber.e(e);
                        }
                        Timber.i("Server 开始轮询心跳链路");
                        try {

                            // 心跳检测
                            List<ClientHandler> cloneClientList = new ArrayList<>(clientList);
                            for (int i = 0; i < cloneClientList.size(); i++) {
                                ClientHandler temp = cloneClientList.get(i);
                                if (temp.isFinished || ((temp.lastBeating > 0) && ((SystemClock.elapsedRealtime() - temp.lastBeating) > 6 * 60 * 1000))) {
                                    if (temp.isFinished) {
                                        Timber.i("Server 链路[" + temp.getLogName() + "] isFinished");
                                    } else {
                                        Timber.i("Server 链路[" + temp.getLogName() + "] 超时");
                                    }
                                    Log.d("ALP", "链路[" + temp.getLogName() + "] " + (temp.isFinished ? "finished" : "超时"));
                                    temp.callStop();
                                    synchronized (Server.this) {
                                        clientList.remove(temp);
                                    }
                                    cloneClientList.remove(temp);
                                    i--;
                                    if (!TextUtils.isEmpty(temp.clientKey)) {
                                        synchronized (Server.this) {
                                            keyClient.remove(temp.clientKey);
                                        }
                                    } else {
                                        if (keyClient.containsValue(temp)) {
                                            ArrayMap<String, ClientHandler> cloneKeyClient = new ArrayMap<>(keyClient);
                                            for (Map.Entry<String, ClientHandler> tempEntry : cloneKeyClient.entrySet()) {
                                                if (tempEntry.getValue() == temp) {
                                                    synchronized (Server.this) {
                                                        keyClient.remove(tempEntry.getKey());
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                }
                            }

                            // 连接数检测
                            checkConnectionRegister();

                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    }
                }
            };
            handlerThread.start();

            ackThread.start();
        }
    }

    /**
     * 初始化服务器
     *
     * @param port     int
     * @param receiver IMsgReceiver
     */
    protected void init(int port, IMsgReceiver receiver) {
        if (checkFinish()) {
            return;
        }
        initClientChecker();
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket();
            this.receiver = receiver;
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            while (!checkFinish()) {
                Socket client = serverSocket.accept();
                receiveClient(client);
            }
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Timber.e(e);
                }
            }
            try {
                Timber.e("server close " + Thread.currentThread().getName());
                if (!checkFinish()) {
                    Thread.sleep(10 * 1000);
                    Timber.e("server start retry " + Thread.currentThread().getName());
                    init(port, receiver);
                } else {
                    Timber.e("server finally finished " + Thread.currentThread().getName());
                }
            } catch (InterruptedException e) {
                Timber.e(e);
            }

        }

    }

    /**
     * 注销当前的链路
     *
     * @param client ClientHandler
     */
    private void unRegriserClient(ClientHandler client) {
        if (client == null) {
            return;
        }
        synchronized (Server.this) {
            if (clientList != null && !clientList.isEmpty()) {
                if (clientList.contains(client)) {
                    clientList.remove(client);
                }
            }
            if (keyClient != null && !keyClient.isEmpty()) {
                String key = client.clientKey;
                if (keyClient.containsKey(key)) {
                    keyClient.remove(key);
                }
            }
        }

    }

    /**
     * 收到链路创建的请求
     *
     * @param client Socket
     */
    private void receiveClient(Socket client) {
        Timber.i("Server receiveClient " + String.format("开始监听客户端: %s", client.getRemoteSocketAddress()));
        Log.d("ALP", String.format("开始监听客户端: %s", client.getRemoteSocketAddress()));
        ClientHandler clientHandler = new ClientHandler(client);
        synchronized (Server.this) {
            clientList.add(clientHandler);
        }
        clientHandler.start();
        clientHandler.lastBeating = SystemClock.elapsedRealtime();
        Log.d("ALP", "开始监听客户端[" + client.getRemoteSocketAddress() + "]\n" +
                "Client List: " + clientList + "\n" +
                "Key Client: " + new Gson().toJson(keyClient.keySet()));
    }

    /**
     * 处理消息
     *
     * @param socket ClientHandler
     * @param key    String
     * @param value  String
     */
    private void processMsg(ClientHandler socket, String key, String value) {
        switch (key) {
            case Configure.MSG_TYPE_BIZ: {
                if (receiver != null) {
                    receiver.receive("", value);
                }
            }
            break;
            case Configure.MSG_TYPE_INNER: {
                int indexValueType = value.indexOf(Configure.SYMBOL_SPLIT);
                String valueType = value.substring(0, indexValueType);
                String msgValue = value.substring(indexValueType + Configure.SYMBOL_SPLIT.length());
                switch (valueType) {
                    case Configure.KEY_HEART:
                        socket.lastBeating = SystemClock.elapsedRealtime();
                        break;
                    case Configure.KEY_REGIST:
                        synchronized (Server.this) {
                            keyClient.put(msgValue, socket);
                        }
                        socket.clientKey = msgValue;
                        Log.d("ALP", "接受到客户端[" + msgValue + "]注册消息.\n" +
                                "Client List: " + clientList + "\n" +
                                "Key Client: " + new Gson().toJson(keyClient.keySet()));
                        break;
                    case Configure.KEY_ACK:
                        // 回执消息
                        synchronized (ackMap) {
                            Timber.i("Receive ack for message [" + msgValue + "]");
                            if (ackMap.containsKey(msgValue)) {
                                ackMap.get(msgValue).second.callback(msgValue, AckStatus.Success);
                            }
                            ackMap.remove(msgValue);
                        }
                        break;
                    default:
                        break;
                }
            }
            break;
            case Configure.MSG_TYPE_BIZ_NEED_ACK: {
                int indexValueType = value.indexOf(Configure.SYMBOL_SPLIT);
                String uniq = value.substring(0, indexValueType);
                String message = value.substring(indexValueType + Configure.SYMBOL_SPLIT.length());

                // 发送回执
                socket.pushMsg(buildAck(uniq));

                if (receiver != null) {
                    receiver.receive(uniq, message);
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

    /**
     * 发送给客户端的连接未注册的消息
     *
     * @return
     */
    public String buildUnregistered() {
        return Configure.MSG_TYPE_INNER + Configure.SYMBOL_SPLIT + Configure.KEY_UNREGISTERED + Configure.SYMBOL_SPLIT;
    }

    /**
     * 给所有链路推送消息
     *
     * @param msg String
     */
    protected void pushMsgToAll(String msg) {
        try {
            String finalMsg = Configure.MSG_TYPE_BIZ + Configure.SYMBOL_SPLIT + msg;
            List<ClientHandler> tempList = new ArrayList<>(clientList);
            for (ClientHandler temp : tempList) {
                temp.pushMsg(finalMsg);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    /**
     * 推送消息给指定的链路
     *
     * @param targetName String
     * @param msg        String
     */
    public void pushMsgToTarget(String targetName, String msg) {
        ClientHandler temp = keyClient.get(targetName);
        if (temp != null) {
            String finalMsg = Configure.MSG_TYPE_BIZ + Configure.SYMBOL_SPLIT + msg;
            temp.pushMsg(finalMsg);
        } else {
            Log.d("ALP", "指定链路[" + targetName + "]不存在, 消息[" + msg + "]取消推送\n" +
                    "Client List: " + clientList + "\n" +
                    "Key Client: " + new Gson().toJson(keyClient.keySet()));
            checkConnectionRegister();
        }
    }

    public void pushMsgToTargetNeedAck(String targetName, String msg, Ack ack) {
        pushMsgToTargetNeedAck(targetName, msg, UUID.randomUUID().toString(), ack);
    }

    /**
     * 推送业务消息给指定的链路
     *
     * @param targetName 指定链路
     * @param msg        消息体
     * @param uniq       消息唯一标识
     * @param ack        回执回调
     */
    public void pushMsgToTargetNeedAck(String targetName, String msg, String uniq, Ack ack) {
        ClientHandler temp = keyClient.get(targetName);
        if (temp != null) {
            if (ack != null) {
                synchronized (ackMap) {
                    ackMap.put(uniq, new Pair<>(SystemClock.elapsedRealtime(), ack));
                }
            }
            String finalMsg = Configure.MSG_TYPE_BIZ_NEED_ACK + Configure.SYMBOL_SPLIT + uniq + Configure.SYMBOL_SPLIT + msg;
            temp.pushMsg(finalMsg);
        } else {
            Log.d("ALP", "消息 [" + uniq + " ---- " + msg + "]取消推送, 目标客户端 [" + targetName + "] 未注册.\n" +
                    "Client List: " + clientList + "\n" +
                    "Key Client: " + new Gson().toJson(keyClient.keySet()));
            if (ack != null) {
                ack.callback(uniq, AckStatus.Disconnected);
            }
            checkConnectionRegister();
        }
    }

    private synchronized boolean checkFinish() {
        return callFinish;
    }

    /**
     * 结束掉当前的服务器
     */
    public void finish() {
        Timber.i("Server call finish " + Thread.currentThread().getName());
        clientList.clear();
        keyClient.clear();
        synchronized (this) {
            callFinish = true;
        }
    }

    /**
     * 校验连接注册
     */
    public void checkConnectionRegister() {
        Log.d("ALP", "校验 Socket 连接注册");
        if (clientList == null || clientList.size() == 0) {
            Log.d("ALP", "ALP 连接池为空，跳过注册校验");
            return;
        }
        for (ClientHandler client : clientList) {
            if (client == null) {
                continue;
            }
            if (keyClient.containsKey(client.clientKey)) {
                continue;
            }
            Log.d("ALP", client + "未注册，即将发送需要注册的消息");
            client.pushMsg(buildUnregistered());
        }
    }

    /**
     * 仅测试使用
     */
    public void removeFirst() {
        if (keyClient == null || keyClient.size() == 0) {
            return;
        }
        keyClient.removeAt(0);
        Log.d("ALP", "Remove First\n" +
                "Client List: " + clientList + "\n" +
                "Key Client: " + new Gson().toJson(keyClient.keySet()));
    }

    /**
     * 链路
     */
    private class ClientHandler extends Thread {
        private Socket client;
        private InputStream in;
        private OutputStream out;
        private boolean callStop = false;
        private String clientKey = "";
        /**
         * 上一次收到心跳包的时间
         */
        private long lastBeating = 0L;
        private boolean isFinished = false;

        ClientHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            super.run();

            try {
                client.setOOBInline(false);
                init();
                //首次链接，即回执一个心跳
                pushMsg(buildHeartBeating());
                while (!callStop) {
                    if (in == null) {
                        break;
                    }
                    if (client.isInputShutdown() || client.isOutputShutdown() || client.isClosed() || !client.isConnected()) {
                        callStop();
                        break;
                    }
                    byte[] header = new byte[4];
                    String clientMsg = Util.socketReader(in, header);
                    if (TextUtils.isEmpty(clientMsg)) {
                        Timber.i("Server " + getLogName() + " 读取失败");
                        callStop();
                        break;
                    }
                    Timber.i("Server receive msg [" + clientMsg + "] from [" + this.getLogName() + "]");
                    int indexMsgType = clientMsg.indexOf(Configure.SYMBOL_SPLIT);
                    String msgType = clientMsg.substring(0, indexMsgType);
                    String value = clientMsg.substring(indexMsgType + Configure.SYMBOL_SPLIT.length());
                    processMsg(this, msgType, value);
                }
            } catch (Throwable e) {
                Timber.e(e);
                callStop();
                Log.d("ALP", "客户端[" + getLogName() + "]连接异常\n" + e.getMessage());
            }
            isFinished = true;
        }

        /**
         * 初始化流
         *
         * @throws IOException
         */
        private void init() throws IOException {
            in = client.getInputStream();
            out = client.getOutputStream();
        }

        /**
         * 通过当前链路推送消息
         *
         * @param msg String
         */
        private void pushMsg(String msg) {
            try {
                synchronized (ClientHandler.this) {
                    if (out != null) {
                        byte[] infoByte = msg.getBytes();
                        byte[] header = Util.integerToBytes(infoByte.length, 4);
                        out.write(header);
                        out.write(infoByte);
                        out.flush();
                    }
                }
            } catch (SocketException e) {
                Timber.e(e);
                callStop();
            } catch (Exception e) {
                Timber.e(e);
            }
        }

        /**
         * 结束链路，并注销掉Server的引用
         */
        private void callStop() {
            callStop = true;
            unRegriserClient(this);
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (client != null) {
                    client.close();
                }
            } catch (Exception e) {
                Timber.e(e);
            }
            in = null;
            out = null;
            client = null;
            isFinished = true;
        }

        /**
         * 获取链路的日志名称
         *
         * @return String
         */
        private String getLogName() {
            return clientKey + "," + (client != null ? client.getRemoteSocketAddress() : "");
        }

        @Override
        public String toString() {
            return "[" + clientKey + "," + (client != null ? client.getRemoteSocketAddress() : "") + "]";
        }
    }
}
