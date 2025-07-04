package com.xuexiang.flutter_xupdate;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xuexiang.xupdate.UpdateManager;
import com.xuexiang.xupdate.XUpdate;
import com.xuexiang.xupdate.entity.UpdateEntity;
import com.xuexiang.xupdate.entity.UpdateError;
import com.xuexiang.xupdate.listener.OnUpdateFailureListener;
import com.xuexiang.xupdate.utils.UpdateUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * FlutterXUpdatePlugin
 *
 * @author xuexiang
 * @since 2020-02-04 16:33
 */
public class FlutterXUpdatePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private static final String PLUGIN_NAME = "com.xuexiang/flutter_xupdate";

    private MethodChannel mMethodChannel;
    private Application mApplication;
    private WeakReference<Activity> mActivity;


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        mMethodChannel.setMethodCallHandler(null);
        mMethodChannel = null;
    }

     // 新版注册方法（Flutter 2.0+）
    public static void registerWith(FlutterPluginBinding binding) {
        final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), PLUGIN_NAME);
        channel.setMethodCallHandler(new FlutterXUpdatePlugin());
    }
     // 新增的必需方法
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        mMethodChannel = new MethodChannel(binding.getBinaryMessenger(), PLUGIN_NAME);
        mMethodChannel.setMethodCallHandler(this);
        mApplication = (Application) binding.getApplicationContext();
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "initXUpdate":
                initXUpdate(call, result);
                break;
            case "checkUpdate":
                checkUpdate(call, result);
                break;
            case "updateByInfo":
                updateByInfo(call, result);
                break;
            case "showRetryUpdateTipDialog":
                showRetryUpdateTipDialog(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    /**
     * 初始化
     *
     * @param call
     * @param result
     */
    private void initXUpdate(MethodCall call, Result result) {
        Map<String, Object> map = (Map<String, Object>) call.arguments;
        Boolean debug = (Boolean) map.get("debug");
        Boolean isGet = (Boolean) map.get("isGet");
        Integer timeout = (Integer) map.get("timeout");
        Boolean isPostJson = (Boolean) map.get("isPostJson");
        Boolean isWifiOnly = (Boolean) map.get("isWifiOnly");
        Boolean isAutoMode = (Boolean) map.get("isAutoMode");
        Boolean supportSilentInstall = (Boolean) map.get("supportSilentInstall");
        Boolean enableRetry = (Boolean) map.get("enableRetry");
        String retryContent = (String) map.get("retryContent");
        String retryUrl = (String) map.get("retryUrl");

        XUpdate.get()
                .debug(debug)
                //默认设置使用get请求检查版本
                .isGet(isGet)
                //默认设置只在wifi下检查版本更新
                .isWifiOnly(isWifiOnly)
                //默认设置非自动模式，可根据具体使用配置
                .isAutoMode(isAutoMode)
                //是否支持静默安装
                .supportSilentInstall(supportSilentInstall)
                .setOnUpdateFailureListener(new OnUpdateFailureListener() {
                    @Override
                    public void onFailure(UpdateError error) {
                        Map<String, Object> errorMap = new HashMap<>();
                        errorMap.put("code", error.getCode());
                        errorMap.put("message", error.getMessage());
                        errorMap.put("detailMsg", error.getDetailMsg());
                        if (mMethodChannel != null) {
                            mMethodChannel.invokeMethod("onUpdateError", errorMap);
                        }
                    }
                })
                //设置默认公共请求参数
                .param("versionCode", UpdateUtils.getVersionCode(mApplication))
                .param("appKey", mApplication.getPackageName())
                .setIUpdateDownLoader(new RetryUpdateDownloader(enableRetry, retryContent, retryUrl))
                //这个必须设置！实现网络请求功能。
                .setIUpdateHttpService(new OKHttpUpdateHttpService(timeout, isPostJson));
        if (map.get("params") != null) {
            XUpdate.get().params((Map<String, Object>) map.get("params"));
        }
        XUpdate.get().init(mApplication);

        result.success(map);

    }

    /**
     * 版本更新
     *
     * @param call
     * @param result
     */
    private void checkUpdate(MethodCall call, Result result) {
        if (mActivity == null || mActivity.get() == null) {
            result.error("1001", "Not attach a Activity", null);
        }

        String url = call.argument("url");
        boolean supportBackgroundUpdate = call.argument("supportBackgroundUpdate");
        boolean isAutoMode = call.argument("isAutoMode");
        boolean isCustomParse = call.argument("isCustomParse");
        String themeColor = call.argument("themeColor");
        String topImageRes = call.argument("topImageRes");
        String buttonTextColor = call.argument("buttonTextColor");

        Double widthRatio = call.argument("widthRatio");
        Double heightRatio = call.argument("heightRatio");

        boolean overrideGlobalRetryStrategy = call.argument("overrideGlobalRetryStrategy");
        boolean enableRetry = call.argument("enableRetry");
        String retryContent = call.argument("retryContent");
        String retryUrl = call.argument("retryUrl");

        UpdateManager.Builder builder = XUpdate.newBuild(mActivity.get())
                .updateUrl(url)
                .isAutoMode(isAutoMode)
                .supportBackgroundUpdate(supportBackgroundUpdate);
        if (call.argument("params") != null) {
            builder.params((Map<String, Object>) call.argument("params"));
        }
        if (isCustomParse) {
            builder.updateParser(new FlutterCustomUpdateParser(mMethodChannel));
        }

        updatePromptStyle(builder, themeColor, topImageRes, buttonTextColor, widthRatio, heightRatio, overrideGlobalRetryStrategy, enableRetry, retryContent, retryUrl);

        builder.update();
    }

    /**
     * 直接传入UpdateEntity进行版本更新
     *
     * @param call
     * @param result
     */
    private void updateByInfo(MethodCall call, Result result) {
        if (mActivity == null || mActivity.get() == null) {
            result.error("1001", "Not attach a Activity", null);
        }

        HashMap<String, Object> map = call.argument("updateEntity");
        UpdateEntity updateEntity = FlutterCustomUpdateParser.parseUpdateEntityMap(map);

        boolean supportBackgroundUpdate = call.argument("supportBackgroundUpdate");
        boolean isAutoMode = call.argument("isAutoMode");
        String themeColor = call.argument("themeColor");
        String topImageRes = call.argument("topImageRes");
        String buttonTextColor = call.argument("buttonTextColor");

        Double widthRatio = call.argument("widthRatio");
        Double heightRatio = call.argument("heightRatio");

        boolean overrideGlobalRetryStrategy = call.argument("overrideGlobalRetryStrategy");
        boolean enableRetry = call.argument("enableRetry");
        String retryContent = call.argument("retryContent");
        String retryUrl = call.argument("retryUrl");


        UpdateManager.Builder builder = XUpdate.newBuild(mActivity.get())
                .isAutoMode(isAutoMode)
                .supportBackgroundUpdate(supportBackgroundUpdate);

        updatePromptStyle(builder, themeColor, topImageRes, buttonTextColor, widthRatio, heightRatio, overrideGlobalRetryStrategy, enableRetry, retryContent, retryUrl);

        builder.build().update(updateEntity);

    }

    /**
     * 更新弹窗的样式
     *
     * @param builder
     * @param themeColor                  主题颜色
     * @param topImageRes                 弹窗顶部的图片
     * @param buttonTextColor             按钮文字的颜色
     * @param widthRatio                  版本更新提示器宽度占屏幕的比例
     * @param heightRatio                 版本更新提示器高度占屏幕的比例
     * @param overrideGlobalRetryStrategy 是否覆盖全局的重试策略
     * @param enableRetry                 在下载过程中，如果点击了取消的话，是否弹出切换下载方式的重试提示弹窗
     * @param retryContent                重试提示弹窗的提示内容
     * @param retryUrl                    重试提示弹窗点击后跳转的url
     */
    private void updatePromptStyle(UpdateManager.Builder builder, String themeColor, String topImageRes, String buttonTextColor, Double widthRatio, Double heightRatio, boolean overrideGlobalRetryStrategy, boolean enableRetry, String retryContent, String retryUrl) {
        if (!TextUtils.isEmpty(themeColor)) {
            builder.promptThemeColor(Color.parseColor(themeColor));
        }
        if (!TextUtils.isEmpty(topImageRes)) {
            int topImageResId = mActivity.get().getResources().getIdentifier(topImageRes, "drawable", mActivity.get().getPackageName());
            builder.promptTopResId(topImageResId);
        }
        if (!TextUtils.isEmpty(buttonTextColor)) {
            builder.promptButtonTextColor(Color.parseColor(buttonTextColor));
        }
        if (widthRatio != null) {
            builder.promptWidthRatio(widthRatio.floatValue());
        }
        if (heightRatio != null) {
            builder.promptHeightRatio(heightRatio.floatValue());
        }
        if (overrideGlobalRetryStrategy) {
            builder.updateDownLoader(new RetryUpdateDownloader(enableRetry, retryContent, retryUrl));
        }
    }


    /**
     * 显示重试提示弹窗
     *
     * @param call
     * @param result
     */
    private void showRetryUpdateTipDialog(MethodCall call, Result result) {
        String retryContent = call.argument("retryContent");
        String retryUrl = call.argument("retryUrl");

        RetryUpdateTipDialog.show(retryContent, retryUrl);
    }


    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        mActivity = new WeakReference<>(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        mActivity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        mActivity = new WeakReference<>(binding.getActivity());
    }

    @Override
    public void onDetachedFromActivity() {
        mActivity = null;
    }
}
