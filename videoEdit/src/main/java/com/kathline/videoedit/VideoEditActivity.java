package com.kathline.videoedit;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kathline.videoedit.util.ProgressDialogUtil;
import com.kathline.videoedit.util.VideoTrimmerUtil;
import com.kathline.videoedit.view.CutView;
import com.kathline.videoedit.view.MarkerView;
import com.kathline.videoedit.view.TextureVideoView;
import com.kathline.videoedit.view.WaveformView;
import com.luoye.bzmedia.FFmpegCMDUtil;
import com.luoye.bzmedia.FFmpegCommandList;
import com.luoye.bzmedia.FMediaMetadata;

import java.io.File;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoEditActivity extends AppCompatActivity {

    private final String TAG = "VideoEditActivity";
    public static final String VIDEO_PATH = "video_path";
    public static final String IS_SHOW_CUT_AREA = "is_show_cut_area";//是否开启裁剪区域功能
    public static final String IS_SHOW_COMPRESS_BTN = "is_show_compress_btn";//是否开启压缩按钮功能
    public static final String MIN_CUT_TIME = "min_cut_time";//设置最小裁剪时长
    public static final String MAX_CUT_TIME = "max_cut_time";//设置最大裁剪时长
    public static final String MAX_RESOLUTION = "max_resolution";//设置最大分辨率，超过就需要进行压缩，取宽高中的最小值
    private String videoPath;
    private String targetPath;
    private String compressPath;
    private boolean isShowCutArea;
    private boolean isShowCompressBtn;
    private WaveformView mWaveformView;
    private MarkerView mStartMarker;
    private MarkerView mEndMarker;
    private ImageButton mPlayButton;
    private ImageButton mRewindButton;
    private ImageButton mFfwdButton;
    private TextView info;
    private TextView cutInfo;
    private boolean mKeyDown;

    private int mWidth;
    private int mMaxPos;
    private int mStartPos;
    private int mEndPos;
    private boolean mStartVisible;
    private boolean mEndVisible;
    private int mLastDisplayedStartPos;
    private int mLastDisplayedEndPos;
    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayStartMsec;
    private int mPlayEndMsec;
    private Handler mHandler;
    private boolean mIsPlaying;

    private boolean mTouchDragging;
    private float mTouchStart;
    private int mTouchInitialOffset;
    private int mTouchInitialStartPos;
    private int mTouchInitialEndPos;
    private long mWaveformTouchStartMsec;

    private TextureVideoView videoView;
    private CutView cutView;
    private Button cut;
    private Button compress;
    private Button cutArea;

    private float mMinCutTime = 3.0f;//裁剪最小时长，单位秒
    private float mMaxCutTime = 15.0f;//裁剪最大时长，单位秒
    private int mMaxResolution = 720;
    private int mMinCutTimePixels;
    private int mMaxCutTimePixels;

    private boolean needCompress;//是否需要压缩

    private Runnable mPlayerHandler = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.getDuration() > 0 && !isPause) {
                finishOpeningSoundFile();
                String mCaption = "0.00 s " + formatTime(mMaxPos) +
                        " s";
                info.setText(mCaption);
                mCaption = getResources().getString(R.string.start_label) + " " + formatTime(mStartPos) +
                        " s " + getResources().getString(R.string.end_label) + " " + formatTime(mEndPos) +
                        " s";
                cutInfo.setText(mCaption);
            } else {
                mHandler.postDelayed(mPlayerHandler, 100);
            }
        }
    };

    private Runnable mTimerRunnable = new Runnable() {
        public void run() {
            // Updating an EditText is slow on Android.  Make sure
            // we only do the update if the text has actually changed.
            if (mStartPos != mLastDisplayedStartPos || mEndPos != mLastDisplayedEndPos) {
                mLastDisplayedStartPos = mStartPos;
                mLastDisplayedEndPos = mEndPos;
                String mCaption = getResources().getString(R.string.start_label) + " " + formatTime(mStartPos) +
                        " s " + getResources().getString(R.string.end_label) + " " + formatTime(mEndPos) +
                        " s";
                cutInfo.setText(mCaption);
            }

            if (mWaveformView.getPlaybackPos() != -1) {
                String mCaption = formatTime(mWaveformView.getPlaybackPos()) + " s " + formatTime(mMaxPos) + " " +
                        "s";
                info.setText(mCaption);
            }

            mHandler.postDelayed(mTimerRunnable, 100);
        }
    };

    public interface Listener {
        void onCreate(VideoEditActivity activity);
        void cutFinish(String cutPath, long duration);
        boolean isPreview();
    }

    public static Listener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        if(mListener != null) {
            mListener.onCreate(this);
        }
        mIsPlaying = false;

        videoPath = getIntent().getStringExtra(VideoEditActivity.VIDEO_PATH);
        isShowCutArea = getIntent().getBooleanExtra(VideoEditActivity.IS_SHOW_CUT_AREA, true);
        isShowCompressBtn = getIntent().getBooleanExtra(VideoEditActivity.IS_SHOW_COMPRESS_BTN, true);
        mMinCutTime = getIntent().getFloatExtra(VideoEditActivity.MIN_CUT_TIME, 3.0f);
        mMaxCutTime = getIntent().getFloatExtra(VideoEditActivity.MAX_CUT_TIME, 15.0f);
        mMaxResolution = getIntent().getIntExtra(VideoEditActivity.MAX_RESOLUTION, 720);
        String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + Environment.DIRECTORY_DCIM + File.separator + getPackageName();
        File parentFile = new File(filePath);
        if(!parentFile.exists()) {
            parentFile.mkdirs();
        }
        targetPath = filePath + File.separator + "VIDEO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4";
        mKeyDown = false;

        mHandler = new Handler();

        loadGui();
    }

    private void loadGui() {
        videoView = findViewById(R.id.video_view);
        cutView = findViewById(R.id.cut_view);
        cut = findViewById(R.id.cut);
        compress = findViewById(R.id.compress);
        cutArea = findViewById(R.id.cut_area);

        compress.setVisibility(View.GONE);
        cutArea.setVisibility(View.GONE);
        if(isShowCutArea) {
            cutView.setVisibility(View.VISIBLE);
        }else {
            cutView.setVisibility(View.GONE);
        }
        if(cutView.getVisibility() == View.VISIBLE) {
            cutArea.setVisibility(View.VISIBLE);
        }else {
            cutArea.setVisibility(View.GONE);
        }

        mPlayButton = (ImageButton) findViewById(R.id.play);
        mPlayButton.setOnClickListener(mPlayListener);
        mRewindButton = (ImageButton) findViewById(R.id.rew);
        mRewindButton.setOnClickListener(mRewindListener);
        mFfwdButton = (ImageButton) findViewById(R.id.ffwd);
        mFfwdButton.setOnClickListener(mFfwdListener);

        enableDisableButtons();

        mWaveformView = (WaveformView) findViewById(R.id.waveform);
        mWaveformView.setListener(waveformListener);

        mMaxPos = 0;
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        mStartMarker = (MarkerView) findViewById(R.id.startmarker);
        mStartMarker.setListener(markerListener);
        mStartMarker.setAlpha(1f);
        mStartMarker.setFocusable(true);
        mStartMarker.setFocusableInTouchMode(true);
        mStartVisible = true;

        mEndMarker = (MarkerView) findViewById(R.id.endmarker);
        mEndMarker.setListener(markerListener);
        mEndMarker.setAlpha(1f);
        mEndMarker.setFocusable(true);
        mEndMarker.setFocusableInTouchMode(true);
        mEndVisible = true;

        info = findViewById(R.id.info);
        cutInfo = findViewById(R.id.cut_info);
        videoView.setVideoPath(videoPath);
        addListener();
    }

    private MarkerView.MarkerListener markerListener = new MarkerView.MarkerListener() {
        @Override
        public void markerTouchStart(MarkerView marker, float x) {
            mTouchDragging = true;
            mTouchStart = x;
            mTouchInitialStartPos = mStartPos;
            mTouchInitialEndPos = mEndPos;
        }

        @Override
        public void markerTouchMove(MarkerView marker, float x) {
            float delta = x - mTouchStart;

            if (marker == mStartMarker) {
                mStartPos = trap((int) (mTouchInitialStartPos + delta));
                mEndPos = trap((int) (mTouchInitialEndPos + delta));
            } else {
                mEndPos = trap((int) (mTouchInitialEndPos + delta));
                if (mEndPos < mStartPos)
                    mEndPos = mStartPos;
            }
            //裁剪时长限制-start
            if(mEndPos - mStartPos > mMaxCutTimePixels) {
                mEndPos = mStartPos + mMaxCutTimePixels;
            }else if(mEndPos - mStartPos < mMinCutTimePixels) {
                mEndPos = mStartPos + mMinCutTimePixels;
                if(mEndPos >= mMaxPos) {
                    mStartPos = mMaxPos - mMinCutTimePixels;
                    mEndPos = mMaxPos;
                }
            }
            //裁剪时长限制-end
            updateDisplay();
        }

        @Override
        public void markerTouchEnd(MarkerView marker) {
            mTouchDragging = false;
            if (marker == mStartMarker) {
                setOffsetGoalStart();
            } else {
                setOffsetGoalEnd();
            }
        }

        @Override
        public void markerFocus(MarkerView marker) {
            mKeyDown = false;
            if (marker == mStartMarker) {
                setOffsetGoalStartNoUpdate();
            } else {
                setOffsetGoalEndNoUpdate();
            }

            // Delay updaing the display because if this focus was in
            // response to a touch event, we want to receive the touch
            // event too before updating the display.
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    updateDisplay();
                }
            }, 100);
        }

        @Override
        public void markerLeft(MarkerView marker, int velocity) {
            mKeyDown = true;

            if (marker == mStartMarker) {
                int saveStart = mStartPos;
                mStartPos = trap(mStartPos - velocity);
                mEndPos = trap(mEndPos - (saveStart - mStartPos));
                setOffsetGoalStart();
            }

            if (marker == mEndMarker) {
                if (mEndPos == mStartPos) {
                    mStartPos = trap(mStartPos - velocity);
                    mEndPos = mStartPos;
                } else {
                    mEndPos = trap(mEndPos - velocity);
                }

                setOffsetGoalEnd();
            }

            updateDisplay();
        }

        @Override
        public void markerRight(MarkerView marker, int velocity) {
            mKeyDown = true;

            if (marker == mStartMarker) {
                int saveStart = mStartPos;
                mStartPos += velocity;
                if (mStartPos > mMaxPos)
                    mStartPos = mMaxPos;
                mEndPos += (mStartPos - saveStart);
                if (mEndPos > mMaxPos)
                    mEndPos = mMaxPos;

                setOffsetGoalStart();
            }

            if (marker == mEndMarker) {
                mEndPos += velocity;
                if (mEndPos > mMaxPos)
                    mEndPos = mMaxPos;

                setOffsetGoalEnd();
            }

            updateDisplay();
        }

        @Override
        public void markerEnter(MarkerView marker) {

        }

        @Override
        public void markerKeyUp() {
            mKeyDown = false;
            updateDisplay();
        }

        @Override
        public void markerDraw() {

        }
    };

    private WaveformView.WaveformListener waveformListener = new WaveformView.WaveformListener() {
        @Override
        public void waveformTouchStart(float x) {
            mTouchDragging = true;
            mTouchStart = x;
            mTouchInitialOffset = mOffset;
            mFlingVelocity = 0;
            mWaveformTouchStartMsec = getCurrentTime();
        }

        @Override
        public void waveformTouchMove(float x) {
            mOffset = trap((int) (mTouchInitialOffset + (mTouchStart - x)));
            updateDisplay();
        }

        @Override
        public void waveformTouchEnd() {
            mTouchDragging = false;
            mOffsetGoal = mOffset;

            long elapsedMsec = getCurrentTime() - mWaveformTouchStartMsec;
            if (elapsedMsec < 300) {
                if (mIsPlaying) {
                    int seekMsec = mWaveformView.pixelsToMillisecs(
                            (int) (mTouchStart + mOffset));
                    if (seekMsec >= mPlayStartMsec &&
                            seekMsec < mPlayEndMsec) {
                        videoView.seekTo(seekMsec);
                    } else {
                        handlePause();
                    }
                } else {
                    onPlay((int) (mTouchStart + mOffset));
                }
            }
        }

        @Override
        public void waveformFling(float x) {
            mTouchDragging = false;
            mOffsetGoal = mOffset;
            mFlingVelocity = (int) (-x);
            updateDisplay();
        }

        @Override
        public void waveformDraw() {
            mWidth = mWaveformView.getMeasuredWidth();
            if (mOffsetGoal != mOffset && !mKeyDown)
                updateDisplay();
            else if (mIsPlaying) {
                updateDisplay();
            } else if (mFlingVelocity != 0) {
                updateDisplay();
            }
        }

        @Override
        public void waveformZoomIn() {
            mWaveformView.zoomIn();
            mStartPos = mWaveformView.getStart();
            mEndPos = mWaveformView.getEnd();
            mMaxPos = mWaveformView.maxPos();
            mMinCutTimePixels = mWaveformView.secondsToPixels(mMinCutTime);
            mMaxCutTimePixels = mWaveformView.secondsToPixels(mMaxCutTime);
            mOffset = mWaveformView.getOffset();
            mOffsetGoal = mOffset;
            updateDisplay();
        }

        @Override
        public void waveformZoomOut() {
            mWaveformView.zoomOut();
            mStartPos = mWaveformView.getStart();
            mEndPos = mWaveformView.getEnd();
            mMaxPos = mWaveformView.maxPos();
            mMinCutTimePixels = mWaveformView.secondsToPixels(mMinCutTime);
            mMaxCutTimePixels = mWaveformView.secondsToPixels(mMaxCutTime);
            mOffset = mWaveformView.getOffset();
            mOffsetGoal = mOffset;
            updateDisplay();
        }

        @Override
        public void waveformImage(int loadSecs) {

        }
    };

    private void addListener() {
        mHandler.postDelayed(mPlayerHandler, 100);
        mHandler.postDelayed(mTimerRunnable, 100);
        //设置准备监听
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                notifyOnPrepared();

                FMediaMetadata fMediaMetadata = FFmpegCMDUtil.getMediaInfo(videoPath);
                int videoWidth = fMediaMetadata.getVideoWidth();
                int videoHeight = fMediaMetadata.getVideoHeight();
//                Log.e("kath---", "width = " + videoWidth + " height = " + videoHeight + " rotate = " + fMediaMetadata.getRotate());
                if (isShowCompressBtn && Math.min(videoWidth, videoHeight) > mMaxResolution) {
                    compress.setVisibility(View.VISIBLE);
                }else {
                    compress.setVisibility(View.GONE);
                }
            }
        });
        //设置错误监听
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
        //设置播放完毕监听
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                notifyOnCompletion();
            }
        });
        videoView.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, final int width, final int height) {
                ViewGroup.LayoutParams layoutParams = cutView.getLayoutParams();
                layoutParams.width = width;
                layoutParams.height = height;
                cutView.setLayoutParams(layoutParams);
            }
        });
    }

    private void notifyOnCompletion() {
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        enableDisableButtons();
    }

    private void notifyOnPrepared() {
        videoView.seekTo(0);
        long duration = videoView.getDuration();
        Log.i(TAG, "duration: " + duration);
        Uri videoUri = Uri.parse(videoPath);
        long startPosition = 0;
        long endPosition = duration;
        int interval = 1000;// 1秒
        final SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
        lastConnectTime = SystemClock.elapsedRealtime();
        VideoTrimmerUtil.shootVideoThumbInBackground(this, videoUri, interval, startPosition, endPosition,
                new VideoTrimmerUtil.SingleCallback<Bitmap, Integer>() {
                    @Override
                    public void onSingleCallback(final Bitmap bitmap, final Integer interval) {
                        if (bitmap != null) {
                            if (bitmaps != null) {
                                bitmaps.put(interval, bitmap);
                            }
//                            Log.i(TAG, "interval: "+interval+", bitmaps.size: " + bitmaps.size());
                            updateThumbnail();
                        }
                    }
                });
    }

    int REFRESH_TIME = 300;
    long lastConnectTime;

    private void updateThumbnail() {
        long time = SystemClock.elapsedRealtime();
        if (time - lastConnectTime > REFRESH_TIME) {
            lastConnectTime = time;
            mWaveformView.postInvalidate();
        }
    }

    private boolean isPause = false;

    public void setVisible(int[] resIds, boolean visible) {
        for (int i = 0; i < resIds.length; i++) {
            View view = findViewById(resIds[i]);
            if(view != null) {
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPause = true;
        if (videoView == null) {
            return;
        }
        videoView.pause();
        enableDisableButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPause = false;
        if (mIsPlaying && videoView != null) {
            videoView.start();
            enableDisableButtons();
        }
    }

    /**
     * Called when the activity is finally destroyed.
     */
    @Override
    protected void onDestroy() {
        Log.v("Ringdroid", "EditActivity OnDestroy");
        mListener = null;
        if (videoView != null) {
            if (videoView.isPlaying()) {
                videoView.stopPlayback();
            }
            videoView.release(true);
            videoView = null;
        }
        mHandler.removeCallbacks(mPlayerHandler);
        mWaveformView.release();
        super.onDestroy();
    }

    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(android.R.drawable.ic_media_pause);
            mPlayButton.setContentDescription(getResources().getText(R.string.stop));
        } else {
            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
            mPlayButton.setContentDescription(getResources().getText(R.string.play));
        }
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(mMaxCutTime);
        mMinCutTimePixels = mWaveformView.secondsToPixels(mMinCutTime);
        mMaxCutTimePixels = mWaveformView.secondsToPixels(mMaxCutTime);
    }

    private void finishOpeningSoundFile() {
        mWaveformView.setDuration(videoView.getDuration());

        mMaxPos = mWaveformView.maxPos();
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        mTouchDragging = false;

        mOffset = 0;
        mOffsetGoal = 0;
        mFlingVelocity = 0;
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;

        updateDisplay();
    }

    private synchronized void updateDisplay() {
        if (mIsPlaying) {
            int now = (int) videoView.getCurrentPosition();
            int frames = mWaveformView.millisecsToPixels(now);
            mWaveformView.setPlayback(frames);
            setOffsetGoalNoUpdate(frames - mWidth / 2);
            if (now >= mPlayEndMsec) {
                handlePause();
            }
        }

        if (!mTouchDragging) {
            int offsetDelta;

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30;
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80;
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80;
                } else {
                    mFlingVelocity = 0;
                }

                mOffset += offsetDelta;

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2;
                    mFlingVelocity = 0;
                }
                if (mOffset < 0) {
                    mOffset = 0;
                    mFlingVelocity = 0;
                }
                mOffsetGoal = mOffset;
            } else {
                offsetDelta = mOffsetGoal - mOffset;

                if (offsetDelta > 10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta > 0)
                    offsetDelta = 1;
                else if (offsetDelta < -10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta < 0)
                    offsetDelta = -1;
                else
                    offsetDelta = 0;
                mOffset += offsetDelta;
            }
        }

        mStartMarker.post(new Runnable() {
            @Override
            public void run() {
                mWaveformView.setLeftOffset(mStartMarker.getWidth());
                mWaveformView.setParameters(mStartPos, mEndPos, mOffset);
                mWaveformView.invalidate();
            }
        });

        mStartMarker.setContentDescription(
                getResources().getText(R.string.start_marker) + " " +
                        formatTime(mStartPos));
        mEndMarker.setContentDescription(
                getResources().getText(R.string.end_marker) + " " +
                        formatTime(mEndPos));

        int startX = mStartPos - mOffset;
        if (startX + mStartMarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mStartVisible = true;
                        mStartMarker.setAlpha(1f);
                    }
                }, 0);
            }
        } else {
            if (mStartVisible) {
                mStartMarker.setAlpha(0f);
                mStartVisible = false;
            }
            startX = 0;
        }

        int endX = mEndPos - mOffset + mWaveformView.getLeftOffset() + 1;
        if (endX + mEndMarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mEndVisible = true;
                        mEndMarker.setAlpha(1f);
                    }
                }, 0);
            }
        } else {
            if (mEndVisible) {
                mEndMarker.setAlpha(0f);
                mEndVisible = false;
            }
            endX = 0;
        }

        int waveformViewWidth = mWaveformView.getWidth();

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        if (startX + mStartMarker.getWidth() < waveformViewWidth) {
            params.setMargins(
                    startX,
                    20,
                    0,
                    20);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        } else {
            params.setMargins(
                    0,
                    20,
                    waveformViewWidth - startX - mStartMarker.getWidth(),
                    20);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        mStartMarker.setLayoutParams(params);

        params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        if (endX + mStartMarker.getWidth() < waveformViewWidth) {
            params.setMargins(
                    endX,
                    20,
                    0,
                    20);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        } else {
            params.setMargins(
                    0,
                    20,
                    waveformViewWidth - endX - mEndMarker.getWidth(),
                    20);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        mEndMarker.setLayoutParams(params);
    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        if (pos > mMaxPos)
            return mMaxPos;
        return pos;
    }

    private void setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2);
    }

    private void setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2);
    }

    private void setOffsetGoal(int offset) {
        setOffsetGoalNoUpdate(offset);
        updateDisplay();
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }

    private String formatTime(int pixels) {
        if (mWaveformView != null && mWaveformView.isInitialized()) {
            return formatDecimal(mWaveformView.pixelsToSeconds(pixels));
        } else {
            return "";
        }
    }

    private double formatTime2Double(int pixels) {
        if (mWaveformView != null && mWaveformView.isInitialized()) {
            return formatDouble(mWaveformView.pixelsToSeconds(pixels));
        } else {
            return 0.00;
        }
    }

    private double formatDouble(double x) {
        BigDecimal bg = new BigDecimal(x);
        /**
         * 参数：
         newScale - 要返回的 BigDecimal 值的标度。
         roundingMode - 要应用的舍入模式。
         返回：
         一个 BigDecimal，其标度为指定值，其非标度值可以通过此 BigDecimal 的非标度值乘以或除以十的适当次幂来确定。
         */
        return bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private String formatDecimal(double x) {
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(x);
    }

    private synchronized void handlePause() {
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        enableDisableButtons();
    }

    private synchronized void onPlay(int startPosition) {
        if (mIsPlaying) {
            handlePause();
            return;
        }

        if (videoView == null) {
            // Not initialized yet
            return;
        }

        try {
            mPlayStartMsec = mWaveformView.pixelsToMillisecs(startPosition);
            if (startPosition < mStartPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mStartPos);
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mMaxPos);
            } else {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mEndPos);
            }
            mIsPlaying = true;

            videoView.seekTo(mPlayStartMsec);
            videoView.start();
            updateDisplay();
            enableDisableButtons();
        } catch (Exception e) {
            return;
        }
    }

    private View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View sender) {
            onPlay(mStartPos);
        }
    };

    private View.OnClickListener mRewindListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = (int) (videoView.getCurrentPosition() - 5000);
                if (newPos < mPlayStartMsec)
                    newPos = mPlayStartMsec;
                videoView.seekTo(newPos);
            } else {
                mStartMarker.requestFocus();
                markerListener.markerFocus(mStartMarker);
            }
        }
    };

    private View.OnClickListener mFfwdListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = (int) (5000 + videoView.getCurrentPosition());
                if (newPos > mPlayEndMsec)
                    newPos = mPlayEndMsec;
                videoView.seekTo(newPos);
            } else {
                mEndMarker.requestFocus();
                markerListener.markerFocus(mEndMarker);
            }
        }
    };

    private long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }

    public void back(View view) {
        finish();
    }

    ProgressDialogUtil progressDialogUtil;
    private float duration;//裁剪时长

    public void startCutAreaVideo(View view) {
        long vst = (long) (Double.parseDouble(formatTime(mStartPos)) * 1000 * 1000);
        long vse = (long) (Double.parseDouble(formatTime(mEndPos)) * 1000 * 1000);
        if (vse - vst < 5 * 1000 * 1000) {
            Toast.makeText(this, "时长不能小于5秒", Toast.LENGTH_SHORT).show();
            return;
        }

        float[] cutArr = cutView.getCutArr();
        float left = cutArr[0];
        float top = cutArr[1];
        float right = cutArr[2];
        float bottom = cutArr[3];

        int leftPro = (int) left;
        int topPro = (int) top;
        int rightPro = (int) (right - left);
        int bottomPro = (int) (bottom - top);

        String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + Environment.DIRECTORY_DCIM + File.separator + getPackageName();
        File parentFile = new File(filePath);
        if(!parentFile.exists()) {
            parentFile.mkdirs();
        }
        final String cutPath = filePath + File.separator + "VIDEO_cut_area_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4";
        //进行裁剪逻辑
        progressDialogUtil = new ProgressDialogUtil(this);
        float startTime = Float.parseFloat(formatTime(mStartPos));
        float endTime = Float.parseFloat(formatTime(mEndPos));
        duration = endTime - startTime;
        FFmpegCommandList cmdlist = new FFmpegCommandList();
        cmdlist.append("-ss");
        cmdlist.append(startTime + "");
        cmdlist.append("-t");
        cmdlist.append(duration + "");
        cmdlist.append("-i");
        cmdlist.append(videoPath);
        cmdlist.append("-vf");
        cmdlist.append(String.format("crop=%d:%d:%d:%d", rightPro, bottomPro, leftPro, topPro));//crop=w=100:h=100:x=12:y=34 w:指定宽度 h:指定高度 x:指定左侧的pos y:指定顶部的pos
        cmdlist.append("-c:v");
        cmdlist.append("libx264");
        cmdlist.append("-c:a");
        cmdlist.append("aac");
        cmdlist.append("-strict");
        cmdlist.append("experimental");
        cmdlist.append("-b");
        cmdlist.append("1000k");
        cmdlist.append(cutPath);
        final String[] commands = cmdlist.build(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                FFmpegCMDUtil.executeFFmpegCommand(commands, new FFmpegCMDUtil.OnActionListener() {

                    @Override
                    public void start() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.openProgressDialog(cutPath);
                            }
                        });
                    }

                    @Override
                    public void progress(final int secs, final long progressTime) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //progressTime 可以在结合视频总时长去计算合适的进度值
                                progressDialogUtil.setProgressDialog("裁剪中：", secs, progressTime, duration);
                            }
                        });
                    }

                    @Override
                    public void fail(final int code, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.cancelProgressDialog("出错了 onError：" + code + " " + message);
                                progressDialogUtil.deleteFile(cutPath);
                            }
                        });
                    }

                    @Override
                    public void success() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //刷新媒体库
//                                MediaScannerConnection.scanFile(VideoEditActivity.this, new String[] { cutPath }, null, null);
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                Uri uri = Uri.fromFile(new File(cutPath));
                                intent.setData(uri);
                                sendBroadcast(intent);
                                progressDialogUtil.onDismiss();
                                videoView.stopPlayback();
                                videoPath = cutPath;
//                                videoView.setVideoPath(videoPath);
                                loadGui();
                            }
                        });
                    }

                    @Override
                    public void cancel() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getBaseContext(), "已取消", Toast.LENGTH_SHORT).show();
                                progressDialogUtil.deleteFile(cutPath);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    public void startClipVideo(View view) {
        progressDialogUtil = new ProgressDialogUtil(this);
        final FMediaMetadata fMediaMetadata = FFmpegCMDUtil.getMediaInfo(videoPath);
        int videoWidth = fMediaMetadata.getVideoWidth();
        int videoHeight = fMediaMetadata.getVideoHeight();
//                Log.e("kath---", "width = " + videoWidth + " height = " + videoHeight + " rotate = " + fMediaMetadata.getRotate());
//        if (Math.min(videoWidth, videoHeight) > mMaxResolution) {//大于1920x1080直接裁剪会崩溃
//            needCompress = true;
//            compress.performClick();
//            return;
//        }

        float startTime = Float.parseFloat(formatTime(mStartPos));
        float endTime = Float.parseFloat(formatTime(mEndPos));
        duration = endTime - startTime;
        FFmpegCommandList cmdlist = new FFmpegCommandList();
        cmdlist.append("-ss");
        cmdlist.append(startTime + "");
        cmdlist.append("-t");
        cmdlist.append(duration + "");
        cmdlist.append("-i");
        cmdlist.append(videoPath);
        cmdlist.append("-c:v");
        cmdlist.append("libx264");
        cmdlist.append("-c:a");
        cmdlist.append("aac");
        cmdlist.append("-strict");
        cmdlist.append("experimental");
        cmdlist.append("-b");
        cmdlist.append("1000k");
        cmdlist.append(targetPath);
        final String[] commands = cmdlist.build(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                //开始执行FFmpeg命令
                FFmpegCMDUtil.executeFFmpegCommand(commands, new FFmpegCMDUtil.OnActionListener() {

                    @Override
                    public void start() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.openProgressDialog(targetPath);
                            }
                        });
                    }

                    @Override
                    public void progress(final int secs, final long progressTime) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //progressTime 可以在结合视频总时长去计算合适的进度值
                                progressDialogUtil.setProgressDialog("裁剪中：", secs, progressTime, duration);
                            }
                        });
                    }

                    @Override
                    public void fail(final int code, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.cancelProgressDialog("出错了 onError：" + code + " " + message);
                                progressDialogUtil.deleteFile(targetPath);
                            }
                        });
                    }

                    @Override
                    public void success() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //刷新媒体库
//                              MediaScannerConnection.scanFile(VideoEditActivity.this, new String[] { targetPath }, null, null);
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                Uri uri = Uri.fromFile(new File(targetPath));
                                intent.setData(uri);
                                sendBroadcast(intent);
                                progressDialogUtil.onDismiss();
                                videoView.stopPlayback();
                                videoPath = targetPath;
//                                videoView.setVideoPath(videoPath);
                                loadGui();
                                if (mListener != null) {
                                    mListener.cutFinish(targetPath, (long) duration);
                                }
                                boolean isPreview = true;
                                if (mListener != null) {
                                    isPreview = mListener.isPreview();
                                    if (!isPreview) {
                                        finish();
                                    }
                                }
                                if (isPreview) {
                                    VideoPreviewActivity.open(getBaseContext(), targetPath);
                                }
                            }
                        });
                    }

                    @Override
                    public void cancel() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getBaseContext(), "已取消", Toast.LENGTH_SHORT).show();
                                progressDialogUtil.deleteFile(targetPath);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    public void startCompressVideo(View view) {
        progressDialogUtil = new ProgressDialogUtil(this);
        final FMediaMetadata fMediaMetadata = FFmpegCMDUtil.getMediaInfo(videoPath);
        String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + Environment.DIRECTORY_DCIM + File.separator + getPackageName();
        File parentFile = new File(filePath);
        if(!parentFile.exists()) {
            parentFile.mkdirs();
        }
        compressPath = filePath + File.separator + "VIDEO_compress_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4";

        FFmpegCommandList cmdlist = new FFmpegCommandList();
        cmdlist.append("-i");
        cmdlist.append(videoPath);
        cmdlist.append("-c:v");
        cmdlist.append("libx264");
        cmdlist.append("-preset");
        cmdlist.append("superfast");
        cmdlist.append("-b:v");
        cmdlist.append("1000k");
        int videoWidth = fMediaMetadata.getVideoWidth();
        int videoHeight = fMediaMetadata.getVideoHeight();
        if (Math.min(videoWidth, videoHeight) > mMaxResolution) {
            cmdlist.append("-filter:v");
            int rotate = fMediaMetadata.getRotate();
            if(videoWidth > videoHeight) {
                if(rotate == 0 || rotate == 180) {
                    cmdlist.append("scale=-2:720");//竖屏
                }else if(rotate == 90 || rotate == 270) {
                    cmdlist.append("scale=720:-2");//横屏
                }
            }else {
                if(rotate == 0 || rotate == 180) {
                    cmdlist.append("scale=720:-2");//横屏
                }else if(rotate == 90 || rotate == 270) {
                    cmdlist.append("scale=-2:720");//竖屏
                }
            }
        }
        cmdlist.append("-r");
        cmdlist.append("30");
        cmdlist.append(compressPath);
        final String[] commands = cmdlist.build(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                //开始执行FFmpeg命令
                FFmpegCMDUtil.executeFFmpegCommand(commands, new FFmpegCMDUtil.OnActionListener() {

                    @Override
                    public void start() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.openProgressDialog(compressPath);
                            }
                        });
                    }

                    @Override
                    public void progress(final int secs, final long progressTime) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //progressTime 可以在结合视频总时长去计算合适的进度值
                                progressDialogUtil.setProgressDialog("压缩中：", secs, progressTime, fMediaMetadata.getDuration() / 1000f);
                            }
                        });
                    }

                    @Override
                    public void fail(final int code, final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialogUtil.cancelProgressDialog("出错了 onError：" + code + " " + message);
                                progressDialogUtil.deleteFile(compressPath);
                            }
                        });
                    }

                    @Override
                    public void success() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //刷新媒体库
//                                MediaScannerConnection.scanFile(VideoEditActivity.this, new String[] { compressPath }, null, null);
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                Uri uri = Uri.fromFile(new File(compressPath));
                                intent.setData(uri);
                                sendBroadcast(intent);
                                progressDialogUtil.onDismiss();
                                videoView.stopPlayback();
                                videoPath = compressPath;
//                                videoView.setVideoPath(videoPath);
                                loadGui();
                                if(needCompress) {
                                    needCompress = false;
                                    cut.performClick();
                                }else {
                                    if (mListener != null) {
                                        mListener.cutFinish(compressPath, fMediaMetadata.getDuration());
                                    }
                                    boolean isPreview = true;
                                    if (mListener != null) {
                                        isPreview = mListener.isPreview();
                                        if (!isPreview) {
                                            finish();
                                        }
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void cancel() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getBaseContext(), "已取消", Toast.LENGTH_SHORT).show();
                                progressDialogUtil.deleteFile(compressPath);
                            }
                        });
                    }
                });
            }
        }).start();
    }

}
