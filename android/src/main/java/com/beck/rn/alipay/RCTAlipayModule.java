package com.beck.rn.alipay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.alipay.sdk.app.AuthTask;
import com.alipay.sdk.app.PayTask;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.List;
import java.util.Map;


public class RCTAlipayModule extends ReactContextBaseJavaModule {
    private static final String TAG = "RCTAlipayModule";
    private static final int SDK_PAY_FLAG = 1;
    private static final int SDK_AUTH_FLAG = 2;
    ReactApplicationContext context;

    public RCTAlipayModule(ReactApplicationContext reactContext) {
        super(reactContext);
        context = reactContext;
    }

    @Override
    public String getName() {
        return "AliPay";
    }

    @ReactMethod
    public void payOrder(ReadableMap params) {
        Activity currentActivity = getCurrentActivity();
        final String orderText = params.getString("orderText");
        payV2(currentActivity, orderText);
    }

    private void payV2(final Activity activity, final String orderText) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                @SuppressLint("HandlerLeak")
                final Handler mHandler = new Handler() {
                    @SuppressWarnings("unused")
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case SDK_PAY_FLAG: {
                                @SuppressWarnings("unchecked")
//                                PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                                PayResult payResult = new PayResult(msg.obj.toString());
                                /**
                                 对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                                 */
                                WritableMap resultMap = setResultMap(payResult);
//                                String resultInfo = payResult.getResult();// 同步返回需要验证的信息, // 该笔订单是否真实支付成功/失败，需要依赖服务端的异步通知。
//                                String resultStatus = payResult.getResultStatus();

                                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                        .emit("alipay.mobile.securitypay.pay.onPaymentResult", resultMap);
                                break;
                            }
                            default:
                                break;
                        }
                    };
                };

                Runnable payRunnable = new Runnable() {

                    @Override
                    public void run() {
                        PayTask alipay = new PayTask(activity);
//                        Map<String, String> result = alipay.payV2(orderText, true); //支付成功却返回resultStatus=6001, 这是新版方法的bug?
                        String result = alipay.pay(orderText, true);    //退回使用旧版的方法, 支付成功可以正常返回resultStatus=6001, 但需要自行解析数据
//                        Log.i("result = ", result.toString());
                        Message msg = new Message();
                        msg.what = SDK_PAY_FLAG;
                        msg.obj = result;
                        mHandler.sendMessage(msg);
                    }
                };

                Thread payThread = new Thread(payRunnable);
                payThread.start();

            }
        });
    }

    private WritableMap setResultMap(PayResult payResult) {
        WritableMap resultMap = Arguments.createMap();

        if (null != payResult) {
            resultMap.putInt("resultStatus", Integer.parseInt(payResult.getResultStatus()));
            resultMap.putString("result", payResult.getResult());
            resultMap.putString("memo", payResult.getMemo());
        }

        return resultMap;
    }

    @ReactMethod
    public void isAlipayInstalled(Callback callback) {
        boolean installed = false;
        PackageManager manager = context.getPackageManager();
        List<PackageInfo> pkgList = manager.getInstalledPackages(0);
        for (int i = 0; i < pkgList.size(); i++) {
            PackageInfo pI = pkgList.get(i);
            if (pI.packageName.equalsIgnoreCase("com.eg.android.AlipayGphone"))
                installed =  true;
        }
        callback.invoke(null, installed);
    }

    @ReactMethod
    public void login(ReadableMap params) {
        Activity currentActivity = getCurrentActivity();
        final String APPID = params.getString("appid");
        final String PID = params.getString("pid");
        final String TARGET_ID = params.getString("target_id");
        final String RSA2_PRIVATE = params.getString("rsa2_private");
        final String RSA_PRIVATE = params.getString("rsa_private");
        if (TextUtils.isEmpty(PID) || TextUtils.isEmpty(APPID)
                || (TextUtils.isEmpty(RSA2_PRIVATE) && TextUtils.isEmpty(RSA_PRIVATE))) {
            Log.d(TAG, "需要配置PARTNER |APP_ID| RSA_PRIVATE| TARGET_ID");
            return;
        }
        /**
         * 这里只是为了方便直接向商户展示支付宝的整个支付流程；所以Demo中加签过程直接放在客户端完成；
         * 真实App里，privateKey等数据严禁放在客户端，加签过程务必要放在服务端完成；
         * 防止商户私密数据泄露，造成不必要的资金损失，及面临各种安全风险；
         *
         * authInfo的获取必须来自服务端；
         */
        boolean rsa2 = (RSA2_PRIVATE.length() > 0);
        Map<String, String> authInfoMap = OrderInfoUtil2_0.buildAuthInfoMap(PID, APPID, TARGET_ID, rsa2);
        String info = OrderInfoUtil2_0.buildOrderParam(authInfoMap);
        String privateKey = rsa2 ? RSA2_PRIVATE : RSA_PRIVATE;
        String sign = OrderInfoUtil2_0.getSign(authInfoMap, privateKey, rsa2);
        final String authInfo = info + "&" + sign;
        auth(currentActivity, authInfo);
    }

    @ReactMethod
    public void loginWithAuthInfo(ReadableMap params) {
        Activity currentActivity = getCurrentActivity();
        final String AUTH = params.getString("auth");
        if (TextUtils.isEmpty(AUTH)) {
            Log.d(TAG, "没有 auth info 信息");
            return;
        }
        auth(currentActivity, AUTH);
    }

    private void auth(final Activity activity, final String authInfo) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                @SuppressLint("HandlerLeak")
                final Handler mHandler = new Handler() {
                    @SuppressWarnings("unused")
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case SDK_AUTH_FLAG: {
                                AuthResult authResult = new AuthResult((Map<String, String>) msg.obj, true);
                                String resultStatus = authResult.getResultStatus();
                                // 判断resultStatus 为“9000”且result_code
                                // 为“200”则代表授权成功，具体状态码代表含义可参考授权接口文档
                                if (TextUtils.equals(resultStatus, "9000") && TextUtils.equals(authResult.getResultCode(), "200")) {
                                    // 获取alipay_open_id，调支付时作为参数extern_token 的value
                                    // 传入，则支付账户为该授权账户
                                    String auth_code = authResult.getAuthCode();
                                    Log.i(TAG, "授权成功");
                                } else {
                                    // 其他状态值则为授权失败
                                    Log.i(TAG, "授权失败");
                                }
                                @SuppressWarnings("unchecked")
//                                PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                                        PayResult payResult = new PayResult(msg.obj.toString());
                                /**
                                 对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                                 */
                                WritableMap resultMap = setResultMap(payResult);
//                                String resultInfo = payResult.getResult();// 同步返回需要验证的信息, // 该笔订单是否真实支付成功/失败，需要依赖服务端的异步通知。
//                                String resultStatus = payResult.getResultStatus();

                                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                        .emit("alipay.mobile.securitypay.pay.onPaymentResult", resultMap);
                                break;
                            }
                            default:
                                break;
                        }
                    };
                };

                Runnable payRunnable = new Runnable() {

                    @Override
                    public void run() {
                        // 构造AuthTask 对象
                        AuthTask authTask = new AuthTask(activity);
                        // 调用授权接口，获取授权结果
                        Map<String, String> result = authTask.authV2(authInfo, true);
                        Message msg = new Message();
                        msg.what = SDK_AUTH_FLAG;
                        msg.obj = result;
                        mHandler.sendMessage(msg);
                    }
                };

                Thread payThread = new Thread(payRunnable);
                payThread.start();
            }
        });
    }
}
