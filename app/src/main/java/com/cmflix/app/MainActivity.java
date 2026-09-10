package com.cmflix.app;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL =
            "https://watch.cmflix.xubi.org/";

    private static final int STORAGE_PERMISSION_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout connectionErrorLayout;
    private Button retryButton;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private boolean isFullscreen = false;
    private boolean pageLoadFailed = false;
    private boolean waitingForNetwork = false;

    private String failedUrl = HOME_URL;

    /*
     * Android 9 နှင့်အောက်မှာ storage permission တောင်းနေချိန်
     * download information ကို ခဏသိမ်းထားရန်။
     */
    private String pendingDownloadUrl;
    private String pendingUserAgent;
    private String pendingContentDisposition;
    private String pendingMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // super.onCreate() မတိုင်ခင် Splash Screen တပ်ရပါမယ်။
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        connectionErrorLayout =
                findViewById(R.id.connectionErrorLayout);
        retryButton = findViewById(R.id.retryButton);

        setupWebView();
        setupBackButton();

        retryButton.setOnClickListener(view -> retryLoading());

        if (savedInstanceState == null) {

            loadHomePage();

        } else {

            webView.restoreState(savedInstanceState);

            if (isInternetAvailable()) {

                showWebView();

            } else {

                failedUrl = HOME_URL;
                waitingForNetwork = true;
                showConnectionError();
            }
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

        /*
         * target="_blank" နှင့် window.open() link တွေကို
         * ဖမ်းနိုင်ရန် လိုအပ်ပါတယ်။
         */
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

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

        webView.setWebViewClient(createMainWebViewClient());
        webView.setWebChromeClient(createWebChromeClient());

        /*
         * WebView ထဲက download link ကို Android DownloadManager
         * ဆီပို့ပေးပါမယ်။
         */
        webView.setDownloadListener(createDownloadListener());
    }

    private WebViewClient createMainWebViewClient() {

        return new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                String url =
                        request.getUrl().toString();

                return handleSpecialUrl(url);
            }

            /*
             * Android 6 အပါအဝင် အဟောင်း WebView များအတွက်။
             */
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {

                return handleSpecialUrl(url);
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon
            ) {

                super.onPageStarted(view, url, favicon);

                pageLoadFailed = false;
                failedUrl = url;

                connectionErrorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                super.onPageFinished(view, url);

                progressBar.setVisibility(View.GONE);

                if (!pageLoadFailed) {

                    waitingForNetwork = false;
                    showWebView();

                    CookieManager
                            .getInstance()
                            .flush();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {

                super.onReceivedError(
                        view,
                        request,
                        error
                );

                /*
                 * Image/CSS/JavaScript error မဟုတ်ဘဲ
                 * main page error ဖြစ်မှသာ error screen ပြပါမယ်။
                 */
                if (request.isForMainFrame()) {

                    pageLoadFailed = true;
                    failedUrl = request.getUrl().toString();

                    waitingForNetwork =
                            !isInternetAvailable();

                    view.stopLoading();

                    showConnectionError();
                }
            }

            /*
             * Android 6 အဟောင်း WebView fallback။
             */
            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl
            ) {

                super.onReceivedError(
                        view,
                        errorCode,
                        description,
                        failingUrl
                );

                pageLoadFailed = true;

                if (failingUrl != null
                        && !failingUrl.trim().isEmpty()) {

                    failedUrl = failingUrl;
                }

                waitingForNetwork =
                        !isInternetAvailable();

                view.stopLoading();

                showConnectionError();
            }
        };
    }

    private WebChromeClient createWebChromeClient() {

        return new WebChromeClient() {

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

            /*
             * Website ရဲ့ download link က target="_blank"
             * သို့မဟုတ် window.open() သုံးထားရင် ဒီနေရာကဖမ်းပါမယ်။
             */
            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg
            ) {

                WebView popupWebView =
                        new WebView(MainActivity.this);

                WebSettings popupSettings =
                        popupWebView.getSettings();

                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);

                CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(
                                popupWebView,
                                true
                        );

                popupWebView.setDownloadListener(
                        (
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType,
                                contentLength
                        ) -> {

                            startDownload(
                                    url,
                                    userAgent,
                                    contentDisposition,
                                    mimeType
                            );

                            popupWebView.destroy();
                        }
                );

                popupWebView.setWebViewClient(
                        new WebViewClient() {

                            @Override
                            public boolean shouldOverrideUrlLoading(
                                    WebView popup,
                                    WebResourceRequest request
                            ) {

                                openPopupUrl(
                                        request
                                                .getUrl()
                                                .toString(),
                                        popup
                                );

                                return true;
                            }

                            @SuppressWarnings("deprecation")
                            @Override
                            public boolean shouldOverrideUrlLoading(
                                    WebView popup,
                                    String url
                            ) {

                                openPopupUrl(
                                        url,
                                        popup
                                );

                                return true;
                            }
                        }
                );

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport)
                                resultMsg.obj;

                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();

                return true;
            }
        };
    }

    private DownloadListener createDownloadListener() {

        return (
                url,
                userAgent,
                contentDisposition,
                mimeType,
                contentLength
        ) -> startDownload(
                url,
                userAgent,
                contentDisposition,
                mimeType
        );
    }

    private void openPopupUrl(
            String url,
            WebView popupWebView
    ) {

        popupWebView.stopLoading();

        /*
         * Movie/file extension ပါတဲ့ target="_blank" link ဆိုရင်
         * WebView ထဲမဖွင့်ဘဲ တိုက်ရိုက် download လုပ်ပါမယ်။
         */
        if (isDirectDownloadUrl(url)) {

            startDownload(
                    url,
                    webView
                            .getSettings()
                            .getUserAgentString(),
                    null,
                    guessMimeType(url)
            );

        } else if (url.startsWith("http://")
                || url.startsWith("https://")) {

            webView.loadUrl(url);

        } else {

            openExternalApplication(url);
        }

        popupWebView.destroy();
    }

    private boolean isDirectDownloadUrl(String url) {

        try {

            String path =
                    Uri.parse(url).getPath();

            if (path == null) {
                return false;
            }

            path = path.toLowerCase();

            return path.endsWith(".mp4")
                    || path.endsWith(".mkv")
                    || path.endsWith(".avi")
                    || path.endsWith(".mov")
                    || path.endsWith(".webm")
                    || path.endsWith(".m4v")
                    || path.endsWith(".mp3")
                    || path.endsWith(".zip")
                    || path.endsWith(".rar")
                    || path.endsWith(".7z")
                    || path.endsWith(".apk");

        } catch (Exception exception) {

            return false;
        }
    }

    private String guessMimeType(String url) {

        String mimeType = null;

        try {

            String extension =
                    android.webkit.MimeTypeMap
                            .getFileExtensionFromUrl(url);

            if (extension != null) {

                mimeType =
                        android.webkit.MimeTypeMap
                                .getSingleton()
                                .getMimeTypeFromExtension(
                                        extension.toLowerCase()
                                );
            }

        } catch (Exception ignored) {
        }

        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        return mimeType;
    }

    private boolean handleSpecialUrl(String url) {

        if (url == null) {
            return true;
        }

        if (url.startsWith("http://")
                || url.startsWith("https://")) {

            // WebView က ပုံမှန်အတိုင်းဖွင့်မယ်။
            return false;
        }

        openExternalApplication(url);

        return true;
    }

    private void openExternalApplication(String url) {

        try {

            Intent intent;

            if (url.startsWith("intent://")) {

                intent = Intent.parseUri(
                        url,
                        Intent.URI_INTENT_SCHEME
                );

            } else {

                intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                );
            }

            startActivity(intent);

        } catch (Exception exception) {

            Toast.makeText(
                    this,
                    "ဒီ link ကိုဖွင့်နိုင်တဲ့ app မရှိပါ",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void loadHomePage() {

        failedUrl = HOME_URL;

        if (!isInternetAvailable()) {

            waitingForNetwork = true;
            showConnectionError();
            return;
        }

        waitingForNetwork = false;
        showWebView();

        webView.loadUrl(HOME_URL);
    }

    private void retryLoading() {

        if (!isInternetAvailable()) {

            waitingForNetwork = true;
            showConnectionError();

            Toast.makeText(
                    this,
                    "အင်တာနက် connection ကို စစ်ဆေးပါ",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        waitingForNetwork = false;
        pageLoadFailed = false;

        showWebView();

        String urlToLoad = failedUrl;

        if (urlToLoad == null
                || urlToLoad.trim().isEmpty()
                || urlToLoad.startsWith("about:")) {

            urlToLoad = HOME_URL;
        }

        webView.loadUrl(urlToLoad);
    }

    private void showConnectionError() {

        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        connectionErrorLayout.setVisibility(View.VISIBLE);
    }

    private void showWebView() {

        connectionErrorLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private boolean isInternetAvailable() {

        ConnectivityManager connectivityManager =
                (ConnectivityManager)
                        getSystemService(
                                Context.CONNECTIVITY_SERVICE
                        );

        if (connectivityManager == null) {
            return false;
        }

        try {

            Network activeNetwork =
                    connectivityManager.getActiveNetwork();

            if (activeNetwork == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(
                            activeNetwork
                    );

            if (capabilities == null) {
                return false;
            }

            boolean hasInternet =
                    capabilities.hasCapability(
                            NetworkCapabilities
                                    .NET_CAPABILITY_INTERNET
                    );

            boolean isValidated =
                    capabilities.hasCapability(
                            NetworkCapabilities
                                    .NET_CAPABILITY_VALIDATED
                    );

            return hasInternet && isValidated;

        } catch (Exception exception) {

            return false;
        }
    }

    private void startDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {

        if (url == null
                || (!url.startsWith("http://")
                && !url.startsWith("https://"))) {

            Toast.makeText(
                    this,
                    R.string.download_failed,
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!isInternetAvailable()) {

            Toast.makeText(
                    this,
                    "Download လုပ်ရန် အင်တာနက်လိုအပ်ပါတယ်",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * Android 9 နှင့်အောက်မှာ public Downloads folder အတွက်
         * storage permission လိုပါတယ်။
         */
        if (Build.VERSION.SDK_INT
                <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {

            pendingDownloadUrl = url;
            pendingUserAgent = userAgent;
            pendingContentDisposition = contentDisposition;
            pendingMimeType = mimeType;

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST
            );

            return;
        }

        enqueueDownload(
                url,
                userAgent,
                contentDisposition,
                mimeType
        );
    }

    private void enqueueDownload(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType
    ) {

        try {

            String fileName =
                    URLUtil.guessFileName(
                            url,
                            contentDisposition,
                            mimeType
                    );

            DownloadManager.Request request =
                    new DownloadManager.Request(
                            Uri.parse(url)
                    );

            if (mimeType != null
                    && !mimeType.trim().isEmpty()) {

                request.setMimeType(mimeType);
            }

            if (userAgent != null
                    && !userAgent.trim().isEmpty()) {

                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            /*
             * Login/session လိုအပ်တဲ့ download URL တွေအတွက်
             * WebView cookie ကို DownloadManager ဆီကူးပေးရပါမယ်။
             */
            String cookies =
                    CookieManager
                            .getInstance()
                            .getCookie(url);

            if (cookies != null
                    && !cookies.trim().isEmpty()) {

                request.addRequestHeader(
                        "Cookie",
                        cookies
                );
            }

            String referer = webView.getUrl();

            if (referer != null
                    && (referer.startsWith("http://")
                    || referer.startsWith("https://"))) {

                request.addRequestHeader(
                        "Referer",
                        referer
                );
            }

            request.setTitle(fileName);
            request.setDescription("CM FLIX download");

            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );

            DownloadManager downloadManager =
                    (DownloadManager)
                            getSystemService(
                                    Context.DOWNLOAD_SERVICE
                            );

            if (downloadManager == null) {
                throw new IllegalStateException(
                        "DownloadManager not available"
                );
            }

            downloadManager.enqueue(request);

            Toast.makeText(
                    this,
                    R.string.downloading,
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {

            Toast.makeText(
                    this,
                    R.string.download_failed,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode
                != STORAGE_PERMISSION_REQUEST) {

            return;
        }

        if (grantResults.length > 0
                && grantResults[0]
                == PackageManager.PERMISSION_GRANTED) {

            if (pendingDownloadUrl != null) {

                enqueueDownload(
                        pendingDownloadUrl,
                        pendingUserAgent,
                        pendingContentDisposition,
                        pendingMimeType
                );
            }

        } else {

            Toast.makeText(
                    this,
                    "Storage permission မပေးထားလို့ download မလုပ်နိုင်ပါ",
                    Toast.LENGTH_LONG
            ).show();
        }

        clearPendingDownload();
    }

    private void clearPendingDownload() {

        pendingDownloadUrl = null;
        pendingUserAgent = null;
        pendingContentDisposition = null;
        pendingMimeType = null;
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
                (FrameLayout)
                        getWindow()
                                .getDecorView();

        decor.addView(
                customView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        webView.setVisibility(View.GONE);
        connectionErrorLayout.setVisibility(View.GONE);

        setRequestedOrientation(
                ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE
        );

        hideSystemBars();
    }

    private void exitFullscreen() {

        if (customView == null) {
            return;
        }

        FrameLayout decor =
                (FrameLayout)
                        getWindow()
                                .getDecorView();

        decor.removeView(customView);

        customView = null;

        if (customViewCallback != null) {

            customViewCallback.onCustomViewHidden();
        }

        customViewCallback = null;
        isFullscreen = false;

        if (pageLoadFailed) {

            showConnectionError();

        } else {

            showWebView();
        }

        setRequestedOrientation(
                ActivityInfo
                        .SCREEN_ORIENTATION_UNSPECIFIED
        );

        showSystemBars();
    }

    private void hideSystemBars() {

        Window window = getWindow();

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.R) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );

                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }

        } else {

            window.getDecorView()
                    .setSystemUiVisibility(
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

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.R) {

            WindowInsetsController controller =
                    window.getInsetsController();

            if (controller != null) {

                controller.show(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );
            }

        } else {

            window.getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    );
        }
    }

    private void setupBackButton() {

        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new androidx.activity
                                .OnBackPressedCallback(true) {

                            @Override
                            public void handleOnBackPressed() {

                                if (isFullscreen) {

                                    exitFullscreen();
                                    return;
                                }

                                if (connectionErrorLayout
                                        .getVisibility()
                                        == View.VISIBLE) {

                                    finish();
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
    protected void onResume() {

        super.onResume();

        /*
         * Internet ပြတ်နေချိန် error ပြထားပြီး နောက်မှ
         * internet ပြန်ရလာရင် app ကိုပြန်ဝင်တာနဲ့ retry လုပ်ပါမယ်။
         */
        if (waitingForNetwork
                && connectionErrorLayout != null
                && connectionErrorLayout.getVisibility()
                == View.VISIBLE
                && isInternetAvailable()) {

            retryLoading();
        }
    }

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {

        if (customView != null) {
            exitFullscreen();
        }

        if (webView != null) {

            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }

        super.onDestroy();
    }
}
