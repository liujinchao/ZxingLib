/*
 * Copyright (c) 2015 [1076559197@qq.com | tchen0707@gmail.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liujc.zxinglib.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Author:  Tau.Chen
 * Email:   1076559197@qq.com | tauchen1990@gmail.com
 * Date:    15/7/23
 * Description:
 */
public class DecodeUtils {

    public static final int DECODE_MODE_ZBAR = 10001;
    public static final int DECODE_MODE_ZXING = 10002;

    public static final int DECODE_DATA_MODE_ALL = 10003;
    public static final int DECODE_DATA_MODE_QRCODE = 10004;
    public static final int DECODE_DATA_MODE_BARCODE = 10005;

    private static int mDataMode;

    public DecodeUtils(int dataMode) {
        mDataMode = (dataMode != 0) ? dataMode : DECODE_DATA_MODE_ALL;
    }
    public Result decodeWithZxing(byte[] data, int width, int height, Rect crop) {
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(changeZXingDecodeDataMode());

        Result rawResult = null;
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height,
                crop.left, crop.top, crop.width(), crop.height(), false);

        if (source != null) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                rawResult = multiFormatReader.decodeWithState(bitmap);
            } catch (ReaderException re) {
                // continue
            } finally {
                multiFormatReader.reset();
            }
        }

        return rawResult != null ? rawResult : null;
    }

    public Result decodeWithZxing(Bitmap bitmap) {
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(changeZXingDecodeDataMode());

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        Result rawResult = null;
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        if (source != null) {
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                rawResult = multiFormatReader.decodeWithState(binaryBitmap);
            } catch (ReaderException re) {
                // continue
            } finally {
                multiFormatReader.reset();
            }
        }

        return rawResult;
    }

    private Map<DecodeHintType, Object> changeZXingDecodeDataMode() {
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        Log.d("TAG","mDataMode:"+mDataMode);
        switch (mDataMode) {
            case DECODE_DATA_MODE_ALL:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            case DECODE_DATA_MODE_QRCODE:
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            case DECODE_DATA_MODE_BARCODE:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                break;
        }
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        return hints;
    }

    public static int getDataMode() {
        return mDataMode;
    }

    public static void setDataMode(int dataMode) {
        mDataMode = dataMode;
    }
}
