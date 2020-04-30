package com.app.zxinglib.decode;

import android.os.Handler;
import android.os.Looper;

import com.app.zxinglib.activity.CaptureActivity;

import java.util.concurrent.CountDownLatch;

public class DecodeThread extends Thread {

    public static final String BARCODE_BITMAP = "barcode_bitmap";
    public static final String DECODE_MODE = "DECODE_MODE";
    public static final String DECODE_TIME = "DECODE_TIME";
    private final CaptureActivity activity;
    private final CountDownLatch handlerInitLatch;
    private Handler handler;

    public DecodeThread(CaptureActivity activity) {

        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new DecodeHandler(activity);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
