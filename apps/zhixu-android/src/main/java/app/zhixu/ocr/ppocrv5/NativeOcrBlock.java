package app.zhixu.ocr.ppocrv5;

import androidx.annotation.Keep;

@Keep
public final class NativeOcrBlock {
    public final String text;
    public final float score;
    // 4 points: x0,y0,x1,y1,x2,y2,x3,y3
    public final float[] points;

    @Keep
    public NativeOcrBlock(String text, float score, float[] points) {
        this.text = text;
        this.score = score;
        this.points = points;
    }
}
