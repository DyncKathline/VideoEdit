package com.kathline.videoedit.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.luoye.bzmedia.FFmpegCMDUtil;

import java.io.File;

public class ProgressDialogUtil {

    private Context context;
    private long startTime;//记录开始时间
    private long endTime;//记录结束时间

    public ProgressDialogUtil(Context context) {
        this.context = context;
    }

    private ProgressDialog mProgressDialog;

    public void openProgressDialog(String targetPath) {
        //统计开始时间
        startTime = System.nanoTime();
        mProgressDialog = openProgressDialog(context, targetPath);
    }

    public void onDismiss() {
        endTime = System.nanoTime();
        String takeUpTime = convertUsToTime((endTime - startTime) / 1000, false);
        Toast.makeText(context, "耗时：" + takeUpTime, Toast.LENGTH_SHORT).show();
        mProgressDialog.cancel();
    }

    public ProgressDialog openProgressDialog(Context context, final String targetPath) {
        final ProgressDialog mProgressDialog = new ProgressDialog(context);
        final int totalProgressTime = 100;
        mProgressDialog.setMessage("正在转换视频，请稍后...");
        mProgressDialog.setButton("取消", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                //中断 ffmpeg
                FFmpegCMDUtil.cancelExecuteFFmpegCommand();
                if(mProgressDialog.getProgress() < 100) {
                    deleteFile(targetPath);
                }
            }
        });
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setProgressNumberFormat("");
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMax(totalProgressTime);
        mProgressDialog.show();
        return mProgressDialog;
    }

    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * 取消进度条
     *
     * @param dialogTitle Title
     */
    public void cancelProgressDialog(String dialogTitle) {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }
        if (!TextUtils.isEmpty(dialogTitle)) {
            showDialog(dialogTitle);
        }
    }

    /**
     * 设置进度条
     */
    public void setProgressDialog(int progress, long progressTime, float duration) {
        Log.i("RxTAG", "progress: " + progress + ", progressTime: " + progressTime + ", ---: " + (int) ((double) progressTime / 1000000 / 10 * 100f));
        if (mProgressDialog != null) {
            mProgressDialog.setProgress((int) ((double) progressTime / 1000000 / duration * 100f));
            //progressTime 可以在结合视频总时长去计算合适的进度值
            double time = (double) progressTime / 1000000;
            mProgressDialog.setMessage("已处理" + String.format("%.2f", time) + "秒");
        }
    }

    public void showDialog(String message) {
        //统计结束时间
        endTime = System.nanoTime();
        Utils.showDialog(context, message, Utils.convertUsToTime((endTime - startTime) / 1000, false));
    }

    //优化内存使用
    StringBuilder mUsDurationText = new StringBuilder();

    /**
     * 微秒转换为 时分秒毫秒,如 00:00:00.000
     *
     * @param us           微秒
     * @param autoEllipsis true:如果小时为0，则这样显示00:00.000;  false:全部显示 00:00:00.000
     * @return
     */
    public String convertUsToTime(long us, boolean autoEllipsis) {

        mUsDurationText.delete(0, mUsDurationText.length());

        long ms = us / 1000;
        int ss = 1000;
        int mi = ss * 60;
        int hh = mi * 60;
        int dd = hh * 24;

        //天
        long day = ms / dd;
        //小时
        long hour = (ms - day * dd) / hh;
        //分
        long minute = (ms - day * dd - hour * hh) / mi;
        //秒
        long second = (ms - day * dd - hour * hh - minute * mi) / ss;
        //毫秒
        long milliSecond = ms - day * dd - hour * hh - minute * mi - second * ss;

        String strDay = day < 10 ? "0" + day : "" + day; //天
        String strHour = hour < 10 ? "0" + hour : "" + hour;//小时
        String strMinute = minute < 10 ? "0" + minute : "" + minute;//分钟
        String strSecond = second < 10 ? "0" + second : "" + second;//秒
        String strMilliSecond = milliSecond < 10 ? "0" + milliSecond : "" + milliSecond;//毫秒
        strMilliSecond = milliSecond < 100 ? "0" + strMilliSecond : "" + strMilliSecond;

        if (autoEllipsis) {
            if (hour > 0) {
                mUsDurationText.append(strHour).append(":");
            }
        } else {
            mUsDurationText.append(strHour).append(":");
        }
        mUsDurationText.append(strMinute).append(":")
                .append(strSecond).append(".").append(strMilliSecond);
        return mUsDurationText.toString();
    }
}
