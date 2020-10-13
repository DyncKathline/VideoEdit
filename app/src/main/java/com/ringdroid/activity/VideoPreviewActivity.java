package com.ringdroid.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.MediaController;

import androidx.appcompat.app.AppCompatActivity;

import com.ringdroid.testvideoedit.R;

import io.microshow.rxffmpeg.player.MeasureHelper;
import io.microshow.rxffmpeg.player.RxFFmpegPlayerControllerImpl;
import io.microshow.rxffmpeg.player.RxFFmpegPlayerView;

public class VideoPreviewActivity extends AppCompatActivity {
    private TextureVideoView mVideoView;

    private String mVideoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_preview);

        setTitle("视频预览");

        mVideoPath = getIntent().getStringExtra("video_path");
        mVideoView = findViewById(R.id.video_view);
        initData();
    }

    public void initData() {
        mVideoView.setVideoPath(mVideoPath);
        mVideoView.setMediaController(new MediaController(this));
        mVideoView.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        //恢复播放
        mVideoView.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //暂停视频
        mVideoView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //销毁播放器
        mVideoView.release(true);
    }

    public static void open(Context context, String videoPath) {
        Intent intent = new Intent(context, VideoPreviewActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("video_path", videoPath);
        context.startActivity(intent);
    }
}
