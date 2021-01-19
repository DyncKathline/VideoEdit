package com.luoye.bzffmpegcmd.fileStoreSAF;

import android.content.Intent;

public interface SAFListener {
    void onResult(int requestCode, int resultCode, Intent data);
}
