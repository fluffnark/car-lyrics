package com.doomslug.carlyrics.displayprobe;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Presentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;

/** Isolated platform experiment. No capture, network, accounts, or privileged permissions. */
public final class ProbeActivity extends Activity implements SurfaceHolder.Callback {
    static final String TAG = "NativeDisplayProbe";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VirtualDisplay display;
    private Presentation presentation;
    private TextView report;
    private String phase;
    private Surface nativeOutput;
    private boolean bound;
    private final ServiceConnection playerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (nativeOutput == null || !nativeOutput.isValid()) return;
            Message message = Message.obtain(null, 1);
            Bundle data = new Bundle();
            data.putParcelable("surface", nativeOutput);
            message.setData(data);
            try {
                new Messenger(binder).send(message);
                write("Surface sent to separate native player process; no virtual display or capture");
            } catch (RemoteException ex) {
                write("Surface transfer failed: " + ex);
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            write("Native player disconnected");
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        phase = getIntent().getStringExtra("phase");
        if (phase == null) phase = "plain";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 80, 24, 24);
        report = new TextView(this);
        report.setTextSize(15);
        root.addView(report);
        SurfaceView output = new SurfaceView(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                int height = MeasureSpec.getSize(heightSpec);
                if (width > height * 2) width = height * 2;
                else height = width / 2;
                setMeasuredDimension(width, height);
            }
        };
        output.getHolder().setFixedSize(800, 400);
        FrameLayout outputFrame = new FrameLayout(this);
        outputFrame.addView(output, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(outputFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        output.getHolder().addCallback(this);
        write("Phase=" + phase + "; SDK=" + android.os.Build.VERSION.SDK_INT);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (phase.equals("shell-display")) {
            SurfaceProvider.output = holder.getSurface();
            write("800x400 surface ready for shell display host; no capture");
            return;
        }
        if (phase.equals("native-video")) {
            nativeOutput = holder.getSurface();
            bound = bindService(new Intent(this, NativeVideoService.class), playerConnection, Context.BIND_AUTO_CREATE);
            write("Native video service binding=" + bound);
            return;
        }
        display = getSystemService(DisplayManager.class).createVirtualDisplay(
                "CarLyricsNativeProbe", 800, 400, 160, holder.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        write("Own-content display=" + display.getDisplay().getDisplayId());
        check("before presentation");
        if (phase.contains("presentation")) {
            presentation = new Presentation(this, display.getDisplay());
            TextView bootstrap = new TextView(presentation.getContext());
            bootstrap.setText("Native Presentation bootstrap");
            bootstrap.setTextColor(Color.WHITE);
            bootstrap.setBackgroundColor(Color.rgb(20, 65, 85));
            bootstrap.setGravity(Gravity.CENTER);
            presentation.setContentView(bootstrap);
            presentation.show();
        }
        handler.postDelayed(() -> {
            if (display == null) return;
            check("after presentation step");
            Class<?> target = phase.startsWith("embedded") ? EmbeddedActivity.class : PlainActivity.class;
            Intent intent = new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ActivityOptions options = ActivityOptions.makeBasic()
                        .setLaunchDisplayId(display.getDisplay().getDisplayId());
                startActivity(intent, options.toBundle());
                write("startActivity returned normally; confirm target display in logcat");
            } catch (RuntimeException ex) {
                write("startActivity: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }, 1000);
    }

    private void check(String label) {
        int id = display.getDisplay().getDisplayId();
        write(label + ": plain=" + allowed(id, new Intent(this, PlainActivity.class))
                + " embedded=" + allowed(id, new Intent(this, EmbeddedActivity.class)));
        Intent morphe = getPackageManager().getLaunchIntentForPackage("app.morphe.android.youtube");
        write(label + ": installed Morphe=" + (morphe == null ? "not resolved" : allowed(id, morphe)));
    }

    private boolean allowed(int id, Intent intent) {
        return getSystemService(ActivityManager.class).isActivityStartAllowedOnDisplay(this, id, intent);
    }

    private void write(String message) {
        Log.i(TAG, message);
        report.append(message + "\n");
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { release(); }
    @Override public void onDestroy() { release(); super.onDestroy(); }
    private void release() {
        handler.removeCallbacksAndMessages(null);
        SurfaceProvider.output = null;
        if (bound) { unbindService(playerConnection); bound = false; }
        nativeOutput = null;
        if (presentation != null) { presentation.dismiss(); presentation = null; }
        if (display != null) { display.release(); display = null; }
    }
}
