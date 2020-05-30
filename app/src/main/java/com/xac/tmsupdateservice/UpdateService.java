package com.xac.tmsupdateservice;

import android.Manifest;
import android.app.DownloadManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
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
import com.xac.packagemanager.OnPMObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.TimerTask;

import saioapi.util.SaioService;

public class UpdateService extends Service {
    private String INFO="TMS_DOWNLOAD_INFO";
    private String ERROR="TMS_DOWNLOAD_ERROR";
    private String WARNING="Warning";
    private String TAG="UpdateService";
    /* 设备BuildNo*/
    private String strBuildNo;
    /* 设备序列号*/
    private String strSN;
    /* 设备名称*/
    private String strProductName;
    /*OS 版本*/
    //private String strSystemVer=Constant.BSP4XX;
    /* Agent安装标志*/
    private Boolean bAgentInstalled=false;
    /*AgentSDK 安装标志*/
    private Boolean bAgentSDKInstalled=false;
    /*WebView安装标志*/
    private Boolean bWebViewInstalled=false;
    /* 远程Agent安装标志*/
    private Boolean bAgentRemoteInstalled=false;
    /*远程AgentSDK 安装标志*/
    private Boolean bAgentSDKRemoteInstalled=false;
    /*远程WebView安装标志*/
    private Boolean bWebViewRemoteInstalled=false;
    /*当前设备中Agent的版本号*/
    private String currentAgentVer;
    /*当前设备中Agent的VersionCode*/
    private long currentAgentVerCode;
    /*当前设备中的SDK的VersionCode*/
    private long currentSDKVerCode;
    /*当前设备中的WebView的VersionCode*/
    private long currentWebViewVerCode;
    /*从服务器下载来的agent versionCode*/
    private long serverAgentVerCode;
    /*从服务器下载来的SDK的VersionCode*/
    private long serverSDKVerCode;
    /*从服务器下载来的WebView的VersionCode*/
    private long serverWebViewVerCode;
    /*当前设备中AgentSDK版本号*/
    String currentSDKVer;
    /*当前设备中WebView版本号*/
    String currentWebViewVer;
    /*Agent Package Name*/
    private final String strAgentPackageName="com.xacusa.tms";
    /*AgentSDK Package Name*/
    private final String strAgentSdkPackageName="com.xacsz.tms.client_sdk";
    /*WebView Package Name*/
    private final String strWebViewPackageName="com.android.webview";
    private final String strWebViewGooglePackageName="com.google.android.webview";
    /*Wifi 连接状态*/
    NetworkInfo mNetworkInfo=null;
    /*从后台服务器传回的URL*/
    List<JSONObject> listUrls=new ArrayList<>();
    /*获取URL成功标志*/
    Boolean bSuccessGetUrl=false;
    /*防止流量失控，校验码连续错误3次则停止下载,否则无限下载会出现问题*/
    private int checkSumErrorCount=0;
    /*连续下载失败5次停止下载*/
    private int checkDownloadFailCount = 0;
    /*重新更新3次则停止后续更新*/
    private int checkUpdateErrorCount=0;
    /* 当前安装的APP数量*/
    private int installedAPPCounts=0;
    /*所有要更新的文件名*/
    private List<String> fileNames=new ArrayList<>();
    private int bFailedInstalled=0;
    private final String strDownLoadPath="/storage/emulated/0/Download/";
    private final int RETRY_COUNT=100;
    SaioService saioService=null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,"onCreate");
        saioService=new SaioService(this);
        initDeviceInfo();
        postLogToBackEnd(INFO,"------------------------","TMSDownLoadService Start------------------------");
        checkApp(false);
    }

    //查看特定APP是否安装
    public Boolean checkApp(Boolean bFinishDownload)
    {
        PackageManager packageManager=UpdateService.this.getPackageManager();
        List<PackageInfo> Packages=packageManager.getInstalledPackages(0);
        for(PackageInfo i : Packages){
            if (strAgentPackageName.equals(i.packageName)) {
                bAgentInstalled=true;
                currentAgentVer=i.versionName;
                currentAgentVerCode=i.versionCode;
            }else if (strAgentSdkPackageName.equals(i.packageName)){
                bAgentSDKInstalled=true;
                currentSDKVer=i.versionName;
                currentSDKVerCode=i.versionCode;
            }else if (strWebViewPackageName.equals(i.packageName)||strWebViewGooglePackageName.equals(i.packageName)){
                bWebViewInstalled=true;
                currentWebViewVer=i.versionName;
                currentWebViewVerCode=i.versionCode;
            }
        }
        Log.i(TAG,"Agent is installed:"+ bAgentInstalled);
        Log.i(TAG,"AgentSDK is installed:"+ bAgentSDKInstalled);
        Log.i(TAG,"WebView is installed:"+ bWebViewInstalled);

        if (!bFinishDownload){
            if (bAgentInstalled){
                Log.i(TAG,"Agent exist,Version:"+currentAgentVer+",VerCode:"+currentAgentVerCode);
                postLogToBackEnd(INFO,"checkApp_001","Agent exist,Version:"+currentAgentVer+",VerCode:"+currentAgentVerCode);
            }
            if(bAgentSDKInstalled){
                Log.i(TAG,"AgentSDK exist,Version:"+currentSDKVer+",VerCode:"+currentSDKVerCode);
                postLogToBackEnd(INFO,"checkApp_002","AgentSDK exist,Version:"+currentSDKVer+",VerCode:"+currentSDKVerCode);
            }
            if (bWebViewInstalled){
                Log.i(TAG,"WebView exist,Version:"+currentWebViewVer+",VerCode:"+currentWebViewVerCode);
                postLogToBackEnd(INFO,"checkApp_003","WebView exist,Version:"+currentWebViewVer+",VerCode:"+currentWebViewVerCode);
            }
            if (!bAgentSDKInstalled&&!bAgentInstalled&&!bWebViewInstalled){
                Log.i(TAG,"None of required APP installed");
                postLogToBackEnd(INFO,"checkApp_004","None of required APP installed");
            }
//           else{
//                postLogToBackEnd(INFO,"checkApp_004","None of required APP installed");
//           }
        }
        return bFinishDownload && bAgentInstalled && bAgentSDKInstalled && bWebViewInstalled;
    }

    //初始化设备信息
    public void initDeviceInfo(){
        try{
            strProductName=StaticInfo.getProductName();
            Log.i(TAG,"ProductName:"+strProductName);
            strBuildNo=  StaticInfo.getBuildNo();
            Log.i(TAG,"BuildNo:"+strBuildNo);
            strSN=StaticInfo.getSN();
            Log.i(TAG,"SN:"+strSN);
            postLogToBackEnd(INFO,"initDeviceInfo_001","Init device info success,BuildNo:"+strBuildNo+",SN"+strSN+",ModuleName:"+strProductName);
        }catch(Exception ex){
            postLogToBackEnd(ERROR,"initDeviceInfo_001","Init device info fail:"+ex.toString());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG,"onStartCommand");
        //检查wifi连接状态
        workThreadStart();
        //向后台查询具体任务
        //如果三个APP都安装完毕，则直接启动Agent即可
        return super.onStartCommand(intent, flags, startId);
    }

    //从后台获取要更新APP列表(包含强制更新)
    private void getTaskFromBackEnd(){
        OkHttpClient client=new OkHttpClient().setProtocols(Collections.singletonList(Protocol.HTTP_1_1));
        RequestBody body = new FormEncodingBuilder()
                .add("ModuleName", strProductName)
                .add("BuildNo",strBuildNo)
                //.add("Platform",strSystemVer)
                .add("SN",strSN)
                .build();

        /*后台请求地址*/
        String strURL = "https://us-central1-xac-download-site.cloudfunctions.net/initialize_download";
        Request request=new Request.Builder()
                .url(strURL)
                .post(body)
                .build();

        Call call=client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
              //发送失败Log
                Log.e(TAG,"Failed to request url:"+e.toString());
                postLogToBackEnd(ERROR,"getTaskFromBackEnd_001","Failed to request url "+e.toString());
                bSuccessGetUrl=false;
            }
            @Override
            public void onResponse(Response response) throws IOException {
                String body=response.body().string();
                JSONObject jsonObj;
                try {
                    jsonObj = new JSONObject(body);
                   String strResponse=(String)jsonObj.get("status");
                    if (strResponse.equals("succeeded")){
                        listUrls.clear();
                        JSONArray json=jsonObj.getJSONArray("downloadUrl");
                        for (int i=0;i<json.length();i++){
                            JSONObject object=json.getJSONObject(i);
                            listUrls.add(object);
                        }
                        /*JSONObject object=json.getJSONObject(0);
                        listUrls.add(object);*/
                        bSuccessGetUrl=true;
                        Log.i(TAG,"Get URL from Backend success,Ready to download");
                        postLogToBackEnd(INFO,"getTaskFromBackEnd_002 ","Get url from backend success");
                    }else{
                        Log.e("UpdateService","Get url from backend fail:"+strResponse);
                        postLogToBackEnd(ERROR,"getTaskFromBackEnd_003 ","Get url from backend fail:"+strResponse);
                        bSuccessGetUrl=false;
                    }
                } catch (JSONException e) {
                    //e.printStackTrace();
                    Log.i("getTaskFromBackEnd",e.toString());
                    bSuccessGetUrl=false;
                    postLogToBackEnd(ERROR,"getTaskFromBackEnd_004 ",e.toString());
                }
            }
        });
    }

    private void postLogToBackEnd(String strTag, final String strWhere, final String strValue) {
      //  Log.i("LogInfo","Call postLog location:"+strWhere);
        OkHttpClient client = new OkHttpClient().setProtocols(Collections.singletonList(Protocol.HTTP_1_1));
        RequestBody body = new FormEncodingBuilder()
                .add("id",strSN)
                .add("codePath",strWhere)
                .add("clientTime:",getTime())
                .add(strTag, strValue)
                .build();

        /*后台请求地址*/
        String strURL="https://us-central1-xac-download-site.cloudfunctions.net/debug_log";
        //String strURL = "  https://sqa-tms2-001.cloudfunctions.net/tmsapi/v1/operation_logs/" + devID + "/debug";
        Request request = new Request.Builder()
                .url(strURL)
                .post(body)
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.e(TAG, "Fail to post log:" + e.toString());
            }
            @Override
            public void onResponse(Response response) throws IOException {
                String body = response.body().string();
                JSONObject jsonObj;
                try {
                    jsonObj = new JSONObject(body);
                    String strResponse = (String) jsonObj.get("status");
                    if (strResponse.equals("succeeded")) {
                        Log.i(TAG,"Post Log to backend success:"+strWhere);
                    }
                } catch (JSONException e) {
                    //e.printStackTrace();
                    Log.e("postLogToBackEnd",e.toString());
                }
            }
        });
    }

    List<DownloadFileInfo> lists=null;
    //获取Wifi连接状态
    private void workThreadStart(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                assert mConnectivityManager != null;
                mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
                while ( mNetworkInfo==null) {
                    try {
                        Log.i("UpdateService", "[Network] Wait Network Connected.");
                        Thread.sleep(10000);
                        mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
                    } catch (Exception e) {
                        //e.printStackTrace();
                        Log.e("workThreadStart",e.toString());
                    }
                }
                Log.i(TAG,"Network available ~");
                postLogToBackEnd(INFO,"workThreadStart_001","Network available");
                //从服务器获取指定需要更新的APP的URL
                while (!bSuccessGetUrl){
                    try {
                        getTaskFromBackEnd();
                        Thread.sleep(10000);
                        Log.i(TAG,"Wait for retry download Url");
                    } catch (InterruptedException e) {
                       // e.printStackTrace();
                        Log.e("workThreadStart_01",e.toString());
                    }
                }

                //URL下载成功并且没有需要更新的APP
                //执行启动Agent
               if (bSuccessGetUrl&&listUrls.size()==0){
                  // startAgent();   //目前不需要Download AP来启动了，由Installer启动
                   postLogToBackEnd(INFO,"workThreadStart_002","Get URL length=0,ready to shutdown updateService");
                   stopSelf();
                 //  saioService.reboot("TMSDownLoad Service Reboot test");
               }else{
                    lists=InitUpdateConfig();
                   if (lists!=null){
                       //开始执行下载
                       postLogToBackEnd(INFO,"workThreadStart_003","Get URL from Backend success,Total will update APK count:"+lists.size());
                       startDownloadAPP(lists);
                   }else{
                       postLogToBackEnd(INFO,"workThreadStart_004","No download object find ,service will shutdown...");
                       stopSelf();
                   }
               }
            }
        }).start();
    }

    //初始化更新事项
    public List<DownloadFileInfo> InitUpdateConfig() {
        List<DownloadFileInfo> listDownLoadFileInfos= new ArrayList<>();
        listDownLoadFileInfos.clear();
        fileNames.clear();
        if (listUrls==null)
            return null;
        for (JSONObject url:listUrls) {
            try {
                DownloadFileInfo fileInfo=new DownloadFileInfo();
                fileInfo.fileName =(String)url.get("fileName");
                fileInfo.hashValue =(String)url.get("hash");
                fileInfo.packageName=(String)url.get("packageName");
                fileInfo.fileURL=(String)url.get("url");
                fileInfo.fileType =(String)url.get("type");
                fileInfo.bForceUpdate=url.getBoolean("forceUpdate");  //强制更新flag
                fileInfo.versionCode=url.getLong("versionCode");
                if (checkIfUpdate(fileInfo)){
                    fileNames.add(fileInfo.fileName);
                    listDownLoadFileInfos.add(fileInfo);
                    Log.i(TAG,"Start to download fileName:"+fileInfo.fileName+"\n URL:"+fileInfo.fileURL+"hashValue:"+fileInfo.hashValue+"versionCode:"+fileInfo.versionCode+"forceUpdate:"+fileInfo.bForceUpdate+" PackageName:"+fileInfo.packageName);
                    postLogToBackEnd(INFO,"InitUpdateConfig","InitUpdateConfig OK,Will update APK Name-----:"+fileInfo.fileName+" URL:"+fileInfo.fileURL+"hashValue:"+fileInfo.hashValue+" versionCode:"+fileInfo.versionCode+" forceUpdate:"+fileInfo.bForceUpdate+" PackageName:"+fileInfo.packageName);
                }

              //  doDownload(fileInfo);
            } catch (Exception e) {
                //e.printStackTrace();
                Log.e("workThreadStart_005",e.toString());
                postLogToBackEnd(ERROR,"InitUpdateConfig_Err","Format URL error:"+e.toString());
                return null;
            }
        }
        return listDownLoadFileInfos;
    }

    public Boolean checkIfUpdate(DownloadFileInfo fileInfo){
        Boolean bNeedUpdate;

        if (!fileInfo.fileType.equals("APP"))
            return false;
        //强制更新的肯定是要更新
        if (fileInfo.bForceUpdate) {
            return true;
        }
        postLogToBackEnd(INFO,"checkIfUpdate_01","FileName:"+fileInfo.fileName+" CodeVersion:"+fileInfo.versionCode);
        switch (fileInfo.packageName){
            case strAgentPackageName:
                if (currentAgentVerCode < fileInfo.versionCode){
                    bNeedUpdate=true;
                    postLogToBackEnd(INFO,"checkIfUpdate","Agent will update");
                }else{
                    bNeedUpdate=false;
                    postLogToBackEnd(INFO,"checkIfUpdate","Current Agent Version is higher ,No need to update");
                }
                break;
            case strAgentSdkPackageName:
                if (currentSDKVerCode < fileInfo.versionCode){
                    bNeedUpdate=true;
                    postLogToBackEnd(INFO,"checkIfUpdate","AgentSDK will update");
                }else{
                    bNeedUpdate=false;
                    postLogToBackEnd(INFO,"checkIfUpdate","Current AgentSDK Version is higher ,No need to update");
                }
                break;
            case strWebViewPackageName:
            case strWebViewGooglePackageName:
                if (currentWebViewVerCode < fileInfo.versionCode){
                    bNeedUpdate=true;
                    postLogToBackEnd(INFO,"checkIfUpdate","WebView will update");
                }else{
                    bNeedUpdate=false;
                    postLogToBackEnd(INFO,"checkIfUpdate","Current WebView Version is higher ,No need to update");
                }
                break;
            default:
                bNeedUpdate=true;
                postLogToBackEnd(INFO,"checkIfUpdate",fileInfo.fileName+" will update");
                break;
        }

        return bNeedUpdate;
    }

    //执行下载流程
    public void startDownloadAPP(List<DownloadFileInfo> listUpdate){
        if (listUpdate==null)
            return;
        for (DownloadFileInfo fileInfo:listUpdate) {
            doDownload(fileInfo);
        }
    }

    //执行下载流程
    private void doDownload(DownloadFileInfo fileInfo)
    {
        File file = new File(fileInfo.path+fileInfo.fileName);
        if (file.exists()){
            if (file.delete()){
                Log.i(TAG,"File exist,delete it");
            }
            else{
                Log.e(TAG,"Failed to delete file:"+fileInfo.fileName);
                postLogToBackEnd(ERROR,"doDownload_001","Failed to delete file:"+fileInfo.fileName);
            }
        }
        fileInfo.file=file;

        final DownloadManager downloadManager=(DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileInfo.fileURL));

        //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileInfo.fileName);

        assert downloadManager != null;
        long id = downloadManager.enqueue(request);

        downloadListener(downloadManager,id,fileInfo);
    }

    //下载监听器
    private void downloadListener(DownloadManager downloadManager,long id,DownloadFileInfo fileInfo){
        Cursor cursor=null;
        DownloadManager.Query query=new DownloadManager.Query();
        query.setFilterById(id);
        boolean downloading=true;
        while(downloading)
        {
            try{
                Thread.sleep(3000);
                cursor=downloadManager.query(query);
                if(cursor!=null&&cursor.moveToFirst())
                {
                    int state=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    int errorCode=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                    String Reason=null;
                    switch(state)
                    {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            //重置连续失败次数
                            checkDownloadFailCount = 0;
                            downloading=false;
                            //查看校验码是否正确
                            String currentHash=getSHA1Value(fileInfo.file);
                            Log.i(TAG,"Get Download file hash:"+currentHash+"\n SeverHash:"+fileInfo.hashValue);
                            postLogToBackEnd(INFO,"downloadListener_001","Download success,fileName:"+fileInfo.fileName+",hash:"+currentHash+"\n SeverHash:"+fileInfo.hashValue);
                            assert currentHash != null;
                            if (currentHash.equalsIgnoreCase(fileInfo.hashValue)){
                                checkSumErrorCount=0;
                                startUpdate(fileInfo);
                             //   checkUpdateItem(fileInfo);
                            }else{ //校验码失败，重新下载
                                if (checkSumErrorCount<3){
                                    checkSumErrorCount++;
                                    postLogToBackEnd(ERROR,"downloadListener_002","CheckSum error,retry downloading...("+checkSumErrorCount+")");
                                    doDownload(fileInfo);
                                }else{
                                  //给后台发送错误码
                                  Log.e(TAG,"CheckSum error");
                                    postLogToBackEnd(ERROR,"downloadListener_003","CheckSum error for 3 times,Update "+fileInfo.fileName+" fail!");
                                    bFailedInstalled++;
                                    updateResult(fileInfo);
                                }
                            }
                            break;
                        case DownloadManager.STATUS_FAILED:
                            switch(errorCode)
                            {
                                case DownloadManager.ERROR_CANNOT_RESUME:
                                    Reason="some possibly transient error occurred but we can't resume the download.";
                                    break;
                                case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                                    Reason="no external storage device was found. Typically, this is because the SD card is not mounted.";
                                    break;
                                case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                                    Reason="the requested destination file already exists (the download manager will not overwrite an existing file)";
                                    break;
                                case DownloadManager.ERROR_FILE_ERROR:
                                    Reason="a storage issue arises which doesn't fit under any other error code.";
                                    break;
                                case DownloadManager.ERROR_HTTP_DATA_ERROR:
                                    Reason="an error receiving or processing data occurred at the HTTP level.";
                                    break;
                                case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                                    Reason="there was insufficient storage space. Typically, this is because the SD card is full.";
                                    break;
                                case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                                    Reason="there were too many redirects.";
                                    break;
                                case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                                    Reason="an HTTP code was received that download manager can't handle.";
                                    break;
                                case DownloadManager.ERROR_UNKNOWN:
                                    Reason="the download has completed with an error that doesn't fit under any other error code.";
                                    break;
                                default:
                                    Reason="Error Code:"+errorCode;
                                    break;
                            }

                            Log.i(TAG,"[Failed] "+Reason);
                            downloadManager.remove(id);
                            downloading=false;
                            if(checkDownloadFailCount < RETRY_COUNT){
                                checkDownloadFailCount++;
                                Log.e(TAG,"Download retry: fileName:"+fileInfo.fileName+"URL:"+fileInfo.fileURL);
                                postLogToBackEnd(ERROR,"downloadListener_004","Download retry: fileName:"+fileInfo.fileName+"URL:"+fileInfo.fileURL);
                                //如果前面重试次数大于8次了，接下来等30分钟再试
                                if (checkDownloadFailCount>8){
                                    Thread.sleep(1000*60*30);
                                }
                                doDownload(fileInfo);
                            }else{
                                Log.e(TAG,"Download fail"+RETRY_COUNT+" times: fileName:"+fileInfo.fileName+"URL:"+fileInfo.fileURL);
                                //发送log
                                postLogToBackEnd(ERROR,"downloadListener_005","Download fail 5 times: fileName:"+fileInfo.fileName+"URL:"+fileInfo.fileURL);
                            }
                            break;
                        case DownloadManager.STATUS_PAUSED:
                            switch(errorCode) {
                                case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                                    Reason="the download exceeds a size limit for downloads over the mobile network and the download manager is waiting for a Wi-Fi connection to proceed.";
                                    break;
                                case DownloadManager.PAUSED_UNKNOWN:
                                    Reason="the download is paused for some other reason.";
                                    break;
                                case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                                    Reason="the download is waiting for network connect.";
                                    break;
                                case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                    Reason="the download is paused because some network error occurred and the download manager is waiting before retrying the request.";
                                    break;
                            }
                            Log.i(TAG,"[Paused] "+Reason);
                            //postLogToBackEnd(ERROR,"downloadListener_006",Reason);
                            break;
                        case DownloadManager.STATUS_PENDING:
                            Log.i(TAG,"[Pending] Pending.");
                            break;
                        case DownloadManager.STATUS_RUNNING:
                            break;
                    }
                }
            }catch (Exception e)
            {
                Log.e("DownLoadError",e.toString());
                postLogToBackEnd(ERROR,"downloadListener_006","doDownload:"+e.toString());
            }
            finally {
                assert cursor != null;
                cursor.close();
            }
        }
    }

    public void checkUpdateItem(DownloadFileInfo fileInfo){
        if (fileInfo.bForceUpdate){
            startUpdate(fileInfo);
        }else{
            //判断一下当前版本是否高于下载的版本并且是否有强制更新标志
            if (fileInfo.fileName.contains("Agent")){
                  serverAgentVerCode=fileInfo.versionCode;
                if (fileInfo.versionCode>currentAgentVerCode){
                    startUpdate(fileInfo);
                }else{
                    Log.i(TAG,"Current Agent Version is higher than server's version，Force update is false,Will not update");
                    postLogToBackEnd(INFO,"checkUpdateItem_001","Current Agent Version is higher than server's version，Force update is false,Will not update");
                }
            }else if (fileInfo.fileName.contains("SDK")){
                serverSDKVerCode=fileInfo.versionCode;
                if (fileInfo.versionCode>currentSDKVerCode){
                    startUpdate(fileInfo);
                }else{
                    Log.i(TAG,"Current AgentSDK Version is higher than server's version，Force update is false,Will not update");
                    postLogToBackEnd(INFO,"checkUpdateItem_002","Current AgentSDK Version is higher than server's version，Force update is false,Will not update");
                }
            }else if (fileInfo.fileName.contains("webview")){
                serverWebViewVerCode=fileInfo.versionCode;
                if (fileInfo.versionCode>currentWebViewVerCode){
                    startUpdate(fileInfo);
                }else{
                    Log.i(TAG,"Current WebView Version is higher than server's version，Force update is false,Will not update");
                    postLogToBackEnd(INFO,"checkUpdateItem_003","Current WebView Version is higher than server's version，Force update is false,Will not update");
                }
            }else{
                if (fileInfo.fileType.equals("apk")){
                    startUpdate(fileInfo);
                    Log.i(TAG,"Unknown APK  start update... name:"+fileInfo.fileName);
                }else{
                    Log.i(TAG,"Unsupported update type :"+fileInfo.fileType+"fileName:"+fileInfo.fileName);
                    postLogToBackEnd(INFO,"checkUpdateItem_004","Unsupported update type :"+fileInfo.fileType+"fileName:"+fileInfo.fileName);
                }
            }
        }
    }
    public void clearCacheFiles(){
        try{
            //删除所有下载的文件
            for (String fileName:fileNames) {
                Log.i(TAG,"To be deleted file name:"+fileName);
                if (fileIsExists(strDownLoadPath+fileName)){
                    Log.i(TAG,"Clear download package:"+fileName+" success");
                    postLogToBackEnd(INFO,"startUpdate_004","Clear download package:"+fileName+" success");
                }else{
                    Log.i(TAG,"Clear download package:"+fileName+" fail");
                    postLogToBackEnd(ERROR,"startUpdate_005","Clear download package:"+fileName+" fail");
                }
            }
            Log.i(TAG,"Download APP finish all task ,shutdown...");
            postLogToBackEnd(INFO,"clearCacheFiles","Download APP finish all task ,shutdown...");
            stopSelf();
        }catch (Exception e){
            Log.e(TAG,"Error while clearCache:"+e.toString());
            postLogToBackEnd(INFO,"clearCacheFiles_01","Error while clearCache:"+e.toString());
        }

        //saioService.reboot("TMSDownLoad Service Reboot test");
    }
    //启动Agent
    public void startAgent(){
        PackageManager packageManager = UpdateService.this.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(strAgentPackageName);
        if (intent!=null) {
            startActivity(intent);
            onDestroy();
        }else{
            Log.i(TAG,"Failed to start Agent,Intent=Null");
            //把Log发送给Server
            //postLogToBackEnd("100");
        }
    }
  //private  Boolean bFinishReDownload=false;
    public void startUpdate(final DownloadFileInfo fileInfo){

        try{
            PackageManager apkPackageManager=getPackageManager();
            PackageInfo apkPackageInfo=apkPackageManager.getPackageArchiveInfo(fileInfo.path+fileInfo.fileName,0);
            Log.i(TAG,"Now start to update package url:"+fileInfo.path+fileInfo.fileName+"\n New APP Version:"+apkPackageInfo.versionName);
            postLogToBackEnd(INFO,"startUpdate_001","Now start to update package url:"+fileInfo.path+fileInfo.fileName+"\n New APP Version:"+apkPackageInfo.versionName);
            final String strCurrentFilePackageName=apkPackageInfo.packageName;
            final saioapi.pm.PackageManager packageManager=new saioapi.pm.PackageManager(getApplicationContext());
            OnPMObserver onPMObserver = new OnPMObserver() {
                @Override
                public void onEvent(int action, int retCode, String packageName) {
                    String strResult;
                    //retCode = -1;
                    if (action == saioapi.pm.PackageManager.ACTION_INSTALL) {
                        if (retCode == saioapi.pm.PackageManager.INSTALL_SUCCEEDED) {
                            //成功安装任何APK，则还原标志位
                            checkUpdateErrorCount=0;
                            installedAPPCounts++;
                            strResult ="Install " + fileInfo.fileName + " success!!"+" InstalledAPPCounts:"+installedAPPCounts;
                            switch (strCurrentFilePackageName) {
                                case strAgentPackageName:
                                    bAgentRemoteInstalled = true;
                                    break;
                                case strAgentSdkPackageName:
                                    bAgentSDKRemoteInstalled = true;
                                    break;
                                case strWebViewPackageName:
                                case strWebViewGooglePackageName:
                                    bWebViewRemoteInstalled = true;
                                    break;
                                default:
                                    Log.i(TAG, "Package:" + strCurrentFilePackageName + " installed");
                                    break;
                            }

                          //  updateResult(fileInfo);
                        } else {
                            strResult = "Install " + fileInfo.fileName + " failed, return code = " + retCode;
                            //尝试重新安装
                            if (checkUpdateErrorCount<5){
                                checkUpdateErrorCount++;
                                startUpdate(fileInfo);
                            }else{
                                bFailedInstalled++;
                                Log.e(TAG,"Install failed 5 times : " + fileInfo.fileName+" Failed install counts:"+bFailedInstalled);
                               //postLogToBackEnd(ERROR,"startUpdate_002_1","Install failed 5 times : " + fileInfo.fileName);
                                postLogToBackEnd(INFO,"startUpdate_002_2",("Install failed 5 times : " + fileInfo.fileName+" Failed install counts:"+bFailedInstalled));
                              // updateResult(fileInfo);

                            }
                        }
                        if (!strResult.isEmpty()){
                            Log.i(TAG, "[Install ] " + strResult);
                            postLogToBackEnd(INFO,"startUpdate_003","[Install ] " + strResult);
                        }
                        packageManager.finish();
                        updateResult(fileInfo);
                    }
                }
            };
            packageManager.setOnPMObserver(onPMObserver);
            packageManager.install(Uri.fromFile(new File(fileInfo.path+fileInfo.fileName)));
        }catch(Exception e){
          Log.e(TAG,"startUpdate->FileName:"+fileInfo.fileName+" get Exception"+e.toString());
            bFailedInstalled++;
           // clearCacheFiles(fileInfo);
            Log.e(TAG,"Install exception failed,File : " + fileInfo.fileName+" Failed install counts:"+bFailedInstalled);
            postLogToBackEnd(ERROR,"startUpdate_004",e.toString());
            postLogToBackEnd(INFO,"startUpdate_005",("Install failed 5 times : " + fileInfo.fileName+" Failed install counts:"+bFailedInstalled));
            updateResult(fileInfo);
        }
    }

    public void updateResult(final DownloadFileInfo fileInfo){
        try{

            if ((installedAPPCounts+bFailedInstalled)==lists.size()){
                Thread.sleep(60000);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG,"--updateResult--: installedAPPCount:"+installedAPPCounts+" FailedInstalled:"+bFailedInstalled+" Total need to installed APP counts:"+lists.size());
                        postLogToBackEnd(INFO,"--updateResult--",("--updateResult--: installedAPPCount:"+installedAPPCounts+" FailedInstalled:"+bFailedInstalled+" Total need to installed APP counts:"+lists.size()));
                        if (!checkFinishStatus()){
                            postLogToBackEnd(ERROR,"startUpdate_003","Some APKs are not same as server version");
                        }else{
                            postLogToBackEnd(INFO,"startUpdate_004","All APKS update success");
                        }
                        clearCacheFiles();
                    }
                }).start();
            }
        }
        catch (Exception e){
          Log.e(TAG,"updateResult_DF"+e.toString());
            postLogToBackEnd(ERROR,"startUpdate_005",e.toString());
        }
    }

    //检查安装后的结果
    public Boolean checkFinishStatus(){
        Boolean bRet=true;
       if (bAgentRemoteInstalled){
          if (!(currentAgentVerCode==serverAgentVerCode)){
              bRet=false;
              Log.e(TAG,"Current agent version is not same as server agent version");
              postLogToBackEnd(ERROR,"checkFinishStatus_001","Current agent version is not same as server agent version");
          }else{
              Log.i(TAG,"Agent check OK");
          }
       }
        if (bAgentSDKRemoteInstalled){
            if (!(currentSDKVerCode==serverSDKVerCode))
            {
                bRet=false;
                Log.e(TAG,"Current agentSDK version is not same as server agentSDK version");
                postLogToBackEnd(ERROR,"checkFinishStatus_002","Current agentSDK version is not same as server agentSDK version");

            }else{
                Log.i(TAG,"agentSDK check OK");
            }
        }
        if (bWebViewRemoteInstalled){
           if(!(currentWebViewVerCode==serverWebViewVerCode)){
               bRet=false;
               Log.e(TAG,"Current webView version is not same as server webView version");
               postLogToBackEnd(ERROR,"checkFinishStatus_003","Current webView version is not same as server webView version");
           }else{
               Log.i(TAG,"webView check OK");
           }
        }
        return bRet;
    }

    //判断文件是否存在
    public boolean fileIsExists(String strFile) {
        try {
            File f = new File(strFile);
            if (f.exists()) {
                if (f.delete()) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * get file md5
     * @param file filename
     * @return hashValue
     * @throws NoSuchAlgorithmException ex
     * @throws IOException ex
     */
    private static String getFileMD5(File file) throws NoSuchAlgorithmException, IOException {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest;
        FileInputStream in;
        byte[] buffer = new byte[1024];
        int len;
        digest = MessageDigest.getInstance("MD5");
        in = new FileInputStream(file);
        while ((len = in.read(buffer, 0, 1024)) != -1) {
            digest.update(buffer, 0, len);
        }
        in.close();
        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }

    public  String getSHA1Value(File file){
        StringBuilder builder = new StringBuilder();
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            MappedByteBuffer mappedByteBuffer = null;
            long bufferSize = 1024*1024*2;//每2M 读取一次，防止内存溢出
            long fileLength = file.length();//文件大小
            long lastBuffer = fileLength%bufferSize;//文件最后不足2M 的部分
            long bufferCount = fileLength/bufferSize;//
            for(int b = 0; b < bufferCount; b++){//分块映射
                mappedByteBuffer = fileInputStream.getChannel().map(FileChannel.MapMode.READ_ONLY, b*bufferSize, bufferSize);//使用内存映射而不是直接用IO读取文件，加快读取速度
                messageDigest.update(mappedByteBuffer);
            }
            if(lastBuffer != 0){
                mappedByteBuffer = fileInputStream.getChannel().map(FileChannel.MapMode.READ_ONLY, bufferCount*bufferSize, lastBuffer);
                messageDigest.update(mappedByteBuffer);
            }
            byte[] digest =messageDigest.digest();
            String hexString = "";
            for(int i =0; i < digest.length; i ++){
                hexString = Integer.toHexString(digest[i]&0xFF);//转16进制数，再转成哈希码
                if(hexString.length()<2){
                    builder.append(0);
                }
                builder.append(hexString);
            }
        } catch (FileNotFoundException e) {
           // e.printStackTrace();
            Log.e("getSHA1Value_001",e.toString());
            postLogToBackEnd(ERROR,"getSHA1Value_001",e.toString());
        } catch (NoSuchAlgorithmException e) {
            Log.e("getSHA1Value_002",e.toString());
            postLogToBackEnd(ERROR,"getSHA1Value_002",e.toString());
        } catch (IOException e) {
            Log.e("getSHA1Value_003",e.toString());
            postLogToBackEnd(ERROR,"getSHA1Value_003",e.toString());
        }finally{
            try {
                fileInputStream.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return builder.toString();
    }
    //获取当前时间
    private String getTime() {
        Calendar cal;
        String year;
        String month;
        String day;
        String hour;
        String minute;
        String second;
        String minSecond;
        cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));

        year = String.valueOf(cal.get(Calendar.YEAR));
        month = String.valueOf(cal.get(Calendar.MONTH) + 1);
        day = String.valueOf(cal.get(Calendar.DATE));
        if (cal.get(Calendar.AM_PM) == 0)
            hour = String.valueOf(cal.get(Calendar.HOUR));
        else
            hour = String.valueOf(cal.get(Calendar.HOUR) + 12);
        minute = String.valueOf(cal.get(Calendar.MINUTE));
        second = String.valueOf(cal.get(Calendar.SECOND));
        minSecond = String.valueOf(cal.get(Calendar.MILLISECOND));

        String my_time_1 = year + "-" + month + "-" + day;
        String my_time_2 = hour + ":" + minute + ":" + second + "-" + minSecond;
        return my_time_1 + " " + my_time_2;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy");
    }
}
