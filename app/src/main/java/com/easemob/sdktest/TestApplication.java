package com.easemob.sdktest;

import android.app.Application;

public final class TestApplication extends Application {
    private SdkSession session;

    @Override public void onCreate() {
        super.onCreate();
        session = new SdkSession(this);
    }

    public SdkSession session() { return session; }
}
