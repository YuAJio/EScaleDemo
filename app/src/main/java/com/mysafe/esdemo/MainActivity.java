package com.mysafe.esdemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.BoringLayout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mysafe.esdemo.Boradcastes.UsbCameraBroadcastReceiver;
import com.mysafe.esdemo.Interfaces.IUsbCameraStateCallBck;

import java.io.File;

import CupCake.EScaleController;
import CupCake.Enums.CameraInitState;
import CupCake.Enums.InitStateCode;
import CupCake.Enums.TakePictureResult;
import CupCake.Interfaces.IMsUsbCameraCallBack;
import CupCake.Interfaces.IMsUsbCameraStateCallBack;
import CupCake.Interfaces.IWeightingDataReceiver;
import CupCake.Moudle.EScaleData;
import CupCake.Views.MsCameraSurfaceView;

public class MainActivity extends AppCompatActivity implements IUsbCameraStateCallBck, IMsUsbCameraCallBack {

    private static final String SpProjectCode = "ESCaleDemo";

    private Boolean isInitFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //检测是否开启对应权限
        if (IsThatPermissionsOn(true, Manifest.permission.READ_EXTERNAL_STORAGE))
            InitEsSDK();

        InitView();
//        InitSDKManager();
    }


    /***
     * 初始化SDK环境
     */
    private void InitEsSDK() {
        //对SDK进行初始化
        InitStateCode initCode = EScaleController.GetInstance().Init(this, SpProjectCode);
        if (initCode != InitStateCode.Succeed)
            //如果初始化失败,则可能无法正常获取称重重量
            Toast.makeText(this, "初始化SDK失败,错误码为:" + initCode.toString(), Toast.LENGTH_LONG).show();
        else
            isInitFinish = true;
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
     * 打开串口,并接收传感器数据
     */
    private void OpenAndReceiveWeightingData() {
        //创建接受称重数据实例
        WeightDataInvoke invoke = new WeightDataInvoke(this, tv_Weight);
        //设置接收传感器参数的实现接口类
        EScaleController.GetInstance().SetWeightingReceiver(invoke);
        //打开传感器串口(参数为设置接收传感器参数的实现接口类,若以实现 则可传null)
        EScaleController.GetInstance().OpenSensor(null);

        EScaleController.GetInstance().GetZeroWeight();
    }

    /**
     * 控件点击事件
     *
     * @param id
     */
    private void OnclickEvent(int id) {
        switch (id) {
            case R.id.bt_TurnOn: {//打开闪光灯
                EScaleController.GetInstance().FlashLight_TurnItOn();
            }
            break;
            case R.id.bt_TurnOff: {//关闭闪光灯
                EScaleController.GetInstance().FlashLight_TurnItOff();
            }
            break;
            case R.id.bt_TakeAShot: {//拍一张照
                if (isOpenCamera)
                    TakePicture();
            }
            break;
        }
    }

    //region 权限请求相关

    /**
     * 检查是否打开指定权限
     *
     * @param isRequestOpenPer
     * @param permission
     * @return
     */
    private boolean IsThatPermissionsOn(boolean isRequestOpenPer, String permission) {

        if (Build.VERSION.SDK_INT >= 23) {
            int checkWriteStoragePermission = ContextCompat.checkSelfPermission(this, permission);
            if (checkWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
                if (isRequestOpenPer)
                    ActivityCompat.requestPermissions(this, new String[]{permission}, 0x114);
                return false;
            } else
                return true;

        } else
            return true;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 0x114) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                InitEsSDK();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //endregion

    //region 摄像头相关(摄像头操作为普通Android_Camera,可使用其他框架或原生自定义代替)
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
        if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
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
                Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/"
                        + "TestImageDir"
                        + "/";
        if (CreateDir(cachePath)) {
            return Combine(cachePath, "TestPic.jpg");
        }
        return "";
    }

    /**
     * 创建文件夹
     *
     * @param path
     * @return
     */
    private boolean CreateDir(String path) {
        try {
            File dir = new File(path);
            return dir.mkdirs();
        } catch (Exception ex) {
            return false;
        }
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
     * @param result
     */
    @Override
    public void TakePictureResultAction(TakePictureResult result) {
        switch (result) {
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
    public void CameraInitStateAction(CameraInitState state) {
        switch (state) {
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
        if (isInitFinish) {
            RegisterUsbCameraBroadcastReceiver();
            HandleCheckCamera();
            OpenAndReceiveWeightingData();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isInitFinish) {
            //关闭接收端口
            EScaleController.GetInstance().CloseSensor();
            UnRegisterUsbCameraBroadcastReceiver();
            //关闭传感器端口
            EScaleController.GetInstance().CloseSensor();
        }

    }
    //endregion

    //region 接口实现类

    /**
     * 传感器数据接受实现类
     */
    private class WeightDataInvoke implements IWeightingDataReceiver {
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
