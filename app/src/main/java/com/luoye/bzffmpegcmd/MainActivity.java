package com.luoye.bzffmpegcmd;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.luoye.bzffmpegcmd.fileStoreSAF.FileUtils;
import com.luoye.bzffmpegcmd.fileStoreSAF.MimeType;
import com.luoye.bzffmpegcmd.fileStoreSAF.SAF;
import com.luoye.bzffmpegcmd.fileStoreSAF.SAFListener;
import com.luoye.bzffmpegcmd.fileStoreSAF.ZFileBean;
import com.luoye.bzmedia.FFmpegCMDUtil;
import com.luoye.bzmedia.FFmpegCommandList;
import com.luoye.bzmedia.FMediaMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("bzffmpeg");
        System.loadLibrary("bzffmpegcmd");
    }

    private static final String TAG = "bz_MainActivity";
    private TextView tv_info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_info = (TextView) findViewById(R.id.tv_info);
        requestPermission();
        FFmpegCMDUtil.showLog(BuildConfig.DEBUG);
    }

    private void requestPermission() {
        PermissionUtil.getInstance().with(this)
                .requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, new PermissionUtil.PermissionListener() {

                    @Override
                    public void onGranted() {

                    }

                    @Override
                    public void onDenied(List<String> deniedPermission) {

                    }

                    @Override
                    public void onShouldShowRationale(List<String> deniedPermission) {

                    }
                });
    }

    public void start(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                String cmd = "ffmpeg -y -i /sdcard/bzmedia/VID_029.mp4 /sdcard/bzmedia/out_" + System.nanoTime() + ".mp4";
                cmd = "ffmpeg -y -ss 9.08 -t 9.0 -i /storage/emulated/0/qqmusic/mv/儿歌-小手拍拍.mp4 -c:v libx264 -c:a aac -strict experimental -b 500k /storage/emulated/0/avEditor/out2.mp4";

                FFmpegCommandList cmdlist = new FFmpegCommandList();
                cmdlist.append("-ss");
                cmdlist.append(12 + "");
                cmdlist.append("-t");
                cmdlist.append(10 + "");
                cmdlist.append("-i");
                cmdlist.append("/storage/emulated/0/qqmusic/mv/贝瓦儿歌 - 拔萝卜.mp4");
                cmdlist.append("-c:v");
                cmdlist.append("libx264");
                cmdlist.append("-c:a");
                cmdlist.append("aac");
                cmdlist.append("-strict");
                cmdlist.append("experimental");
                cmdlist.append("-b");
                cmdlist.append("500k");
                cmdlist.append("/storage/emulated/0/avEditor/out2.mp4");
                String[] commands = cmdlist.build(true);
                int ret = FFmpegCMDUtil.executeFFmpegCommand(commands, new FFmpegCMDUtil.OnActionListener() {
                    @Override
                    public void progress(int secs, final long progress) {
                        //progressTime 可以在结合视频总时长去计算合适的进度值
                        Log.d(TAG, "executeFFmpegCommand secs= " + secs + ", progress=" + progress / 1000000f);
                        tv_info.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_info.setText("progress=" + progress / 1000000f);
                            }
                        });
                    }

                    @Override
                    public void fail(int code, String message) {
                        Log.e(TAG, "executeFFmpegCommand fail" + code + " " + message);
                    }

                    @Override
                    public void success() {
                        Log.d(TAG, "executeFFmpegCommand success");
                    }

                    @Override
                    public void cancel() {
                        Log.d(TAG, "executeFFmpegCommand cancel");
                    }
                });
                Log.d(TAG, "ret=" + ret + "-----time cost=" + (System.currentTimeMillis() - startTime));
            }
        }).start();
    }

    public void cancel(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                FFmpegCMDUtil.cancelExecuteFFmpegCommand();
            }
        }).start();
    }

    public void compress(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                String cmd = "ffmpeg -y -i /sdcard/bzmedia/VID_029.mp4 /sdcard/bzmedia/out_" + System.nanoTime() + ".mp4";
                cmd = "ffmpeg -y -ss 9.08 -t 9.0 -i /storage/emulated/0/qqmusic/mv/儿歌-小手拍拍.mp4 -c:v libx264 -c:a aac -strict experimental -b 500k /storage/emulated/0/avEditor/out2.mp4";

                //[ffmpeg, -y, -c:v, h264_mediacodec, -i, /storage/emulated/0/DCIM/Camera/VID_20201207_223556.mp4, -preset, superfast, -b:v, 4000k, -s, 1920x1080, -r, 30, /storage/emulated/0/Movies/2020-12-23-17-03-59.mp4]

                FMediaMetadata fMediaMetadata = FFmpegCMDUtil.readAVInfo("/storage/emulated/0/DCIM/Camera/VID_20201207_223556.mp4");

                FFmpegCommandList cmdlist = new FFmpegCommandList();
                cmdlist.append("-i");
//                cmdlist.append("/storage/emulated/0/qqmusic/mv/贝瓦儿歌 - 拔萝卜.mp4");
                cmdlist.append("/storage/emulated/0/DCIM/Camera/VID_20201207_223556.mp4");
                cmdlist.append("-c:v");
                cmdlist.append("libx264");
                cmdlist.append("-preset");
                cmdlist.append("superfast");
                cmdlist.append("-crf");
                cmdlist.append("25");
                cmdlist.append("-b:v");
                cmdlist.append("4000k");
                cmdlist.append("-s");
                int width = fMediaMetadata.getVideoWidth();
                int height = fMediaMetadata.getVideoHeight();
                if (fMediaMetadata.getVideoWidth() > 1920) {
                    width = width / 2;
                    height = height / 2;
                }
                cmdlist.append("" + width);
                cmdlist.append("x");
                cmdlist.append("" + height);
//                cmdlist.append("1920x1080");
                cmdlist.append("-r");
                cmdlist.append("30");
                cmdlist.append("/storage/emulated/0/avEditor/out2.mp4");
                String[] commands = cmdlist.build(true);
                int ret = FFmpegCMDUtil.executeFFmpegCommand(commands, new FFmpegCMDUtil.OnActionListener() {
                    @Override
                    public void progress(int secs, final long progress) {
                        //progressTime 可以在结合视频总时长去计算合适的进度值
                        Log.d(TAG, "executeFFmpegCommand secs= " + secs + ", progress=" + progress / 1000000f);
                        tv_info.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_info.setText("progress=" + progress / 1000000f);
                            }
                        });
                    }

                    @Override
                    public void fail(int code, String message) {
                        Log.e(TAG, "executeFFmpegCommand fail " + code + " " + message);
                    }

                    @Override
                    public void success() {
                        Log.d(TAG, "executeFFmpegCommand success");
                    }

                    @Override
                    public void cancel() {
                        Log.d(TAG, "executeFFmpegCommand cancel");
                    }
                });
                Log.d(TAG, "ret=" + ret + "-----time cost=" + (System.currentTimeMillis() - startTime));
            }
        }).start();
    }

    public void saf(View view) {
        final SAF saf = SAF.with(this);
        saf.requestCode(0)
                .mimeTypes(new String[]{MimeType._pdf.getValue(), MimeType._image.getValue()})
                .maxCount(2)
                .request(new SAFListener() {

                    @Override
                    public void onResult(int requestCode, int resultCode, Intent data) {
                        List<ZFileBean> fileList = FileUtils.getSelectData(MainActivity.this, requestCode, resultCode, data, saf);
                        if (fileList.size() <= 0) {
                            return;
                        }
                        Toast.makeText(MainActivity.this, "获取成功，请查看控制台", Toast.LENGTH_SHORT).show();
                        StringBuilder sb = new StringBuilder();
                        for (ZFileBean bean : fileList) {
                            sb.append(bean.toString()).append("\n\n");
                        }
                        Log.i(SAF.TAG, sb.toString());
                    }
                });
    }

    public void ffmpeg_mediametadataretriever(View view) {
        FFmpegMediaMetadataRetriever mediaMetadataRetriever = new FFmpegMediaMetadataRetriever();
        try {
//            mediaMetadataRetriever.setDataSource("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
            mediaMetadataRetriever.setDataSource("/storage/emulated/0/DCIM/Camera/VID_20201207_223556.mp4");
            FFmpegMediaMetadataRetriever.Metadata metadata = mediaMetadataRetriever.getMetadata();
            StringBuilder videoInfoJson = new StringBuilder("{");
            for (Map.Entry<String, String> entry: metadata.getAll().entrySet()) {
                Log.i("kath", "key = " + entry.getKey() + ", value = " + entry.getValue());
                videoInfoJson.append("\"").append(entry.getKey()).append("\"").append(":").append(entry.getValue()).append(",");
            }
            if(videoInfoJson.length() > 1) {
                videoInfoJson.substring(0, videoInfoJson.length() - 1);
            }
            videoInfoJson.append("}");
            String json = videoInfoJson.toString();
            Log.d("kath", json);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } finally {
            mediaMetadataRetriever.release();
        }
    }

}
