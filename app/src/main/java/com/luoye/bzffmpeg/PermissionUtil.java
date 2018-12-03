package com.luoye.bzffmpeg;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by admin on 2016/2/17.
 * 动态权限请求的相关工具类
 */
public class PermissionUtil {
    private static final String TAG = "PermissionUtil";
    public static final int CODE_REQ_PERMISSION = 1100;//权限请求
    public static final int CODE_REQ_AUDIO_PERMISSION = 601;
    public static final int CODE_REQ_CAMERA_PERMISSION = 602;

    /**
     * 权限请求
     *
     * @param activity
     * @return
     */
    public static void requestPermission(Activity activity, String[] permissionArr, int requestCode) {
        if (permissionArr != null) {
            ActivityCompat.requestPermissions(activity, permissionArr, requestCode);
        }

    }

    /**
     * 判断是否拥有该权限
     *
     * @param activity
     * @return
     */
    public static boolean isPermissionGranted(Activity activity, String permission) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断是否拥有该权限
     *
     * @param activity
     * @return
     */
    public static boolean isPermissionProhibited(Activity activity, String permission) {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            return true;
        } else {
            return false;
        }
    }

}
