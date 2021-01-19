package com.luoye.bzffmpegcmd.fileStoreSAF;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileUtils {

    public static List<ZFileBean> getSelectData(Context context, int requestCode, int resultCode, Intent data, SAF saf) {
        List<ZFileBean> list = new ArrayList<>();
        if (data == null) {
            return list;
        }
        if(data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            int itemCount = clipData.getItemCount();
            for (int i = 0; i < itemCount; i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                safToData(context, list, uri, saf);
            }
        }else {
            Uri uri = data.getData();
            safToData(context, list, uri, saf);
        }
        return list;
    }

    /**
     * SAF框架选择文件后转化为ZFileBean列表
     * @param context
     * @param list
     * @param uri
     */
    public static void safToData(Context context, List<ZFileBean> list, Uri uri, SAF saf) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
//                for (String name : cursor.getColumnNames()) {
//                    Log.d("kath--", cursor.getColumnIndexOrThrow(name) + name);
//                }
                while (cursor.moveToNext()) {
                    String path = "";
                    boolean isDATA = false;
                    if(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA) != -1) {
                        path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                        isDATA = true;
                    }else if(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME) != -1) {
                        path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
                    }
//                    String mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE));
                    long size = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE));
                    long date = 0;
                    if(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED) != -1) {
                        date = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED));
                    }else if(cursor.getColumnIndex("last_modified") != -1) {
                        date = cursor.getLong(cursor.getColumnIndex("last_modified")) / 1000;
                    }
                    String fileSize = getFileSize(size);
                    String lastModified = getFormatFileDate(date * (long) 1000);
                    String name;
                    if(isDATA) {
                        name = path.substring(path.lastIndexOf("/") + 1);
                    }else {
                        name = path;
                    }
                    double originSize = size / 1048576d; // byte -> MB
                    ZFileBean bean = new ZFileBean(name, true, path, lastModified, String.valueOf(date), fileSize, size);
                    if(originSize <= saf.getMaxSize()) {
                        if(list.size() < saf.getMaxCount()) {
                            list.add(bean);
                        }else {
                            Log.e(SAF.TAG, "超过配置的maxLength长度文件：" + bean.toString());
                        }
                    }else {
                        Log.e(SAF.TAG, "超过配置的maxSize大小文件：" + bean.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * 获取文件大小
     */
    public static String getFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        Double byte_size = Double.valueOf(df.format(fileS));
        if (byte_size < 1024) {
            return byte_size + " B";
        }
        Double kb_size = Double.valueOf(df.format(fileS / 1024d));
        if (kb_size < 1024) {
            return kb_size + " KB";
        }
        Double mb_size = Double.valueOf(df.format(fileS / 1048576d));
        if (mb_size < 1024) {
            return mb_size + " MB";
        }
        Double gb_size = Double.valueOf(df.format(fileS / 1073741824d));
        if (gb_size < 1024) {
            return gb_size + " GB";
        }
        return ">1TB";
    }

    /**
     * 时间戳格式化
     */
    public static String getFormatFileDate(long seconds) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return dateFormat.format(new Date(seconds));
    }
}
