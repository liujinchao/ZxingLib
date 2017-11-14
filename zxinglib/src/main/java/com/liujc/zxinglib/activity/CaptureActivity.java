package com.liujc.zxinglib.activity;

import android.animation.Animator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.Result;
import com.liujc.zxinglib.R;
import com.liujc.zxinglib.camera.CameraManager;
import com.liujc.zxinglib.decode.DecodeUtils;
import com.liujc.zxinglib.utils.BeepManager;
import com.liujc.zxinglib.utils.CaptureActivityHandler;
import com.liujc.zxinglib.utils.InactivityTimer;
import com.liujc.zxinglib.utils.UriUtils;

import java.io.IOException;

/**
 * 类名称：CaptureActivity
 * 创建者：Create by liujc
 * 创建时间：Create on 2017/2/17 14:17
 * 描述：二维码扫描入口
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = CaptureActivity.class.getSimpleName();
    public static final int IMAGE_PICKER_REQUEST_CODE = 100;

    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private FrameLayout scanCropView;
    private ImageView scanLine;
    private Button back_btn;

    private Rect mCropRect = null;
    private boolean isHasSurface = false;

    private int mQrcodeCropWidth = 0;
    private int mQrcodeCropHeight = 0;
    private int mBarcodeCropWidth = 0;
    private int mBarcodeCropHeight = 0;
    Button capturePictureBtn;
    Button captureLightBtn;
    RadioGroup captureModeGroup;
    private boolean isLightOn;
    //默认是二维码扫描模式
    private int dataMode = DecodeUtils.DECODE_DATA_MODE_QRCODE;
    public static final String SCAN_RESULT = "result";
    private boolean needCallBack = false;//默认不需要回调
    public static final String SCAN_RESULT_CALLBACK = "scan_result_callback";

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_capture);
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        needCallBack = getIntent().getBooleanExtra(SCAN_RESULT_CALLBACK,false);
        initView();
        initCropViewAnimator();
        initEvent();
    }

    private void initView() {
        scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
        scanCropView = (FrameLayout) findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) findViewById(R.id.capture_scan_line);
        capturePictureBtn = (Button) findViewById(R.id.capture_picture_btn);
        captureLightBtn = (Button) findViewById(R.id.capture_light_btn);
        captureModeGroup = (RadioGroup) findViewById(R.id.capture_mode_group);
        back_btn = (Button) findViewById(R.id.back_btn);
        //扫描动画开始
        mHandler.sendEmptyMessage(1);
    }


    private void initEvent() {
        back_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        capturePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPicFromGallery();
            }
        });

        captureLightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLightOn) {
                    cameraManager.setTorch(false);
                    captureLightBtn.setSelected(false);
                } else {
                    cameraManager.setTorch(true);
                    captureLightBtn.setSelected(true);
                }
                isLightOn = !isLightOn;
            }
        });

        captureModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.capture_mode_barcode) {
                    PropertyValuesHolder qr2barWidthVH = PropertyValuesHolder.ofFloat("width",
                            1.0f, (float) mBarcodeCropWidth / mQrcodeCropWidth);
                    PropertyValuesHolder qr2barHeightVH = PropertyValuesHolder.ofFloat("height",
                            1.0f, (float) mBarcodeCropHeight / mQrcodeCropHeight);
                    ValueAnimator valueAnimator = ValueAnimator.ofPropertyValuesHolder(qr2barWidthVH, qr2barHeightVH);
                    valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            Float fractionW = (Float) animation.getAnimatedValue("width");
                            Float fractionH = (Float) animation.getAnimatedValue("height");

                            RelativeLayout.LayoutParams parentLayoutParams = (RelativeLayout.LayoutParams) scanCropView.getLayoutParams();
                            parentLayoutParams.width = (int) (mQrcodeCropWidth * fractionW);
                            parentLayoutParams.height = (int) (mQrcodeCropHeight * fractionH);
                            scanCropView.setLayoutParams(parentLayoutParams);
                        }
                    });
                    valueAnimator.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            initCrop();
                            setDataMode(DecodeUtils.DECODE_DATA_MODE_BARCODE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    valueAnimator.start();
                } else if (checkedId == R.id.capture_mode_qrcode) {
                    PropertyValuesHolder bar2qrWidthVH = PropertyValuesHolder.ofFloat("width",
                            1.0f, (float) mQrcodeCropWidth / mBarcodeCropWidth);
                    PropertyValuesHolder bar2qrHeightVH = PropertyValuesHolder.ofFloat("height",
                            1.0f, (float) mQrcodeCropHeight / mBarcodeCropHeight);
                    ValueAnimator valueAnimator = ValueAnimator.ofPropertyValuesHolder(bar2qrWidthVH, bar2qrHeightVH);
                    valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            Float fractionW = (Float) animation.getAnimatedValue("width");
                            Float fractionH = (Float) animation.getAnimatedValue("height");

                            RelativeLayout.LayoutParams parentLayoutParams = (RelativeLayout.LayoutParams) scanCropView.getLayoutParams();
                            parentLayoutParams.width = (int) (mBarcodeCropWidth * fractionW);
                            parentLayoutParams.height = (int) (mBarcodeCropHeight * fractionH);
                            scanCropView.setLayoutParams(parentLayoutParams);
                        }
                    });
                    valueAnimator.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            initCrop();
                            setDataMode(DecodeUtils.DECODE_DATA_MODE_QRCODE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    valueAnimator.start();
                }
            }
        });
    }

    private String selectImgPath = "";
    private void getPicFromGallery(){
        Intent innerIntent = new Intent(); // "android.intent.action.GET_CONTENT"
        if (Build.VERSION.SDK_INT < 19) {
            innerIntent.setAction(Intent.ACTION_GET_CONTENT);
        } else {
            innerIntent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        }
        innerIntent.setType("image/*");
        Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
        startActivityForResult(wrapperIntent, IMAGE_PICKER_REQUEST_CODE);
    }

    @SuppressWarnings("ResourceType")
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static Uri ensureUriPermission(Context context, Intent intent) {
        Uri uri = intent.getData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final int takeFlags = intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
        return uri;
    }

    private void initCropViewAnimator() {
        mQrcodeCropWidth = getResources().getDimensionPixelSize(R.dimen.qrcode_crop_width);
        mQrcodeCropHeight = getResources().getDimensionPixelSize(R.dimen.qrcode_crop_height);

        mBarcodeCropWidth = getResources().getDimensionPixelSize(R.dimen.barcode_crop_width);
        mBarcodeCropHeight = getResources().getDimensionPixelSize(R.dimen.barcode_crop_height);
    }

    private void startAnimation(final ImageView img,int id,float fromY, float toY){
        scanLine.setImageResource(id);
        scanLine.startAnimation(getMyAnimSet(fromY, toY));
    }
    private AnimationSet getMyAnimSet(float fromY, float toY) {
        Animation translateAnim = new TranslateAnimation(0, 0, fromY, toY);
        translateAnim.setDuration(3000);
        translateAnim.setInterpolator(new LinearInterpolator());

        AnimationSet animSet = new AnimationSet(false);
        animSet.setFillAfter(true);
        animSet.addAnimation(translateAnim);
        return animSet;
    }
    private Handler mHandler = new Handler() {
        public void handleMessage(Message message) {

            switch (message.what) {
                case 0:
                    startAnimation(scanLine, R.drawable.scan_line_up, 300,-300);
                    if (mHandler != null) {
                        mHandler.sendEmptyMessageDelayed(1, 3000);
                    }
                    break;
                case 1:
                    startAnimation(scanLine, R.drawable.scan_line, -300, 300);
                    if (mHandler != null) {
                        mHandler.sendEmptyMessageDelayed(0, 3000);
                    }
                    break;
            }

        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        cameraManager = new CameraManager(getApplication());
        handler = null;
        if (isHasSurface) {
            initCamera(scanPreview.getHolder());
        } else {
            scanPreview.getHolder().addCallback(this);
        }
        inactivityTimer.onResume();
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!isHasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }


    /**
     * @param rawResult  解码返回结果
     * @param bundle  数据源
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();

//        Toast.makeText(this, rawResult.getText(), Toast.LENGTH_SHORT).show();
        Intent resultIntent = new Intent();
        bundle.putInt("width", mCropRect.width());
        bundle.putInt("height", mCropRect.height());
        bundle.putString(SCAN_RESULT, rawResult.getText());
        resultIntent.putExtras(bundle);
        if (needCallBack){
            this.setResult(RESULT_OK, resultIntent);
            CaptureActivity.this.finish();
        }else {
            Intent intent = new Intent(this, ResultActivity.class);
            if (null != bundle) {
                intent.putExtras(bundle);
            }
            startActivity(intent);
        }
//        restartPreviewAfterDelay(2000);
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
//                handler = new CaptureActivityHandler(this, cameraManager,getDataMode());
                handler = new CaptureActivityHandler(this, cameraManager);
            }

            initCrop();
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        // camera error
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("扫一扫");
        builder.setMessage("Camera error");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }

        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        builder.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * 初始化截取的矩形区域
     */
    public void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        /** 获取布局中扫描框的位置信息 */
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1];

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        /** 获取布局容器的宽高 */
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        /** 计算最终截取的矩形的左上角顶点x坐标 */
        int x = cropLeft * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的左上角顶点y坐标 */
        int y = cropTop * cameraHeight / containerHeight;

        /** 计算最终截取的矩形的宽度 */
        int width = cropWidth * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的高度 */
        int height = cropHeight * cameraHeight / containerHeight;

        /** 生成最终的截取的矩形 */
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    public int getDataMode() {
        return dataMode;
    }

    public void setDataMode(int dataMode) {
        this.dataMode = dataMode;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case IMAGE_PICKER_REQUEST_CODE:
                    String[] proj = { MediaStore.Images.Media.DATA };
                    // 获取选中图片的路径
                    Cursor cursor = getContentResolver().query(data.getData(),
                            proj, null, null, null);
                    if (cursor.moveToFirst()) {
                        int column_index = cursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        selectImgPath = cursor.getString(column_index);
                        Uri uri = ensureUriPermission(this, data);
                        if (selectImgPath == null) {
                            selectImgPath = UriUtils.getPathByUri(getApplicationContext(),uri);
                            Log.i("selectImgPath  Utils", selectImgPath);
                        }
                        Log.i("selectImgPath", selectImgPath);
                    }
                    Bitmap loadedImage = UriUtils.readBitmapFromFileDescriptor(selectImgPath,720,1280);
                    Result resultZxing = new DecodeUtils(DecodeUtils.DECODE_DATA_MODE_ALL)
                            .decodeWithZxing(loadedImage);
                    if (resultZxing != null) {
                        Bundle extras = new Bundle();
                        handleDecode(resultZxing, extras);
                    } else {
                        Toast.makeText(this, "解析无数据", Toast.LENGTH_SHORT).show();
                    }
                    cursor.close();
                    break;

            }

        }
    }
}