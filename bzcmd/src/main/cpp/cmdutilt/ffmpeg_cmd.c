#include <string.h>
#include <jni.h>
#include "ffmpeg.h"
#include <android/log.h>
#include <libavutil/log.h>

//保证同时只能一个线程执行
static pthread_mutex_t cmdLock;
static int cmdLockHasInit = 0;

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

void progressCallBack(int64_t handle, int secs, long progress) {
    if (handle != 0) {
        struct CallBackInfo *onActionListener = (struct CallBackInfo *) (handle);
        JNIEnv *env = onActionListener->env;
        (*env)->CallVoidMethod(env, onActionListener->obj, onActionListener->methodID, secs, progress);
    }
}

JNIEXPORT jint JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_executeFFmpegCommand(JNIEnv *env,
                                                          jclass type,
                                                          jobjectArray stringArray,
                                                          jobject actionCallBack) {
    if (!cmdLockHasInit) {
        pthread_mutex_init(&cmdLock, NULL);//初始化
        cmdLockHasInit = 1;
    }
    pthread_mutex_lock(&cmdLock);

    int cmdNum = 0;
    char **argv = NULL;//命令集 二维指针
    jstring *tempArray = NULL;

    if (stringArray != NULL) {
        cmdNum = (*env)->GetArrayLength(env, stringArray);
        argv = (char **) malloc(sizeof(char *) * cmdNum);
        tempArray = (jstring *) malloc(sizeof(jstring) * cmdNum);

        int i = 0;
        for (i = 0; i < cmdNum; ++i) {//转换
            tempArray[i] = (jstring)(*env)->GetObjectArrayElement(env, stringArray, i);
            argv[i] = (char *) (*env)->GetStringUTFChars(env, tempArray[i], 0);
        }

    }

    int ret = 0;
    if (NULL != actionCallBack) {
        jclass actionClass = (*env)->GetObjectClass(env, actionCallBack);
        jmethodID progressMID = (*env)->GetMethodID(env, actionClass, "progress", "(IJ)V");
        jmethodID failMID = (*env)->GetMethodID(env, actionClass, "fail", "()V");
        jmethodID successMID = (*env)->GetMethodID(env, actionClass, "success", "()V");
        jmethodID cancelMID = (*env)->GetMethodID(env, actionClass, "cancel", "()V");


        CallBackInfo onActionListener;
        onActionListener.env = env;
        onActionListener.obj = actionCallBack;
        onActionListener.methodID = progressMID;

        ret = exe_ffmpeg_cmd(cmdNum, argv, (int64_t) (&onActionListener), progressCallBack);
        av_log(NULL, AV_LOG_ERROR, "exe_ffmpeg_cmd ret=%d\n", ret);
        if (ret < 0) {
            (*env)->CallVoidMethod(env, actionCallBack, failMID);
        } else {
            if(ret == 255) {
                (*env)->CallVoidMethod(env, actionCallBack, cancelMID);
            } else {
                (*env)->CallVoidMethod(env, actionCallBack, successMID);
            }
        }
        (*env)->DeleteLocalRef(env, actionClass);
    } else {
        ret = exe_ffmpeg_cmd(0, argv, 0, NULL);
    }

    free(tempArray);
    pthread_mutex_unlock(&cmdLock);
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

JNIEXPORT jint JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_cancelExecuteFFmpegCommand(JNIEnv *env, jclass clazz) {
    return cancel_exe_ffmpeg_cmd();
}
