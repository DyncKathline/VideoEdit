package com.luoye.bzmedia;

public class FMediaMetadata {
    int videoWidth;
    int videoHeight;
    long duration;
    double fileSize;
    int rotate;
    float frameRate;
    float bitrate;
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

    public String getVideoCodec() {
        return videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }
}
