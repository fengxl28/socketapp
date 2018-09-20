package com.mwee.android.alp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import timber.log.Timber;

/**
 * @Description: 工具包
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
class Util {
    /**
     * 权限时是否被授权
     *
     * @param context android.Manifest.permission.ACCESS_NETWORK_STATE
     * @return
     */
    private static boolean isPermissionGranted(Context context, String permission) {
        int resultOfCheck = context.checkCallingOrSelfPermission(permission);
        return resultOfCheck == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 网络是否可用
     *
     * @return boolean｜true:可用；false：不可用
     */
    public static boolean isNetworkAvailable(Context context) {
        boolean flag = false;
        if (isPermissionGranted(context, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            ConnectivityManager manager = (ConnectivityManager) (context.getSystemService(Context.CONNECTIVITY_SERVICE));
            if (manager != null) {
                NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                if (networkInfo != null) {
                    flag = networkInfo.isAvailable();
                }
            }
        }
        return flag;
    }

    public static byte[] integerToBytes(int x, int len) {
        return ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN).putInt(x).array();
    }

    public static int bytesToInteger(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        return wrapped.getInt();
    }

    private static String getStringByByte(byte[] header) {
        StringBuilder sb = new StringBuilder("[");
        for (byte b : header) {
            sb.append(String.format("0x%2x", b)).append(",");
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    public static String socketReader(InputStream in, byte[] header) throws IOException {
        String originStr = getStringByByte(header);
        //当断开链接的时候也会调用
        int tempHead = in.read(header, 0, 4);
        if (tempHead < 0) {
            Timber.i("tempHead=" + tempHead);
            return null;
        }
        int totalLength = bytesToInteger(header);
        if (totalLength < 0) {

            Timber.i("totalLength=" + totalLength + ";now=" + getStringByByte(header) + ";origin=" + originStr);
            return null;
        }
        if (totalLength >= 10000000) {
            return "2" + Configure.SYMBOL_SPLIT + "4" + Configure.SYMBOL_SPLIT;
        }

        byte[] content = new byte[totalLength];
        int totalReadCount = 0;
        int readCount;
        while (totalLength > 0) {
            readCount = in.read(content, totalReadCount, totalLength);
            totalReadCount += readCount;
            totalLength = totalLength - readCount;
        }
        String strRequest = new String(content);
        if (TextUtils.isEmpty(strRequest)) {
            strRequest = null;
        }
        return strRequest;
    }
}
