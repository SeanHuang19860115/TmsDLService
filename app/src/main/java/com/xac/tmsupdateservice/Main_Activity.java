package com.xac.tmsupdateservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public class Main_Activity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Main_Activity","onCreate");
        setContentView(R.layout.activity_main);
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
