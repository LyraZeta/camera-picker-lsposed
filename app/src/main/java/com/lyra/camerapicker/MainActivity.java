package com.lyra.camerapicker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small companion screen for installation checks and a standalone picker test. */
public final class MainActivity extends Activity {
    private static final int REQUEST_TEST = 701;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.screen_title));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(R.string.screen_title);
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(this);
        description.setText(R.string.description);
        description.setTextSize(16);
        description.setPadding(0, padding / 2, 0, padding / 2);
        root.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView scope = new TextView(this);
        scope.setText(R.string.scope_hint);
        scope.setTextSize(14);
        root.addView(scope, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button test = new Button(this);
        test.setText(R.string.test_button);
        test.setOnClickListener(view -> startActivityForResult(
                new Intent(this, PickerActivity.class), REQUEST_TEST));
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testParams.topMargin = padding;
        root.addView(test, testParams);

        resultView = new TextView(this);
        resultView.setGravity(Gravity.CENTER_VERTICAL);
        resultView.setTextSize(13);
        resultView.setPadding(0, padding / 2, 0, 0);
        root.addView(resultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TEST || resultView == null) {
            return;
        }
        Uri uri = data == null ? null : data.getData();
        resultView.setText(uri == null
                ? getString(R.string.picker_cancelled)
                : getString(R.string.test_result, uri.toString()));
    }
}
