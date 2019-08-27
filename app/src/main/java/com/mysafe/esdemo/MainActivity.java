package com.mysafe.esdemo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.estestsdk.EScaleManager;
import com.example.estestsdk.MsCameraSurfaceView;
import com.example.estestsdk.boradcastes.UsbCameraBroadcastReceiver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;

import Beans.EScaleData;
import Beans.SensorDeviceParmas;
import Beans.SensorDeviceParmasDetail;
import Utils.AndroidPreferenceProvider;
import Utils.FilePathManager;
import Utils.LocalFileUtil;

public class MainActivity extends AppCompatActivity implements UsbCameraBroadcastReceiver.IUsbCameraStateCallBck, MsCameraSurfaceView.IMCSV_CallBack {

    private static final String SpProjectCode = "ESCaleDemo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //初始化SDK中的文件管理模块
        FilePathManager.GetInstance().Init(this);
        //初始化SDK中的Sp模块
        AndroidPreferenceProvider.GetInstance().Init(this, SpProjectCode);
        ///初始化的操作都可放在Application中

        //定义SDK中的Sp存储区域代号*必须*
        EScaleManager.GetInstance().SetSpAuthKey(SpProjectCode);

        InitView();

        InitSDKManager();
    }

    private TextView tv_Weight;
    private FrameLayout fl_CC;

    /**
     * 初始化控件
     */
    private void InitView() {
        tv_Weight = findViewById(R.id.tv_WeightNum);
        fl_CC = findViewById(R.id.fl_CameraCarrier);
        Button bt_TIOff = findViewById(R.id.bt_TurnOff);
        Button bt_TIOn = findViewById(R.id.bt_TurnOn);
        Button bt_TakeAShot = findViewById(R.id.bt_TakeAShot);

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
        bt_TakeAShot.setOnClickListener(new View.OnClickListener() {
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
        //检查称重参数是否初始化完成
        boolean isInit = CheckInitFinished();
        if (isInit) {
            //创建接受称重数据实例
            WeightDataInvoke invoke = new WeightDataInvoke(this, tv_Weight);
            //设置接收传感器参数的实现接口类
            EScaleManager.GetInstance().SetInterface(invoke);
            //重置参数
            EScaleManager.GetInstance().ResetParams();
            //初始化称重接收器,开始接收数据
            EScaleManager.GetInstance().InitSerialPort();
        } else {
            //出现未初始化的情况时
            //请先返回OS页面初始化传感器
            Log.i("Info", "称重参数未初始化");
        }

    }

    /**
     * 控件点击事件
     *
     * @param id
     */
    private void OnclickEvent(int id) {
        switch (id) {
            case R.id.bt_TurnOn: {//打开闪光灯
                EScaleManager.GetInstance().FlashLight_TurnItOn();
            }
            break;
            case R.id.bt_TurnOff: {//关闭闪光灯
                EScaleManager.GetInstance().FlashLight_TurnItOff();
            }
            break;
            case R.id.bt_TakeAShot: {//拍一张照
                if (isOpenCamera)
                    TakePicture();
            }
            break;
        }
    }

    /**
     * 检查本地参数文件是否完整
     */
    private boolean CheckInitFinished() {
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
        boolean b2 = AndroidPreferenceProvider.GetInstance().GetBoolean("EInitFinished", SpProjectCode);
        boolean b = b2 || b1;
        if (b1 && !b2) {
            //如果本地初始化成功但SP未正常初始化
            EScaleManager.GetInstance().InitOutPutStable(true);
            EScaleManager.GetInstance().InitZeroValue(esParamsObject.ZeroPoint);
            EScaleManager.GetInstance().InitMaxWeight(esParamsObject.MaxWeight);
            EScaleManager.GetInstance().InitJingdu(esParamsObject.Jingdu);
            AndroidPreferenceProvider.GetInstance().PutFloat("EJingduPerAd", esParamsObject.JingduAD, SpProjectCode);
            AndroidPreferenceProvider.GetInstance().PutBoolean("EInitFinished", true, SpProjectCode);
        }
        return b;
    }

    //region 摄像头相关
    //摄像头控件
    private MsCameraSurfaceView iv_Camera;
    //Usb摄像头挂载检测广播
    private UsbCameraBroadcastReceiver usbCameraBroadcastReceiver;
    //是否存在摄像头
    private boolean isOpenCamera;
    //拍摄图片保存路径
    private String takePictureFilePath;

    /**
     * 拍摄照片
     */
    private void TakePicture() {
        takePictureFilePath = null;
        //获取拍摄后保存地址
        takePictureFilePath = GetPhotoPath();
        iv_Camera.TakePic(takePictureFilePath);
    }

    /**
     * 挂载以及初始化摄像头
     */
    private void HandleCheckCamera() {
        if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            if (iv_Camera != null) {
                iv_Camera.CleanMCSV_CallBack();
                fl_CC.removeAllViews();
                iv_Camera = null;
            }
            iv_Camera = new MsCameraSurfaceView(this);
            iv_Camera.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv_Camera.SetMCSV_CallBack(this);
            fl_CC.addView(iv_Camera);
        }
    }

    //<editor-fold desc="广播注册">
    private void RegisterUsbCameraBroadcastReceiver() {
        usbCameraBroadcastReceiver = new UsbCameraBroadcastReceiver();
        usbCameraBroadcastReceiver.SetCallBack(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("Ys.ES.Receivers:UsbCameraBroadcastReceiver");
        this.registerReceiver(usbCameraBroadcastReceiver, filter);
    }

    private void UnRegisterUsbCameraBroadcastReceiver() {
        if (usbCameraBroadcastReceiver != null) {
            iv_Camera.HandleCamera(0);
            usbCameraBroadcastReceiver.CleanCallBack();
            unregisterReceiver(usbCameraBroadcastReceiver);
            usbCameraBroadcastReceiver = null;
        }
    }
    //</editor-fold>


    /**
     * 获取拍摄照片图片路径
     *
     * @return
     */
    private String GetPhotoPath() {
        //获取的地址在存储中Android.data.包名中
        String cachePath =
                FilePathManager.GetInstance().GetPrivateRootDirPath()
                        + "/"
                        + "TestImageDir"
                        + "/";
        FilePathManager.GetInstance().CreateDir(cachePath);
        return Combine(cachePath, "TestPic.jpg");
    }

    /**
     * 组装地址
     *
     * @param path1 目录
     * @param path2 文件名
     * @return 组装好的地址
     */
    public static String Combine(String path1, String path2) {
        File file1 = new File(path1);
        File file2 = new File(file1, path2);
        return file2.getPath();
    }


    /**
     * @param takePictureResult
     */
    @Override
    public void TakePictureResultAction(MsCameraSurfaceView.TakePictureResult takePictureResult) {
        switch (takePictureResult) {
            case None:
                break;
            case Fail: {
                //拍照失败
            }
            break;
            case Exception: {
                //拍照异常
            }
            break;
            case PicPathNull: {
                //拍照异常
            }
            break;
            case CameraServiceFail: {
                //摄像头不存在
            }
            break;
            case Success: {
                //成功 路径下出现了图片
                //可直接做保存或上传处理
                Toast.makeText(this, "照片保存成功", Toast.LENGTH_LONG).show();
            }
            break;
        }
    }

    @Override
    public void CameraInitStateAction(MsCameraSurfaceView.CameraInitState cameraInitState) {
        switch (cameraInitState) {
            case Fail: {
                //摄像头挂载失败
                isOpenCamera = false;
            }
            break;
            case Success: {
                isOpenCamera = true;
            }
            break;
        }
    }

    @Override
    public void UsbCameraStateAction(Intent intent) {
        int state = intent.getIntExtra("UsbCameraState", -1);

        if (iv_Camera != null)
            iv_Camera.HandleCamera(state);
    }
    //endregion

    //region 生命周期
    @Override
    protected void onResume() {
        super.onResume();
        RegisterUsbCameraBroadcastReceiver();
        HandleCheckCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //关闭接收端口
        EScaleManager.GetInstance().CloseSerialPort();
        UnRegisterUsbCameraBroadcastReceiver();
    }
    //endregion

    //region 接口实现类

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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //接收的重量 单位为g
                    String showWeight = String.valueOf(eScaleData.Weight + "g");
                    tv_Loader.setText(showWeight);
                }
            });
        }

    }
    //endregion
}
