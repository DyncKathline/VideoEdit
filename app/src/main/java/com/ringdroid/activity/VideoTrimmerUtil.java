package com.ringdroid.activity;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.ringdroid.util.ThreadUtil;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Author：J.Chou
 * Date：  2016.08.01 2:23 PM
 * Email： who_know_me@163.com
 * Describe:
 */
public class VideoTrimmerUtil {

  private static final String TAG = VideoTrimmerUtil.class.getSimpleName();
  public static final long MIN_SHOOT_DURATION = 3000L;// 最小剪辑时间3s
  public static final int VIDEO_MAX_TIME = 10;// 10秒
  public static final long MAX_SHOOT_DURATION = VIDEO_MAX_TIME * 1000L;//视频最多剪切多长时间10s

  public static final int MAX_COUNT_RANGE = 10;  //seekBar的区域内一共有多少张图片
  private static int SCREEN_WIDTH_FULL;
  public static int RECYCLER_VIEW_PADDING;
  public static final int VIDEO_FRAMES_WIDTH = SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 2;
  public static final int THUMB_WIDTH = (SCREEN_WIDTH_FULL - RECYCLER_VIEW_PADDING * 2) / VIDEO_MAX_TIME;
  private static int THUMB_HEIGHT;

  public interface SingleCallback<T,V> {
    void onSingleCallback(T t,V v);
  }

  public static void shootVideoThumbInBackground(final Activity context, final Uri videoUri, final long interval, final long startPosition,
                                                 final long endPosition, final SingleCallback<Bitmap, Integer> callback) {
    final WeakReference<Activity> weakReference = new WeakReference<>(context);
    SCREEN_WIDTH_FULL = getDeviceWidth(context);
    RECYCLER_VIEW_PADDING = dp2px(context, 35);
    THUMB_HEIGHT = dp2px(context, 50);
    ThreadUtil.getInstance().doBackTaskDelay(new ThreadUtil.FutureRunnable() {
      @Override
      public void run() {
        try {
          MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
          mediaMetadataRetriever.setDataSource(weakReference.get(), videoUri);
          int width = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));//宽
          int height = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));//高
          float vh = width * 1.0f / height;
          int w = 300;
          int h = 300;
          if (width > height) {
            h = (int) (w / vh);
          } else if (width < height) {
            w = (int) (h * vh);
          }
          // Retrieve media data use microsecond
          long totalThumbsCount = endPosition / 1000;
          for (long i = 0; i < totalThumbsCount; ++i) {
            if(weakReference.get().isFinishing() || weakReference.get().isDestroyed()) {
              getFuture().cancel(false);
              break;
            }
            long frameTime = startPosition + interval * i;
            Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if(bitmap == null) continue;
            try {
              bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
            } catch (final Throwable t) {
              t.printStackTrace();
            }
            callback.onSingleCallback(bitmap, (int) frameTime / 1000);
          }
          mediaMetadataRetriever.release();
        } catch (final Throwable e) {
          Log.e("TAG", e.getMessage(), e);
        }
      }
    }, 0L);
  }

  public static String getVideoFilePath(String url) {
    if (TextUtils.isEmpty(url) || url.length() < 5) return "";
    if (url.substring(0, 4).equalsIgnoreCase("http")) {

    } else {
      url = "file://" + url;
    }

    return url;
  }

  private static String convertSecondsToTime(long seconds) {
    String timeStr = null;
    int hour = 0;
    int minute = 0;
    int second = 0;
    if (seconds <= 0) {
      return "00:00";
    } else {
      minute = (int) seconds / 60;
      if (minute < 60) {
        second = (int) seconds % 60;
        timeStr = "00:" + unitFormat(minute) + ":" + unitFormat(second);
      } else {
        hour = minute / 60;
        if (hour > 99) return "99:59:59";
        minute = minute % 60;
        second = (int) (seconds - hour * 3600 - minute * 60);
        timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
      }
    }
    return timeStr;
  }

  private static String unitFormat(int i) {
    String retStr = null;
    if (i >= 0 && i < 10) {
      retStr = "0" + Integer.toString(i);
    } else {
      retStr = "" + i;
    }
    return retStr;
  }

  private static int getDeviceWidth(Context context) {
    return context.getResources().getDisplayMetrics().widthPixels;
  }

  private static int dp2px(Context context, float dpValue) {
    return (int) (dpValue * context.getResources().getDisplayMetrics().density + 0.5f);
  }
}
