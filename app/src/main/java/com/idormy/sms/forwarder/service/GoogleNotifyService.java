package com.idormy.sms.forwarder.service;


import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.idormy.sms.forwarder.MainActivity;
import com.idormy.sms.forwarder.R;
import com.idormy.sms.forwarder.utils.SettingUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class GoogleNotifyService extends FirebaseMessagingService {
    private final String TAG = "NotifyService";
    public final static String GATEWAY_LOG_PREFS = "GATEWAY_LOG_PREFS";  // 保存网关日志的XML文件名
    public final static String OTHER_LOG = "OTHER_LOG";  // 其他需要在消息中心显示的日志
    private int requestCode = 0;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // 接收到数据，执行如下操作：
        // 1. 解析并持久化消息；
        // 2. 创建并显示通知；
        if (remoteMessage.getData().size() > 0) {
            String userId = getCurrentUserId();
            Log.d(TAG, "userId is " + userId);
            if (!userId.equals("")) {
                Map<String, String> data = remoteMessage.getData();
                String msgType = data.get("msg_type");
                String body = data.get("body");
                Log.d(TAG, "msgType is " + msgType + " body is " + body);

                if (msgType != null) {
                    if (msgType.equals("PowerOff")) {
                        try {
                            long currentTime = System.currentTimeMillis();

                            JSONObject jsonObject = new JSONObject();
                            jsonObject.putOpt("msgType", msgType);
                            jsonObject.putOpt("body", body);
                            String GATEWAY_LOG_PREFS_WITH_USER_ID = userId + "_" + GATEWAY_LOG_PREFS;  // user id + 特定名称 构造存储消息文件名
                            saveNotifyMsgToPrefs(GATEWAY_LOG_PREFS_WITH_USER_ID, String.valueOf(currentTime), jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            long currentTime = System.currentTimeMillis();

                            JSONObject jsonObject = new JSONObject();
                            jsonObject.putOpt("msgType", msgType);
                            jsonObject.putOpt("body", body);
                            String OTHER_LOG_PREFS_WITH_USER_ID = userId + "_" + OTHER_LOG;  // user id + 特定名称 构造存储消息文件名
                            saveNotifyMsgToPrefs(OTHER_LOG_PREFS_WITH_USER_ID, String.valueOf(currentTime), jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        if (remoteMessage.getNotification() != null) {
            String notifyTitle = remoteMessage.getNotification().getTitle();
            String notifyBody = remoteMessage.getNotification().getBody();
            Log.d(TAG, "notification title is " + notifyTitle);
            Log.d(TAG, "notification body is " + notifyBody);
            sendNotification(notifyTitle, notifyBody);
        }
    }

    @Override
    public void onNewToken(@NonNull String refreshToken) {
        super.onNewToken(refreshToken);
        Log.d(TAG, "refreshed token: " + refreshToken);
        SettingUtil.setAddExtraDeviceMark(SettingUtil.getAddExtraDeviceMark() + "---" + refreshToken);
        // 1. 持久化生成的token，
        // 2. 发送事件通知RN层，分为两种情况：
        //      用户未登录，RN层不做处理（待用户登录后读取本地存储的token，并上报）
        //      用户已登录，RN层获取当前用户id、token及当前语言上报服务端
        SharedPreferences.Editor editor = getSharedPreferences("fcmToken", MODE_PRIVATE).edit();
        editor.putString("token", refreshToken);
        editor.apply();
        sendRefreshTokenBroadcast(refreshToken);
    }

    /**
     * 获取当前已登录用户的id
     * @return 用户id，如果未登录则为空
     */
    private String getCurrentUserId() {
        SharedPreferences prefs = getSharedPreferences("userMsg", MODE_PRIVATE);
        return prefs.getString("userId", "");
    }

    /**
     * 发送通知
     * @param contentTitle 通知标题
     * @param contentText  通知内容
     */
    private void sendNotification(String contentTitle, String contentText) {
        requestCode++;
        String channel_id = getString(R.string.default_notify_channel_id);
        String channel_name = getString(R.string.default_notify_channel_name);
        Uri defaultNotifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Intent intent = new Intent(this, MainActivity.class);
        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_ONE_SHOT);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel_id);
        notificationBuilder.setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setAutoCancel(true)
                .setSound(defaultNotifySound)
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channel_id, channel_name, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        notificationManager.notify(requestCode, notificationBuilder.build());
    }

    /**
     * 发送广播通知NotificationModule更新token，并发送给RN层
     * @param refreshToken 更新的token
     */
    private void sendRefreshTokenBroadcast(String refreshToken) {
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent(getString(R.string.REFRESH_TOKEN_BROADCAST_ACTION));
        intent.putExtra("refreshToken", refreshToken);
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * 持久化通知消息
     * @param prefsName 文件名
     * @param key       键
     * @param value     值
     */
    private void saveNotifyMsgToPrefs(String prefsName, String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        editor.putString(key, value);
        editor.apply();
    }
}
