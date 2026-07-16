package io.github.lightchainr.inkscapeandroid.inputprobe;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;

import org.json.JSONArray;
import org.json.JSONObject;

final class SessionMetadata {
    private SessionMetadata() {}

    static String create(Activity activity, String sessionId) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", EventSample.SCHEMA_VERSION);
        root.put("recordType", "session");
        root.put("sessionId", sessionId);
        root.put("createdWallTimeMillis", System.currentTimeMillis());
        root.put("app", app(activity));
        root.put("device", device());
        root.put("display", display(activity));
        root.put("inputDevices", inputDevices());
        return root.toString();
    }

    private static JSONObject app(Activity activity) throws PackageManager.NameNotFoundException {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        boolean debuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        JSONObject json = new JSONObject();
        json.put("versionName", info.versionName == null ? "unknown" : info.versionName);
        json.put("versionCode", info.getLongVersionCode());
        json.put("buildType", debuggable ? "debug" : "release");
        json.put("gitSha", "unknown");
        return json;
    }

    private static JSONObject device() throws Exception {
        JSONObject json = new JSONObject();
        json.put("manufacturer", Build.MANUFACTURER);
        json.put("brand", Build.BRAND);
        json.put("model", Build.MODEL);
        json.put("device", Build.DEVICE);
        json.put("hardware", Build.HARDWARE);
        json.put("sdkInt", Build.VERSION.SDK_INT);
        json.put("release", Build.VERSION.RELEASE);
        json.put("fingerprint", Build.FINGERPRINT);
        return json;
    }

    private static JSONObject display(Activity activity) throws Exception {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        Display currentDisplay = activity.getDisplay();
        JSONObject json = new JSONObject();
        json.put("widthPixels", metrics.widthPixels);
        json.put("heightPixels", metrics.heightPixels);
        json.put("density", metrics.density);
        json.put("densityDpi", metrics.densityDpi);
        json.put("scaledDensity", metrics.scaledDensity);
        json.put("xdpi", metrics.xdpi);
        json.put("ydpi", metrics.ydpi);
        json.put("refreshRateHz", currentDisplay == null ? 0.0f : currentDisplay.getRefreshRate());
        return json;
    }

    private static JSONArray inputDevices() throws Exception {
        JSONArray devices = new JSONArray();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) {
                continue;
            }
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", device.getName());
            json.put("descriptor", device.getDescriptor());
            json.put("sources", device.getSources());
            json.put("vendorId", device.getVendorId());
            json.put("productId", device.getProductId());
            json.put("external", device.isExternal());
            json.put("virtual", device.isVirtual());

            JSONArray ranges = new JSONArray();
            for (InputDevice.MotionRange range : device.getMotionRanges()) {
                JSONObject item = new JSONObject();
                item.put("axis", range.getAxis());
                item.put("source", range.getSource());
                item.put("min", range.getMin());
                item.put("max", range.getMax());
                item.put("flat", range.getFlat());
                item.put("fuzz", range.getFuzz());
                item.put("resolution", range.getResolution());
                ranges.put(item);
            }
            json.put("motionRanges", ranges);
            devices.put(json);
        }
        return devices;
    }
}
