package work.icu007.cameraxscan.decoder;

/*
 * Author: Charlie Liao
 * Time: 2025/3/19-12:02
 * E-mail: charlie.liao@icu007.work
 */

import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Log;

import androidx.camera.core.ImageProxy;

/*import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;*/

import java.nio.ByteBuffer;

public class ZBarDecoder {
 private static final String TAG = "ZBarDecoder";
 /*private final ImageScanner scanner;

 public ZBarDecoder() {
  // 初始化ZBar扫描器
  scanner = new ImageScanner();
  scanner.setConfig(0, Config.X_DENSITY, 3);
  scanner.setConfig(0, Config.Y_DENSITY, 3);
  scanner.setConfig(Symbol.NONE, Config.ENABLE, 1);
 }

 public String decode(ImageProxy imageProxy) {
  try {
   if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
    Log.e(TAG, "不支持的图像格式: " + imageProxy.getFormat());
    return null;
   }

   Image.Plane[] planes = imageProxy.getImage().getPlanes();
   ByteBuffer buffer = planes[0].getBuffer();
   byte[] data = new byte[buffer.remaining()];
   buffer.get(data);

   int width = imageProxy.getWidth();
   int height = imageProxy.getHeight();

   // 创建ZBar图像对象
   Image zbarImage = new Image(width, height, "Y800");
   zbarImage.setData(data);

   // 执行扫描
   int result = scanner.scanImage(zbarImage);

   if (result > 0) {
    // 获取扫描结果
    SymbolSet symbols = scanner.getResults();
    for (Symbol symbol : symbols) {
     // 返回第一个找到的结果
     return symbol.getData();
    }
   }
   return null;
  } catch (Exception e) {
   Log.e(TAG, "ZBar解码失败: ", e);
   return null;
  }
 }*/
}

