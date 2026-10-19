package com.easemob.sdktest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.hyphenate.*;
import com.hyphenate.chat.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application lifetime state; SDK callbacks never retain an Activity. */
final class SdkSession implements EMConnectionListener, EMMessageListener {
    final Context context;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService files = Executors.newSingleThreadExecutor();
    final List<DraftBody> drafts = new ArrayList<>();
    final List<EMMessage> messages = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final Set<Runnable> observers = new LinkedHashSet<>();
    String appKey = "", username = "", token = "", target = "";
    boolean busy, importing;
    EMMessage selected;
    private boolean listening;

    SdkSession(Context context) { this.context = context; }

    void observe(Runnable observer) { observers.add(observer); observer.run(); }
    void unobserve(Runnable observer) { observers.remove(observer); }
    void changed() { for (Runnable observer : new ArrayList<>(observers)) observer.run(); }

    String stateText() {
        EMClient client = EMClient.getInstance();
        return "初始化：" + client.isSdkInited() + "  登录：" + client.isLoggedIn()
                + "  连接：" + client.isConnected() + "\n账号：" + (client.isSdkInited() ? client.getCurrentUser() : "");
    }

    void initialize(Boolean transfer, Boolean autoDownload, Boolean https, Boolean debug) {
        if (busy) return;
        EMClient client = EMClient.getInstance();
        if (client.isSdkInited()) { log("当前进程已初始化；修改初始化配置请重启 App"); return; }
        if (appKey.trim().isEmpty()) { log("请填写 AppKey"); return; }
        try {
            EMOptions options = new EMOptions();
            options.setAppKey(appKey.trim());
            if (transfer != null) options.setAutoTransferMessageAttachments(transfer);
            if (autoDownload != null) options.setAutoDownloadThumbnail(autoDownload);
            if (https != null) options.setUsingHttpsOnly(https);
            client.init(context, options);
            if (debug != null) client.setDebugMode(debug);
            if (!listening) {
                client.addConnectionListener(this);
                client.chatManager().addMessageListener(this);
                listening = true;
            }
            log("初始化完成；已注册常驻连接、消息和附件状态监听");
        } catch (RuntimeException | LinkageError error) {
            log("初始化失败：" + error.getClass().getSimpleName() + "；请检查 SDK/ABI 构建与配置");
        }
    }

    void login() {
        EMClient client = EMClient.getInstance();
        if (busy || !client.isSdkInited()) { log("请先初始化 SDK"); return; }
        if (client.isLoggedIn()) { log("当前已登录；切换账号请先退出"); return; }
        if (username.trim().isEmpty() || token.trim().isEmpty()) { log("请填写用户名与 Token"); return; }
        busy = true;
        messages.clear();
        selected = null;
        log("开始 Token 登录：" + username.trim());
        client.loginWithToken(username.trim(), token.trim(), new EMCallBack() {
            @Override public void onSuccess() { main.post(() -> { busy = false; token = ""; log("登录成功"); }); }
            @Override public void onError(int code, String error) {
                main.post(() -> { busy = false; log("登录失败 error=" + code); });
            }
        });
    }

    void logout() {
        if (busy || !EMClient.getInstance().isLoggedIn()) return;
        busy = true;
        EMClient.getInstance().logout(false, new EMCallBack() {
            @Override public void onSuccess() {
                main.post(() -> { busy = false; selected = null; messages.clear(); token = ""; log("已退出登录"); });
            }
            @Override public void onError(int code, String error) {
                main.post(() -> { busy = false; log("退出失败 error=" + code); });
            }
        });
    }

    void log(String text) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> log(text)); return; }
        Log.i("SDKDemo", text);
        logs.add(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + " " + text);
        while (logs.size() > 200) logs.remove(0);
        changed();
    }

    String logsText() { return android.text.TextUtils.join("\n", logs); }
    void clearLogs() { logs.clear(); changed(); }

    void remember(EMMessage message) {
        for (Iterator<EMMessage> it = messages.iterator(); it.hasNext();) {
            EMMessage item = it.next();
            if (item == message || item.getMsgId().equals(message.getMsgId())) it.remove();
        }
        messages.add(0, message);
        while (messages.size() > 30) messages.remove(messages.size() - 1);
        selected = message;
        context.getSharedPreferences("test-messages", Context.MODE_PRIVATE).edit()
                .putString(accountKey(), message.getMsgId()).apply();
    }

    String recentMessageId() {
        return context.getSharedPreferences("test-messages", Context.MODE_PRIVATE).getString(accountKey(), "");
    }

    private String accountKey() {
        EMClient client = EMClient.getInstance();
        return appKey + "/" + (client.isSdkInited() ? client.getCurrentUser() : "");
    }

    @Override public void onConnected() { log("连接成功"); }
    @Override public void onDisconnected(int code) { log("连接断开 error=" + code); }
    @Override public void onTokenExpired() { log("Token 已过期，请更新 Token 后登录"); }
    @Override public void onMessageReceived(List<EMMessage> received) {
        main.post(() -> {
            for (EMMessage message : received) {
                remember(message);
                log("收到消息\n" + MessageDetails.describe(message));
            }
        });
    }
    @Override public void onMessageChanged(EMMessage message, Object change) {
        main.post(() -> { remember(message); log("附件/消息状态变化\n" + MessageDetails.describe(message)); });
    }

    static final class DraftBody {
        final String kind, value, name;
        final long size;
        DraftBody(String kind, String value, String name, long size) {
            this.kind = kind; this.value = value; this.name = name; this.size = size;
        }
        String describe() { return kind.equals("TEXT") ? "TEXT " + value : kind + " " + name + " (" + size + " bytes)\n" + value; }
    }
}
