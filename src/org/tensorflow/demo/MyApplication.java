package org.tensorflow.demo;

import android.app.Application;
import android.content.Context;

/**
 * Created by wcxdhr on 2019/4/15.
 */

public class MyApplication extends Application {

    private static Context mContext;
    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    public static Context getContext() {
        return mContext;
    }
}
