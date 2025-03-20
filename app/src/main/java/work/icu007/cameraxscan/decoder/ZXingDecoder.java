package work.icu007.cameraxscan.decoder;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:03
 * E-mail: charlie.liao@icu007.work
 */

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ZXingDecoder {
    private static final String TAG = "ZXingDecoder";
    private static final MultiFormatReader multiFormatReader = new MultiFormatReader();

    public ZXingDecoder() {
        // 设置解码提示
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        // 只添加需要的条码格式
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        formats.add(BarcodeFormat.CODE_128);
        formats.add(BarcodeFormat.CODE_39);
        formats.add(BarcodeFormat.EAN_13);
        formats.add(BarcodeFormat.EAN_8);
        formats.add(BarcodeFormat.UPC_A);
        formats.add(BarcodeFormat.UPC_E);
        // 可以根据需要添加其他格式

        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        multiFormatReader.setHints(hints);
    }

    public static String decode(Bitmap bitmap) {
        try {
            // 将Android的Bitmap转换为ZXing可以处理的BinaryBitmap
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

            // 创建RGBLuminanceSource
            RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels);

            // 创建BinaryBitmap
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            // 执行解码
            Result result = multiFormatReader.decodeWithState(binaryBitmap);
            if (result != null) {
                return result.getText();
            }
            return null;
        } catch (NotFoundException e) {
            // 找不到条码的异常单独处理，这是常见的非错误情况
            Log.d(TAG, "ZXing未找到条码");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "ZXing解码失败: ", e);
            return null;
        } finally {
            multiFormatReader.reset();
        }
    }

    public static String decode(ImageProxy imageProxy) {
        try {
            if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
                Log.e(TAG, "不支持的图像格式: " + imageProxy.getFormat());
                return null;
            }

            byte[] yuvData = yuv420ToNv21(imageProxy);
            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            // 创建ZXing需要的亮度源
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    yuvData, width, height, 0, 0, width, height, false);

            // 创建二进制位图
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            // 执行解码
            Result result = multiFormatReader.decodeWithState(bitmap);
            if (result != null) {
                return result.getText();
            }
            return null;
        } catch (NotFoundException e) {
            // 找不到条码的异常单独处理，这是常见的非错误情况
            Log.d(TAG, "ZXing未找到条码");
            return null;
        }  catch (Exception e) {
            Log.e(TAG, "ZXing解码失败: ", e);
            return null;
        } finally {
            multiFormatReader.reset();
        }
    }

    // 将YUV_420_888格式转换为NV21格式，ZXing可以处理NV21
    @OptIn(markerClass = ExperimentalGetImage.class)
    private static byte[] yuv420ToNv21(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        byte[] data = new byte[image.getWidth() * image.getHeight() * 3 / 2];
        Image.Plane[] planes = image.getPlanes();
        int bufferIndex = 0;

        // Y平面
        ByteBuffer yBuffer = planes[0].getBuffer();
        int ySize = yBuffer.remaining();
        yBuffer.get(data, 0, ySize);

        // U和V平面交错
        ByteBuffer uvBuffer = planes[2].getBuffer();
        int uvSize = uvBuffer.remaining();
        uvBuffer.get(data, ySize, uvSize);

        return data;
    }

    public void release() {

    }
}
