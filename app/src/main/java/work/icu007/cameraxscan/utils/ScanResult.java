package work.icu007.cameraxscan.utils;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:21
 * E-mail: charlie.liao@icu007.work
 */

/**
 * 封装扫描结果的类
 */
public class ScanResult {
    private final boolean success;
    private final String text;
    private final String decoderType;
    private final long decodeTime; // 新增解码时间字段
    /**
     * 创建一个扫描结果对象
     *
     * @param success 是否成功识别条码
     * @param text 条码内容，如果未识别成功则为null
     * @param decoderType 解码器类型（如"MLKit", "ZXing"等）
     */
    public ScanResult(boolean success, String text, String decoderType) {
        this(success, text, decoderType, 0); // 调用新构造函数，默认解码时间为0
    }
    /**
     * 创建一个带解码时间的扫描结果对象
     *
     * @param success 是否成功识别条码
     * @param text 条码内容，如果未识别成功则为null
     * @param decoderType 解码器类型（如"MLKit", "ZXing"等）
     * @param decodeTime 解码耗时（毫秒）
     */
    public ScanResult(boolean success, String text, String decoderType, long decodeTime) {
        this.success = success;
        this.text = text;
        this.decoderType = decoderType;
        this.decodeTime = decodeTime;
    }


    public long getDecodeTime() {
        return decodeTime;
    }
    @Override
    public String toString() {
        return "ScanResult{" +
                "success=" + success +
                ", text='" + text + '\'' +
                ", decoderType='" + decoderType + '\'' +
                ", decodeTime=" + decodeTime + "ms" +
                '}';
    }
    /**
     * 判断扫描是否成功
     *
     * @return 如果成功解码返回true，否则返回false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取扫描到的文本内容
     *
     * @return 条码内容，如果未扫描成功则为null
     */
    public String getText() {
        return text;
    }

    /**
     * 获取用于解码的解码器类型
     *
     * @return 解码器类型的标识符
     */
    public String getDecoderType() {
        return decoderType;
    }

    public String getValue() {
        return text;
    }
}

