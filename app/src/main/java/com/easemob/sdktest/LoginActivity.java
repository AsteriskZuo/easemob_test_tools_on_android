package com.easemob.sdktest;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.*;
import com.hyphenate.chat.EMClient;

public final class LoginActivity extends TestActivity {
    private TextView status, logs;
    private EditText appKey, username, token;
    private Spinner transfer, autoDownload, https, debug;
    private final Runnable refresh = () -> {
        status.setText(session.stateText());
        logs.setText(session.logsText());
        boolean editable = !EMClient.getInstance().isSdkInited();
        appKey.setEnabled(editable);
        transfer.setEnabled(editable); autoDownload.setEnabled(editable);
        https.setEnabled(editable); debug.setEnabled(editable);
        if (EMClient.getInstance().isLoggedIn() && token.length() > 0) token.setText("");
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        navigation("初始化与登录", true);
        status = text("");
        appKey = input("AppKey：org#app", false);
        username = input("用户名", false);
        token = input("用户 Token（粘贴）", false);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setSaveEnabled(false);
        appKey.setText(session.appKey); username.setText(session.username); token.setText(session.token);
        bind(appKey, value -> session.appKey = value);
        bind(username, value -> session.username = value);
        bind(token, value -> session.token = value);
        text("可选配置：默认不覆盖 SDK 值。初始化后修改需重启。");
        transfer = choices("附件传输：默认", "启用", "禁用");
        autoDownload = choices("缩略图/语音自动下载：默认", "启用", "禁用");
        https = choices("仅 HTTPS：默认", "启用", "禁用");
        debug = choices("SDK 调试日志：默认", "启用", "禁用");
        button("初始化", () -> session.initialize(value(transfer), value(autoDownload), value(https), value(debug)));
        button("登录", () -> session.login());
        button("退出登录", () -> session.logout());
        text("登录与接收日志（切换页面继续接收）");
        button("复制日志", () -> copy("日志", session.logsText()));
        button("清空日志", () -> session.clearLogs());
        logs = logView();
    }

    @Override protected void onStart() { super.onStart(); session.observe(refresh); }
    @Override protected void onStop() { session.unobserve(refresh); super.onStop(); }
    private Boolean value(Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        return position == 0 ? null : position == 1;
    }
    interface InputChange { void accept(String value); }
    private void bind(EditText field, InputChange change) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { change.accept(s.toString()); }
            @Override public void afterTextChanged(Editable value) {}
        });
    }
}
