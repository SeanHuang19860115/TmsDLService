package com.xac.tmsupdateservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class UpdateService extends Service {
    /* 设备BuildNo*/
    private String strBuildNo;
    /* 设备序列号*/
    private String strSN;
    /* 设备名称*/
    private String strProductName;
    /* Agent安装标志*/
    private Boolean bAgentInstalled=false;
    /*AgentSDK 安装标志*/
    private Boolean bAgentSDKInstalled=false;
    /*WebView安装标志*/
    private Boolean bWebViewInstalled=false;
    /*Agent Package Name*/
    private final String strAgentPackageName="com.xacusa.tms";
    /*AgentSDK Package Name*/
    private String strAgentSdkPackageName="com.xacsz.tms.client_sdk";
    /*WebView Package Name*/
    private String strWebViewPackageName="com.android.webview";
    /*Wifi 连接状态*/
    NetworkInfo mNetworkInfo=null;
    /*后台请求地址*/
    private final String strURL="https://us-central1-sitedispatcher-25215.cloudfunctions.net/initialize_download";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("UpdateService","onCreate");
        initDeviceInfo();
        checkApp();
    }

    //查看特定APP是否安装
    public void checkApp()
    {
        PackageManager packageManager=UpdateService.this.getPackageManager();
        List<PackageInfo> Packages=packageManager.getInstalledPackages(0);
        for(PackageInfo i : Packages){
            if (strAgentPackageName.equals(i.packageName)) {
                bAgentInstalled=true;
            }else if (strAgentSdkPackageName.equals(i.packageName)){
                bAgentSDKInstalled=true;
            }else if (strWebViewPackageName.equals(i.packageName)){
                bWebViewInstalled=true;
            }
        }
        Log.i("UpdateService","Agent is installed:"+String.valueOf(bAgentInstalled));
        Log.i("UpdateService","AgentSDK is installed:"+String.valueOf(bAgentSDKInstalled));
        Log.i("UpdateService","WebView is installed:"+String.valueOf(bWebViewInstalled));
    }

    //初始化设备信息
    public void initDeviceInfo(){
        strProductName=StaticInfo.getProductName();
        Log.i("UpdateService","ProductName:"+strProductName);
        strBuildNo=  StaticInfo.getBuildNo();
        Log.i("UpdateService","BuildNo:"+strBuildNo);
        strSN=StaticInfo.getSN();
        Log.i("UpdateService","SN:"+strSN);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("UpdateService","onStartCommand");
        //检查wifi连接状态
        checkWifiStatus();
        //向后台查询具体任务
        getTaskFromBackEnd(strURL);




        //如果三个APP都安装完毕，则直接启动Agent即可
        if (bWebViewInstalled&&bAgentSDKInstalled&&bAgentInstalled){
            startAgent();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    //从后台获取要更新APP列表(包含强制更新)
    private void getTaskFromBackEnd(String url){
        OkHttpClient client=new OkHttpClient().setProtocols(Collections.singletonList(Protocol.HTTP_1_1));
        RequestBody body = new FormEncodingBuilder()
                .add("ModuleName", strProductName)
                .add("BuildID",strBuildNo)
                .add("SN",strSN)
                .build();

        Request request=new Request.Builder()
                .url(url)
                .post(body)
                .build();

        Call call=client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
              //发送失败Log
                Log.e("UpdateService","Failed to request url:"+e.toString());
            }

            @Override
            public void onResponse(Response response) throws IOException {

            }
        });
    }

    //获取Wifi连接状态
    private void checkWifiStatus(){

        final ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mNetworkInfo= mConnectivityManager.getActiveNetworkInfo();
                while (mNetworkInfo == null) {
                    try {
                        //Log.i("UpdateService", "[Network] Wait Network Connected.");
                        Thread.sleep(10000);
                        mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Log.i("UpdateService","Wifi connected");
            }
        }).start();


    }
    //启动Agent
    public void startAgent(){
        PackageManager packageManager = UpdateService.this.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(strAgentPackageName);
        if (intent!=null) {
            startActivity(intent);
            onDestroy();
        }else{
            Log.i("UpdateService","Failed to start Agent,Intent=Null");
            //把Log发送给Server
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("UpdateService","onDestroy");
    }
}
