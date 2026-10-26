package com.easemob.sdktest;

import android.content.Intent;
import android.os.Bundle;

public final class MainActivity extends TestActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        navigation("环信 SDK 测试工具", false);
        text("先初始化和登录，再进入独立测试页面。接收监听在页面切换后继续工作。");
        button("初始化与登录", () -> startActivity(new Intent(this, LoginActivity.class)));
        button("多 bodies 消息", () -> startActivity(new Intent(this, MultiBodiesActivity.class)));
        button("API 调用", () -> startActivity(new Intent(this, ApiTestActivity.class)));
    }
}
