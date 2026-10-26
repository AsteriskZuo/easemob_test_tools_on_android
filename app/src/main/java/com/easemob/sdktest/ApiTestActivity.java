package com.easemob.sdktest;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** API 列表页：关键字过滤（匹配名称与分组，大小写不敏感），点行进入调用页。 */
public final class ApiTestActivity extends TestActivity {
    private TextView state;
    private EditText filter;
    private LinearLayout list;
    private final Runnable refresh = this::renderState;

    @Override protected void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        navigation("API 调用", true);
        state = text("");
        text("初始化与登录在「初始化与登录」页面完成，此处共享其状态。");
        filter = input("输入关键字过滤，如 send / Chat", false);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderList(); }
            @Override public void afterTextChanged(Editable value) {}
        });
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        renderList();
    }

    @Override protected void onStart() { super.onStart(); session.observe(refresh); }
    @Override protected void onStop() { session.unobserve(refresh); super.onStop(); }

    private void renderState() { state.setText(session.stateText()); }

    private void renderList() {
        String keyword = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        list.removeAllViews();
        for (ApiRegistry.ApiEntry entry : ApiRegistry.ENTRIES) {
            if (!keyword.isEmpty()
                    && !entry.name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !entry.group.toLowerCase(Locale.ROOT).contains(keyword)) continue;
            Button row = new Button(this);
            row.setText(entry.name + "\n" + entry.group);
            row.setAllCaps(false);
            row.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            row.setTypeface(Typeface.MONOSPACE);
            row.setOnClickListener(v -> startActivity(
                    new Intent(this, ApiCallActivity.class).putExtra(ApiCallActivity.EXTRA_NAME, entry.name)));
            list.addView(row);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("无匹配 API，共注册 " + ApiRegistry.ENTRIES.size() + " 个");
            list.addView(empty);
        }
    }
}
