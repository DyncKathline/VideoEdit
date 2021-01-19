package com.kathline.videoedit;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kathline.videoedit.util.Utils;
import com.kathline.videoedit.util.VideoTrimmerUtil;
import com.kathline.videoedit.view.CutView;
import com.kathline.videoedit.view.MarkerView;
import com.kathline.videoedit.view.TextureVideoView;
import com.kathline.videoedit.view.WaveformView;
import com.luoye.bzmedia.FFmpegCMDUtil;
import com.luoye.bzmedia.FFmpegCommandList;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoEditActivity extends AppCompatActivity {

    private final String TAG = "VideoEditActivity";
    public static final String VIDEO_PATH = "video_path";
    public static final String SAVE_NAME = "save_name";
    private String targetPath;
    private String videoPath;
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

    private float mMinCutTime = 3.0f;//裁剪最小时长，单位秒
    private float mMaxCutTime = 15.0f;//裁剪最大时长，单位秒
    private int mMinCutTimePixels;
    private int mMaxCutTimePixels;

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

    public static void open(Context context, String videoPath, Listener listener) {
        mListener = listener;
        Intent intent = new Intent(context, VideoEditActivity.class);
        intent.putExtra(VideoEditActivity.VIDEO_PATH, videoPath);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        if(mListener != null) {
            mListener.onCreate(this);
        }
        mIsPlaying = false;

        videoPath = getIntent().getStringExtra(VideoEditActivity.VIDEO_PATH);
        String saveName = getIntent().getStringExtra(VideoEditActivity.SAVE_NAME);
        String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + Environment.DIRECTORY_DCIM + File.separator + "Camera" + File.separator;
        File parentFile = new File(filePath);
        if(!parentFile.exists()) {
            parentFile.mkdirs();
        }
        if(!TextUtils.isEmpty(saveName)) {
            targetPath = filePath + File.separator + saveName;
        }else {
            targetPath = filePath + File.separator + "VIDEO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4";
        }
        mKeyDown = false;

        mHandler = new Handler();

        loadGui();
    }

    private void loadGui() {
        videoView = findViewById(R.id.video_view);
        cutView = findViewById(R.id.cut_view);

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
        mHandler.postDelayed(mPlayerHandler, 100);
        mHandler.postDelayed(mTimerRunnable, 100);
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
        //设置准备监听
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                notifyOnPrepared();
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

        mWaveformView.setParameters(mStartPos, mEndPos, mOffset);
        mWaveformView.invalidate();

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

        int endX = mEndPos - mOffset;
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
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(
                startX,
                20,
                0,
                20);
        mStartMarker.setLayoutParams(params);

        int waveformViewWidth = mWaveformView.getWidth();
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
                    waveformViewWidth - endX - mStartMarker.getWidth(),
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

    private String getStackTrace(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public void back(View view) {
        finish();
    }

    public void onConfirm(View view) {
        long vst = (long) (Double.parseDouble(formatTime(mStartPos)) * 1000 * 1000);
        long vse = (long) (Double.parseDouble(formatTime(mEndPos)) * 1000 * 1000);
        if (vse - vst < 5 * 1000 * 1000) {
            Toast.makeText(this, "时长不能小于5秒", Toast.LENGTH_SHORT).show();
            return;
        }
        //展示裁剪进度回调
        //todo

        float[] cutArr = cutView.getCutArr();
        float left = cutArr[0];
        float top = cutArr[1];
        float right = cutArr[2];
        float bottom = cutArr[3];

        int cutWidth = cutView.getRectWidth();
        int cutHeight = cutView.getRectHeight();


        float leftPro = left / cutWidth;
        float topPro = top / cutHeight;
        float rightPro = right / cutWidth;
        float bottomPro = bottom / cutHeight;

        //得到裁剪位置
        int cropWidth = (int) (videoView.getVideoWidth() * (rightPro - leftPro));
        int cropHeight = (int) (videoView.getVideoHeight() * (bottomPro - topPro));
        if (cropWidth % 2 != 0) {
            cropWidth = cropWidth - 1;
        }
        if (cropHeight % 2 != 0) {
            cropHeight = cropHeight - 1;
        }
        float f = left / cutView.getWidth();
        float t = 1.0f - top / cutView.getHeight();
        float r = right / cutView.getWidth();
        float b = 1.0f - bottom / cutView.getHeight();


        float[] textureVertexData = {
                r, b,
                f, b,
                r, t,
                f, t
        };
        //进行裁剪逻辑
//        videoEncode.init(mFilename,
//                vst,
//                vse,
//                cropWidth,cropHeight,textureVertexData);
    }

    public void startClipVideo(View view) {
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
        cmdlist.append("500k");
        cmdlist.append(targetPath);
        final String[] commands = cmdlist.build(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                //开始执行FFmpeg命令
                FFmpegCMDUtil.executeFFmpegCommand(commands, new MyFFmpegSubscriber(VideoEditActivity.this));
            }
        }).start();
    }

    private long startTime;//记录开始时间
    private long endTime;//记录结束时间
    private float duration;//裁剪时长

    private ProgressDialog mProgressDialog;

    private void openProgressDialog() {
        //统计开始时间
        startTime = System.nanoTime();
        mProgressDialog = Utils.openProgressDialog(this, targetPath);
    }

    /**
     * 取消进度条
     *
     * @param dialogTitle Title
     */
    private void cancelProgressDialog(String dialogTitle) {
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
    private void setProgressDialog(int progress, long progressTime) {
        Log.i("RxTAG", "progress: " + progress + ", progressTime: " + progressTime + ", ---: " + (int) ((double) progressTime / 1000000 / 10 * 100f));
        if (mProgressDialog != null) {
            mProgressDialog.setProgress((int) ((double) progressTime / 1000000 / duration * 100f));
            //progressTime 可以在结合视频总时长去计算合适的进度值
            double time = (double) progressTime / 1000000;
            mProgressDialog.setMessage("已处理" + String.format("%.2f", time) + "秒");
        }
    }

    private void showDialog(String message) {
        //统计结束时间
        endTime = System.nanoTime();
        Utils.showDialog(this, message, Utils.convertUsToTime((endTime - startTime) / 1000, false));
    }

    // 这里设为静态内部类，防止内存泄露
    public class MyFFmpegSubscriber extends FFmpegCMDUtil.OnActionListener {

        private WeakReference<AppCompatActivity> mWeakReference;

        public MyFFmpegSubscriber(AppCompatActivity appCompatActivity) {
            mWeakReference = new WeakReference<>(appCompatActivity);
        }

        @Override
        public void start() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openProgressDialog();
                }
            });
        }

        @Override
        public void progress(final int secs, final long progressTime) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //progressTime 可以在结合视频总时长去计算合适的进度值
                    setProgressDialog(secs, progressTime);
                }
            });
        }

        @Override
        public void fail(final int code, final String message) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final AppCompatActivity appCompatActivity = mWeakReference.get();
                    if (appCompatActivity != null) {
                        cancelProgressDialog("出错了 onError：" + code + " " + message);
                    }
                }
            });
        }

        @Override
        public void success() {//刷新媒体库
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri uri = Uri.fromFile(new File(targetPath));
                    intent.setData(uri);
                    sendBroadcast(intent);
                    final AppCompatActivity appCompatActivity = mWeakReference.get();
                    if (appCompatActivity != null) {
                        endTime = System.nanoTime();
                        String takeUpTime = Utils.convertUsToTime((endTime - startTime) / 1000, false);
                        Toast.makeText(getBaseContext(), "耗时：" + takeUpTime, Toast.LENGTH_SHORT).show();
                        if (mProgressDialog != null) {
                            mProgressDialog.cancel();
                        }
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
                            VideoPreviewActivity.open(getBaseContext(), targetPath, "video_cut.mp4");
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
                }
            });
        }
    }

}
