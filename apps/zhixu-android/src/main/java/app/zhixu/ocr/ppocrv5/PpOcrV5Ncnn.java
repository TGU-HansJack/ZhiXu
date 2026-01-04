package app.zhixu.ocr.ppocrv5;

import android.graphics.Bitmap;
import androidx.annotation.Keep;

@Keep
public final class PpOcrV5Ncnn {
    static {
        System.loadLibrary("ppocrv5ncnn");
    }

    @Keep
    public native boolean nativeLoadModel(
            String detParamPath,
            String detBinPath,
            String recParamPath,
            String recBinPath,
            boolean useFp16,
            boolean useGpu
    );

    @Keep
    public native NativeOcrBlock[] nativeRecognizeBitmap(Bitmap bitmap);

    @Keep
    public native void nativeRelease();
}
