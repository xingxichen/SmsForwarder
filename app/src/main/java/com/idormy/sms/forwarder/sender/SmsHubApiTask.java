package com.idormy.sms.forwarder.sender;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.idormy.sms.forwarder.model.vo.SmsHubVo;
import com.idormy.sms.forwarder.utils.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;

/**
 * 主动发送短信轮询任务
 * @author xxc
 * 2022/1/10 9:53
 */
public class SmsHubApiTask extends TimerTask {
    private static Boolean hasInit = false;
    public static final long DELAY_SECONDS = 30;
    private static final String TAG = "SmsHubApiTask";
    private static Timer sendApiTimer;
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static final SmsHubActionHandler.SmsHubMode smsHubMode = SmsHubActionHandler.SmsHubMode.client;


    @SuppressLint("HandlerLeak")
    public static void init(Context context) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (hasInit) {
            if (hasInit) return;

            hasInit = true;
            SmsHubApiTask.context = context;
            SmsHubActionHandler.init(SmsHubApiTask.context);
        }
    }

    @Override
    public void run() {
        try {
            SmsHubVo smsHubVo = SmsHubVo.heartbeatInstance();
            List<SmsHubVo> data = SmsHubActionHandler.getData(smsHubMode);
            if (data != null && data.size() > 0) {
                smsHubVo.setChildren(data);
            }
            smsHubVo.setChildren(data);
            String url = SettingUtil.getSmsHubApiUrl();
            boolean asRetry = data != null && data.size() > 0;
            Observable.create((ObservableEmitter<Object> emitter) -> {
                AtomicBoolean isSusess = new AtomicBoolean(false);
                HttpUtil.asyncPostJson(TAG, url, smsHubVo, response -> {
                    //HttpUtil.Toast(TAG, "Response：" + response.code() + "，" + responseStr);
                    if (response.code() == 200) {
                        isSusess.set(true);
                        emitter.onComplete();
                        String responseStr = Objects.requireNonNull(response.body()).string();
                        List<SmsHubVo> vos = JSON.parseArray(responseStr, SmsHubVo.class);
                        for (SmsHubVo vo : vos) {
                            SmsHubActionHandler.handle(TAG, vo);
                        }
                        SmsHubActionHandler.putData(smsHubMode, vos.toArray(new SmsHubVo[0]));
                    }
                }, null);
                if (!asRetry) {
                    emitter.onError(new RuntimeException("请求接口异常"));
                }
            }).retryWhen((Observable<Throwable> errorObservable) -> errorObservable
                    .zipWith(Observable.just(
                            SettingUtil.getRetryDelayTime(1),
                            SettingUtil.getRetryDelayTime(2),
                            SettingUtil.getRetryDelayTime(3),
                            SettingUtil.getRetryDelayTime(4),
                            SettingUtil.getRetryDelayTime(5)
                    ), (Throwable e, Integer time) -> time)
                    .flatMap((Integer delay) -> {
                        HttpUtil.Toast(TAG, "请求接口异常，" + delay + "秒后重试");
                        return Observable.timer(delay, TimeUnit.SECONDS);
                    }))
                    .subscribe(System.out::println);
        } catch (Exception e) {
            HttpUtil.Toast(TAG, "SmsHubApiTask 执行出错,请检查问题后重新开启" + e.getMessage());
            cancelTimer();
            SettingUtil.switchEnableSmsHubApi(false);
        }
    }


    public static void updateTimer() {
        cancelTimer();
        if (SettingUtil.getSwitchEnableSmsHubApi()) {
            // FirebaseMessaging messaging = FirebaseMessaging.getInstance();
            // messaging.getToken().addOnCompleteListener(task -> {
            // if (task.isSuccessful()) {
            // String result = task.getResult();
            //                    SettingUtil.setAddExtraDeviceMark(SettingUtil.getAddExtraDeviceMark() + result);
            //                     HttpUtil.Toast(TAG, result);
            // }
            // });
            // OkHttpClient okHttpClient = new OkHttpClient();
            // Request request = new Request.Builder().url(resUrl.toString()).get().build();
            // Response execute = okHttpClient.newCall(request).execute();
            // execute.body()
            // InputStream in = HttpUtil.createGet(
            //         "https://gitee.com/xingxichen/configs/raw/master/smsforward/google-server.json")
            //         .getConnection().getInputStream();
            // FirebaseOptions options = new FirebaseOptions.Builder()
            //         .setCredentials(GoogleCredentials.fromStream(in))
            //         .build();
            // FirebaseApp firebaseApp = FirebaseApp.initializeApp(options);
            // String send = FirebaseMessaging.getInstance(firebaseApp).send(Message.builder()
            //         .putData("msg_type", "PowerOff")
            //         .putData("body", "description for message poweroff from DT background")
            //         .setTopic("weather")
            //         .build());
            SmsHubVo.getDevInfoMap(true);
            startTimer();
        } else {
            SmsHubActionHandler.getData(smsHubMode);
            Log.d(TAG, "Cancel SmsHubApiTaskTimer");
            HttpUtil.Toast(TAG, "Cancel SmsHubApiTaskTimer");
        }
    }

    private static void cancelTimer() {
        if (sendApiTimer != null) {
            sendApiTimer.cancel();
            sendApiTimer = null;
        }
    }

    private static void startTimer() {
        Log.d(TAG, "Start SmsHubApiTimer");
        if (SettingUtil.getSwitchEnableSmsHubApi()) {
            long seconds = SmsHubApiTask.DELAY_SECONDS;
            Log.d(TAG, "SmsHubApiTimer started  " + seconds);
            sendApiTimer = new Timer("SmsHubApiTimer", true);
            sendApiTimer.schedule(new SmsHubApiTask(), 3000, seconds * 1000);
        }
    }
}
