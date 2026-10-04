package com.doomslug.carlyrics;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Surface;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Shizuku user service. Only the owning app can request a fixed Morphe display or video URL. */
public final class NativeMorpheService extends INativeMorphe.Stub {
    private final int ownerUid;
    private final HandlerThread thread = new HandlerThread("Native Morphe host");
    private final Handler handler;
    private VirtualDisplay display;
    private Surface surface;
    private IBinder lifetime;
    private final IBinder.DeathRecipient death = () -> handlerExit();

    public NativeMorpheService(Context context) {
        ownerUid = context.getApplicationInfo().uid;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    private void checkCaller() {
        if (Binder.getCallingUid() != ownerUid) throw new SecurityException("Car Lyrics caller required");
    }

    private <T> T run(Callable<T> operation) {
        checkCaller();
        FutureTask<T> task = new FutureTask<>(operation);
        handler.post(task);
        try { return task.get(8, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new IllegalStateException("Native Morphe: " + failure.getMessage(), failure); }
    }

    @Override public int create(Surface input, IBinder client) {
        return run(() -> {
            closeDisplay();
            if (input == null || !input.isValid() || client == null) throw new IllegalArgumentException("Live video surface required");
            // A new car session or repair must not inherit Morphe's compact,
            // stopped player from an old display. App data/login are preserved.
            Process stop = new ProcessBuilder("/system/bin/am", "force-stop", "app.morphe.android.youtube").start();
            if (!stop.waitFor(3, TimeUnit.SECONDS)) { stop.destroy(); throw new IllegalStateException("Morphe restart timed out"); }
            if (stop.exitValue() != 0) throw new IllegalStateException("Morphe restart failed");
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object current = activityThread.getMethod("currentActivityThread").invoke(null);
            if (current == null) current = activityThread.getMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getMethod("getSystemContext").invoke(current);
            Context shell = system.createPackageContext("com.android.shell", 0);
            int trusted = DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_TRUSTED").getInt(null);
            // Morphe must not steal the phone/AA input focus when its Activity opens.
            int ownFocus = DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS").getInt(null);
            int keepFocus = DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED").getInt(null);
            // End this display's Activity when repairing/disconnecting, rather
            // than relocating its old task onto the passenger's phone display.
            int destroyContent = DisplayManager.class.getField("VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL").getInt(null);
            surface = input;
            lifetime = client;
            lifetime.linkToDeath(death, 0);
            display = shell.getSystemService(DisplayManager.class).createVirtualDisplay(
                "Car Lyrics native Morphe", 1280, 720, 160, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | trusted | ownFocus | keepFocus | destroyContent, null, handler);
            if (display == null) throw new IllegalStateException("Display creation failed");
            return display.getDisplay().getDisplayId();
        });
    }

    @Override public void play(String videoId, long positionMs) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Invalid video ID");
        run(() -> {
            if (display == null) throw new IllegalStateException("No native display");
            String url = "https://www.youtube.com/watch?v=" + videoId + "&t=" + Math.max(0, positionMs / 1000) + "s";
            Process command = new ProcessBuilder("/system/bin/am", "start", "--display",
                Integer.toString(display.getDisplay().getDisplayId()), "-a", "android.intent.action.VIEW", "-n",
                "app.morphe.android.youtube/com.google.android.apps.youtube.app.application.Shell_UrlActivity",
                "-f", "0x10000000", "-d", url).redirectErrorStream(true).start();
            if (!command.waitFor(5, TimeUnit.SECONDS)) { command.destroy(); throw new IllegalStateException("Morphe launch timed out"); }
            String output = new String(command.getInputStream().readAllBytes());
            if (command.exitValue() != 0 || output.contains("Error:")) throw new IllegalStateException(output);
            return null;
        });
    }

    private void closeDisplay() {
        if (lifetime != null) { lifetime.unlinkToDeath(death, 0); lifetime = null; }
        if (display != null) { display.release(); display = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
    @Override public void release() { run(() -> { closeDisplay(); return null; }); }
    private void handlerExit() { handler.post(() -> { closeDisplay(); System.exit(0); }); }
    // Shizuku itself invokes this transaction when unbinding/updating the user service.
    @Override public void destroy() {
        int caller = Binder.getCallingUid();
        if (caller != ownerUid && caller != 2000 && caller != 0) throw new SecurityException("Unauthorized shutdown");
        handlerExit();
    }
}
