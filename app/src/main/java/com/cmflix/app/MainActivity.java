package com.cmflix.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private boolean isFullscreen = false;

    private static final String HOME_URL =
            "https://kkflix.xubi.org/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        setupWebView();
        setupBackButton();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(
                webView,
                true
        );

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        String url =
                                request.getUrl().toString();

                        if (url.startsWith("http://")
                                || url.startsWith("https://")) {

                            return false;
                        }

                        return true;
                    }

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            android.graphics.Bitmap favicon
                    ) {

                        progressBar.setVisibility(
                                View.VISIBLE
                        );
                    }

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        progressBar.setVisibility(
                                View.GONE
                        );
                    }
                }
        );

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public void onShowCustomView(
                            View view,
                            CustomViewCallback callback
                    ) {

                        enterFullscreen(
                                view,
                                callback
                        );
                    }

                    @Override
                    public void onHideCustomView() {

                        exitFullscreen();
                    }
                }
        );
    }

    private void enterFullscreen(
            View view,
            WebChromeClient.CustomViewCallback callback
    ) {

        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }

        customView = view;
        customViewCallback = callback;
        isFullscreen = true;

        FrameLayout decor =
                (FrameLayout) getWindow()
                        .getDecorView();

        decor.addView(
                customView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        webView.setVisibility(View.GONE);

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        );

        hideSystemBars();
    }

    private void exitFullscreen() {

        if (customView == null) {
            return;
        }

        FrameLayout decor =
                (FrameLayout) getWindow()
                        .getDecorView();

        decor.removeView(customView);

        customView = null;

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        customViewCallback = null;

        webView.setVisibility(View.VISIBLE);

        isFullscreen = false;

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        );

        showSystemBars();
    }

    private void hideSystemBars() {

        Window window = getWindow();

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.R) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                                | WindowInsets.Type.systemBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }

        } else {

            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void showSystemBars() {

        Window window = getWindow();

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.R) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {
                controller.show(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );
            }

        } else {

            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void setupBackButton() {

        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {

                            @Override
                            public void handleOnBackPressed() {

                                if (isFullscreen) {

                                    exitFullscreen();
                                    return;
                                }

                                if (webView.canGoBack()) {

                                    webView.goBack();

                                } else {

                                    finish();
                                }
                            }
                        }
                );
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        webView.saveState(outState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {

        if (customView != null) {
            exitFullscreen();
        }

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
