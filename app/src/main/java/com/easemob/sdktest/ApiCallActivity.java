package com.easemob.sdktest;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;
import org.json.JSONObject;

/** API 调用页：JSON 参数预填必填字段模板，结果为格式化 JSON（可选择、复制），同时输出到 Logcat（APITester）。 */
public final class ApiCallActivity extends TestActivity {
    static final String EXTRA_NAME = "apiName";

    private ApiRegistry.ApiEntry entry;
    private EditText paramsInput;
    private TextView resultView;
    private Button invokeButton, copyButton;
    private boolean running;

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        entry = ApiRegistry.find(String.valueOf(getIntent().getStringExtra(EXTRA_NAME)));
        if (entry == null) { finish(); return; }
        navigation(entry.name, true);
        text(entry.detail);
        text("参数 JSON（必填字段模板）");
        paramsInput = input("参数 JSON", true);
        paramsInput.setText(entry.paramsTemplate);
        paramsInput.setTypeface(Typeface.MONOSPACE);
        paramsInput.setMinLines(4);
        invokeButton = button("调用", this::invoke);
        copyButton = button("复制结果", () -> copy("结果", resultView.getText().toString()));
        copyButton.setEnabled(false);
        text("结果");
        resultView = text("");
        resultView.setTypeface(Typeface.MONOSPACE);
        resultView.setTextSize(12);
    }

    private void invoke() {
        if (running) return;
        JSONObject params;
        try {
            params = new JSONObject(paramsInput.getText().toString());
        } catch (JSONException error) {
            showResult(ApiRegistry.failure(-1, "参数必须是合法 JSON Object：" + error.getMessage()));
            return;
        }
        running = true;
        invokeButton.setEnabled(false);
        resultView.setText("调用中…");
        SdkSession.logChunked("APITester", "api." + entry.name + " params=" + params);
        JSONObject finalParams = params;
        AtomicBoolean delivered = new AtomicBoolean();
        ApiRegistry.Result once = result -> {
            if (delivered.compareAndSet(false, true)) session.main.post(() -> showResult(result));
        };
        ApiRegistry.executor.execute(() -> {
            try {
                entry.invoker.invoke(finalParams, once);
            } catch (Throwable error) {
                once.accept(ApiRegistry.failure(error));
            }
        });
    }

    private void showResult(JSONObject result) {
        running = false;
        invokeButton.setEnabled(true);
        String text;
        try {
            text = result.toString(2);
        } catch (JSONException error) {
            text = result.toString();
        }
        SdkSession.logChunked("APITester", "api." + entry.name + " " + text);
        resultView.setText(text);
        copyButton.setEnabled(true);
    }
}
