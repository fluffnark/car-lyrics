package com.doomslug.carlyrics.displayprobe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.view.Surface;

/** Lab-only Surface exchange. Only the explicitly authorized ADB shell may call it. */
public final class SurfaceProvider extends ContentProvider {
    static volatile Surface output;
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 2000) throw new SecurityException("ADB shell required");
        if (!"surface".equals(method)) throw new IllegalArgumentException("Unknown operation");
        Surface surface = output;
        if (surface == null || !surface.isValid()) throw new IllegalStateException("Open shell-display phase first");
        Bundle result = new Bundle();
        result.putParcelable("surface", surface);
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
