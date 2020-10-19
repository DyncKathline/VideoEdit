#include <string.h>
#include <jni.h>
#include "ffmpeg.h"
#include <android/log.h>
#include <libavutil/log.h>

/** Defines tag used for Android logging. */
#define LIB_NAME "ffmpeg-cmd"

typedef struct CallBackInfo {
    JNIEnv *env;
    jobject obj;
    jmethodID methodID;
} CallBackInfo;
const char *TAG = "bz_";

void log_call_back(void *ptr, int level, const char *fmt, va_list vl) {
    //自定义的日志
    if (level == 3) {
        __android_log_vprint(ANDROID_LOG_ERROR, TAG, fmt, vl);
    } else if (level == 2) {
        __android_log_vprint(ANDROID_LOG_DEBUG, TAG, fmt, vl);
    } else if (level == 1) {
        __android_log_vprint(ANDROID_LOG_VERBOSE, TAG, fmt, vl);
    } else {
        if (level <= 16) {//ffmpeg 来的日志
            __android_log_vprint(ANDROID_LOG_ERROR, TAG, fmt, vl);
        } else {
//            __android_log_vprint(ANDROID_LOG_VERBOSE, TAG, fmt, vl);
        }
    }
}

void progressCallBack(int64_t handle, int what, float progress) {
    if (handle != 0) {
        struct CallBackInfo *onActionListener = (struct CallBackInfo *) (handle);
        JNIEnv *env = onActionListener->env;
        (*env)->CallVoidMethod(env, onActionListener->obj, onActionListener->methodID,
                               progress);
    }
}

JNIEXPORT jint JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_executeFFmpegCommand(JNIEnv *env,
                                                          jclass type,
                                                          jobjectArray stringArray,
                                                          jobject actionCallBack) {
    int cmdNum = 0;
    char **argv = NULL;//命令集 二维指针
    jstring *strr = NULL;

    if (stringArray != NULL) {
        cmdNum = (*env)->GetArrayLength(env, stringArray);
        argv = (char **) malloc(sizeof(char *) * cmdNum);
        strr = (jstring *) malloc(sizeof(jstring) * cmdNum);

        int i = 0;
        for (i = 0; i < cmdNum; ++i) {//转换
            strr[i] = (jstring)(*env)->GetObjectArrayElement(env, stringArray, i);
            argv[i] = (char *) (*env)->GetStringUTFChars(env, strr[i], 0);
        }

    }

    int ret = 0;
    if (NULL != actionCallBack) {
        jclass actionClass = (*env)->GetObjectClass(env, actionCallBack);
        jmethodID progressMID = (*env)->GetMethodID(env, actionClass, "progress", "(F)V");
        jmethodID failMID = (*env)->GetMethodID(env, actionClass, "fail", "()V");
        jmethodID successMID = (*env)->GetMethodID(env, actionClass, "success", "()V");


        CallBackInfo onActionListener;
        onActionListener.env = env;
        onActionListener.obj = actionCallBack;
        onActionListener.methodID = progressMID;

        ret = exe_ffmpeg_cmd(cmdNum, argv, (int64_t) (&onActionListener), progressCallBack);
        if (ret < 0) {
            (*env)->CallVoidMethod(env, actionCallBack, failMID);
        } else {
            (*env)->CallVoidMethod(env, actionCallBack, successMID);
        }
        (*env)->DeleteLocalRef(env, actionClass);
    } else {
        ret = exe_ffmpeg_cmd(0, argv, 0, NULL);
    }

    // CLEANUP
    free(strr);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_showLog(JNIEnv *env, jclass clazz, jboolean showLog) {
    if (showLog) {
        av_log_set_callback(log_call_back);
    } else {
        av_log_set_callback(NULL);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_executeFFmpegCancel(JNIEnv *env, jclass clazz) {
    cancel_operation();
}
