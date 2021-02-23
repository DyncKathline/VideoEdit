package com.ringdroid.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kathline.videoedit.VideoEditActivity;
import com.kathline.videoedit.util.ProgressDialogUtil;
import com.kathline.videoedit.util.Utils;
import com.luoye.bzmedia.FFmpegCMDUtil;
import com.luoye.bzmedia.FFmpegCommandList;
import com.luoye.bzmedia.FMediaMetadata;
import com.ringdroid.PermissionUtil;
import com.ringdroid.R;
import com.ringdroid.util.SpacingDecoration;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VideoListActivity extends AppCompatActivity implements VideoAdapter.OnItemClickListener {
    private static final int MSG_NOTIFY_DATA_CHANGED = 100;

    private RecyclerView mRecyclerView;

    private VideoAdapter mVideoAdapter;

    private List<Video> mVideoList = new ArrayList<>();

    @SuppressLint("HandlerLeak")
    private Handler mUiHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_NOTIFY_DATA_CHANGED) {
                mVideoAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);

        setTitle("视频列表");


        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        mRecyclerView.addItemDecoration(new SpacingDecoration(this, 10, 10, true));
        mVideoAdapter = new VideoAdapter(this);
        mVideoAdapter.setOnItemClickListener(this);
        mVideoAdapter.setVideoList(mVideoList);
        mRecyclerView.setAdapter(mVideoAdapter);

        PermissionUtil.getInstance().with(this).requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, new PermissionUtil.PermissionListener() {
            @Override
            public void onGranted() {
                loadData();
            }

            @Override
            public void onDenied(List<String> deniedPermission) {

            }

            @Override
            public void onShouldShowRationale(List<String> deniedPermission) {

            }
        });
    }

    private void loadData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadVideoList();
            }
        }).start();
    }

    private void loadVideoList() {
        mVideoList.clear();
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, null, MediaStore.Video.Media.DATE_ADDED + " desc");
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
        } else {
            return;
        }
        int fileNum = cursor.getCount();
        for(int count = 0; count < fileNum; count++) {
            String videoPath = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
            File file = new File(videoPath);

            if (!file.exists()) {
                cursor.moveToNext();
                continue;
            }
            Video video = new Video();
            video.setId(cursor.getInt(cursor.getColumnIndex(MediaStore.Video.Media._ID)));
            video.setVideoName(cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)));
            video.setVideoPath(videoPath);

            String videoPathLowerCase = videoPath.toLowerCase();
            if (!videoPathLowerCase.endsWith("mp4") && !videoPathLowerCase.endsWith("3gp") && !videoPathLowerCase.endsWith("mkv") && !videoPathLowerCase.endsWith("avi")) {
                cursor.moveToNext();
                continue;
            }
            long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.DURATION));
            if (duration == 0) {
                duration = getVideoDurationFromMediaMetadata(video.getVideoPath());
            }
            if (duration > 0) {
                video.setDuration(duration);
                video.setVideoSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)));
                mVideoList.add(video);
            }
            cursor.moveToNext();
        }
        cursor.close();

        mUiHandler.sendEmptyMessage(MSG_NOTIFY_DATA_CHANGED);
    }

    private long getVideoDurationFromMediaMetadata(String path) {
        long duration = 0;

        if (!TextUtils.isEmpty(path)) {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            try {
                Log.i("TAG----", path);
                mediaMetadataRetriever.setDataSource(getBaseContext(), Uri.parse(path));
                duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); // 视频时长 毫秒
            } catch (Exception e) {
//                e.printStackTrace();
            }
        }
        return duration;
    }

    @Override
    public void onItemClick(int position, Video video) {
        String videoPath = video.getVideoPath();
        Intent intent = new Intent(this, VideoEditActivity.class);
        intent.putExtra(VideoEditActivity.VIDEO_PATH, videoPath);
//        intent.putExtra(VideoEditActivity.IS_SHOW_CUT_AREA, false);
        intent.putExtra(VideoEditActivity.MAX_CUT_TIME, 90f);
        startActivity(intent);
    }

}
