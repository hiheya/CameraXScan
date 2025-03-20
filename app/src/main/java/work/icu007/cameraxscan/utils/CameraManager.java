package work.icu007.cameraxscan.utils;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:01
 * E-mail: charlie.liao@icu007.work
 */

import android.content.Context;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import work.icu007.cameraxscan.decoder.DecoderManager;
import work.icu007.cameraxscan.processor.ImageProcessor;

public class CameraManager {
    private static final String TAG = "CameraManager";

    private final Context context;
    private final PreviewView previewView;

    private Camera camera;
    private boolean flashOn = false;
    private final ScanResultListener resultListener;
    private final ExecutorService cameraExecutor;
    private final DecoderManager decoderManager;
    private final ImageProcessor imageProcessor;

    private ProcessCameraProvider cameraProvider;

    public CameraManager(Context context, PreviewView previewView, ScanResultListener resultListener) {
        this.context = context;
        this.previewView = previewView;
        this.resultListener = resultListener;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        this.imageProcessor = new ImageProcessor();
        this.decoderManager = new DecoderManager(resultListener);
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "相机初始化失败: ", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            throw new IllegalStateException("相机初始化失败");
        }

        // 配置相机预览
        Preview.Builder previewBuilder = new Preview.Builder();

        // 使用Camera2Interop设置自动对焦
        Camera2Interop.Extender<Preview> previewExtender = new Camera2Interop.Extender<>(previewBuilder);
        previewExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        previewExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);

        Preview preview = previewBuilder.build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 配置图像分析
        ImageAnalysis.Builder imageAnalysisBuilder = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);

        // 同样为图像分析设置自动对焦
        Camera2Interop.Extender<ImageAnalysis> imageAnalysisExtender =
                new Camera2Interop.Extender<>(imageAnalysisBuilder);
        imageAnalysisExtender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        ImageAnalysis imageAnalysis = imageAnalysisBuilder.build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // 后置摄像头
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            cameraProvider.unbindAll();

            // 绑定用例
            camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) context,
                    cameraSelector,
                    preview,
                    imageAnalysis);

            Log.d(TAG, "相机已初始化，已设置自动对焦");

        } catch (Exception e) {
            Log.e(TAG, "用例绑定失败", e);
        }
    }

    private void analyzeImage(ImageProxy imageProxy) {
        // 使用OpenCV处理图像
        if (imageProxy != null) {
            //imageProcessor.process(imageProxy);
        }
        // 将处理后的图像传给解码管理器进行并行解码
        decoderManager.decodeAsync(imageProxy);
    }

    public void shutdown() {
        cameraExecutor.shutdown();
        decoderManager.release();
    }

    public void toggleFlash() {
        if (camera != null) {
            try {
                camera.getCameraControl().enableTorch(!flashOn);
                flashOn = !flashOn;
            } catch (Exception e) {
                Log.e(TAG, "闪光灯控制失败: ", e);
            }
        }
    }

    public boolean isFlashOn() {
        return flashOn;
    }

    /**
     * 手动触发一次对焦
     * 在某些场景下可能需要手动触发对焦
     */
    public void triggerFocus() {
        if (camera == null) return;

        try {
            // 在预览视图中心创建测光点
            MeteringPointFactory factory = previewView.getMeteringPointFactory();
            float centerX = previewView.getWidth() / 2f;
            float centerY = previewView.getHeight() / 2f;
            MeteringPoint point = factory.createPoint(centerX, centerY);

            // 创建对焦操作
            FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                    .disableAutoCancel() // 不自动取消
                    .build();

            // 执行对焦
            camera.getCameraControl().startFocusAndMetering(action);
            Log.d(TAG, "已手动触发对焦");
        } catch (Exception e) {
            Log.e(TAG, "触发对焦失败: ", e);
        }
    }
}


