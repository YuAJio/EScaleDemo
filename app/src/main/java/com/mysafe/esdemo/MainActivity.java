package com.mysafe.esdemo;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.estestsdk.EScaleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import Beans.EScaleData;
import Beans.SensorDeviceParmas;
import Beans.SensorDeviceParmasDetail;
import Utils.AndroidPreferenceProvider;
import Utils.LocalFileUtil;

public class MainActivity extends AppCompatActivity {

    private static final String SpPrjectCode = "ESCaleDemo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //初始化SDK中的Sp模块
        AndroidPreferenceProvider.GetInstance().Init(this, SpPrjectCode);
        //定义SDK中的Sp存储区域代号*必须*
        EScaleManager.GetInstance().SetSpAuthKey(SpPrjectCode);

        InitView();

        InitSDKManager();
    }

    private TextView tv_Weight;
    private FrameLayout fl_CC;
    private Button bt_TIOn;
    private Button bt_TIOff;

    /**
     * 初始化控件
     */
    private void InitView() {
        tv_Weight = findViewById(R.id.tv_WeightNum);
        fl_CC = findViewById(R.id.fl_CameraCarrier);
        bt_TIOff = findViewById(R.id.bt_TurnOff);
        bt_TIOn = findViewById(R.id.bt_TurnOn);

        bt_TIOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnclickEvent(v.getId());
            }
        });
        bt_TIOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnclickEvent(v.getId());
            }
        });
    }

    /**
     * 初始化SDK的管理器
     */
    private void InitSDKManager() {
        //检查是否初始化称重参数
        boolean isInit = ChechInitFinished();
        if (isInit) {
            //创建接受称重数据实例
            WeightDataInvoke invoke = new WeightDataInvoke(this, tv_Weight);
            //设置实现
            EScaleManager.GetInstance().SetInterface(invoke);
            //重置参数
            EScaleManager.GetInstance().ResetParams();
            //初始化称重接收器,开始接收数据
            EScaleManager.GetInstance().InitSerialPort();
        } else {
            Log.i("Info", "称重参数未初始化");
        }

    }

    private void OnclickEvent(int id) {
        switch (id) {
            case R.id.bt_TurnOn: {
                EScaleManager.GetInstance().FlashLight_TurnItOn();
            }
            break;
            case R.id.bt_TurnOff: {
                EScaleManager.GetInstance().FlashLight_TurnItOff();
            }
            break;
        }
    }

    /**
     * 检查本地参数文件是否完整
     */
    private boolean ChechInitFinished() {
        //获取本地称重参数保存文件
        SensorDeviceParmas esParams = LocalFileUtil.GetOneAndOnlySensorParams();
        if (esParams == null)
            return false;
        //初始化Gson
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();

        //检测是否在本地和SP中都已初始化
        SensorDeviceParmasDetail esParamsObject = esParams.getDetail().equals("") ? null : gson.fromJson(esParams.getDetail(), SensorDeviceParmasDetail.class);
        boolean b1 = esParamsObject != null && (esParamsObject.isInit() && (esParamsObject.getJingdu() > 0));
        boolean b2 = AndroidPreferenceProvider.GetInstance().GetBoolean("EInitFinished", SpPrjectCode);
        boolean b = b2 || b1;
        if (b1 && !b2) {
            //如果本地初始化成功但SP未正常初始化
            EScaleManager.GetInstance().InitOutPutStable(true);
            EScaleManager.GetInstance().InitZeroValue(esParamsObject.ZeroPoint);
            EScaleManager.GetInstance().InitMaxWeight(esParamsObject.MaxWeight);
            EScaleManager.GetInstance().InitJingdu(esParamsObject.Jingdu);
            AndroidPreferenceProvider.GetInstance().PutFloat("EJingduPerAd", esParamsObject.JingduAD, SpPrjectCode);
            AndroidPreferenceProvider.GetInstance().PutBoolean("EInitFinished", true, SpPrjectCode);
        }
        return b;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //关闭接收端口
        EScaleManager.GetInstance().CloseSerialPort();
    }

    /**
     * 传感器数据接受实现类
     */
    private class WeightDataInvoke implements EScaleManager.IEsCaleDateReceive {
        private Context context;
        private TextView tv_Loader;

        public WeightDataInvoke(Context context) {
            this.context = context;
        }

        public WeightDataInvoke(Context context, TextView tv_Loader) {
            this.context = context;
            this.tv_Loader = tv_Loader;
        }

        @Override
        public void ReceiveData(final EScaleData eScaleData) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    tv_Loader.setText(String.valueOf(eScaleData.Weight));
//                }
//            });
        }

    }
}
