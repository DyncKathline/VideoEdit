#include <string.h>
#include <jni.h>
#include <stdbool.h>
#include "ffmpeg.h"
#include <android/log.h>
#include <libavutil/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "ffmpeg-cmd", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ffmpeg-cmd", __VA_ARGS__)

//保证同时只能一个线程执行
static pthread_mutex_t cmdLock;
static int cmdLockHasInit = 0;
bool hasRegistered = false;
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
        } else if (level <= 24) {
            __android_log_vprint(ANDROID_LOG_WARN, TAG, fmt, vl);
        } else {
//            __android_log_vprint(ANDROID_LOG_VERBOSE, TAG, fmt, vl);
        }
    }
}

void progressCallBack(int64_t handle, int secs, long long progress) {
    if (handle != 0) {
        struct CallBackInfo *onActionListener = (struct CallBackInfo *) (handle);
        JNIEnv *env = onActionListener->env;
        (*env)->CallVoidMethod(env, onActionListener->obj, onActionListener->methodID, secs,
                               progress);
    }
}

JNIEXPORT jint JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_executeFFmpegCommand(JNIEnv *env,
                                                                jclass type,
                                                                jobjectArray stringArray,
                                                                jobject actionCallBack,
                                                                jlong totalTime) {
    int ret = 0;
    int cmdNum = 0;
    char **argv = NULL;//命令集 二维指针
    jstring *tempArray = NULL;

    if (stringArray != NULL) {
        cmdNum = (*env)->GetArrayLength(env, stringArray);
        argv = (char **) malloc(sizeof(char *) * cmdNum);
        tempArray = (jstring *) malloc(sizeof(jstring) * cmdNum);

        int i = 0;
        for (i = 0; i < cmdNum; ++i) {//转换
            tempArray[i] = (jstring) (*env)->GetObjectArrayElement(env, stringArray, i);
            argv[i] = (char *) (*env)->GetStringUTFChars(env, tempArray[i], 0);
        }

    }

    if (NULL == argv) {
        av_log(NULL, AV_LOG_ERROR, "NULL==command");
        return -1;
    }
    if (!hasRegistered) {
        av_register_all();
        avcodec_register_all();
        avfilter_register_all();
        avformat_network_init();
        hasRegistered = true;
    }
    if (!cmdLockHasInit) {
        pthread_mutex_init(&cmdLock, NULL);//初始化
        cmdLockHasInit = 1;
    }
    pthread_mutex_lock(&cmdLock);

    if (NULL != actionCallBack) {
        jclass actionClass = (*env)->GetObjectClass(env, actionCallBack);
        jmethodID progressMID = (*env)->GetMethodID(env, actionClass, "progress", "(IJ)V");
        jmethodID failMID = (*env)->GetMethodID(env, actionClass, "fail", "(ILjava/lang/String;)V");
        jmethodID startMID = (*env)->GetMethodID(env, actionClass, "start", "()V");
        jmethodID successMID = (*env)->GetMethodID(env, actionClass, "success", "()V");
        jmethodID cancelMID = (*env)->GetMethodID(env, actionClass, "cancel", "()V");

        CallBackInfo onActionListener;
        onActionListener.env = env;
        onActionListener.obj = actionCallBack;
        onActionListener.methodID = progressMID;

        (*env)->CallVoidMethod(env, actionCallBack, startMID);
        ret = exe_ffmpeg_cmd(cmdNum, argv, (int64_t) (&onActionListener), progressCallBack, totalTime);

        if (ret < 0) {
            jstring error = "unknown";
            if(ret == -100) {
                error = "Error splitting the argument list: ";
            } else if(ret == -101) {
                error = "Error parsing global options: ";
            } else if(ret == -102) {
                error = "Error opening input files: ";
            } else if(ret == -103) {
                error = "Error initializing complex filters.\n";
            } else if(ret == -104) {
                error = "Error opening output files: ";
            }
            (*env)->CallVoidMethod(env, actionCallBack, failMID, ret, (*env)->NewStringUTF(env, error));
        } else {
            if (ret == 255) {
                (*env)->CallVoidMethod(env, actionCallBack, cancelMID);
            } else {
                (*env)->CallVoidMethod(env, actionCallBack, successMID);
            }
        }
        (*env)->DeleteLocalRef(env, actionClass);
    } else {
        ret = exe_ffmpeg_cmd(0, argv, NULL, progressCallBack, -1);
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

JNIEXPORT jlong JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_getMediaDuration(JNIEnv *env, jclass clazz,
                                                            jstring media_path) {
    if (NULL == media_path) {
        return -1;
    }
    if (!hasRegistered) {
        av_register_all();
        avcodec_register_all();
        avfilter_register_all();
        avformat_network_init();
        hasRegistered = true;
    }
    const char *mediaPath = (*env)->GetStringUTFChars(env, media_path, 0);
    AVFormatContext *in_fmt_ctx = NULL;
    int ret = 0;
    if ((ret = avformat_open_input(&in_fmt_ctx, mediaPath, NULL, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot open input file mediaPath=%s", mediaPath);
        return ret;
    }
    if ((ret = avformat_find_stream_info(in_fmt_ctx, NULL)) < 0) {
        av_log(NULL, AV_LOG_ERROR, "Cannot find stream information\n");
        return ret;
    }
    int64_t videoDuration = 0;
    for (int i = 0; i < in_fmt_ctx->nb_streams; i++) {
        AVStream *stream;
        stream = in_fmt_ctx->streams[i];
        int64_t temp = stream->duration * 1000 * stream->time_base.num /
                       stream->time_base.den;
        if (temp > videoDuration)
            videoDuration = temp;
    }
    if (NULL != in_fmt_ctx)
        avformat_close_input(&in_fmt_ctx);

    (*env)->ReleaseStringUTFChars(env, media_path, mediaPath);
    return videoDuration;
}

char* BytesToSize(double Bytes) {
    float tb = 1099511627776;
    float gb = 1073741824;
    float mb = 1048576;
    float kb = 1024;

    char returnSize[256];

    if (Bytes >= tb)
        sprintf(returnSize, "%.2f TB", (float) Bytes / tb);
    else if (Bytes >= gb && Bytes < tb)
        sprintf(returnSize, "%.2f GB", (float) Bytes / gb);
    else if (Bytes >= mb && Bytes < gb)
        sprintf(returnSize, "%.2f MB", (float) Bytes / mb);
    else if (Bytes >= kb && Bytes < mb)
        sprintf(returnSize, "%.2f KB", (float) Bytes / kb);
    else if (Bytes < kb)
        sprintf(returnSize, "%.2f Bytes", Bytes);
    else
        sprintf(returnSize, "%.2f Bytes", Bytes);

    static char ret[256];
    strcpy(ret, returnSize);
    return ret;
}

JNIEXPORT jobject JNICALL
Java_com_luoye_bzmedia_FFmpegCMDUtil_getMediaInfo(JNIEnv *env, jclass clazz, jstring _path) {
    const char *path = (*env)->GetStringUTFChars(env, _path, JNI_FALSE);
    AVFormatContext *pfmtCxt = NULL;

    int audioStreamIdx = -1;
    int videoStreamIdx = -1;
    //初始化 libavformat和注册所有的muxers、demuxers和protocols
    if (!hasRegistered) {
        av_register_all();
        avcodec_register_all();
        avfilter_register_all();
        avformat_network_init();
        hasRegistered = true;
    }

    //以输入方式打开一个媒体文件,也即源文件
    int ok = avformat_open_input(&pfmtCxt, path, NULL, NULL);
    if (ok != 0) {
        LOGD("Couldn't open file %s: %d(%s)", path, ok, av_err2str(ok));
    }

    //通过读取媒体文件的中的包来获取媒体文件中的流信息,对于没有头信息的文件如(mpeg)是非常有用的
    //也就是把媒体文件中的音视频流等信息读出来,保存在容器中,以便解码时使用
    ok = avformat_find_stream_info(pfmtCxt, NULL);
    if (ok != 0) {
        LOGD("find stream info error.");
    }

    for (int i = 0; i < pfmtCxt->nb_streams; i++) {
        if (pfmtCxt->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoStreamIdx = i;
        } else if (pfmtCxt->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioStreamIdx = i;
        }
    }

    AVStream *videostream = NULL;
    AVStream *audiostream = NULL;
    if (videoStreamIdx != -1) {
        videostream = pfmtCxt->streams[videoStreamIdx];
    }
    if (audioStreamIdx != -1) {
        audiostream = pfmtCxt->streams[audioStreamIdx];
    }
    LOGD("==============================av_dump_format==================================\n");
    av_dump_format(pfmtCxt, 0, 0, 0);
    /*******************************输出相关media文件信息*******************************/
    LOGD("===========================================================================\n");
    LOGD("文件名 : %s \n", path);
    LOGD("输入格式 : %s \n全称 : %s \n", pfmtCxt->iformat->name, pfmtCxt->iformat->long_name);

    int64_t tns, thh, tmm, tss;
    tns = pfmtCxt->duration / 1000000;
    thh = tns / 3600;
    tmm = (tns % 3600) / 60;
    tss = (tns % 60);

    long _duration = (pfmtCxt->duration * 1.0 / AV_TIME_BASE) * 1000;
    float _bitRate = pfmtCxt->bit_rate / 1000.0;
    LOGD("总时长 : %f ms,fmt:%02lld:%02lld:%02lld \n总比特率 : %f kbs\n",
         _duration, thh, tmm, tss,
         _bitRate);//1000 bit/s = 1 kbit/s
    double _fileSize = (pfmtCxt->duration * 1.0 / AV_TIME_BASE * pfmtCxt->bit_rate / 8.0);
    LOGD("文件大小 : %s\n", BytesToSize(_fileSize));
    LOGD("协议白名单 : %s \n协义黑名单 : %s\n", pfmtCxt->protocol_whitelist, pfmtCxt->protocol_blacklist);
    LOGD("数据包的最大数量 : %d\n", pfmtCxt->max_ts_probe);
    LOGD("最大缓冲时间 : %lld\n", pfmtCxt->max_interleave_delta);
    LOGD("缓冲帧的最大缓冲 : %u Bytes\n", pfmtCxt->max_picture_buffer);
    LOGD("metadata:\n");
    AVDictionary *metadata = pfmtCxt->metadata;
    if (metadata) {
        AVDictionaryEntry *entry = NULL;
        while ((entry = av_dict_get(metadata, "", entry, AV_DICT_IGNORE_SUFFIX))) {
            LOGD("\t%s : %s\n", entry->key, entry->value);
        }
    }
    int _width = 0;
    int _height = 0;
    float _frameRate = 0;
    int _rotation = 0;
    const char* _videoCodec;
    const char* _audioCodec;
    float _video_bit_rate = 0;
    float _audio_bit_rate = 0;
    if (videostream) {
        LOGD("视频流信息(%s):\n", av_get_media_type_string(videostream->codecpar->codec_type));
        LOGD("\tStream #%d\n", videoStreamIdx);
        LOGD("\t总帧数 : %lld\n", videostream->nb_frames);
        const char *avcodocname = avcodec_get_name(videostream->codecpar->codec_id);
        const char *profilestring = avcodec_profile_name(videostream->codecpar->codec_id,
                                                         videostream->codecpar->profile);
        char *codec_fourcc = av_fourcc2str(videostream->codecpar->codec_tag);
        _videoCodec = avcodocname;
        LOGD("\t编码方式 : %s\n\tCodec Profile : %s\n\tCodec FourCC : %s\n", avcodocname, profilestring,
             codec_fourcc);
        ///如果是C++引用(AVPixelFormat)注意下强转类型
//        const char *pix_fmt_name = videostream->codecpar->format == AV_PIX_FMT_NONE ? "none" : av_get_pix_fmt_name(videostream->codecpar->format);
//
//        LOGD("\t显示编码格式(color space) : %s \n",pix_fmt_name);
        _width = videostream->codecpar->width;
        _height = videostream->codecpar->height;
        LOGD("\t宽 : %d pixels,高 : %d pixels \n", _width,
             _height);
        AVRational display_aspect_ratio;
        av_reduce(&display_aspect_ratio.num, &display_aspect_ratio.den,
                  videostream->codecpar->width * (int64_t) videostream->sample_aspect_ratio.num,
                  videostream->codecpar->height * (int64_t) videostream->sample_aspect_ratio.den,
                  1024 * 1024);
        LOGD("\tsimple_aspect_ratio(SAR) : %d : %d\n\tdisplay_aspect_ratio(DAR) : %d : %d \n",
             videostream->sample_aspect_ratio.num,
             videostream->sample_aspect_ratio.den, display_aspect_ratio.num,
             display_aspect_ratio.den);
        _rotation = get_rotation(videostream);
        LOGD("\t视频旋转角度 : %d\n", _rotation);
        _frameRate = av_q2d(videostream->avg_frame_rate);
        LOGD("\t最低帧率 : %f fps\n\t平均帧率 : %f fps\n", av_q2d(videostream->r_frame_rate),
             _frameRate);
        LOGD("\t每个像素点的比特数 : %d bits\n", videostream->codecpar->bits_per_raw_sample);
        LOGD("\t每个像素点编码比特数 : %d bits\n",
             videostream->codecpar->bits_per_coded_sample); //YUV三个分量每个分量是8,即24
        _video_bit_rate = videostream->codecpar->bit_rate / 1000.0;
        LOGD("\t视频流比特率 : %f kbps\n", videostream->codecpar->bit_rate / 1000.0);
        LOGD("\t基准时间 : %d / %d = %f \n", videostream->time_base.num, videostream->time_base.den,
             av_q2d(videostream->time_base));
        LOGD("\t视频流时长 : %f ms\n", videostream->duration * av_q2d(videostream->time_base) * 1000);
        LOGD("\t帧率(tbr) : %f\n", av_q2d(videostream->r_frame_rate));
        LOGD("\t文件层的时间精度(tbn) : %f\n", 1 / av_q2d(videostream->time_base));
        LOGD("\t视频层的时间精度(tbc) : %f\n", 1 / av_q2d(videostream->codec->time_base));

        double s = videostream->duration * av_q2d(videostream->time_base);
        int64_t tbits = videostream->codecpar->bit_rate * s;
        double stsize = tbits / 8;
        LOGD("\t视频流大小(Bytes) : %s \n", BytesToSize(stsize));
        LOGD("\tmetadata:\n");

        AVDictionary *metadata = videostream->metadata;
        if (metadata) {
            AVDictionaryEntry *entry = NULL;
            while ((entry = av_dict_get(metadata, "", entry, AV_DICT_IGNORE_SUFFIX))) {
                LOGD("\t\t%s : %s\n", entry->key, entry->value);
            }
        }
    }

    if (audiostream) {
        LOGD("音频流信息(%s):\n", av_get_media_type_string(audiostream->codecpar->codec_type));
        LOGD("\tStream #%d\n", audioStreamIdx);
        LOGD("\t音频时长 : %f ms\n", audiostream->duration * av_q2d(audiostream->time_base) * 1000);
        const char *avcodocname = avcodec_get_name(audiostream->codecpar->codec_id);
        const char *profilestring = avcodec_profile_name(audiostream->codecpar->codec_id,
                                                         audiostream->codecpar->profile);
        char *codec_fourcc = av_fourcc2str(audiostream->codecpar->codec_tag);
        _audioCodec = avcodocname;
        LOGD("\t编码格式 %s (%s,%s)\n", avcodocname, profilestring, codec_fourcc);
        LOGD("\t音频采样率 : %d Hz\n", audiostream->codecpar->sample_rate);
        LOGD("\t音频声道数 : %d \n", audiostream->codecpar->channels);
        _audio_bit_rate = audiostream->codecpar->bit_rate / 1000.0;
        LOGD("\t音频流比特率 : %f kbps\n", audiostream->codecpar->bit_rate / 1000.0);
        double s = audiostream->duration * av_q2d(audiostream->time_base);
        int64_t tbits = audiostream->codecpar->bit_rate * s;
        double stsize = tbits / 8;
        LOGD("\t音频流大小(Bytes) : %s\n", BytesToSize(stsize));
    }
    jclass myClass = (*env)->FindClass(env, "com/luoye/bzmedia/FMediaMetadata");
    // 获取类的构造函数，记住这里是调用无参的构造函数
    jmethodID id = (*env)->GetMethodID(env, myClass, "<init>", "()V");
    // 创建一个新的对象
    jobject fMediaMetadata = (*env)->NewObject(env, myClass, id);
    jfieldID videoWidth = (*env)->GetFieldID(env, myClass, "videoWidth", "I");
    jfieldID videoHeight = (*env)->GetFieldID(env, myClass, "videoHeight", "I");
    jfieldID duration = (*env)->GetFieldID(env, myClass, "duration", "J");
    jfieldID fileSize = (*env)->GetFieldID(env, myClass, "fileSize", "D");
    jfieldID rotate = (*env)->GetFieldID(env, myClass, "rotate", "I");
    jfieldID frameRate = (*env)->GetFieldID(env, myClass, "frameRate", "F");
    jfieldID bitrate = (*env)->GetFieldID(env, myClass, "bitrate", "F");
    jfieldID videoBitrate = (*env)->GetFieldID(env, myClass, "videoBitrate", "F");
    jfieldID audioBitrate = (*env)->GetFieldID(env, myClass, "audioBitrate", "F");
    jfieldID videoCodec = (*env)->GetFieldID(env, myClass, "videoCodec", "Ljava/lang/String;");
    jfieldID audioCodec = (*env)->GetFieldID(env, myClass, "audioCodec", "Ljava/lang/String;");

    (*env)->SetIntField(env, fMediaMetadata, videoWidth, _width);
    (*env)->SetIntField(env, fMediaMetadata, videoHeight, _height);
    (*env)->SetIntField(env, fMediaMetadata, rotate, _rotation);
    (*env)->SetFloatField(env, fMediaMetadata, bitrate, _bitRate);
    (*env)->SetFloatField(env, fMediaMetadata, videoBitrate, _video_bit_rate);
    (*env)->SetFloatField(env, fMediaMetadata, audioBitrate, _audio_bit_rate);
    (*env)->SetFloatField(env, fMediaMetadata, frameRate, _frameRate);
    (*env)->SetLongField(env, fMediaMetadata, duration, _duration);
    (*env)->SetDoubleField(env, fMediaMetadata, fileSize, _fileSize);
    (*env)->SetObjectField(env, fMediaMetadata, videoCodec, (*env)->NewStringUTF(env, _videoCodec));
    (*env)->SetObjectField(env, fMediaMetadata, audioCodec, (*env)->NewStringUTF(env, _audioCodec));
    return fMediaMetadata;
}


