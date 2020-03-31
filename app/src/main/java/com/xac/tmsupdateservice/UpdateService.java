package com.xac.tmsupdateservice;

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
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UpdateService extends Service {
    private String TAG="UpdateService";
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
    /*从后台服务器传回的URL*/
    List<JSONObject> listUrls=new ArrayList<>();
    /*获取URL成功标志*/
    Boolean bSuccessGetUrl=false;
    /*防止流量失控，校验码连续错误3次则停止下载,否则无限现在会出现问题*/
    private int checkSumErrorCount=0;
    /* 当前安装的APP数量*/
    private int installedAPPCounts=0;
    /*所有要更新的文件名*/
    private List<String> fileNames=new ArrayList<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,"onCreate");
        initDeviceInfo();
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
            }else if (strAgentSdkPackageName.equals(i.packageName)){
                bAgentSDKInstalled=true;
            }else if (strWebViewPackageName.equals(i.packageName)){
                bWebViewInstalled=true;
            }
        }
        Log.i(TAG,"Agent is installed:"+ bAgentInstalled);
        Log.i(TAG,"AgentSDK is installed:"+ bAgentSDKInstalled);
        Log.i(TAG,"WebView is installed:"+ bWebViewInstalled);
        if (bFinishDownload&&bAgentInstalled&&bAgentSDKInstalled&&bWebViewInstalled){
            return true;
        }
        return false;
    }

    //初始化设备信息
    public void initDeviceInfo(){
        strProductName=StaticInfo.getProductName();
        Log.i(TAG,"ProductName:"+strProductName);
        strBuildNo=  StaticInfo.getBuildNo();
        Log.i(TAG,"BuildNo:"+strBuildNo);
        strSN=StaticInfo.getSN();
        Log.i(TAG,"SN:"+strSN);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG,"onStartCommand");
        //检查wifi连接状态
        workThreadStart();
        //向后台查询具体任务

        //如果三个APP都安装完毕，则直接启动Agent即可
        if (bWebViewInstalled&&bAgentSDKInstalled&&bAgentInstalled){
            startAgent();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    //从后台获取要更新APP列表(包含强制更新)
    private void getTaskFromBackEnd(){

        OkHttpClient client=new OkHttpClient().setProtocols(Collections.singletonList(Protocol.HTTP_1_1));
        RequestBody body = new FormEncodingBuilder()
                .add("ModuleName", strProductName)
                .add("BuildID",strBuildNo)
                .add("SN",strSN)
                .build();

        /*后台请求地址*/
        String strURL = "https://us-central1-sitedispatcher-25215.cloudfunctions.net/initialize_download";
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
                        JSONArray json=jsonObj.getJSONArray("downloadUrl");
                        for (int i=0;i<json.length();i++){
                            JSONObject object=json.getJSONObject(i);
                            listUrls.add(object);
                        }
                        bSuccessGetUrl=true;
                    }else{
                        Log.e("UpdateService","Get url from backend fail:"+strResponse);
                        bSuccessGetUrl=false;
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    bSuccessGetUrl=false;
                }

            }
        });
    }

    //获取Wifi连接状态
    private void workThreadStart(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                assert mConnectivityManager != null;
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
                Log.i(TAG,"Wifi connected");

                //从服务器获取指定需要更新的APP的URL
                while (!bSuccessGetUrl){
                    try {
                        getTaskFromBackEnd();
                        Thread.sleep(10000);
                        Log.i(TAG,"Wait for retry download Url");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.i(TAG,"Get URL from Backend success,Ready to download");
                //URL下载成功并且没有需要更新的APP
                //执行启动Agent
               if (bSuccessGetUrl&&listUrls.size()==0){
                   startAgent();
               }else{
                 //开始执行下载
                   startDownloadAPP();
               }

            }
        }).start();
    }

    //执行下载流程
    public void startDownloadAPP(){

        if (listUrls==null)
            return;
        for (JSONObject url:listUrls) {
            try {
                DownloadFileInfo fileInfo=new DownloadFileInfo();
                fileInfo.fileName =(String)url.get("fileName");
                fileInfo.hashValue =(String)url.get("hash");
                fileInfo.fileURL=(String)url.get("url");
                fileInfo.fileType =(String)url.get("type");
                fileNames.add(fileInfo.fileName);
                Log.i(TAG,"Start to download fileName:"+fileInfo.fileName+"\n URL:"+fileInfo.fileURL+"hashValue:"+fileInfo.hashValue);
                doDownload(fileInfo);
            } catch (JSONException e) {
                e.printStackTrace();
            }
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
            }
        }
        fileInfo.file=file;

        final DownloadManager downloadManager=(DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileInfo.fileURL));

        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
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
                            downloading=false;
                            //查看校验码是否正确
                            String currentHash=getFileMD5(fileInfo.file);
                            Log.i(TAG,"Get Download file hash:"+currentHash+"\n SeverHash:"+fileInfo.hashValue);
                            assert currentHash != null;
                            if (currentHash.equals(fileInfo.hashValue)||true){
                                checkSumErrorCount=0;
                                startUpdate(fileInfo);
                            }else{ //校验码失败，重新下载
                                if (checkSumErrorCount<3){
                                    doDownload(fileInfo);
                                    checkSumErrorCount++;
                                }else{
                                  //给后台发送错误码
                                  Log.e(TAG,"CheckSum error");
                                  return;
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
                            }

                            Log.i(TAG,"[Failed] "+Reason);
                            downloadManager.remove(id);
                            downloading=false;
                            Log.e(TAG,"Download retry: fileName:"+fileInfo.fileName+"URL:"+fileInfo.fileURL);
                            doDownload(fileInfo);

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
                e.printStackTrace();
            }
            finally {
                assert cursor != null;
                cursor.close();
            }
        }
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
        }
    }

    public void startUpdate(final DownloadFileInfo fileInfo){

        PackageManager apkPackageManager=getPackageManager();
        PackageInfo apkPackageInfo=apkPackageManager.getPackageArchiveInfo(fileInfo.path+fileInfo.fileName,0);
        Log.i(TAG,"Now start to update package url:"+fileInfo.path+fileInfo.fileName+"\n New APP Version:"+apkPackageInfo.versionName);

        final String strCurrentFilePackageName=apkPackageInfo.packageName;
        final saioapi.pm.PackageManager packageManager=new saioapi.pm.PackageManager(getApplicationContext());
        OnPMObserver onPMObserver = new OnPMObserver() {
            @Override
            public void onEvent(int action, int retCode, String packageName) {
                String strResult;
                if (action == saioapi.pm.PackageManager.ACTION_INSTALL) {
                    if (retCode == saioapi.pm.PackageManager.INSTALL_SUCCEEDED) {
                        installedAPPCounts++;
                        strResult ="Install " + fileInfo.fileName + " successed!!";
                        if (strCurrentFilePackageName.equals(strAgentPackageName)){
                            bAgentInstalled=true;
                        }else if (strCurrentFilePackageName.equals(strAgentSdkPackageName)){
                            bAgentSDKInstalled=true;
                        }else if (strCurrentFilePackageName.equals(strWebViewPackageName)){
                            bWebViewInstalled=true;
                        }else{
                            Log.i(TAG,"Package:"+strCurrentFilePackageName+" installed");
                        }
                    } else {
                        strResult = "Install " + fileInfo.fileName + " failed, return code = " + retCode;
                        //尝试重新安装
                        startUpdate(fileInfo);
                        //发送错误代码给服务器
                    }
                    if (!strResult.isEmpty())
                        Log.i(TAG, "[Install ] " + strResult);
                    packageManager.finish();
                    if (installedAPPCounts==listUrls.size()&&checkApp(true)){
                        //删除所有下载的文件
                        for (String fileName:fileNames) {
                            if (fileIsExists(fileInfo.path+fileName)){
                              Log.i(TAG,"Clear download package:"+fileName+" success");
                            }else{
                                Log.i(TAG,"Clear download package:"+fileName+" fail");
                            }
                        }
                        Log.i(TAG,"Download APP finish all task ,shutdown...");
                        stopSelf();
                    }
                }
            }
        };
        packageManager.setOnPMObserver(onPMObserver);
        packageManager.install(Uri.fromFile(new File(fileInfo.path+fileInfo.fileName)));
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"onDestroy");
    }
}
