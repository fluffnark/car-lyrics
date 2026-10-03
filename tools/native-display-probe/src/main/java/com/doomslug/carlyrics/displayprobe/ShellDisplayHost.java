package com.doomslug.carlyrics.displayprobe;

import android.content.Context;
import android.content.AttributionSource;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Looper;
import android.view.Surface;

/** Run only via authorized adb/app_process. Models the display work of a Shizuku user service. */
public final class ShellDisplayHost {
    private static VirtualDisplay display;
    private static final IBinder providerToken = new Binder();
    public static void main(String[] args) {
        try { run(); } catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }
    private static void run() throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("ADB shell UID required");
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context systemContext = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context shellContext = systemContext.createPackageContext("com.android.shell", 0);
        // Shell processes have no registered application thread: acquire an external provider.
        String authority = "com.doomslug.carlyrics.displayprobe.surface";
        Object manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Object holder = Class.forName("android.app.IActivityManager").getMethod(
                "getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                .invoke(manager, authority, 0, providerToken, "NativeDisplayProbe");
        Object provider = holder.getClass().getField("provider").get(holder);
        AttributionSource attribution = new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        Bundle result = (Bundle) Class.forName("android.content.IContentProvider").getMethod(
                "call", AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                .invoke(provider, attribution, authority, "surface", null, null);
        Surface surface = result.getParcelable("surface");
        int trusted = DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_TRUSTED").getInt(null);
        display = shellContext.getSystemService(DisplayManager.class).createVirtualDisplay(
                "CarLyricsNativeShellProbe", 800, 400, 160, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | trusted);
        System.out.println("Native shell display=" + display.getDisplay().getDisplayId()
                + " uid=" + android.os.Process.myUid() + " flags=" + display.getDisplay().getFlags());
        System.out.flush();
        Looper.loop();
    }
}
