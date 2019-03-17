/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.Math;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;
import org.tensorflow.demo.R; // Explicit import needed for internal Google builds.

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private int num;
  private TextView num_dis;


  private static final int TF_OD_API_INPUT_SIZE = 300;//模型输入图片分辨率

  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/person.pbtxt";//

  private enum DetectorMode {
    TF_OD_API, MULTIBOX, YOLO;
  }
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;

  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 360);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private long minTime = 1000;
  private long maxTime = 0;
  private long avgTime;
  private long totalTime = 0;
  private long timeCount = 0;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;

    /**
     * @param size
     * @param rotation
     */
  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) throws IOException {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

      String fileName = "/ssd-obj.pb";
      String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
      File file = new File(directory + fileName);
      if (file.exists()){
          String filepath = file.getAbsolutePath();
          //Log.i("dd",filepath);
          detector = TensorFlowObjectDetectionAPIModel.create(
                  getAssets(), filepath, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
          cropSize = TF_OD_API_INPUT_SIZE;
      }


    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    num_dis=(TextView)findViewById(R.id.person_number);//num_dis用于显示人数
    num_dis.setText("人数：0");//初始化为0
    num=0;//num用于计数

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropSize, cropSize,
                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              }
            });

    addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                if (!isDebug()) {
                  return;
                }
                final Bitmap copy = cropCopyBitmap;
                if (copy == null) {
                  return;
                }

                final int backgroundColor = Color.argb(100, 0, 0, 0);
                canvas.drawColor(backgroundColor);

                final Matrix matrix = new Matrix();
                final float scaleFactor = 2;
                matrix.postScale(scaleFactor, scaleFactor);
                matrix.postTranslate(
                        canvas.getWidth() - copy.getWidth() * scaleFactor,
                        canvas.getHeight() - copy.getHeight() * scaleFactor);
                canvas.drawBitmap(copy, matrix, new Paint());

                final Vector<String> lines = new Vector<String>();
                if (detector != null) {
                  final String statString = detector.getStatString();
                  final String[] statLines = statString.split("\n");
                  for (final String line : statLines) {
                    lines.add(line);
                  }
                }
                lines.add("");

                  lines.add("Inference time: " + lastProcessingTimeMs + "ms"+"  avg："+ avgTime+"  min："+
                          minTime + "  max："+maxTime+ "  count："+ timeCount);
                lines.add("Frame: " + previewWidth + "x" + previewHeight);
                lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                lines.add("Rotation: " + sensorOrientation);


                borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
              }
            });
  }

  OverlayView trackingOverlay;

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
            previewWidth,
            previewHeight,
            getLuminanceStride(),
            sensorOrientation,
            originalLuminance,
            timestamp);
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
            new Runnable() {
              @Override
              public void run() {
                LOGGER.i("Running detection on image " + currTimestamp);
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                if (timeCount < 1000){
                    if (lastProcessingTimeMs < minTime){
                        minTime = lastProcessingTimeMs;
                    }
                    if (timeCount > 1){
                        if (lastProcessingTimeMs > maxTime){
                            maxTime = lastProcessingTimeMs;
                        }
                    }
                    ++ timeCount;
                    totalTime += lastProcessingTimeMs;
                    avgTime = totalTime/timeCount;
                }

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;


                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {//results为检测模型的输出链表
                        final RectF location = result.getLocation();//得到检测对象的位置
                        if (location != null  && result.getConfidence() >= minimumConfidence) {//判断置信度是否大于阈值（0.5）
                            canvas.drawRect(location, paint);//这一步不会在屏幕上画出矩形框，而是在检测窗口中(默认不可见）进行绘制
                            cropToFrameTransform.mapRect(location);
                            result.setLocation(location);
                            mappedRecognitions.add(result);//mappedRecognitions用于存放筛选后的检测对象
                    }
                }
                tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);//tracker为MultiBoxTracker类，调用trackResults方法比对检测对象和跟踪过的对象
                num = (tracker.getCount());//获得当前行人计数值
                  runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                          num_dis.setText("人数："+num);
                      }
                  });//刷新界面上的计数值
                trackingOverlay.postInvalidate();
                requestRender();
                computingDetection = false;
              }
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
}