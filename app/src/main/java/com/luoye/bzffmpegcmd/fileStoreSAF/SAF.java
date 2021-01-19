package com.luoye.bzffmpegcmd.fileStoreSAF;

import android.content.Intent;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public class SAF {

    public static final String TAG = SAF.class.getSimpleName();
    private FragmentActivity activity;
    private int requestCode;
    private String type;
    private String[] mime_types;
    private int maxCount = 1;//最大文件数
    private int maxSize = 10;//最大文件大小，单位M

    private SAF(AppCompatActivity hostActivity) {
        if (hostActivity == null) {
            throw new IllegalArgumentException("Activity must not be null!");
        }
        activity = hostActivity;
    }

    private SAF(Fragment hostFragment) {
        activity = hostFragment.getActivity();
        if (activity == null) {
            throw new IllegalArgumentException("Activity must not be null!");
        }
    }

    public static SAF with(AppCompatActivity appCompatActivity) {
        return new SAF(appCompatActivity);
    }

    public static SAF with(Fragment fragment) {
        return new SAF(fragment);
    }

    public SAF requestCode(int requestCode) {
        this.requestCode = requestCode;
        return this;
    }

    public SAF type(String type) {
        this.type = type;
        return this;
    }

    public SAF mimeTypes(String[] mime_types) {
        this.mime_types = mime_types;
        return this;
    }

    public SAF maxCount(int maxCount) {
        this.maxCount = maxCount;
        return this;
    }

    public SAF maxSize(int maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    public FragmentActivity getActivity() {
        return activity;
    }

    public int getRequestCode() {
        return requestCode;
    }

    public String getType() {
        return type;
    }

    public String[] getMime_types() {
        return mime_types;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void request(SAFListener listener) {
        // 如果传入 Activity 为空或者 Activity 状态非法则直接屏蔽这次权限申请
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        SAFFragment fragment = SAFFragment.beginRequest(activity, requestCode, listener);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, maxCount > 1);
        if (mime_types != null && mime_types.length > 0) {
            intent.setType(TextUtils.isEmpty(type) ? "*/*" : type);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mime_types);
        }
        else {
            intent.setType("*/*");
        }
        fragment.startActivityForResult(intent, requestCode);
    }

}
