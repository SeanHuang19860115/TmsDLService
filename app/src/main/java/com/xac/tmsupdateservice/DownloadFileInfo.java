package com.xac.tmsupdateservice;

import java.io.File;

public class DownloadFileInfo {

    //文件名
    public String fileName;
    //哈希值
    public String hashValue;
    //对象
    public File file;
    //是否强制更新
    public Boolean bUpdate;

    //APK的版本
    public long versionCode;
    //文件类型
    public String fileType;
    //文件下载路径
    public String fileURL;
    //文件路径
    public  String path="/storage/emulated/0/Download/";
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setHashValue(String hashValue) {
        this.hashValue = hashValue;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setFileURL(String fileURL) {
        this.fileURL = fileURL;
    }

    public String getFileName() {
        return fileName;
    }


    public String getHashValue() {
        return hashValue;
    }

    public File getFile() {
        return file;
    }

    public String getFileType() {
        return fileType;
    }

    public String getFileURL() {
        return fileURL;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(long versionCode) {
        this.versionCode = versionCode;
    }

}
