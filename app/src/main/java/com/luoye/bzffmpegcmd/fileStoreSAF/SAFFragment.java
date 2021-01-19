package com.luoye.bzffmpegcmd.fileStoreSAF;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class SAFFragment extends Fragment {

    private FragmentActivity mActivity;
    public static String REQUEST_CODE = "request_code";
    private SAFListener mListener;
    private int requestCode = 0;

    public SAFFragment() {

    }

    /**
     * 开启权限申请
     */
    public static SAFFragment beginRequest(FragmentActivity activity, int requestCode, SAFListener listener) {
        SAFFragment fragment = new SAFFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(REQUEST_CODE, requestCode);
        fragment.setArguments(bundle);
        // 设置保留实例，不会因为配置变化而重新创建
        fragment.setRetainInstance(true);
        // 设置权限回调监听
        fragment.setSAFListener(listener);
        addFragment(activity.getSupportFragmentManager(), fragment);
        return fragment;
    }

    private void setSAFListener(SAFListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getArguments() != null) {
            requestCode = getArguments().getInt(REQUEST_CODE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(this.requestCode == requestCode && resultCode == Activity.RESULT_OK) {
            if (mListener != null) {
                mListener.onResult(requestCode, resultCode, data);
            }
        }
        // 将 Fragment 从 Activity 移除
        removeFragment(getFragmentManager(), this);
    }

    /**
     * 添加 Fragment
     */
    public static void addFragment(FragmentManager manager, Fragment fragment) {
        if (manager == null) {
            return;
        }
        manager.beginTransaction().add(fragment, fragment.toString()).commitNowAllowingStateLoss();
    }

    /**
     * 移除 Fragment
     */
    public static void removeFragment(FragmentManager manager, Fragment fragment) {
        if (manager == null) {
            return;
        }
        manager.beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
}
