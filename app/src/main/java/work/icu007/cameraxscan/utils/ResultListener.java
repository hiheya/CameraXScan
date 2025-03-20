package work.icu007.cameraxscan.utils;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:05
 * E-mail: charlie.liao@icu007.work
 */


import androidx.annotation.NonNull;

public interface ResultListener {
    /**
     * 当解码器找到结果时回调
     * @param text 解码的文本结果
     * @param source 结果来源（"ZBar"或"ZXing"）
     */
    void onResult(@NonNull String text, String source);
}
