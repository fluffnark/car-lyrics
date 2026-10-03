package com.doomslug.carlyrics.displayprobe;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.util.Log;
import android.view.Surface;

/** Tests a native decoder writing to a Surface received from another process. */
public final class NativeVideoService extends Service {
    private MediaPlayer player;
    private Surface surface;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what != 1) return false;
        release();
        surface = message.getData().getParcelable("surface");
        if (surface == null || !surface.isValid()) return true;
        player = MediaPlayer.create(this, R.raw.native_probe);
        player.setSurface(surface);
        player.setLooping(true);
        player.setVolume(0, 0);
        player.setOnErrorListener((mp, what, extra) -> {
            Log.e(ProbeActivity.TAG, "Native player error=" + what + ", extra=" + extra);
            return true;
        });
        player.start();
        Log.i(ProbeActivity.TAG, "Native video started in pid=" + android.os.Process.myPid()
                + " size=" + player.getVideoWidth() + "x" + player.getVideoHeight());
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (player == null) return;
                Log.i(ProbeActivity.TAG, "Native video position=" + player.getCurrentPosition());
                handler.postDelayed(this, 2000);
            }
        }, 2000);
        return true;
    }));

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }
    @Override public void onDestroy() { release(); super.onDestroy(); }
    private void release() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
}
