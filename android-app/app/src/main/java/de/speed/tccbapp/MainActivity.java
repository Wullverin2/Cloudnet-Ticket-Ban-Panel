package de.speed.tccbapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class MainActivity extends Activity {

  private static final String PREFS_NAME = "ticket-console-cloud-ban";
  private static final String KEY_PANEL_URL = "panel_url";
  private static final String DEFAULT_PANEL_URL = "http://10.0.2.2:8088";

  private SharedPreferences preferences;
  private EditText panelUrlInput;
  private TextView statusView;
  private ProgressBar progressBar;
  private WebView webView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.preferences = this.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    this.createLayout();
    this.configureWebView();
    this.loadConfiguredPanel();
  }

  @Override
  public void onBackPressed() {
    if (this.webView != null && this.webView.canGoBack()) {
      this.webView.goBack();
      return;
    }
    super.onBackPressed();
  }

  private void createLayout() {
    var root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(246, 248, 251));
    root.setLayoutParams(new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT));

    var toolbar = new LinearLayout(this);
    toolbar.setOrientation(LinearLayout.VERTICAL);
    toolbar.setPadding(dp(14), dp(12), dp(14), dp(10));
    toolbar.setBackgroundColor(Color.WHITE);

    var title = new TextView(this);
    title.setText(R.string.app_name);
    title.setTextColor(Color.rgb(10, 22, 34));
    title.setTextSize(18);
    title.setGravity(Gravity.START);
    title.setPadding(0, 0, 0, dp(8));
    toolbar.addView(title, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    var controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setGravity(Gravity.CENTER_VERTICAL);

    this.panelUrlInput = new EditText(this);
    this.panelUrlInput.setSingleLine(true);
    this.panelUrlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
    this.panelUrlInput.setSelectAllOnFocus(true);
    this.panelUrlInput.setHint("https://panel.example.de");
    controls.addView(this.panelUrlInput, new LinearLayout.LayoutParams(
      0,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      1));

    var openButton = new Button(this);
    openButton.setText("Oeffnen");
    openButton.setAllCaps(false);
    openButton.setOnClickListener(view -> this.saveAndLoadPanel());
    controls.addView(openButton, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    var reloadButton = new Button(this);
    reloadButton.setText("Neu laden");
    reloadButton.setAllCaps(false);
    reloadButton.setOnClickListener(view -> this.webView.reload());
    controls.addView(reloadButton, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    toolbar.addView(controls, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    this.statusView = new TextView(this);
    this.statusView.setTextColor(Color.rgb(91, 103, 117));
    this.statusView.setTextSize(13);
    this.statusView.setPadding(0, dp(6), 0, 0);
    toolbar.addView(this.statusView, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    root.addView(toolbar, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT));

    this.progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    this.progressBar.setMax(100);
    this.progressBar.setVisibility(View.GONE);
    root.addView(this.progressBar, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      dp(3)));

    this.webView = new WebView(this);
    root.addView(this.webView, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      0,
      1));

    this.setContentView(root);
  }

  private void configureWebView() {
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

    var cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      cookieManager.setAcceptThirdPartyCookies(this.webView, true);
    }

    var settings = this.webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setLoadWithOverviewMode(false);
    settings.setUseWideViewPort(true);
    settings.setBuiltInZoomControls(false);
    settings.setCacheMode(WebSettings.LOAD_DEFAULT);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    this.webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int newProgress) {
        MainActivity.this.progressBar.setProgress(newProgress);
        MainActivity.this.progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
      }
    });

    this.webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        var uri = request.getUrl();
        var scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
          return false;
        }
        MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        return true;
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        MainActivity.this.setStatus("Verbunden mit " + url, false);
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) {
          MainActivity.this.setStatus("Panel konnte nicht geladen werden. Pruefe URL, Netzwerk und Port.", true);
        }
      }
    });
  }

  private void loadConfiguredPanel() {
    var panelUrl = this.preferences.getString(KEY_PANEL_URL, DEFAULT_PANEL_URL);
    this.panelUrlInput.setText(panelUrl);
    this.loadPanel(panelUrl);
  }

  private void saveAndLoadPanel() {
    var panelUrl = this.normalizePanelUrl(this.panelUrlInput.getText().toString());
    if (panelUrl == null) {
      this.setStatus("Bitte eine gueltige Panel-URL eintragen.", true);
      return;
    }

    this.preferences.edit().putString(KEY_PANEL_URL, panelUrl).apply();
    this.panelUrlInput.setText(panelUrl);
    this.loadPanel(panelUrl);
  }

  private void loadPanel(String panelUrl) {
    var normalized = this.normalizePanelUrl(panelUrl);
    if (normalized == null) {
      this.setStatus("Bitte eine gueltige Panel-URL eintragen.", true);
      return;
    }

    this.setStatus("Lade Panel ...", false);
    this.webView.loadUrl(normalized);
  }

  private String normalizePanelUrl(String value) {
    var normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      return null;
    }
    if (!normalized.contains("://")) {
      normalized = "http://" + normalized;
    }

    var uri = Uri.parse(normalized);
    var scheme = uri.getScheme();
    if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
      return null;
    }
    return normalized.replaceAll("/+$", "");
  }

  private void setStatus(String value, boolean error) {
    this.statusView.setText(value);
    this.statusView.setTextColor(error ? Color.rgb(190, 18, 60) : Color.rgb(91, 103, 117));
  }

  private int dp(int value) {
    return Math.round(value * this.getResources().getDisplayMetrics().density);
  }
}
