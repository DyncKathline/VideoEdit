package com.luoye.bzffmpegcmd;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import com.luoye.bzmedia.FFmpegCMDUtil;
import com.luoye.bzmedia.FFmpegCommandList;

import java.util.ArrayList;

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

    private boolean requestPermission() {
        ArrayList<String> permissionList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !PermissionUtil.isPermissionGranted(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!PermissionUtil.isPermissionGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        String[] permissionStrings = new String[permissionList.size()];
        permissionList.toArray(permissionStrings);

        if (permissionList.size() > 0) {
            PermissionUtil.requestPermission(this, permissionStrings, PermissionUtil.CODE_REQ_PERMISSION);
            return false;
        } else {
            Log.d(TAG, "Have all permissions");
            return true;
        }
    }

    public void start(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                String cmd = "ffmpeg -y -i /sdcard/bzmedia/VID_029.mp4 /sdcard/bzmedia/out_" + System.nanoTime() + ".mp4";

//                String cmd = "ffmpeg -y -i /sdcard/bzmedia/VID_029.mp4 -i /sdcard/bzmedia/VID_812.mp4 -filter_complex amix=inputs=2 -vn /storage/emulated/0/bzmedia/mix_" + System.nanoTime() + ".m4a";
//
//                String cmd = "ffmpeg -y -i %s -i %s -c:v copy -filter_complex [0:a]aformat=fltp:44100:stereo,volume=%.2f,apad[0a];[1]aformat=fltp:44100:stereo,volume=%.2f[1a];[0a][1a]amerge[a] -map 0:v -map [a] -ac 2 %s";
//                cmd = String.format(cmd, "/sdcard/Filter/temp_6.mp4", "/sdcard/Filter/input_bg.mp3", 0.5f, 1.0f, "/sdcard/Filter/output.mp4");
//                cmd = "ffmpeg -y -i /sdcard/Filter/temp_6.mp4 -i /sdcard/Filter/input_bg.aac -filter_complex [0:a]aformat=fltp:44100:stereo,volume=0.5,apad[0a];[1]aformat=fltp:44100:stereo,volume=1[1a];[0a][1a]amerge[a] -map 0:v -map [a] -ac 2 /sdcard/Filter/output.mp4";
//                String cmd = "ffmpeg -y -ss 13.658 -t 13.418 -i /sdcard/bzmedia/VID_3093.mp4 -acodec copy -vcodec copy /sdcard/bzmedia/test.mp4";
//                String cmd = "ffmpeg -y -ss 0 -t 10.123 -i /sdcard/bzmedia/out_na.mp4 -vn -acodec copy /sdcard/bzmedia/out_test.m4a";
//                String cmd = "ffmpeg -y -ss 0 -t 10.030 -i \"/storage/emulated/0/bzmedia/test.mp3\" -vn -acodec copy /storage/emulated/0/bzmedia/alignment_music_1559539530386715.mp3";

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

//                Log.d(TAG, "cmd: " + cmd);
//                String[] commands = cmd.split(" ");
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
                    public void fail() {
                        Log.e(TAG, "executeFFmpegCommand fail");
                    }

                    @Override
                    public void success() {
                        Log.d(TAG, "executeFFmpegCommand success");
                    }
                });
                Log.d(TAG, "ret=" + ret + "-----time cost=" + (System.currentTimeMillis() - startTime));
            }
        }).start();
    }

    public void cancel(View view) {
        FFmpegCMDUtil.executeFFmpegCancel();
    }

}
