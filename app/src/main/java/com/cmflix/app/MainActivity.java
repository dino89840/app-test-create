package com.cmflix.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    private ValueCallback<Uri[]> filePathCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;

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

        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                String url = request.getUrl().toString();

                if (url.startsWith("http://")
                        || url.startsWith("https://")) {

                    view.loadUrl(url);
                    return true;
                }

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );

                    startActivity(intent);

                } catch (ActivityNotFoundException e) {
                    Toast.makeText(
                            MainActivity.this,
                            "Cannot open this link",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(
                    View view,
                    CustomViewCallback callback
            ) {

                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;

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

                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                );
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePath,
                    FileChooserParams fileChooserParams
            ) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = filePath;

                Intent intent;

                try {
                    intent = fileChooserParams.createIntent();
                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "File picker not available",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }

                return true;
            }
        });

        webView.setDownloadListener(
                new DownloadListener() {

                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimetype,
                            long contentLength
                    ) {

                        try {

                            DownloadManager.Request request =
                                    new DownloadManager.Request(
                                            Uri.parse(url)
                                    );

                            String cookies =
                                    CookieManager
                                            .getInstance()
                                            .getCookie(url);

                            if (cookies != null) {
                                request.addRequestHeader(
                                        "Cookie",
                                        cookies
                                );
                            }

                            request.addRequestHeader(
                                    "User-Agent",
                                    userAgent
                            );

                            request.setNotificationVisibility(
                                    DownloadManager
                                            .Request
                                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            );

                            request.setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    getFileName(
                                            url,
                                            contentDisposition
                                    )
                            );

                            DownloadManager manager =
                                    (DownloadManager)
                                            getSystemService(
                                                    Context.DOWNLOAD_SERVICE
                                            );

                            manager.enqueue(request);

                            Toast.makeText(
                                    MainActivity.this,
                                    "Download started",
                                    Toast.LENGTH_SHORT
                            ).show();

                        } catch (Exception e) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Download failed",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                });
    }

    private String getFileName(
            String url,
            String contentDisposition
    ) {

        String fileName = null;

        if (contentDisposition != null) {

            fileName =
                    android.webkit.URLUtil
                            .guessFileName(
                                    url,
                                    contentDisposition,
                                    null
                            );
        }

        if (fileName == null || fileName.isEmpty()) {

            fileName =
                    android.webkit.URLUtil
                            .guessFileName(
                                    url,
                                    null,
                                    null
                            );
        }

        if (fileName == null || fileName.isEmpty()) {
            fileName = "download";
        }

        return fileName;
    }

    private void hideFullscreen() {

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

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    private void setupBackButton() {

        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {

                            @Override
                            public void handleOnBackPressed() {

                                if (customView != null) {
                                    hideFullscreen();
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
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK
                    && data != null) {

                Uri uri = data.getData();

                if (uri != null) {
                    results = new Uri[]{uri};
                }
            }

            filePathCallback.onReceiveValue(results);

            filePathCallback = null;
        }
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

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
