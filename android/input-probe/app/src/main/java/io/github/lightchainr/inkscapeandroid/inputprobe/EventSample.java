package io.github.lightchainr.inkscapeandroid.inputprobe;

import android.os.Build;
import android.view.MotionEvent;

import org.json.JSONObject;

final class EventSample implements AsyncJsonlWriter.JsonRecord {
    static final int SCHEMA_VERSION = 1;

    private final String sessionId;
    private final long sequence;
    private final String dispatchPath;
    private final String sampleKind;
    private final long captureMonotonicNanos;
    private final long captureUptimeMillis;
    private final long eventTimeMillis;
    private final Long eventTimeNanos;
    private final long downTimeMillis;
    private final int actionMasked;
    private final int actionIndex;
    private final int actionButton;
    private final int pointerCount;
    private final int pointerIndex;
    private final int pointerId;
    private final boolean actionPointer;
    private final int toolType;
    private final int source;
    private final int deviceId;
    private final int displayId;
    private final int buttonState;
    private final int metaState;
    private final int flags;
    private final int edgeFlags;
    private final int classification;
    private final int historySize;
    private final float x;
    private final float y;
    private final Float rawX;
    private final Float rawY;
    private final float pressure;
    private final float orientation;
    private final float tilt;
    private final float distance;
    private final float size;
    private final float touchMajor;
    private final float touchMinor;
    private final float toolMajor;
    private final float toolMinor;
    private final float xPrecision;
    private final float yPrecision;

    private EventSample(
            String sessionId,
            long sequence,
            String dispatchPath,
            String sampleKind,
            long captureMonotonicNanos,
            long captureUptimeMillis,
            long eventTimeMillis,
            Long eventTimeNanos,
            long downTimeMillis,
            int actionMasked,
            int actionIndex,
            int actionButton,
            int pointerCount,
            int pointerIndex,
            int pointerId,
            boolean actionPointer,
            int toolType,
            int source,
            int deviceId,
            int displayId,
            int buttonState,
            int metaState,
            int flags,
            int edgeFlags,
            int classification,
            int historySize,
            float x,
            float y,
            Float rawX,
            Float rawY,
            float pressure,
            float orientation,
            float tilt,
            float distance,
            float size,
            float touchMajor,
            float touchMinor,
            float toolMajor,
            float toolMinor,
            float xPrecision,
            float yPrecision) {
        this.sessionId = sessionId;
        this.sequence = sequence;
        this.dispatchPath = dispatchPath;
        this.sampleKind = sampleKind;
        this.captureMonotonicNanos = captureMonotonicNanos;
        this.captureUptimeMillis = captureUptimeMillis;
        this.eventTimeMillis = eventTimeMillis;
        this.eventTimeNanos = eventTimeNanos;
        this.downTimeMillis = downTimeMillis;
        this.actionMasked = actionMasked;
        this.actionIndex = actionIndex;
        this.actionButton = actionButton;
        this.pointerCount = pointerCount;
        this.pointerIndex = pointerIndex;
        this.pointerId = pointerId;
        this.actionPointer = actionPointer;
        this.toolType = toolType;
        this.source = source;
        this.deviceId = deviceId;
        this.displayId = displayId;
        this.buttonState = buttonState;
        this.metaState = metaState;
        this.flags = flags;
        this.edgeFlags = edgeFlags;
        this.classification = classification;
        this.historySize = historySize;
        this.x = x;
        this.y = y;
        this.rawX = rawX;
        this.rawY = rawY;
        this.pressure = pressure;
        this.orientation = orientation;
        this.tilt = tilt;
        this.distance = distance;
        this.size = size;
        this.touchMajor = touchMajor;
        this.touchMinor = touchMinor;
        this.toolMajor = toolMajor;
        this.toolMinor = toolMinor;
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
    }

    static EventSample capture(
            MotionEvent event,
            String sessionId,
            long sequence,
            String dispatchPath,
            long captureMonotonicNanos,
            long captureUptimeMillis,
            int displayId,
            int pointerIndex,
            int historyIndex) {
        boolean historical = historyIndex >= 0;
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        boolean pointerSpecific = action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP;

        long eventTimeMillis = historical
                ? event.getHistoricalEventTime(historyIndex)
                : event.getEventTime();
        Long eventTimeNanos = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            eventTimeNanos = historical
                    ? event.getHistoricalEventTimeNanos(historyIndex)
                    : event.getEventTimeNanos();
        }

        float x;
        float y;
        Float rawX = null;
        Float rawY = null;
        float pressure;
        float orientation;
        float tilt;
        float distance;
        float size;
        float touchMajor;
        float touchMinor;
        float toolMajor;
        float toolMinor;

        if (historical) {
            x = event.getHistoricalX(pointerIndex, historyIndex);
            y = event.getHistoricalY(pointerIndex, historyIndex);
            pressure = event.getHistoricalPressure(pointerIndex, historyIndex);
            orientation = event.getHistoricalOrientation(pointerIndex, historyIndex);
            tilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, historyIndex);
            distance = event.getHistoricalAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex, historyIndex);
            size = event.getHistoricalSize(pointerIndex, historyIndex);
            touchMajor = event.getHistoricalTouchMajor(pointerIndex, historyIndex);
            touchMinor = event.getHistoricalTouchMinor(pointerIndex, historyIndex);
            toolMajor = event.getHistoricalToolMajor(pointerIndex, historyIndex);
            toolMinor = event.getHistoricalToolMinor(pointerIndex, historyIndex);
        } else {
            x = event.getX(pointerIndex);
            y = event.getY(pointerIndex);
            rawX = event.getRawX(pointerIndex);
            rawY = event.getRawY(pointerIndex);
            pressure = event.getPressure(pointerIndex);
            orientation = event.getOrientation(pointerIndex);
            tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex);
            distance = event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex);
            size = event.getSize(pointerIndex);
            touchMajor = event.getTouchMajor(pointerIndex);
            touchMinor = event.getTouchMinor(pointerIndex);
            toolMajor = event.getToolMajor(pointerIndex);
            toolMinor = event.getToolMinor(pointerIndex);
        }

        return new EventSample(
                sessionId,
                sequence,
                dispatchPath,
                historical ? "historical" : "current",
                captureMonotonicNanos,
                captureUptimeMillis,
                eventTimeMillis,
                eventTimeNanos,
                event.getDownTime(),
                action,
                actionIndex,
                event.getActionButton(),
                event.getPointerCount(),
                pointerIndex,
                event.getPointerId(pointerIndex),
                pointerSpecific && pointerIndex == actionIndex,
                event.getToolType(pointerIndex),
                event.getSource(),
                event.getDeviceId(),
                displayId,
                event.getButtonState(),
                event.getMetaState(),
                event.getFlags(),
                event.getEdgeFlags(),
                event.getClassification(),
                event.getHistorySize(),
                x,
                y,
                rawX,
                rawY,
                pressure,
                orientation,
                tilt,
                distance,
                size,
                touchMajor,
                touchMinor,
                toolMajor,
                toolMinor,
                event.getXPrecision(),
                event.getYPrecision());
    }

    @Override
    public long sequence() {
        return sequence;
    }

    @Override
    public String toJsonLine() throws Exception {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", SCHEMA_VERSION);
        json.put("recordType", "event");
        json.put("sessionId", sessionId);
        json.put("sequence", sequence);
        json.put("dispatchPath", dispatchPath);
        json.put("sampleKind", sampleKind);
        json.put("captureMonotonicNanos", captureMonotonicNanos);
        json.put("captureUptimeMillis", captureUptimeMillis);
        json.put("eventTimeMillis", eventTimeMillis);
        json.put("eventTimeNanos", eventTimeNanos == null ? JSONObject.NULL : eventTimeNanos);
        json.put("downTimeMillis", downTimeMillis);
        json.put("actionMasked", actionMasked);
        json.put("actionIndex", actionIndex);
        json.put("actionButton", actionButton);
        json.put("pointerCount", pointerCount);
        json.put("pointerIndex", pointerIndex);
        json.put("pointerId", pointerId);
        json.put("isActionPointer", actionPointer);
        json.put("toolType", toolType);
        json.put("source", source);
        json.put("deviceId", deviceId);
        json.put("displayId", displayId);
        json.put("buttonState", buttonState);
        json.put("metaState", metaState);
        json.put("flags", flags);
        json.put("edgeFlags", edgeFlags);
        json.put("classification", classification);
        json.put("historySize", historySize);
        json.put("x", x);
        json.put("y", y);
        json.put("rawX", rawX == null ? JSONObject.NULL : rawX);
        json.put("rawY", rawY == null ? JSONObject.NULL : rawY);
        json.put("pressure", pressure);
        json.put("orientation", orientation);
        json.put("tilt", tilt);
        json.put("distance", distance);
        json.put("size", size);
        json.put("touchMajor", touchMajor);
        json.put("touchMinor", touchMinor);
        json.put("toolMajor", toolMajor);
        json.put("toolMinor", toolMinor);
        json.put("xPrecision", xPrecision);
        json.put("yPrecision", yPrecision);
        return json.toString();
    }
}
