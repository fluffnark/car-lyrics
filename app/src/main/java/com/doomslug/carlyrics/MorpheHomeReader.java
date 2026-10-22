package com.doomslug.carlyrics;

import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.HandlerThread;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Reads only Morphe's Home cards on the helper-owned display. No cookies, accounts or files. */
final class MorpheHomeReader {
    static String read(int displayId, boolean more) throws Exception {
        HandlerThread events = new HandlerThread("Morphe Home accessibility"); events.start();
        Object connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();
        java.lang.reflect.Constructor<?> ctor = UiAutomation.class.getDeclaredConstructor(android.os.Looper.class,
            Class.forName("android.app.IUiAutomationConnection"));
        ctor.setAccessible(true);
        UiAutomation ui = (UiAutomation) ctor.newInstance(events.getLooper(), connection);
        boolean connected = false;
        try {
            UiAutomation.class.getMethod("connect", int.class).invoke(ui, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            connected = true;
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(info);
            Thread.sleep(400);
            AccessibilityNodeInfo root = root(ui, displayId);
            if (more && root != null) { scroll(root); Thread.sleep(600); }
            JSONArray cards = new JSONArray();
            for (int attempt = 0; attempt < 4; attempt++) {
                root = root(ui, displayId);
                if (root != null) collect(root, cards);
                if (cards.length() > 0) break;
                Thread.sleep(500);
            }
            return cards.toString();
        } finally {
            if (connected) try { UiAutomation.class.getMethod("disconnect").invoke(ui); } catch (Exception ignored) { }
            events.quitSafely();
        }
    }
    private static AccessibilityNodeInfo root(UiAutomation ui, int displayId) {
        if (android.os.Build.VERSION.SDK_INT < 30) return null;
        List<AccessibilityWindowInfo> windows = ui.getWindowsOnAllDisplays().get(displayId);
        if (windows == null) return null;
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root != null && "app.morphe.android.youtube".contentEquals(root.getPackageName())) return root;
        }
        return null;
    }
    private static boolean scroll(AccessibilityNodeInfo n) {
        if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null && scroll(c)) return true;
        }
        return false;
    }
    private static void collect(AccessibilityNodeInfo n, JSONArray cards) throws Exception {
        if (cards.length() >= 12) return;
        String description = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
        Rect bounds = new Rect(); n.getBoundsInScreen(bounds);
        if (n.isVisibleToUser() && n.isClickable() && description.endsWith(" - play video") && bounds.width() > 100 && bounds.height() > 60) {
            List<String> text = new ArrayList<>(); texts(n, text);
            String title = "", channel = "";
            for (String value : text) {
                if (value.matches("[0-9:]+")) continue;
                if (title.isEmpty()) title = value;
                else if (value.contains("·")) { channel = value.substring(0, value.indexOf('·')).trim(); break; }
            }
            if (title.isEmpty()) title = description.split(" - ", 2)[0];
            if (channel.isEmpty() && description.contains(" - Go to channel ")) {
                String rest = description.substring(description.indexOf(" - Go to channel ") + 17);
                channel = rest.split(" - ", 2)[0];
            }
            if (!title.isEmpty() && !channel.isEmpty()) cards.put(new JSONObject().put("title", title).put("channel", channel));
            return;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            if (child != null) collect(child, cards);
        }
    }
    private static void texts(AccessibilityNodeInfo n, List<String> text) {
        if (n.getText() != null && n.getText().length() > 0) text.add(n.getText().toString().trim());
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo c = n.getChild(i); if (c != null) texts(c, text); }
    }
}
