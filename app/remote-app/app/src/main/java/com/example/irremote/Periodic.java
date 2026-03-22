package com.example.irremote;

import android.os.Handler;
import android.os.Looper;

/**
 * A utility class to execute a Runnable task repeatedly at a fixed interval.
 */
public class Periodic {
    private final int period;
    private final Runnable runnable;
    private Runnable toPost;
    private final Handler handler;

    Periodic(int period, Looper looper, Runnable runnable){
        this.runnable = runnable;
        this.period = period;
        this.handler = new Handler(looper);
    }

    void start(){
        toPost = new Runnable() {
            @Override
            public void run() {
                runnable.run();
                handler.postDelayed(this, period);
            }
        };
        handler.post(toPost);
    }

    void finish(){
        handler.removeCallbacks(toPost);
    }
}
