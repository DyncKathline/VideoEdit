package com.luoye.bzmedia;

public class FMediaMetadata {
    int videoWidth;
    int videoHeight;
    long duration;
    double fileSize;
    int rotate;
    float frameRate;
    float bitrate;
    float videoBitrate;
    float audioBitrate;
    String videoCodec;
    String audioCodec;

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public long getDuration() {
        return duration;
    }

    public double getFileSize() {
        return fileSize;
    }

    public int getRotate() {
        return rotate;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public float getBitrate() {
        return bitrate;
    }

    public float getVideoBitrate() {
        return videoBitrate;
    }

    public void setVideoBitrate(float videoBitrate) {
        this.videoBitrate = videoBitrate;
    }

    public float getAudioBitrate() {
        return audioBitrate;
    }

    public void setAudioBitrate(float audioBitrate) {
        this.audioBitrate = audioBitrate;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    @Override
    public String toString() {
        return "FMediaMetadata{" +
                "videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", duration=" + duration +
                ", fileSize=" + fileSize +
                ", rotate=" + rotate +
                ", frameRate=" + frameRate +
                ", bitrate=" + bitrate +
                ", videoBitrate=" + videoBitrate +
                ", audioBitrate=" + audioBitrate +
                ", videoCodec='" + videoCodec + '\'' +
                ", audioCodec='" + audioCodec + '\'' +
                '}';
    }
}
