package com.mysafe.esdemo.Boradcastes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mysafe.esdemo.Interfaces.IUsbCameraStateCallBck;


public class UsbCameraBroadcastReceiver extends BroadcastReceiver {
    private IUsbCameraStateCallBck callBack;

    public void SetCallBack(IUsbCameraStateCallBck callBack) {
        this.callBack = callBack;
    }

    public void CleanCallBack() {
        this.callBack = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (callBack != null)
            callBack.UsbCameraStateAction(intent);
    }
}
