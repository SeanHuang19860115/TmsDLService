package com.xac.tmsupdateservice;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class TmsDownloadReceiver extends BroadcastReceiver {
    /*要接收的intent源*/
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION))
        {
           // context.startService(new Intent(context,UpdateService.class));//启动倒计时服务
            Intent newIntent = new Intent(context, Main_Activity.class);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(newIntent);
            //context.startActivity(new Intent( TmsDownloadReceiver.this,UpdateService.class));
        }
    }

}
