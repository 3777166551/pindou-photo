package com.pindou.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.view.BoardProjector;
import com.pindou.app.view.FakeArView;

/**
 * AR 试摆(假 AR):后置相机取景打底,陀螺仪驱动效果图"板子"
 * 固定立在放置时的视线前方,转动手机时透视变化;纯展示不可交互。
 * 不依赖任何 AR 框架:姿态来自旋转矢量传感器,无陀螺仪时降级为固定视角。
 */
public class FakeArActivity extends Activity {

    private static final int REQ_CAMERA = 41;

    private TextureView preview;
    private FakeArView arView;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private HandlerThread camThread;
    private Handler camHandler;
    private int sensorOrientation = 90;
    private Size previewSize;
    private boolean resumed;

    private android.hardware.SensorManager sensorManager;
    private android.hardware.SensorEventListener sensorListener;
    /** 三级降级:旋转矢量 → 游戏旋转矢量 → 加速度+磁场融合;全无则固定视角 */
    private int sensorMode = 0;
    private final float[] gravityVals = new float[3];
    private final float[] magVals = new float[3];
    private boolean hasGravity;
    private boolean hasMag;
    private final float[] tmpR = new float[9];
    private final float[] tmpQ = new float[4];
    private long lastFusionMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_fake_ar);

        preview = findViewById(R.id.preview);
        arView = findViewById(R.id.arView);
        TextView btnBack = findViewById(R.id.btnBack);
        TextView chipRecenter = findViewById(R.id.chipRecenter);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        chipRecenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                arView.recenter();
            }
        });

        String path = getIntent().getStringExtra("path");
        float wm = getIntent().getFloatExtra("wm", 0.3f);
        float hm = getIntent().getFloatExtra("hm", 0.3f);
        Bitmap bmp = null;
        if (path != null) {
            bmp = BitmapFactory.decodeFile(path);
        }
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.ar_load_failed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // 叠层贴图 1280 宽足够,省内存
        if (bmp.getWidth() > 1280) {
            float k = 1280f / bmp.getWidth();
            Bitmap scaled = Bitmap.createScaledBitmap(bmp,
                    1280, Math.max(1, Math.round(bmp.getHeight() * k)), true);
            bmp.recycle();
            bmp = scaled;
        }
        arView.setBoard(bmp, wm, hm);

        sensorManager = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            hookPreview();
        }
    }

    /** 权限到手(或本就有)后,TextureView 才允许挂监听开相机 */
    private void hookPreview() {
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,
                                                  int w, int h) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,
                                                    int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                hookPreview();
            } else {
                Toast.makeText(this, getString(R.string.ar_need_camera),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ---------------- 相机(Camera2,零依赖手写) ----------------

    private void openCamera() {
        if (!resumed || camHandler == null) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String camId = pickBackCamera(cm);
            if (camId == null) {
                fail(getString(R.string.ar_no_camera));
                return;
            }
            CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            android.graphics.SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) {
                return;
            }
            previewSize = pickPreviewSize(chars, preview.getWidth(), preview.getHeight());
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            applyPreviewTransform();
            Surface surface = new Surface(st);
            cm.openCamera(camId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice dev) {
                    camera = dev;
                    try {
                        previewBuilder = dev.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        previewBuilder.addTarget(surface);
                        dev.createCaptureSession(java.util.Collections.singletonList(surface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            s.setRepeatingRequest(previewBuilder.build(),
                                                    null, camHandler);
                                        } catch (Exception e) {
                                            fail(getString(R.string.ar_no_camera));
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(
                                            CameraCaptureSession s) {
                                        fail(getString(R.string.ar_no_camera));
                                    }
                                }, camHandler);
                    } catch (CameraAccessException e) {
                        fail(getString(R.string.ar_no_camera));
                    }
                }

                @Override
                public void onDisconnected(CameraDevice dev) {
                    dev.close();
                    if (camera == dev) {
                        camera = null;
                    }
                }

                @Override
                public void onError(CameraDevice dev, int error) {
                    dev.close();
                    if (camera == dev) {
                        camera = null;
                    }
                    fail(getString(R.string.ar_no_camera));
                }
            }, camHandler);
        } catch (SecurityException | CameraAccessException e) {
            fail(getString(R.string.ar_no_camera));
        }
    }

    /** 优先后置;没有明确标注的(部分模拟器)就取第一个 */
    private String pickBackCamera(CameraManager cm) throws CameraAccessException {
        String first = null;
        for (String id : cm.getCameraIdList()) {
            Integer facing = cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
            if (first == null) {
                first = id;
            }
        }
        return first;
    }

    /** 在支持列表里挑最接近视口宽高比、约 720p 的预览档(省电够用) */
    private Size pickPreviewSize(CameraCharacteristics chars, int vw, int vh) {
        android.hardware.camera2.params.StreamConfigurationMap map =
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return new Size(640, 480);
        }
        Size[] sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
        if (sizes == null || sizes.length == 0) {
            return new Size(640, 480);
        }
        float wantAspect = vh > 0 && vw > 0 ? (float) vw / vh : 9f / 16f;
        Size best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Size s : sizes) {
            // 传感器输出是横的,视口是竖的:比较时把宽高对调
            float sa = (float) s.getWidth() / s.getHeight();
            float aspectDiff = Math.abs(sa - 1f / wantAspect);
            if (aspectDiff > 0.25f) {
                continue;
            }
            // 越接近 720p 越好(像素面积差作为分数)
            int score = Math.abs(s.getWidth() * s.getHeight() - 1280 * 720);
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best != null ? best : sizes[0];
    }

    /** 把横着的传感器画面旋转+铺满到竖屏 TextureView 上 */
    private void applyPreviewTransform() {
        if (previewSize == null) {
            return;
        }
        int vw = preview.getWidth(), vh = preview.getHeight();
        if (vw == 0 || vh == 0) {
            return;
        }
        boolean swap = sensorOrientation == 90 || sensorOrientation == 270;
        int bw = swap ? previewSize.getHeight() : previewSize.getWidth();
        int bh = swap ? previewSize.getWidth() : previewSize.getHeight();
        float scale = Math.max(vw / (float) bw, vh / (float) bh);
        float cx = vw * 0.5f, cy = vh * 0.5f;
        Matrix m = new Matrix();
        m.setScale(scale, scale, cx, cy);
        m.postRotate((360 - sensorOrientation) % 360, cx, cy);
        preview.setTransform(m);
    }

    private void closeCamera() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (camera != null) {
                camera.close();
                camera = null;
            }
            if (previewBuilder != null) {
                previewBuilder = null;
            }
        } catch (Exception e) {
            // 退出路上关闭失败不影响流程
        }
    }

    private void fail(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FakeArActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // ---------------- 姿态传感器 ----------------

    private void setupSensors() {
        sensorMode = 0;
        if (sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null) {
            sensorMode = 1;
        } else if (sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR) != null) {
            sensorMode = 2;
        } else if (sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_ACCELEROMETER) != null
                && sensorManager.getDefaultSensor(
                android.hardware.Sensor.TYPE_MAGNETIC_FIELD) != null) {
            sensorMode = 3;
        }
        if (sensorMode == 0) {
            // 无任何姿态传感器:喂单位姿态,板子固定正对用户
            Toast.makeText(this, getString(R.string.ar_no_gyro), Toast.LENGTH_LONG).show();
            arView.onQuaternion(0, 0, 0, 1);
            return;
        }
        sensorListener = new android.hardware.SensorEventListener() {
            @Override
            public void onSensorChanged(android.hardware.SensorEvent event) {
                handleSensor(event);
            }

            @Override
            public void onAccuracyChanged(android.hardware.Sensor s, int a) {
            }
        };
        switch (sensorMode) {
            case 1:
                sensorManager.registerListener(sensorListener,
                        sensorManager.getDefaultSensor(
                                android.hardware.Sensor.TYPE_ROTATION_VECTOR),
                        android.hardware.SensorManager.SENSOR_DELAY_GAME);
                break;
            case 2:
                sensorManager.registerListener(sensorListener,
                        sensorManager.getDefaultSensor(
                                android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR),
                        android.hardware.SensorManager.SENSOR_DELAY_GAME);
                break;
            default:
                sensorManager.registerListener(sensorListener,
                        sensorManager.getDefaultSensor(
                                android.hardware.Sensor.TYPE_ACCELEROMETER),
                        android.hardware.SensorManager.SENSOR_DELAY_GAME);
                sensorManager.registerListener(sensorListener,
                        sensorManager.getDefaultSensor(
                                android.hardware.Sensor.TYPE_MAGNETIC_FIELD),
                        android.hardware.SensorManager.SENSOR_DELAY_GAME);
                break;
        }
    }

    private void handleSensor(android.hardware.SensorEvent event) {
        int t = event.sensor.getType();
        if (t == android.hardware.Sensor.TYPE_ROTATION_VECTOR
                || t == android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // 旋转矢量 = [x*sin(θ/2), y*sin(θ/2), z*sin(θ/2)],标量项自己补
            float x = event.values[0], y = event.values[1], z = event.values[2];
            float w = (float) Math.sqrt(Math.max(0f,
                    1f - x * x - y * y - z * z));
            arView.onQuaternion(x, y, z, w);
        } else if (t == android.hardware.Sensor.TYPE_ACCELEROMETER) {
            gravityVals[0] = event.values[0];
            gravityVals[1] = event.values[1];
            gravityVals[2] = event.values[2];
            hasGravity = true;
            fuseAccelMag();
        } else if (t == android.hardware.Sensor.TYPE_MAGNETIC_FIELD) {
            magVals[0] = event.values[0];
            magVals[1] = event.values[1];
            magVals[2] = event.values[2];
            hasMag = true;
            fuseAccelMag();
        }
    }

    /** 加速度+磁场兜底:两组数据都到位后融出旋转矩阵(限频,够用即可) */
    private void fuseAccelMag() {
        if (!hasGravity || !hasMag) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFusionMs < 20) {
            return;
        }
        lastFusionMs = now;
        if (sensorManager.getRotationMatrix(tmpR, null, gravityVals, magVals)) {
            BoardProjector.quaternionFromRotationMatrix(tmpR, tmpQ);
            arView.onQuaternion(tmpQ[0], tmpQ[1], tmpQ[2], tmpQ[3]);
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        camThread = new HandlerThread("fakear_cam");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
        setupSensors();
        if (preview.getSurfaceTexture() != null
                && checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (sensorManager != null && sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
            sensorListener = null;
        }
        closeCamera();
        if (camThread != null) {
            camThread.quitSafely();
            camThread = null;
        }
        camHandler = null;
        super.onPause();
    }
}
