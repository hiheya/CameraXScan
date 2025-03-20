package work.icu007.cameraxscan;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import work.icu007.cameraxscan.utils.CameraManager;
import work.icu007.cameraxscan.utils.ScanResult;
import work.icu007.cameraxscan.utils.ScanResultListener;


public class ScannerActivity extends AppCompatActivity implements ScanResultListener {
    private CameraManager cameraManager;
    private PreviewView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        // 在ScannerActivity.java的onCreate方法中添加以下代码
        ImageButton flashButton = findViewById(R.id.flashButton);
        flashButton.setOnClickListener(v -> {
            if (cameraManager != null) {
                cameraManager.toggleFlash();
                // 切换闪光灯图标
                boolean isFlashOn = cameraManager.isFlashOn();
                flashButton.setImageResource(isFlashOn ?
                        R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            }
        });


        // 初始化相机管理器
        cameraManager = new CameraManager(this, previewView, this);
        cameraManager.startCamera();
    }


    @Override
    protected void onDestroy() {
        if (cameraManager != null) {
            cameraManager.shutdown();
        }
        super.onDestroy();
    }

    public void onScanResult(ScanResult result) {
        runOnUiThread(() -> {
            if (result.isSuccess()) {
                Toast.makeText(this, "扫描结果: " + result.getText() + " (来自: " + result.getDecoderType() + ")",
                        Toast.LENGTH_LONG).show();

                Intent resultIntent = new Intent();
                resultIntent.putExtra("SCAN_RESULT", result.getText());
                resultIntent.putExtra("SCAN_SOURCE", result.getDecoderType());
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }
}
