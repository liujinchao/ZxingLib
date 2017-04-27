package com.liujc.zxinglib.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.liujc.zxinglib.R;
import com.liujc.zxinglib.activity.CaptureActivity;

import java.io.ByteArrayOutputStream;

public class DecodeHandler extends Handler {

    private final CaptureActivity activity;
    private boolean running = true;
    private DecodeUtils mDecodeUtils = null;

    public DecodeHandler(CaptureActivity activity) {
        this.activity = activity;
        mDecodeUtils = new DecodeUtils(DecodeUtils.DECODE_DATA_MODE_ALL);
    }

    private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
        int[] pixels = source.renderThumbnail();
        int width = source.getThumbnailWidth();
        int height = source.getThumbnailHeight();
        Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
        bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
    }

    @Override
    public void handleMessage(Message message) {
        if (!running) {
            return;
        }
        if (message.what == R.id.decode) {
            decode((byte[]) message.obj, message.arg1, message.arg2);

        } else if (message.what == R.id.quit) {
            running = false;
            Looper.myLooper().quit();

        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it
     * took. For efficiency, reuse the same reader objects from one decode to
     * the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
//        // 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
//        byte[] rotatedData = new byte[data.length];
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++)
//                rotatedData[x * height + height - y - 1] = data[x + y * width];
//        }
//
//        // 宽高也要调整
//        int tmp = width;
//        width = height;
//        height = tmp;
//
//        Result rawResult = null;
//        PlanarYUVLuminanceSource source = buildLuminanceSource(rotatedData, width, height);
//        if (source != null) {
//            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
//            try {
//                rawResult = multiFormatReader.decodeWithState(bitmap);
//            } catch (ReaderException re) {
//                // continue
//            } finally {
//                multiFormatReader.reset();
//            }
//        }
//
//        Handler handler = activity.getHandler();
//        if (rawResult != null) {
//            // Don't log the barcode contents for security.
//            if (handler != null) {
//                Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
//                Bundle bundle = new Bundle();
//                bundleThumbnail(source, bundle);
//                message.setData(bundle);
//                message.sendToTarget();
//            }
//        } else {
//            if (handler != null) {
//                Message message = Message.obtain(handler, R.id.decode_failed);
//                message.sendToTarget();
//            }
//        }

        long start = System.currentTimeMillis();

        Camera.Size size = activity.getCameraManager().getPreviewSize();
        byte[] rotatedData = new byte[data.length];
        for (int y = 0; y < size.height; y++) {
            for (int x = 0; x < size.width; x++)
                rotatedData[x * size.height + size.height - y - 1] = data[x + y * size.width];
        }

        int tmp = size.width;
        size.width = size.height;
        size.height = tmp;

        String resultStr = null;
        Rect cropRect = activity.getCropRect();
        if (null == cropRect) {
            activity.initCrop();
        }
        cropRect = activity.getCropRect();

        mDecodeUtils.setDataMode(activity.getDataMode());

        Result rawResult = mDecodeUtils.decodeWithZxing(rotatedData, size.width, size.height, cropRect);


        Handler handler = activity.getHandler();
        if (rawResult!=null) {
            long end = System.currentTimeMillis();
            if (handler != null) {
                Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
                Bundle bundle = new Bundle();
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(rotatedData, size.width, size.height,
                        cropRect.left, cropRect.top,
                        cropRect.width(), cropRect.height(), false);

                bundle.putString(DecodeThread.DECODE_TIME, (end - start) + "ms");

                bundleThumbnail(source, bundle);
                message.setData(bundle);
                message.sendToTarget();
            }
        } else {
            if (handler != null) {
                Message message = Message.obtain(handler, R.id.decode_failed);
                message.sendToTarget();
            }
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = activity.getCropRect();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect
                .height(), false);
    }

}
