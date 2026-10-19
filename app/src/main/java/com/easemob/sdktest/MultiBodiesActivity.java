package com.easemob.sdktest;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import com.hyphenate.EMMessageBodyCallBack;
import com.hyphenate.chat.*;
import java.io.File;
import java.util.*;

public final class MultiBodiesActivity extends TestActivity {
    private static final int PICK_FILES = 10, PICK_IMAGES = 11;
    private EditText target, textInput, pathInput, messageId, bodyIndex;
    private Spinner chatType, resource;
    private LinearLayout drafts, received;
    private TextView state, details, logs;
    private Button sendButton;
    private final Runnable refresh = () -> render();

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        navigation("多 bodies 消息", true);
        state = text("");
        button("打开初始化与登录", () -> startActivity(new Intent(this, LoginActivity.class)));
        target = input("目标用户 / 群 / 聊天室 ID", false);
        target.setText(session.target);
        chatType = choices("单聊", "群聊（需已加入）", "聊天室（需已加入）");
        textInput = input("输入一段文本，可反复添加", true);
        button("添加文本 body", () -> {
            String text = textInput.getText().toString();
            if (text.isEmpty()) { session.log("文本不能为空"); return; }
            session.drafts.add(new SdkSession.DraftBody("TEXT", text, "", 0));
            textInput.setText(""); session.changed();
        });
        button("选择多个文件", () -> selectFiles(false));
        button("选择多个图片文件", () -> selectFiles(true));
        pathInput = input("粘贴本设备可读路径 / content URI，每行一个", true);
        button("按文件添加路径", () -> addPaths(false));
        button("按图片添加路径", () -> addPaths(true));
        text("待发送列表：按下标顺序构成一条消息");
        drafts = new LinearLayout(this); drafts.setOrientation(LinearLayout.VERTICAL); page.addView(drafts);
        button("复制草稿文件路径", () -> {
            List<String> paths = new ArrayList<>();
            for (SdkSession.DraftBody draft : session.drafts) if (!draft.kind.equals("TEXT")) paths.add(draft.value);
            copy("路径", android.text.TextUtils.join("\n", paths));
        });
        button("清空草稿", () -> { session.drafts.clear(); session.changed(); });
        sendButton = button("发送这一条消息", this::send);
        text("最近发送 / 接收的消息（点击选择）");
        received = new LinearLayout(this); received.setOrientation(LinearLayout.VERTICAL); page.addView(received);
        messageId = input("消息 ID（支持重启后读库）", false);
        messageId.setText(session.recentMessageId());
        button("按 ID 从本地读取", this::load);
        button("保存所选消息", () -> {
            if (!ready() || session.selected == null) return;
            EMMessage message = session.selected;
            session.files.execute(() -> {
                EMClient.getInstance().chatManager().saveMessage(message);
                session.log("已调用保存；请按 ID 读取并重启比对 messageId=" + message.getMsgId());
            });
        });
        details = text("");
        button("复制所选消息文件路径", () -> {
            if (session.selected != null) copy("路径", MessageDetails.paths(session.selected));
        });
        bodyIndex = input("bodyIndex 留空=整条；填写原始下标=指定 body", false);
        bodyIndex.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        resource = choices("原附件", "缩略图 / 语音", "图片大图");
        button("下载 / 重试所选资源", this::download);
        text("操作和接收日志");
        button("复制日志", () -> copy("日志", session.logsText()));
        button("清空日志", () -> session.clearLogs());
        logs = logView();
    }

    @Override protected void onStart() { super.onStart(); session.observe(refresh); }
    @Override protected void onStop() {
        session.target = target.getText().toString(); session.unobserve(refresh); super.onStop();
    }

    private void render() {
        state.setText(session.stateText());
        logs.setText(session.logsText());
        sendButton.setEnabled(!session.busy && !session.importing && !session.drafts.isEmpty());
        drafts.removeAllViews();
        for (int i = 0; i < session.drafts.size(); i++) {
            final int index = i;
            TextView label = new TextView(this);
            label.setText("[" + i + "] " + session.drafts.get(i).describe());
            label.setTextIsSelectable(true); drafts.addView(label);
            LinearLayout actions = new LinearLayout(this); drafts.addView(actions);
            action(actions, "上移", () -> move(index, -1)).setEnabled(index > 0);
            action(actions, "下移", () -> move(index, 1)).setEnabled(index + 1 < session.drafts.size());
            action(actions, "删除", () -> { session.drafts.remove(index); session.changed(); });
            if (!session.drafts.get(i).kind.equals("TEXT")) action(actions, "复制路径", () -> copy("路径", session.drafts.get(index).value));
        }
        received.removeAllViews();
        for (EMMessage message : session.messages) {
            action(received, message.getMsgId() + " · " + message.getBodies().size() + " bodies", () -> {
                session.selected = message; messageId.setText(message.getMsgId()); session.changed();
            });
        }
        details.setText(session.selected == null ? "暂无所选消息" : MessageDetails.describe(session.selected));
    }

    private Button action(LinearLayout parent, String label, Runnable block) {
        Button button = new Button(this); button.setText(label); button.setTextSize(12);
        button.setOnClickListener(v -> block.run()); parent.addView(button); return button;
    }
    private void move(int index, int delta) { Collections.swap(session.drafts, index, index + delta); session.changed(); }

    private void selectFiles(boolean images) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(images ? "image/*" : "*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, images ? PICK_IMAGES : PICK_FILES);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || (request != PICK_FILES && request != PICK_IMAGES)) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        FileImports.add(session, uris, request == PICK_IMAGES);
    }

    private void addPaths(boolean images) {
        List<Uri> uris = new ArrayList<>();
        for (String line : pathInput.getText().toString().split("\\r?\\n")) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            uris.add(value.startsWith("/") ? Uri.fromFile(new File(value)) : Uri.parse(value));
        }
        if (uris.isEmpty()) { session.log("请填写本设备可读的路径"); return; }
        FileImports.add(session, uris, images); pathInput.setText("");
    }

    private boolean ready() {
        if (!EMClient.getInstance().isSdkInited() || !EMClient.getInstance().isLoggedIn()) {
            session.log("请先初始化并登录"); return false;
        }
        return true;
    }

    private void send() {
        if (!ready() || session.busy || session.importing) return;
        String to = target.getText().toString().trim();
        if (to.isEmpty() || session.drafts.isEmpty()) { session.log("请填写目标并添加 body"); return; }
        try {
            List<EMMessageBody> bodies = new ArrayList<>();
            for (SdkSession.DraftBody draft : session.drafts) {
                if (draft.kind.equals("TEXT")) bodies.add(new EMTextMessageBody(draft.value));
                else if (draft.kind.equals("IMAGE")) {
                    EMImageMessageBody image = new EMImageMessageBody(Uri.fromFile(new File(draft.value)));
                    image.setSendOriginalImage(true); bodies.add(image);
                } else bodies.add(new EMNormalFileMessageBody(new File(draft.value)));
            }
            EMMessage message = EMMessage.createSendMessage(to, bodies);
            message.setChatType(EMMessage.ChatType.values()[chatType.getSelectedItemPosition()]);
            session.busy = true;
            session.remember(message);
            session.log("开始发送\n" + MessageDetails.describe(message));
            message.setMessageStatusCallback(callback(session, message, "发送", true));
            EMClient.getInstance().chatManager().sendMessage(message);
        } catch (RuntimeException error) {
            session.busy = false; session.log("构造/发送失败：" + error.getMessage());
        }
    }

    private static EMMessageBodyCallBack callback(SdkSession shared, EMMessage message, String operation, boolean sending) {
        // Capture application state, not the Activity: a request may finish after navigating away.
        return new EMMessageBodyCallBack() {
            @Override public void onBodyProgress(int progress, int index) {
                shared.log(operation + " messageId=" + message.getMsgId() + " bodyIndex=" + index + " progress=" + progress);
            }
            @Override public void onBodyComplete(int index, int code, String error) {
                shared.log(operation + " body终态 messageId=" + message.getMsgId() + " bodyIndex=" + index + " error=" + code);
            }
            @Override public void onSuccess() { finish(0); }
            @Override public void onError(int code, String error) { finish(code); }
            private void finish(int code) {
                shared.main.post(() -> {
                    if (sending) shared.busy = false;
                    shared.remember(message);
                    shared.log(operation + " 整次完成 error=" + code + "\n" + MessageDetails.describe(message));
                });
            }
        };
    }

    private void load() {
        if (!ready()) return;
        String id = messageId.getText().toString().trim();
        if (id.isEmpty()) id = session.recentMessageId();
        final String lookup = id;
        session.files.execute(() -> {
            EMMessage message = EMClient.getInstance().chatManager().getMessage(lookup);
            session.main.post(() -> {
                if (message == null) { session.log("本地未找到 messageId=" + lookup); return; }
                session.remember(message); session.log("本地读取\n" + MessageDetails.describe(message));
            });
        });
    }

    private void download() {
        if (!ready() || session.selected == null) return;
        EMMessage message = session.selected;
        String input = bodyIndex.getText().toString().trim();
        Integer index = null;
        if (!input.isEmpty()) {
            try { index = Integer.parseInt(input); }
            catch (NumberFormatException error) { session.log("bodyIndex 必须是整数"); return; }
            if (index < 0 || index >= message.getBodies().size()) { session.log("bodyIndex 超出列表范围"); return; }
        }
        EMChatManager manager = EMClient.getInstance().chatManager();
        String operation = "下载#" + System.nanoTime() + " resource=" + resource.getSelectedItemPosition();
        EMMessageBodyCallBack callback = callback(session, message, operation, false);
        session.log(operation + " messageId=" + message.getMsgId() + " bodyIndex=" + (index == null ? "全部" : index));
        switch (resource.getSelectedItemPosition()) {
            case 0:
                if (index == null) manager.downloadAttachment(message, callback);
                else manager.downloadAttachment(message, index, callback);
                break;
            case 1:
                if (index == null) manager.downloadThumbnail(message, callback);
                else manager.downloadThumbnail(message, index, callback);
                break;
            default:
                if (index == null) manager.downloadBigImage(message, callback);
                else manager.downloadBigImage(message, index, callback);
        }
    }
}
