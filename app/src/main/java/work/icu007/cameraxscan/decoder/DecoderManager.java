package work.icu007.cameraxscan.decoder;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:02
 * E-mail: charlie.liao@icu007.work
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import org.opencv.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import work.icu007.cameraxscan.processor.ImageProcessor;
import work.icu007.cameraxscan.utils.ScanResult;
import work.icu007.cameraxscan.utils.ScanResultListener;

public class DecoderManager {
    private static final String TAG = "DecoderManager";
    private final ScheduledExecutorService scheduledExecutor;
    private final ScanResultListener listener;
    private final MLKitDecoder mlKitDecoder;
    private final ZXingDecoder zXingDecoder;
    private final ImageProcessor imageProcessor;
    private final AtomicBoolean resultFound = new AtomicBoolean(false);
    private volatile boolean isScanning = false;

    // 跟踪正在处理的图像
    private final Set<ImageProxy> activeImages = Collections.synchronizedSet(new HashSet<>());

    public DecoderManager(ScanResultListener resultListener) {
        this.listener = resultListener;
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.mlKitDecoder = new MLKitDecoder();
        this.zXingDecoder = new ZXingDecoder();
        this.imageProcessor = new ImageProcessor();
    }

    // 资源共享类
    private static class SharedImageResources {
        Bitmap originalBitmap;
        Bitmap processedBitmap;
    }


    public void decodeAsync(ImageProxy imageProxy) {
        // 每次开始新解码前重置状态
        resumeScanning();

        // 如果不在扫描状态，直接返回
        if (!isScanning) {
            imageProxy.close();
            return;
        }

        // 记录处理中的图像
        activeImages.add(imageProxy);

        // 记录开始时间
        final long startTime = System.currentTimeMillis();

        try {
            // 创建一个共享资源对象，包含所有从imageProxy中提取的图像数据
            SharedImageResources resources = extractImageResources(imageProxy);

            // 如果资源提取失败，直接清理并返回
            if (resources == null || resources.originalBitmap == null) {
                cleanupImageProxy(imageProxy);
                return;
            }

            // 创建超时处理
            ScheduledFuture<?> timeoutFuture = scheduleTimeout(imageProxy, resources, startTime);

            // 创建两个解码任务
            CompletableFuture<ScanResult> mlKitFuture = createMLKitTask(resources, startTime);
            CompletableFuture<ScanResult> zxingFuture = createZXingTask(resources, startTime);

            // 处理成功的结果
            handleSuccessfulResult(mlKitFuture, zxingFuture, imageProxy, resources, timeoutFuture);

            // 处理所有任务完成的情况
            handleTasksCompletion(mlKitFuture, zxingFuture, imageProxy, resources, timeoutFuture);

        } catch (Exception e) {
            Log.e(TAG, "解码初始化失败", e);
            cleanupImageProxy(imageProxy);
        }
    }

    private SharedImageResources extractImageResources(ImageProxy imageProxy) {
        try {
            SharedImageResources resources = new SharedImageResources();

            // 转换原始图像
            resources.originalBitmap = imageToBitmap(imageProxy);
            if (resources.originalBitmap == null) {
                Log.e(TAG, "无法从ImageProxy创建Bitmap");
                return null;
            }

            // 处理图像以增强条码识别
            try {
                resources.processedBitmap = imageProcessor.process(imageProxy);

                // 保存调试图像（仅在DEBUG模式）
                if (BuildConfig.DEBUG) {
                    saveDebugImage(resources.originalBitmap, "original");
                    if (resources.processedBitmap != null) {
                        saveDebugImage(resources.processedBitmap, "processed");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "图像处理失败", e);
                // 继续使用原始图像
            }

            return resources;
        } catch (Exception e) {
            Log.e(TAG, "提取图像资源失败", e);
            return null;
        }
    }

    private CompletableFuture<ScanResult> createMLKitTask(SharedImageResources resources, long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            if (resultFound.get()) return null; // 如果已找到结果，立即返回

            try {
                ScanResult result = null;
                // 首先尝试处理过的图像
                if (resources.processedBitmap != null && !resources.processedBitmap.isRecycled()) {
                    result = mlKitDecoder.decodeFromBitmap(resources.processedBitmap);
                }

                // 如果处理过的图像失败，尝试原始图像
                if ((result == null || !result.isSuccess()) && !resultFound.get()
                        && resources.originalBitmap != null && !resources.originalBitmap.isRecycled()) {
                    result = mlKitDecoder.decodeFromBitmap(resources.originalBitmap);
                }

                // 如果解码成功，计算耗时并创建新的ScanResult
                if (result != null && result.isSuccess()) {
                    long decodeTime = System.currentTimeMillis() - startTime;
                    return new ScanResult(true, result.getText(), "MLKit", decodeTime);
                }
                return result;
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    Log.e(TAG, "MLKit解码失败", e);
                }
                return null;
            }
        }, AsyncTask.THREAD_POOL_EXECUTOR);
    }


    private CompletableFuture<ScanResult> createZXingTask(SharedImageResources resources, long startTime) {
        return CompletableFuture.supplyAsync(() -> {
            if (resultFound.get()) return null; // 如果已找到结果，立即返回

            try {
                String decodedText = null;
                // 首先尝试处理过的图像
                if (resources.processedBitmap != null) {
                    decodedText = zXingDecoder.decode(resources.processedBitmap);
                }

                // 如果处理过的图像失败，尝试原始图像
                if (decodedText == null && !resultFound.get() && resources.originalBitmap != null
                        && !resources.originalBitmap.isRecycled()) {
                    decodedText = zXingDecoder.decode(resources.originalBitmap);
                }

                // 计算解码时间
                long decodeTime = System.currentTimeMillis() - startTime;

                return decodedText != null ?
                        new ScanResult(true, decodedText, "ZXing", decodeTime) :
                        new ScanResult(false, null, "ZXing", decodeTime);
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    Log.e(TAG, "ZXing解码失败", e);
                }
                return null;
            }
        }, AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private ScheduledFuture<?> scheduleTimeout(ImageProxy imageProxy, SharedImageResources resources, long startTime) {
        if (scheduledExecutor.isShutdown()) return null;
        return scheduledExecutor.schedule(() -> {
            if (!resultFound.get()) {
                // 保存超时图像用于调试
                if (BuildConfig.DEBUG) {
                    saveTimeoutDebugImages(resources);
                }

                // 清理资源
                cleanupResources(imageProxy, resources);

                long timeoutTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "解码超时取消, 耗时: " + timeoutTime + "ms");
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void handleSuccessfulResult(
            CompletableFuture<ScanResult> mlKitFuture,
            CompletableFuture<ScanResult> zxingFuture,
            ImageProxy imageProxy,
            SharedImageResources resources,
            ScheduledFuture<?> timeoutFuture) {

        // 处理MLKit结果
        mlKitFuture.thenAccept(result -> {
            if (result != null && result.isSuccess() && resultFound.compareAndSet(false, true)) {
                Log.d(TAG, "MLKit成功解码: " + result.getText() + ", 耗时: " + result.getDecodeTime() + "ms");

                // 取消其他任务
                cancelTasks(zxingFuture, timeoutFuture);

                // 通知结果
                handleScanResult(result);

                // 清理资源
                cleanupResources(imageProxy, resources);
            }
        });

        // 处理ZXing结果
        zxingFuture.thenAccept(result -> {
            if (result != null && result.isSuccess() && resultFound.compareAndSet(false, true)) {
                Log.d(TAG, "ZXing成功解码: " + result.getText() + ", 耗时: " + result.getDecodeTime() + "ms");

                // 取消其他任务
                cancelTasks(mlKitFuture, timeoutFuture);

                // 通知结果
                handleScanResult(result);

                // 清理资源
                cleanupResources(imageProxy, resources);
            }
        });
    }

    private void handleTasksCompletion(
            CompletableFuture<ScanResult> mlKitFuture,
            CompletableFuture<ScanResult> zxingFuture,
            ImageProxy imageProxy,
            SharedImageResources resources,
            ScheduledFuture<?> timeoutFuture) {

        CompletableFuture.allOf(mlKitFuture, zxingFuture)
                .exceptionally(ex -> {
                    // CancellationException是正常的取消操作，不应视为错误
                    if (!(ex.getCause() instanceof CancellationException)) {
                        Log.e(TAG, "解码任务异常", ex);
                    }
                    return null;
                })
                .thenRun(() -> {
                    // 只有在没有找到结果的情况下清理资源
                    if (!resultFound.get()) {
                        // 取消超时任务
                        if (timeoutFuture != null && !timeoutFuture.isDone()) {
                            timeoutFuture.cancel(false);
                        }

                        cleanupResources(imageProxy, resources);
                    }
                });
    }

    private void cancelTasks(CompletableFuture<?> future, ScheduledFuture<?> timeoutFuture) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
        }
    }

    private void cleanupResources(ImageProxy imageProxy, SharedImageResources resources) {
        try {
            // 回收位图
            if (resources != null) {
                if (resources.originalBitmap != null && !resources.originalBitmap.isRecycled()) {
                    resources.originalBitmap.recycle();
                }
                if (resources.processedBitmap != null && !resources.processedBitmap.isRecycled()) {
                    resources.processedBitmap.recycle();
                }
            }

            // 关闭ImageProxy
            cleanupImageProxy(imageProxy);

        } catch (Exception e) {
            Log.e(TAG, "清理资源时出错", e);
        }
    }

    private void cleanupImageProxy(ImageProxy imageProxy) {
        try {
            // 确保ImageProxy尚未关闭
            if (isImageProxyClosed(imageProxy)) {
                Log.d(TAG, "ImageProxy已经关闭，跳过关闭操作");
            } else {
                imageProxy.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭ImageProxy时出错", e);
        } finally {
            // 从活动集合中移除
            activeImages.remove(imageProxy);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private boolean isImageProxyClosed(ImageProxy imageProxy) {
        if (imageProxy == null) return true;

        try {
            return imageProxy.getImage() == null;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private void saveTimeoutDebugImages(SharedImageResources resources) {
        try {
            if (resources.originalBitmap != null && !resources.originalBitmap.isRecycled()) {
                saveDebugImage(resources.originalBitmap, "timeout_original");
            }
            if (resources.processedBitmap != null && !resources.processedBitmap.isRecycled()) {
                saveDebugImage(resources.processedBitmap, "timeout_processed");
            }
        } catch (Exception e) {
            Log.e(TAG, "保存超时调试图像失败", e);
        }
    }


    // 优化后的图像转换方法，更加健壮
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) {
            Log.e(TAG, "ImageProxy中的Image为null");
            return null;
        }

        try {
            return convertYuv420ToBitmap(image, imageProxy.getImageInfo().getRotationDegrees());
        } catch (Exception e) {
            Log.e(TAG, "将ImageProxy转换为Bitmap失败", e);
            return null;
        }
    }

    // 保留您原有的convertYuv420ToBitmap方法，这里不再重复
    private Bitmap convertYuv420ToBitmap(Image image, int rotationDegrees) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            Log.e(TAG, "不支持的图像格式: " + image.getFormat());
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 获取YUV平面
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // 计算每个平面的行跨度
        int yStride = planes[0].getRowStride();
        int uvStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // 准备NV21数据，这是YuvImage所需的格式
        byte[] nv21 = new byte[width * height * 3 / 2];
        int ySize = width * height;

        // 复制Y平面
        if (yStride == width) {
            // 没有行填充，可以一次性复制
            yBuffer.get(nv21, 0, ySize);
        } else {
            // 有行填充，需要按行复制
            int yBufferPos = 0;
            for (int row = 0; row < height; row++) {
                yBuffer.position(yBufferPos);
                yBuffer.get(nv21, row * width, width);
                yBufferPos += yStride;
            }
        }

        // 交错复制U和V平面
        int uvPos = 0;
        int uvBufferPos = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                // 复制V值
                vBuffer.position(uvBufferPos);
                nv21[ySize + uvPos] = vBuffer.get();
                uvPos++;

                // 复制U值
                uBuffer.position(uvBufferPos);
                nv21[ySize + uvPos] = uBuffer.get();
                uvPos++;

                uvBufferPos += uvPixelStride;
            }
            if (uvPixelStride == 2 && width % 2 == 1) {
                // 调整UV缓冲区位置
                uvBufferPos += 1;
            }
            uvBufferPos += (uvStride - width);
        }

        // 使用YuvImage将NV21转换为JPEG
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);

        // 从JPEG创建Bitmap
        byte[] jpegData = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        // 应用旋转
        if (rotationDegrees != 0 && bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            try {
                Bitmap rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        matrix, true);

                // 回收原始位图
                if (bitmap != rotatedBitmap) {
                    bitmap.recycle();
                }

                bitmap = rotatedBitmap;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "旋转位图时内存不足", e);
                // 保持原始位图不变
            }
        }

        try {
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭输出流失败", e);
        }

        return bitmap;
    }


    private void saveDebugImage(Bitmap bitmap, String prefix) {
        if (bitmap == null || bitmap.isRecycled()) return;

        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File debugDir = new File(picturesDir, "ScannerDebug");
            if (!debugDir.exists() && !debugDir.mkdirs()) {
                Log.e(TAG, "无法创建调试目录");
                return;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File imageFile = new File(debugDir, prefix + "_" + timestamp + ".jpg");

            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                Log.d(TAG, "已保存调试图像: " + imageFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "保存调试图像失败", e);
        }
    }

    private void handleScanResult(ScanResult result) {
        if (result.isSuccess() && listener != null) {
            isScanning = false;
            listener.onScanResult(result);
        }
    }

    public void resumeScanning() {
        isScanning = true;
        resultFound.set(false); // 重置结果状态
    }

    public void release() {
        mlKitDecoder.release();
        zXingDecoder.release();

        // 清理所有活动图像
        synchronized (activeImages) {
            for (ImageProxy imageProxy : activeImages) {
                try {
                    if (!isImageProxyClosed(imageProxy)) {
                        imageProxy.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "释放时关闭ImageProxy失败", e);
                }
            }
            activeImages.clear();
        }

        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
            try {
                // 等待任务完成
                if (!scheduledExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
            }
        }
    }
}

