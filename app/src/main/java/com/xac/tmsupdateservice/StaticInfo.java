package com.xac.tmsupdateservice;

import android.os.SystemProperties;
import android.util.Log;

import saioapi.base.Misc;

public class StaticInfo {

   private static Misc mMisc = new Misc();

    public static String getProductName(){
        String strPN="";

        byte[] info = new byte[20];
        mMisc.getSystemInfo(Misc.INFO_PRODUCT, info);
        int len = info.length;
        for (int i = 0; i < info.length; i++) {
            if (info[i] == 0) {
                len = i;
                break;
            }
        }
        strPN = new String(info);
        strPN = strPN.substring(0, len);
        return strPN;
    }

    public static String getSN() {
        String mSerialNum;
        byte[] infoSN = new byte[20];

        if (mMisc.getSystemInfo(Misc.INFO_SERIAL_NUM, infoSN) == 0)
            mSerialNum = byte2String(infoSN);
        else
            mSerialNum = null;
        return mSerialNum;
    }
    public static String getBuildNo(){
        return SystemProperties.get("ro.build.id");
    }

    public static String byte2String(byte[] data) {
        int length = data.length;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                length = i;
                break;
            }
        }
        return new String(data).substring(0, length);
    }
}
