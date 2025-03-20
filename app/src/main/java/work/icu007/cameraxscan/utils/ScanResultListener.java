package work.icu007.cameraxscan.utils;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:21
 * E-mail: charlie.liao@icu007.work
 */


/**
 * 扫描结果回调接口
 */
public interface ScanResultListener {
    /**
     * 当成功扫描到条码时调用
     *
     * @param result 包含扫描结果的对象
     */
    void onScanResult(ScanResult result);
}

