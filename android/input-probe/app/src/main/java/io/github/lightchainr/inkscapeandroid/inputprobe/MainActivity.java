package io.github.lightchainr.inkscapeandroid.inputprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Locale;

public final class MainActivity extends Activity {
    private InputProbeView probeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        probeView = new InputProbeView(this);
        setContentView(probeView);
    }

    @Override
    protected void onPause() {
        if (probeView != null) {
            probeView.flush();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (probeView != null) {
            probeView.close();
        }
        super.onDestroy();
    }

    private static final class InputProbeView extends View {
        private static final int MAX_VISIBLE_LINES = 18;
        private static final int FLUSH_INTERVAL = 8;
        private static final long NANOS_PER_MILLISECOND = 1_000_000L;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayDeque<String> recentLines = new ArrayDeque<>();
        private final File logFile;
        private BufferedWriter writer;
        private int recordsSinceFlush;

        InputProbeView(Activity activity) {
            super(activity);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setBackgroundColor(Color.rgb(16, 16, 16));

            paint.setColor(Color.WHITE);
            paint.setTextSize(28f);

            File logDir = new File(activity.getFilesDir(), "input-probe");
            if (!logDir.exists() && !logDir.mkdirs()) {
                addVisibleLine("ERROR cannot create log directory");
            }
            logFile = new File(logDir, "events.jsonl");
            try {
                writer = new BufferedWriter(new FileWriter(logFile, true));
            } catch (IOException error) {
                addVisibleLine("ERROR opening log: " + error.getMessage());
            }
            addVisibleLine("Log: " + logFile.getAbsolutePath());
            addVisibleLine("Touch, hover and stylus events are recorded as JSONL.");
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            record(event, "touch");
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }

        @Override
        public boolean onHoverEvent(MotionEvent event) {
            record(event, "hover");
            return true;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            record(event, "generic");
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void record(MotionEvent event, String dispatchPath) {
            final long arrivalTimeNanos = System.nanoTime();
            final int pointerCount = event.getPointerCount();
            final int historySize = event.getHistorySize();

            for (int historyIndex = 0; historyIndex < historySize; historyIndex++) {
                for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
                    writeRecord(event, dispatchPath, arrivalTimeNanos, pointerIndex, historyIndex);
                }
            }
            for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
                writeRecord(event, dispatchPath, arrivalTimeNanos, pointerIndex, -1);
            }

            int actionIndex = Math.min(event.getActionIndex(), pointerCount - 1);
            String summary = String.format(
                    Locale.US,
                    "%s action=%s pointers=%d id=%d tool=%s p=%.3f flags=0x%X",
                    dispatchPath,
                    actionName(event.getActionMasked()),
                    pointerCount,
                    event.getPointerId(actionIndex),
                    toolName(event.getToolType(actionIndex)),
                    event.getPressure(actionIndex),
                    event.getFlags());
            addVisibleLine(summary);
            invalidate();
        }

        private void writeRecord(
                MotionEvent event,
                String dispatchPath,
                long arrivalTimeNanos,
                int pointerIndex,
                int historyIndex) {
            if (writer == null) {
                return;
            }

            final boolean historical = historyIndex >= 0;
            try {
                JSONObject json = new JSONObject();
                json.put("dispatchPath", dispatchPath);
                json.put("historical", historical);
                json.put("arrivalTimeNanos", arrivalTimeNanos);
                json.put("eventTimeNanos", eventTimeNanos(event, historyIndex));
                json.put("actionMasked", event.getActionMasked());
                json.put("actionIndex", event.getActionIndex());
                json.put("pointerCount", event.getPointerCount());
                json.put("pointerIndex", pointerIndex);
                json.put("pointerId", event.getPointerId(pointerIndex));
                json.put("toolType", event.getToolType(pointerIndex));
                json.put("source", event.getSource());
                json.put("deviceId", event.getDeviceId());
                json.put("buttonState", event.getButtonState());
                json.put("metaState", event.getMetaState());
                json.put("flags", event.getFlags());
                json.put("historySize", event.getHistorySize());

                if (historical) {
                    json.put("x", event.getHistoricalX(pointerIndex, historyIndex));
                    json.put("y", event.getHistoricalY(pointerIndex, historyIndex));
                    json.put("pressure", event.getHistoricalPressure(pointerIndex, historyIndex));
                    json.put("orientation", event.getHistoricalOrientation(pointerIndex, historyIndex));
                    json.put(
                            "tilt",
                            event.getHistoricalAxisValue(
                                    MotionEvent.AXIS_TILT, pointerIndex, historyIndex));
                    json.put(
                            "distance",
                            event.getHistoricalAxisValue(
                                    MotionEvent.AXIS_DISTANCE, pointerIndex, historyIndex));
                } else {
                    json.put("x", event.getX(pointerIndex));
                    json.put("y", event.getY(pointerIndex));
                    json.put("pressure", event.getPressure(pointerIndex));
                    json.put("orientation", event.getOrientation(pointerIndex));
                    json.put("tilt", event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex));
                    json.put(
                            "distance",
                            event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex));
                }

                writer.write(json.toString());
                writer.newLine();
                recordsSinceFlush++;
                if (recordsSinceFlush >= FLUSH_INTERVAL) {
                    flush();
                }
            } catch (Exception error) {
                addVisibleLine("ERROR recording event: " + error.getMessage());
            }
        }

        private static long eventTimeNanos(MotionEvent event, int historyIndex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return historyIndex >= 0
                        ? event.getHistoricalEventTimeNanos(historyIndex)
                        : event.getEventTimeNanos();
            }
            long eventTimeMillis = historyIndex >= 0
                    ? event.getHistoricalEventTime(historyIndex)
                    : event.getEventTime();
            return eventTimeMillis * NANOS_PER_MILLISECOND;
        }

        private void addVisibleLine(String line) {
            recentLines.addLast(line);
            while (recentLines.size() > MAX_VISIBLE_LINES) {
                recentLines.removeFirst();
            }
        }

        void flush() {
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
                recordsSinceFlush = 0;
            } catch (IOException error) {
                addVisibleLine("ERROR flushing log: " + error.getMessage());
            }
        }

        void close() {
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
                writer.close();
            } catch (IOException error) {
                addVisibleLine("ERROR closing log: " + error.getMessage());
            } finally {
                writer = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = 24f;
            float y = 42f;
            for (String line : recentLines) {
                canvas.drawText(line, x, y, paint);
                y += 36f;
            }
        }

        private static String actionName(int action) {
            return switch (action) {
                case MotionEvent.ACTION_DOWN -> "DOWN";
                case MotionEvent.ACTION_UP -> "UP";
                case MotionEvent.ACTION_MOVE -> "MOVE";
                case MotionEvent.ACTION_CANCEL -> "CANCEL";
                case MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN";
                case MotionEvent.ACTION_POINTER_UP -> "POINTER_UP";
                case MotionEvent.ACTION_HOVER_ENTER -> "HOVER_ENTER";
                case MotionEvent.ACTION_HOVER_MOVE -> "HOVER_MOVE";
                case MotionEvent.ACTION_HOVER_EXIT -> "HOVER_EXIT";
                case MotionEvent.ACTION_BUTTON_PRESS -> "BUTTON_PRESS";
                case MotionEvent.ACTION_BUTTON_RELEASE -> "BUTTON_RELEASE";
                case MotionEvent.ACTION_SCROLL -> "SCROLL";
                default -> Integer.toString(action);
            };
        }

        private static String toolName(int toolType) {
            return switch (toolType) {
                case MotionEvent.TOOL_TYPE_FINGER -> "FINGER";
                case MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS";
                case MotionEvent.TOOL_TYPE_ERASER -> "ERASER";
                case MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE";
                case MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN";
                default -> Integer.toString(toolType);
            };
        }
    }
}
