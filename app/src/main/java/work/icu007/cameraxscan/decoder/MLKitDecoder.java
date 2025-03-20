package work.icu007.cameraxscan.decoder;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:19
 * E-mail: charlie.liao@icu007.work
 */


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import work.icu007.cameraxscan.utils.ScanResult;

public class MLKitDecoder {
    private static final String TAG = "MLKitDecoder";
    private final ExecutorService executor;
    private final BarcodeScanner scanner;

    public MLKitDecoder() {
        // 创建一个线程池用于异步处理
        this.executor = Executors.newSingleThreadExecutor();

        // 配置条码扫描选项 - 支持所有格式
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_ALL_FORMATS)
                .build();

        // 创建扫描器实例
        this.scanner = BarcodeScanning.getClient(options);
    }

    /**
     * 从ImageProxy解码条码
     */
    @SuppressLint("UnsafeOptInUsageError")
    public CompletableFuture<ScanResult> decode(ImageProxy imageProxy) {
        CompletableFuture<ScanResult> future = new CompletableFuture<>();

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            future.complete(new ScanResult(false, null, "MLKit"));
            imageProxy.close();
            return future;
        }

        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        // 添加安全检查
        if (isShutdown(executor)) {
            Log.w(TAG, "Executor已被关闭，无法处理解码请求");
            future.complete(new ScanResult(false, null, "MLKit"));
            imageProxy.close();
            return future;
        }

        // 使用MLKit扫描条码
        scanner.process(image)
                .addOnSuccessListener(task -> {
                    // 在主线程处理结果，避免使用可能被关闭的executor
                    if (task != null && !task.isEmpty()) {
                        Barcode barcode = task.get(0);
                        String value = barcode.getRawValue();
                        if (value != null && !value.isEmpty()) {
                            Log.d(TAG, "MLKit解码成功: " + value);
                            future.complete(new ScanResult(true, value, "MLKit"));
                        } else {
                            future.complete(new ScanResult(false, null, "MLKit"));
                        }
                    } else {
                        future.complete(new ScanResult(false, null, "MLKit"));
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    // 在主线程处理错误
                    Log.e(TAG, "MLKit解码失败", e);
                    future.complete(new ScanResult(false, null, "MLKit"));
                    imageProxy.close();
                });

        return future;
    }

    // 检查执行器是否已关闭
    private boolean isShutdown(Executor executor) {
        if (executor instanceof ExecutorService) {
            return ((ExecutorService) executor).isShutdown();
        } else if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).isShutdown();
        }
        // 无法确定状态时，假设未关闭
        return false;
    }


    /**
     * 从Bitmap解码条码
     */
    public CompletableFuture<ScanResult> decodeBitmap(Bitmap bitmap) {
        CompletableFuture<ScanResult> future = new CompletableFuture<>();

        // 从Bitmap创建输入图像
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 使用MLKit扫描条码
        scanner.process(image)
                .addOnSuccessListener(executor, barcodes -> {
                    if (barcodes.size() > 0) {
                        Barcode barcode = barcodes.get(0);
                        String value = barcode.getRawValue();
                        if (value != null && !value.isEmpty()) {
                            Log.d(TAG, "MLKit从Bitmap解码成功: " + value);
                            future.complete(new ScanResult(true, value, "MLKit"));
                        } else {
                            future.complete(new ScanResult(false, null, "MLKit"));
                        }
                    } else {
                        future.complete(new ScanResult(false, null, "MLKit"));
                    }
                })
                .addOnFailureListener(executor, e -> {
                    Log.e(TAG, "MLKit从Bitmap解码失败", e);
                    future.complete(new ScanResult(false, null, "MLKit"));
                });

        return future;
    }

    /**
     * 优雅地关闭解码器，释放资源
     */
    public void release() {
        scanner.close();
        executor.shutdown();
    }

    /**
     * 从Bitmap解码条码（同步版本）
     *
     * @param bitmap 要解码的位图
     * @return 扫描结果
     */
    public ScanResult decodeFromBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return new ScanResult(false, null, "MLKit");
        }

        // 从Bitmap创建输入图像
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // 使用CountDownLatch实现同步等待
        CountDownLatch latch = new CountDownLatch(1);
        final ScanResult[] result = new ScanResult[1];

        // 使用MLKit扫描条码
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes != null && !barcodes.isEmpty()) {
                        Barcode barcode = barcodes.get(0);
                        String value = barcode.getRawValue();
                        if (value != null && !value.isEmpty()) {
                            Log.d(TAG, "MLKit从Bitmap同步解码成功: " + value);
                            result[0] = new ScanResult(true, value, "MLKit");
                        } else {
                            result[0] = new ScanResult(false, null, "MLKit");
                        }
                    } else {
                        result[0] = new ScanResult(false, null, "MLKit");
                    }
                    latch.countDown();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "MLKit从Bitmap同步解码失败", e);
                    result[0] = new ScanResult(false, null, "MLKit");
                    latch.countDown();
                });

        try {
            // 等待最多2秒钟
            boolean completed = latch.await(2, TimeUnit.SECONDS);
            if (!completed) {
                Log.w(TAG, "MLKit从Bitmap解码超时");
                return new ScanResult(false, null, "MLKit");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "等待MLKit解码结果被中断", e);
            Thread.currentThread().interrupt();
            return new ScanResult(false, null, "MLKit");
        }

        return result[0] != null ? result[0] : new ScanResult(false, null, "MLKit");
    }
}