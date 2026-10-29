package com.easemob.sdktest;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

/** Small shared UI helpers; each test keeps its inputs and actions in its own Activity. */
abstract class TestActivity extends Activity {
    LinearLayout page;
    SdkSession session;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = ((TestApplication) getApplication()).session();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setMinimumHeight(dp(56));
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(content);
        setActionBar(toolbar);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    void navigation(String title, boolean showBack) {
        setTitle(title);
        getActionBar().setDisplayHomeAsUpEnabled(showBack);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    TextView text(String value) { return text(page, value); }

    TextView text(LinearLayout parent, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextIsSelectable(true);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view);
        return view;
    }

    EditText input(String hint, boolean multiline) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setSingleLine(!multiline);
        page.addView(view);
        return view;
    }

    Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        page.addView(button);
        return button;
    }

    Spinner choices(String... options) { return choices(page, options); }

    Spinner choices(LinearLayout parent, String... options) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        parent.addView(spinner);
        return spinner;
    }

    TextView logView() {
        ScrollView scroll = new ScrollView(this);
        TextView view = new TextView(this);
        view.setTextIsSelectable(true);
        view.setTextSize(12);
        scroll.addView(view);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, dp(240)));
        return view;
    }

    void copy(String label, String value) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "已复制" + label, Toast.LENGTH_SHORT).show();
    }
}
