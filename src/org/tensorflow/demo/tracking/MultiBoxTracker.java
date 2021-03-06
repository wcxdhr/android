/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.demo.tracking;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.tensorflow.demo.Classifier.Recognition;
import org.tensorflow.demo.MyApplication;
import org.tensorflow.demo.R;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {

  private int count = 0;

  private final Logger logger = new Logger();

  private static final float TEXT_SIZE_DIP = 18;

  // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
  // the lower scored box (new or old) will be removed.
  private static final float MAX_OVERLAP = 0.2f;

  private static final float MIN_SIZE = 16.0f;

  // Allow replacement of the tracked box with new results if
  // correlation has dropped below this level.
  private static final float MARGINAL_CORRELATION = 0.75f;

  // Consider object to be lost if correlation falls below this threshold.
  private static final float MIN_CORRELATION = 0.2f;

  private static final int[] COLORS = {
    Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
    Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
  };

  private final Queue<Integer> availableColors = new LinkedList<Integer>();

  public ObjectTracker objectTracker;

  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();//屏幕中矩形框

  private static class TrackedRecognition {//TR类
    ObjectTracker.TrackedObject trackedObject;//新建一个跟踪对象
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }

  public final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();//当前跟踪的物体个数

  private final Paint boxPaint = new Paint();

  private final float textSizePx;
  private final BorderedText borderedText;

  private Matrix frameToCanvasMatrix;

  private int frameWidth;
  private int frameHeight;

  private int sensorOrientation;
  private Context context;

  public MultiBoxTracker(final Context context) {
    this.context = context;
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(12.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }

    if (objectTracker == null) {
      return;
    }

    // Draw correlations.
    for (final TrackedRecognition recognition : trackedObjects) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;

      final RectF trackedPos = trackedObject.getTrackedPositionInPreviewFrame();

      if (getFrameToCanvasMatrix().mapRect(trackedPos)) {
        final String labelString = String.format("%.2f", trackedObject.getCurrentCorrelation());
        borderedText.drawText(canvas, trackedPos.right, trackedPos.bottom, labelString);
      }
    }

    final Matrix matrix = getFrameToCanvasMatrix();
    objectTracker.drawDebug(canvas, matrix);
  }
//跟踪目标
  public synchronized void trackResults(
      final List<Recognition> results, final byte[] frame, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);//显示当前检测到行人数
    processResults(timestamp, results, frame);//跟踪
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
        Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                 canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos =
          (objectTracker != null)
              ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
              : new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
//      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
        /**图片替换识别对象（注：图片太大会造成内存溢出**/
        canvas.drawBitmap(BitmapFactory.decodeResource(MyApplication.getContext().getResources(), R.drawable.ic_launcher), null, trackedPos, boxPaint);


        final String labelString =
          !TextUtils.isEmpty(recognition.title)
              ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
              : String.format("%.2f", recognition.detectionConfidence);
      borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
    }
  }

  private boolean initialized = false;

  public synchronized void onFrame(
      final int w,
      final int h,
      final int rowStride,
      final int sensorOrientation,
      final byte[] frame,
      final long timestamp) {
    if (objectTracker == null && !initialized) {
      ObjectTracker.clearInstance();

      logger.i("Initializing ObjectTracker: %dx%d", w, h);
      objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
      frameWidth = w;
      frameHeight = h;
      this.sensorOrientation = sensorOrientation;
      initialized = true;

      if (objectTracker == null) {
        String message =
            "Object tracking support not found. "
                + "See tensorflow/examples/android/README.md for details.";
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        logger.e(message);
      }
    }

    if (objectTracker == null) {
      return;
    }

    objectTracker.nextFrame(frame, null, timestamp, null, true);

    // Clean up any objects not worth tracking any more.
    final LinkedList<TrackedRecognition> copyList =
        new LinkedList<TrackedRecognition>(trackedObjects);
    for (final TrackedRecognition recognition : copyList) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
      final float correlation = trackedObject.getCurrentCorrelation();
      if (correlation < MIN_CORRELATION) {
        logger.v("Removing tracked object %s because NCC is %.2f", trackedObject, correlation);
        trackedObject.stopTracking();
        trackedObjects.remove(recognition);

        availableColors.add(recognition.color);
      }
    }
  }

  private void processResults(
      final long timestamp, final List<Recognition> results, final byte[] originalFrame) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();
//要跟踪的矩形框
    screenRects.clear();//屏幕中矩形框清空
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());//图形处理

    for (final Recognition result : results) { //对检测到的行人
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);
      //将Frame的值映射到detectionScreenRect中

      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));//添加到屏幕中矩形框

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {//跟踪框宽度最小为16.0f
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));//置信度+行人/要跟踪的矩形框
    }

    if (rectsToTrack.isEmpty()) {//跟踪list为空
      logger.v("Nothing to track, aborting.");
      return;
    }

    if (objectTracker == null) {//若为空
      trackedObjects.clear();//清空
      for (final Pair<Float, Recognition> potential : rectsToTrack) {//对跟踪list
        final TrackedRecognition trackedRecognition = new TrackedRecognition();//新建TR对象
        trackedRecognition.detectionConfidence = potential.first;//得到置信度
        trackedRecognition.location = new RectF(potential.second.getLocation());//得到位置
        trackedRecognition.trackedObject = null;//跟踪目标置空
        trackedRecognition.title = potential.second.getTitle();
        trackedRecognition.color = COLORS[trackedObjects.size()];
        trackedObjects.add(trackedRecognition);//跟踪目标list中添加这个TR对象

        if (trackedObjects.size() >= COLORS.length) {//如果人数多于了设置的颜色
          break;
        }
      }
      return;
    }

    logger.i("%d rects to track", rectsToTrack.size());//跟踪list大小
    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      handleDetection(originalFrame, timestamp, potential);//处理
    }
  }

  private void handleDetection(
      final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
    final ObjectTracker.TrackedObject potentialObject =
        objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);//新建跟踪目标

    final float potentialCorrelation = potentialObject.getCurrentCorrelation();//关联目标
    logger.v(
        "Tracked object went from %s to %s with correlation %.2f",
        potential.second, potentialObject.getTrackedPositionInPreviewFrame(), potentialCorrelation);

    if (potentialCorrelation < MARGINAL_CORRELATION) {//关联度小于0.75f停止跟踪
      logger.v("Correlation too low to begin tracking %s.", potentialObject);
      potentialObject.stopTracking();
      return;
    }

    final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();//新建remove list

    float maxIntersect = 0.0f;

    // This is the current tracked object whose color we will take. If left null we'll take the
    // first one from the color queue.存放颜色
    TrackedRecognition recogToReplace = null;

    // Look for intersections that will be overridden by this object or an intersection that would
    // prevent this one from being placed.
    for (final TrackedRecognition trackedRecognition : trackedObjects) {
      final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();//得到跟踪对象矩形框位置
      final RectF b = potentialObject.getTrackedPositionInPreviewFrame();//得到检测对象矩形框位置
      final RectF intersection = new RectF();
      final boolean intersects = intersection.setIntersect(a, b);//是否相交，取交集
      final float intersectArea = intersection.width() * intersection.height();//交集面积
      final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;//并集
      final float intersectOverUnion = intersectArea / totalArea;//IoU

      if (intersects && intersectOverUnion > MAX_OVERLAP) {//两人允许最大重叠0.2f，否则视为一人
        if (potential.first < trackedRecognition.detectionConfidence
            && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
          //如果前对象置信度更高且跟踪关联够高，舍弃跟踪新的对象
          potentialObject.stopTracking();
          return;
        } else {
          removeList.add(trackedRecognition);//否则前对象加入remove list
          if (intersectOverUnion > maxIntersect) {//有最大交集的对象交付矩形框颜色
            maxIntersect = intersectOverUnion;
            recogToReplace = trackedRecognition;
          }
        }
      }
    }

    // If we're already tracking the max object and no intersections were found to bump off,
    // pick the worst current tracked object to remove, if it's also worse than this candidate
    // object. 若跟踪对象数目到达最大，无交集可舍弃，选择置信度最低的对象舍弃
    if (availableColors.isEmpty() && removeList.isEmpty()) {
      for (final TrackedRecognition candidate : trackedObjects) {
        if (candidate.detectionConfidence < potential.first) {
          if (recogToReplace == null
              || candidate.detectionConfidence < recogToReplace.detectionConfidence) {
            // Save it so that we use this color for the new object.
            recogToReplace = candidate;
          }
        }
      }
      if (recogToReplace != null) {//若找到被丢弃对象
        logger.v("Found non-intersecting object to remove.");
        removeList.add(recogToReplace);
      } else {
        logger.v("No non-intersecting object found to remove");
      }
    }

    // Remove everything that got intersected.丢弃所有remove list中的对象
    for (final TrackedRecognition trackedRecognition : removeList) {
      logger.v(
          "Removing tracked object %s with detection confidence %.2f, correlation %.2f",
          trackedRecognition.trackedObject,
          trackedRecognition.detectionConfidence,
          trackedRecognition.trackedObject.getCurrentCorrelation());
      trackedRecognition.trackedObject.stopTracking();
      trackedObjects.remove(trackedRecognition);
      if (trackedRecognition != recogToReplace) {
        availableColors.add(trackedRecognition.color);
      }
    }

    if (recogToReplace == null && availableColors.isEmpty()) {//没办法跟踪对象（颜色不够）
      logger.e("No room to track this object, aborting.");
      potentialObject.stopTracking();
      return;
    }

    // Finally safe to say we can track this object.终于可以跟踪他了
    logger.v(
        "Tracking object %s (%s) with detection confidence %.2f at position %s",
        potentialObject,
        potential.second.getTitle(),
        potential.first,
        potential.second.getLocation());
    final TrackedRecognition trackedRecognition = new TrackedRecognition();//新建跟踪对象
    trackedRecognition.detectionConfidence = potential.first;
    trackedRecognition.trackedObject = potentialObject;
    trackedRecognition.title = potential.second.getTitle();

    // Use the color from a replaced object before taking one from the color queue.
    if (recogToReplace == null){
        count ++;//行人计数
    }
    trackedRecognition.color =
        recogToReplace != null ? recogToReplace.color : availableColors.poll();
    trackedObjects.add(trackedRecognition);//加入跟踪list
  }

    public int getCount() {
        return count;
    }
}
