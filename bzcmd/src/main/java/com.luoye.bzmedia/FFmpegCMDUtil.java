package com.luoye.bzmedia;

/**
 * Created by zhandalin on 2017-05-25 16:20.
 * 说明:
 */
public class FFmpegCMDUtil {
    static {
        System.loadLibrary("bzffmpeg");
        System.loadLibrary("bzffmpegcmd");
    }

    public synchronized static native int showLog(boolean showLog);

    public synchronized static native int executeFFmpegCommand(String[] command, OnActionListener onActionListener);

    /**
     * <p>Cancels an ongoing FFmpeg operation natively. This function does not wait for termination
     * to complete and returns immediately.
     */
    public synchronized native static void executeFFmpegCancel();

    public interface OnActionListener {
        /**
         *
         * @param secs              处理进度，单位秒
         * @param progressTime      处理进度，单位纳秒
         */
        void progress(int secs, long progressTime);

        void fail();

        void success();
    }
}
