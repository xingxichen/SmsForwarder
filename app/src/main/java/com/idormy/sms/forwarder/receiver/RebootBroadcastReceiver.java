package com.idormy.sms.forwarder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.idormy.sms.forwarder.service.BatteryService;
import com.idormy.sms.forwarder.service.FrontService;
import com.idormy.sms.forwarder.service.MusicService;
import com.idormy.sms.forwarder.utils.InitUtil;
import com.idormy.sms.forwarder.utils.SettingUtil;

public class RebootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String receiveAction = intent.getAction();
        String TAG = "RebootBroadcastReceiver";
        Log.d(TAG, "onReceive intent " + receiveAction);
        if (receiveAction.equals("android.intent.action.BOOT_COMPLETED")) {
            InitUtil.init(context);

            //前台服务
            Intent frontServiceIntent = new Intent(context, FrontService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(frontServiceIntent);
            } else {
                context.startService(frontServiceIntent);
            }

            //电池状态监听
            Intent batteryServiceIntent = new Intent(context, BatteryService.class);
            context.startService(batteryServiceIntent);

            //后台播放无声音乐
            if (SettingUtil.getPlaySilenceMusic()) {
                context.startService(new Intent(context, MusicService.class));
            }
        }

    }

}
