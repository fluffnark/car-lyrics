package com.doomslug.carlyrics;
import android.view.Surface;

interface INativeMorphe {
    int create(in Surface surface, IBinder lifetime) = 0;
    void play(String videoId, long positionMs) = 1;
    void release() = 2;
    void destroy() = 16777114;
}
