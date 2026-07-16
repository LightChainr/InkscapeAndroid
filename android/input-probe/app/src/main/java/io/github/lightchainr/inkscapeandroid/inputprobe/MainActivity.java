package io.github.lightchainr.inkscapeandroid.inputprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
        private static final int WRITER_QUEUE_CAPACITY = 8192;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayDeque<String> recentLines = new ArrayDeque<>();
        private final AtomicLong sequence = new AtomicLong();
        private final String sessionId = UUID.randomUUID().toString();
        private final File logFile;
        private AsyncJsonlWriter logWriter;

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
                logWriter = new AsyncJsonlWriter(logFile, sessionId, WRITER_QUEUE_CAPACITY);
                logWriter.enqueueJson(SessionMetadata.create(activity, sessionId));
            } catch (Exception error) {
                addVisibleLine("ERROR opening log: " + error.getMessage());
            }
            addVisibleLine("Session: " + sessionId);
            addVisibleLine("Log: " + logFile.getAbsolutePath());
            addVisibleLine("Primitive snapshots are encoded and written on a background thread.");
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
            final AsyncJsonlWriter writer = logWriter;
            if (writer == null) {
                return;
            }

            final long captureMonotonicNanos = System.nanoTime();
            final long captureUptimeMillis = SystemClock.uptimeMillis();
            final int displayId = getDisplay() == null ? -1 : getDisplay().getDisplayId();
            final int pointerCount = event.getPointerCount();
            final int historySize = event.getHistorySize();

            for (int historyIndex = 0; historyIndex < historySize; historyIndex++) {
                for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
                    enqueueSample(
                            writer,
                            EventSample.capture(
                                    event,
                                    sessionId,
                                    sequence.incrementAndGet(),
                                    dispatchPath,
                                    captureMonotonicNanos,
                                    captureUptimeMillis,
                                    displayId,
                                    pointerIndex,
                                    historyIndex));
                }
            }
            for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
                enqueueSample(
                        writer,
                        EventSample.capture(
                                event,
                                sessionId,
                                sequence.incrementAndGet(),
                                dispatchPath,
                                captureMonotonicNanos,
                                captureUptimeMillis,
                                displayId,
                                pointerIndex,
                                -1));
            }

            int actionIndex = Math.min(event.getActionIndex(), pointerCount - 1);
            String summary = String.format(
                    Locale.US,
                    "%s action=%s pointers=%d id=%d tool=%s p=%.3f q=%d drop=%d",
                    dispatchPath,
                    actionName(event.getActionMasked()),
                    pointerCount,
                    event.getPointerId(actionIndex),
                    toolName(event.getToolType(actionIndex)),
                    event.getPressure(actionIndex),
                    writer.queueSize(),
                    writer.pendingDropCount());
            addVisibleLine(summary);
            String writerFailure = writer.failureMessage();
            if (writerFailure != null) {
                addVisibleLine("ERROR writer: " + writerFailure);
            }
            invalidate();
        }

        private void enqueueSample(AsyncJsonlWriter writer, EventSample sample) {
            if (!writer.enqueue(sample) && writer.pendingDropCount() == 1L) {
                addVisibleLine("WARN writer queue full; drop record will be emitted");
            }
        }

        private void addVisibleLine(String line) {
            recentLines.addLast(line);
            while (recentLines.size() > MAX_VISIBLE_LINES) {
                recentLines.removeFirst();
            }
        }

        void flush() {
            if (logWriter != null) {
                logWriter.flush();
            }
        }

        void close() {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
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
