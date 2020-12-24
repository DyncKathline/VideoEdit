package com.luoye.bzffmpegcmd;

import android.text.TextUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

/**
 * 数字转换算法工具
 */
public class DecimalUtil {
    private static Pattern pattern = Pattern.compile("^[-\\+]?[.\\d]*$");

    /**
     * 四舍五入算法 取整
     *
     * @param str 输入值
     * @return 返回值
     */
    public static String roundHalfUp(String str) {
        if (TextUtils.isEmpty(str)) {
            return "0";
        }
        if (!isNum(str)) {
            return "0";
        }
        BigDecimal decimal = new BigDecimal(str);
        double num = decimal.setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
        DecimalFormat format = new DecimalFormat("##0");
        return format.format(num);
    }

    /**
     * 四舍五入算法
     *
     * @param str   输入值
     * @param scale 精度,保留几位小数 0, 1 ,2 , 3.....
     * @return 返回值
     */
    public static double roundHalfUp(String str, int scale) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        if (!isNum(str)) {
            return 0;
        }
        BigDecimal decimal = new BigDecimal(str);
        return decimal.setScale(scale, BigDecimal.ROUND_UP).doubleValue();
    }

    /**
     * 字符串转换成整形
     *
     * @param str str
     * @return int
     */
    public static int string2Int(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        if (!isNum(str)) {
            return 0;
        }
        return Integer.parseInt(str);
    }

    /**
     * 字符串转换成长整形
     *
     * @param str str
     * @return long
     */
    public static long string2Long(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        if (!isNum(str)) {
            return 0;
        }
        return Long.parseLong(str);
    }

    /**
     * 字符串转换成float
     *
     * @param str str
     * @return float
     */
    public static float string2Float(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        if (!isNum(str)) {
            return 0;
        }
        return Float.parseFloat(str);
    }

    /**
     * 判断字符串是否可以转成数字
     *
     * @param str str
     * @return boolean
     */
    public static boolean isNum(String str) {
        if (null == str || "".equals(str)) {
            return false;
        }

        return pattern.matcher(str).matches();
    }
}
