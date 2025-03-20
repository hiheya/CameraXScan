package work.icu007.cameraxscan.processor;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:04
 * E-mail: charlie.liao@icu007.work
 */


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";
    private boolean isOpenCVInitialized = false;

    public ImageProcessor() {
        // 初始化OpenCV
        if (!isOpenCVInitialized) {
            isOpenCVInitialized = OpenCVLoader.initDebug();
            if (!isOpenCVInitialized) {
                Log.e(TAG, "OpenCV初始化失败");
            } else {
                Log.i(TAG, "OpenCV初始化成功");
            }
        }
    }

    public Bitmap process(ImageProxy imageProxy) {
        // 如果OpenCV没有初始化，则跳过处理
        if (!isOpenCVInitialized) {
            Log.d(TAG, "OpenCV未初始化，跳过图像处理");
            return null;
        }

        Log.d(TAG, "process: OpenCV 处理图像");

        Bitmap bitmap = null;
        Mat srcMat = null;
        Mat dstMat = null;

        try {
            // 将ImageProxy转换为Bitmap
            bitmap = imageToBitmap(imageProxy);
            if (bitmap == null) {
                Log.e(TAG, "无法从ImageProxy创建位图");
                return null;
            }

            // 将Bitmap转换为Mat
            srcMat = new Mat();
            Utils.bitmapToMat(bitmap, srcMat);

            if (srcMat.empty()) {
                Log.e(TAG, "转换为Mat失败或Mat为空");
                return bitmap; // 返回原始位图，不做处理
            }

            // 创建目标Mat，避免修改源Mat
            dstMat = new Mat();

            // 执行图像增强处理
            enhanceImage(srcMat, dstMat);

            if (dstMat.empty()) {
                Log.e(TAG, "增强处理后的Mat为空");
                return bitmap; // 返回原始位图，不做处理
            }

            // 创建新的Bitmap来存储处理结果
            Bitmap processedBitmap = Bitmap.createBitmap(
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);

            // 将处理后的Mat转回Bitmap
            Utils.matToBitmap(dstMat, processedBitmap);

            // 回收原始bitmap
            bitmap.recycle();

            // 返回处理后的bitmap
            return processedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "图像处理发生异常: ", e);
            // 异常情况下返回原始bitmap，如果它还有效的话
            return bitmap;
        } finally {
            // 确保释放所有OpenCV资源，无论成功与否
            try {
                if (srcMat != null) {
                    srcMat.release();
                }
                if (dstMat != null ) {
                    dstMat.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "释放Mat资源时出错", e);
            }
        }
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageToBitmap(ImageProxy imageProxy) {
        Image image = null;
        try {
            image = imageProxy.getImage();
            if (image == null) return null;

            // 直接使用ImageProxy提供的格式转换方法
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0及以上可以使用ImageReader转换
                return imageToBitmapApi23(image);
            } else {
                // 下面是改进的YUV处理方式
                return yuv420ToBitmap(image);
            }
        } catch (Exception e) {
            Log.e(TAG, "图像转换失败: ", e);
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /**
     * 仅从图像提取Y通道数据，创建灰度位图
     * 这种方法适合用于条码/二维码识别，因为大多数识别算法只需要亮度信息
     *
     * @param image 源图像
     * @return 灰度位图，仅包含Y通道数据
     */
    private Bitmap extractYChannelAsBitmap(Image image) {
        if (image == null) {
            Log.e(TAG, "Image为null，无法处理");
            return null;
        }

        try {
            // 获取图像尺寸
            int width = image.getWidth();
            int height = image.getHeight();

            // 获取Y通道平面
            Image.Plane yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int yRowStride = yPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();

            // 创建灰度位图 (ALPHA_8格式仅存储透明度通道，但我们用它来存储灰度值)
            Bitmap grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);

            // 如果Y平面是连续的且没有填充，可以一次性处理
            if (yPixelStride == 1 && yRowStride == width) {
                // 创建字节数组接收数据
                byte[] data = new byte[yBuffer.remaining()];
                yBuffer.get(data);

                // 创建单字节灰度位图
                grayBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data));
            } else {
                // Y平面有填充，需要逐行复制
                int[] pixels = new int[width * height];

                // 以一种更高效的方式复制Y通道数据
                int bufferPosition, pixelIndex = 0;
                for (int row = 0; row < height; row++) {
                    bufferPosition = row * yRowStride;
                    for (int col = 0; col < width; col++) {
                        // 获取Y值 (0-255)
                        int y = yBuffer.get(bufferPosition) & 0xFF;

                        // 由于ALPHA_8只有透明度通道，我们将Y值存储为透明度
                        pixels[pixelIndex++] = (y << 24) | 0xFFFFFF; // ARGB格式

                        // 移动到下一像素
                        bufferPosition += yPixelStride;
                    }
                }

                // 将数组写入位图
                grayBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            }

            return grayBitmap;
        } catch (Exception e) {
            Log.e(TAG, "提取Y通道失败", e);
            return null;
        }
    }


    // Android 6.0+的方法
    private Bitmap imageToBitmapApi23(Image image) {
        ByteBuffer buffer = null;
        Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();

        // 为RGB图像分配空间
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding/pixelStride, height, Bitmap.Config.ARGB_8888);

        // 从Y、U、V平面构建NV21数据
        int offset = 0;
        int y, u, v;
        int color;

        for (int i = 0; i < height; ++i) {
            for (int j = 0; j < width; ++j) {
                y = planes[0].getBuffer().get(i * rowStride + j * pixelStride) & 0xff;

                // 由于YUV格式的色度下采样，U/V需要计算正确的索引
                int uvIndex = (i / 2) * (rowStride / 2) + (j / 2) * pixelStride;

                u = planes[1].getBuffer().get(uvIndex) & 0xff;
                v = planes[2].getBuffer().get(uvIndex) & 0xff;

                // YUV转RGB
                y = y < 16 ? 16 : y;
                int y1192 = 1192 * (y - 16);
                int r = (y1192 + 1634 * (v - 128));
                int g = (y1192 - 833 * (v - 128) - 400 * (u - 128));
                int b = (y1192 + 2066 * (u - 128));

                r = r > 262143 ? 262143 : (r < 0 ? 0 : r);
                g = g > 262143 ? 262143 : (g < 0 ? 0 : g);
                b = b > 262143 ? 262143 : (b < 0 ? 0 : b);

                color = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                bitmap.setPixel(j, i, color);
            }
        }

        return bitmap;
    }

    // 通用方法
    private Bitmap yuv420ToBitmap(Image image) {
        if (image == null) return null;

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();

        // 使用更保守的内存分配，确保数据空间足够
        int bufferSize = imageWidth * imageHeight * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        byte[] data = new byte[bufferSize];
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();

        int offset = 0;
        for (int i = 0; i < imageHeight; ++i) {
            for (int j = 0; j < imageWidth; ++j) {
                byte pixel = planes[0].getBuffer().get(i * rowStride + j * pixelStride);
                data[offset++] = pixel;
            }
        }

        // 色度平面
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // UV平面的行跨度和像素跨度可能不同
        pixelStride = planes[1].getPixelStride();
        rowStride = planes[1].getRowStride();
        int uvWidth = imageWidth / 2;
        int uvHeight = imageHeight / 2;

        for (int i = 0; i < uvHeight; i++) {
            for (int j = 0; j < uvWidth; j++) {
                int uvIndex = i * rowStride + j * pixelStride;
                data[offset++] = vBuffer.get(uvIndex);  // NV21要求先V后U
                data[offset++] = uBuffer.get(uvIndex);
            }
        }

        try {
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, imageWidth, imageHeight, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, imageWidth, imageHeight), 90, out);
            byte[] jpegBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "YUV转JPEG失败", e);
            return null;
        }
    }


    /*private void enhanceImage(Mat src, Mat dst) {
        try {
            // 灰度转换
            Mat grayMat = new Mat();
            Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY);

            // 去噪处理
            Imgproc.GaussianBlur(grayMat, grayMat, new Size(5, 5), 0);

            // 自适应阈值处理，改善不同光照条件
            Mat binaryMat = new Mat();
            Imgproc.adaptiveThreshold(grayMat, binaryMat, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 11, 2);

            // 形态学操作
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.morphologyEx(binaryMat, binaryMat, Imgproc.MORPH_CLOSE, kernel);

            // 寻找条码区域（ROI）
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(binaryMat, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 如果找到条码区域，可以进一步处理
            if (!contours.isEmpty()) {
                // 这里只是示例，实际上可能需要更复杂的筛选和处理逻辑
                MatOfPoint2f approxCurve = new MatOfPoint2f();

                for (MatOfPoint contour : contours) {
                    double contourArea = Imgproc.contourArea(contour);
                    if (contourArea > 1000) {  // 忽略小区域
                        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                        Imgproc.approxPolyDP(contour2f, approxCurve,
                                0.02 * Imgproc.arcLength(contour2f, true), true);

                        // 绘制找到的轮廓
                        Imgproc.drawContours(src, contours, -1, new Scalar(0, 255, 0), 2);
                    }
                }
            }

            // 将结果复制到输出Mat
            grayMat.copyTo(dst);

            // 释放Mat资源
            grayMat.release();
            binaryMat.release();
        } catch (Exception e) {
            Log.e(TAG, "图像增强失败: ", e);
        }
    }*/

    /**
     * 增强图像中的条码区域，提高条码识别率
     * @param src 源图像
     * @param dst 目标图像
     */
    private void enhanceImage(Mat src, Mat dst) {
        Mat grayMat = null;
        Mat blurredMat = null;
        Mat gradX = null;
        Mat gradY = null;
        Mat gradient = null;
        Mat binaryMat = null;
        Mat dilatedMat = null;
        Mat kernel = null;
        Mat hierarchy = null;
        List<MatOfPoint> contours = new ArrayList<>();

        try {
            // 复制源图像，避免修改原始数据
            src.copyTo(dst);

            // 1. 预处理 - 转为灰度并降噪
            grayMat = new Mat();
            Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY);

            blurredMat = new Mat();
            Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

            // 2. 边缘增强 - 使用Sobel算子增强条码边缘
            gradX = new Mat();
            gradY = new Mat();
            gradient = new Mat();

            // 计算X方向梯度 (对垂直条码线非常有效)
            Imgproc.Sobel(blurredMat, gradX, CvType.CV_32F, 1, 0, -1);
            // 计算Y方向梯度 (对水平条码线非常有效)
            Imgproc.Sobel(blurredMat, gradY, CvType.CV_32F, 0, 1, -1);

            // 合并梯度
            Core.subtract(gradX, gradY, gradient);
            Core.convertScaleAbs(gradient, gradient);

            // 3. 形态学处理 - 连接条码线条
            // 使用矩形结构元素，宽度大于高度有利于连接水平条码的竖线
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(21, 7));
            dilatedMat = new Mat();
            Imgproc.morphologyEx(gradient, dilatedMat, Imgproc.MORPH_CLOSE, kernel);

            // 4. 二值化处理
            binaryMat = new Mat();
            Imgproc.threshold(dilatedMat, binaryMat, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            // 进一步去除小噪点
            Mat smallKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.morphologyEx(binaryMat, binaryMat, Imgproc.MORPH_OPEN, smallKernel);
            smallKernel.release();

            // 5. 寻找条码区域
            contours.clear();
            hierarchy = new Mat();
            Imgproc.findContours(binaryMat.clone(), contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 6. 处理找到的轮廓
            if (!contours.isEmpty()) {
                // 找出最大的轮廓(很可能是条码区域)
                MatOfPoint largestContour = null;
                double maxArea = 0;

                for (MatOfPoint contour : contours) {
                    double contourArea = Imgproc.contourArea(contour);
                    if (contourArea > maxArea && contourArea > 1000) {
                        maxArea = contourArea;
                        largestContour = contour;
                    }
                }

                if (largestContour != null) {
                    // 7. 提取并处理条码区域
                    org.opencv.core.Rect boundRect = Imgproc.boundingRect(largestContour);

                    // 扩大边界确保完整包含条码
                    int padding = 20;
                    boundRect.x = Math.max(0, boundRect.x - padding);
                    boundRect.y = Math.max(0, boundRect.y - padding);
                    boundRect.width = Math.min(src.width() - boundRect.x, boundRect.width + 2*padding);
                    boundRect.height = Math.min(src.height() - boundRect.y, boundRect.height + 2*padding);

                    // 提取条码区域
                    Mat barcodeRegion = new Mat(src, boundRect);
                    Mat enhancedBarcode = new Mat();

                    // 8. 条码特定增强
                    Imgproc.cvtColor(barcodeRegion, enhancedBarcode, Imgproc.COLOR_BGR2GRAY);

                    // 根据条码区域的宽高比判断可能是一维码还是二维码
                    boolean isLikelyBarcode = (double)boundRect.width / boundRect.height > 1.5;

                    if (isLikelyBarcode) {
                        // 一维条码增强 - 增强垂直线条
                        Mat barcodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 5));
                        Imgproc.morphologyEx(enhancedBarcode, enhancedBarcode, Imgproc.MORPH_OPEN, barcodeKernel);
                        barcodeKernel.release();
                    } else {
                        // 二维条码增强 - 保持精细结构
                        Imgproc.GaussianBlur(enhancedBarcode, enhancedBarcode, new Size(3, 3), 0);
                    }

                    // 自适应阈值处理提高对比度
                    Imgproc.adaptiveThreshold(enhancedBarcode, enhancedBarcode, 255,
                            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                            Imgproc.THRESH_BINARY, 15, 5);

                    // 9. 将增强后的条码区域放回目标图像
                    enhancedBarcode.copyTo(new Mat(dst, boundRect));

                    // 可选：在目标图像上绘制找到的条码区域边界
                    Imgproc.rectangle(dst, boundRect, new Scalar(0, 255, 0), 2);

                    enhancedBarcode.release();
                    barcodeRegion.release();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "图像增强失败: ", e);
            // 确保在异常情况下也能返回有效结果
            if (dst.empty() && !src.empty()) {
                src.copyTo(dst);
            }
        } finally {
            // 释放所有Mat资源
            releaseMatIfNotNull(grayMat, blurredMat, gradX, gradY, gradient,
                    binaryMat, dilatedMat, kernel, hierarchy);

            // 清理轮廓列表
            for (MatOfPoint contour : contours) {
                contour.release();
            }
            contours.clear();
        }
    }

    /**
     * 释放多个Mat资源
     */
    private void releaseMatIfNotNull(Mat... mats) {
        for (Mat mat : mats) {
            if (mat != null) mat.release();
        }
    }


}

