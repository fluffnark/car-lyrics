package com.doomslug.carlyrics.displayprobe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

public class PlainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int displayId = getDisplay().getDisplayId();
        String result = getClass().getSimpleName() + " running natively on display " + displayId;
        Log.i(ProbeActivity.TAG, result);
        TextView content = new TextView(this);
        content.setText(result + "\nNo MediaProjection");
        content.setGravity(Gravity.CENTER);
        content.setTextSize(26);
        content.setTextColor(Color.WHITE);
        content.setBackgroundColor(Color.rgb(35, 90, 45));
        setContentView(content);
    }
}
