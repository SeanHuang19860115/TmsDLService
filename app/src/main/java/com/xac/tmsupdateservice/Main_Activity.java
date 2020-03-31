package com.xac.tmsupdateservice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class Main_Activity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Main_Activity","onCreate");
        setContentView(R.layout.activity_main);
        //permission
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_PERMISSION_STORAGE = 100;
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    this.requestPermissions(permissions, REQUEST_CODE_PERMISSION_STORAGE);
                    return;
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("Main_Activity","onResume");
        Intent intent=new Intent();
        intent.setClass(Main_Activity.this,UpdateService.class);
        startService(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("Main_Activity","onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i("Main_Activity","onDestroy");
    }
}
