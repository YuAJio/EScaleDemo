package com.mysafe.esdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InitView();
    }

    private TextView tv_Weight;
    private FrameLayout fl_CC;
    private Button bt_TIOn;
    private Button bt_TIOff;

    private void InitView() {
        tv_Weight = findViewById(R.id.tv_WeightNum);
        fl_CC = findViewById(R.id.fl_CameraCarrier);
        bt_TIOff = findViewById(R.id.bt_TurnOff);
        bt_TIOn = findViewById(R.id.bt_TurnOn);

    }
}
