package com.mysafe.esdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.text.BoringLayout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ContentView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mysafe.esdemo.Boradcastes.UsbCameraBroadcastReceiver;
import com.mysafe.esdemo.Interfaces.IUsbCameraStateCallBck;
import com.yurishi.camerax.interfaces.ICameraFrameAnalysis;
import com.yurishi.camerax.interfaces.ITakePictureCallBack;
import com.yurishi.camerax.views.CameraX;

import java.io.File;
import java.util.UUID;

import CupCake.EScaleController;
import CupCake.Enums.InitStateCode;
import CupCake.Interfaces.IWeightingDataReceiver;
import CupCake.Moudle.EScaleData;

public class MainActivity extends AppCompatActivity implements IUsbCameraStateCallBck
        , ICameraFrameAnalysis, ITakePictureCallBack {

    private static final String SpProjectCode = "ESCaleDemo";
    private static final int PERMISSION_REQUEST_CODE_STORAGE = 0x114;
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 0x514;

    private Boolean isInitFinish = false;

    /**
     * 防止重复初始化摄像头
     */
    private static boolean InitCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InitView();

        //检测是否开启对应权限
        if (IsThatPermissionsOn(true, Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSION_REQUEST_CODE_STORAGE))
            InitEsSDK();
//        InitSDKManager();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !InitCamera)
            if (IsThatPermissionsOn(true, Manifest.permission.CAMERA, PERMISSION_REQUEST_CODE_CAMERA))
                HandleCheckCamera();
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
    private CameraX cx_Camera;

    /**
     * 初始化控件
     */
    private void InitView() {
        tv_Weight = findViewById(R.id.tv_WeightNum);
        cx_Camera = findViewById(R.id.cx_Camera);
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
    private boolean IsThatPermissionsOn(boolean isRequestOpenPer, String permission, int requestCode) {

        if (Build.VERSION.SDK_INT >= 23) {
            int checkWriteStoragePermission = ContextCompat.checkSelfPermission(this, permission);
            if (checkWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
                if (isRequestOpenPer)
                    ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
                return false;
            } else
                return true;

        } else
            return true;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case PERMISSION_REQUEST_CODE_STORAGE:
                    InitEsSDK();
                    break;
                case PERMISSION_REQUEST_CODE_CAMERA:
                    HandleCheckCamera();
                    break;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //endregion

    //region 摄像头相关(摄像头操作为普通Android_Camera,可使用其他框架或原生自定义代替)
    //Usb摄像头挂载检测广播
    private UsbCameraBroadcastReceiver usbCameraBroadcastReceiver;
    //拍摄图片保存路径
    private String takePictureFilePath;

    /**
     * 拍摄照片
     */
    private void TakePicture() {
        takePictureFilePath = null;
        //获取拍摄后保存地址
        takePictureFilePath = GetPhotoPath();
        cx_Camera.TakeAPicture(new File(takePictureFilePath));
    }

    /**
     * 初始化以及绑定摄像头的LifeCycle
     */
    private void HandleCheckCamera() {
        if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            BindCameraXInterface(true);
            //绑定摄像头,在这之前要先检查是否打开了摄像头权限,如果没有则通知打开
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cx_Camera.InitAndStartCamera(this, this);
                InitCamera = true;
            }
        }
    }

    /**
     * 摄像头每帧画面回调
     *
     * @param imageProxy
     */
    @Override
    public void Analyze(androidx.camera.core.ImageProxy imageProxy) {
        imageProxy.close();
    }

    /**
     * 拍照结果回调_成功
     *
     * @param uri 照片保存的地址
     *            (*注意*)
     *            Uri一般结果将会是Null,只有在ImageCapture.OutputFileOptions中配置了特定参数才会正常返回
     *            在Uri为空的情况下 图片保存的地址为调用拍照方法时传入的地址↓
     * @see #takePictureFilePath
     */
    @Override
    public void OnSuccess(Uri uri) {
        //TODO 处理拍摄好的照片
    }

    /**
     * 拍照结果回调_失败
     *
     * @param e 失败详情{@link androidx.camera.core.ImageCaptureException}
     */
    @Override
    public void OnFailed(androidx.camera.core.ImageCaptureException e) {
        //TODO 检查拍照异常原因 并处理
    }


    /**
     * 注册或者注销摄像头控件的接口回调(可选)
     */
    private void BindCameraXInterface(boolean isBind) {
        if (isBind) {
            cx_Camera.SetInterface_CameraFrameAnalysis(this);//摄像头Preview画面的每帧回调
            cx_Camera.SetInterface_TakePictureCallBack(this);//摄像头拍照结果回调
        } else {
            cx_Camera.CancelInterface_CameraFrameAnalysis();
            cx_Camera.CancelInterface_TakePictureCallBack();
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
            return Combine(cachePath, "TestPic" + UUID.randomUUID() + ".jpg");
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
            if (!dir.exists())
                return dir.mkdirs();
            return
                    true;
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
     * 检测到USB摄像头连接状态改变
     *
     * @param intent
     */
    @Override
    public void UsbCameraStateAction(Intent intent) {
        //TODO 自行选择处理逻辑
    }
    //endregion

    //region 生命周期
    @Override
    protected void onResume() {
        super.onResume();
        if (isInitFinish) {
            RegisterUsbCameraBroadcastReceiver();
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
            //解绑摄像头相关接口
            BindCameraXInterface(false);
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
