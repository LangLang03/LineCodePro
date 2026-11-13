package cn.lineai.terminalprovider;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import java.io.File;

public final class MainActivity extends Activity {
    private ITerminalProviderService service;
    private boolean bound = false;
    private TextView logView;
    private TextView statusView;
    private final StringBuilder logBuilder = new StringBuilder();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ITerminalProviderService.Stub.asInterface(binder);
            bound = true;
            updateStatus();
            appendLog("service bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            updateStatus();
            appendLog("service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        TerminalTheme.Palette theme = TerminalTheme.Palette.of(dark);
        getWindow().setStatusBarColor(theme.bg);getWindow().setNavigationBarColor(theme.bg);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scrollView = new ScrollView(this);scrollView.setBackgroundColor(theme.bg);scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TerminalTheme.padding(root,28,28,28,32);scrollView.addView(root);
        TextView title=TerminalTheme.textMedium(this,getString(R.string.app_name),26,theme.text);
        root.addView(title);
        statusView=TerminalTheme.text(this,"",14,theme.textSecondary,android.graphics.Typeface.NORMAL);
        TerminalTheme.padding(statusView,0,16,0,28);root.addView(statusView);
        addAction(theme,root,R.string.bind_service, true,this::bindLocalService);
        addAction(theme,root,R.string.test_identity,false,()->testShell("whoami"));
        addAction(theme,root,R.string.test_files,false,()->testShell("ls /"));
        addAction(theme,root,R.string.provider_info,false,this::testProviderInfo);
        LinearLayout logHeader=new LinearLayout(this);logHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TerminalTheme.padding(logHeader,0,24,0,12);
        logHeader.addView(TerminalTheme.textMedium(this,getString(R.string.logs),16,theme.text),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=TerminalTheme.text(this,getString(R.string.clear_logs),14,theme.textSecondary,android.graphics.Typeface.NORMAL);
        clear.setMinimumHeight(TerminalTheme.dp(this,48));clear.setGravity(android.view.Gravity.CENTER_VERTICAL);
        clear.setOnClickListener(v->{logBuilder.setLength(0);logView.setText("");});logHeader.addView(clear);root.addView(logHeader);
        logView=TerminalTheme.text(this,"",13,theme.text,android.graphics.Typeface.NORMAL);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);logView.setTextIsSelectable(true);
        logView.setLineSpacing(TerminalTheme.dp(this,6),1);logView.setBackground(TerminalTheme.rounded(this,theme.inputBg,12));
        TerminalTheme.padding(logView,16,16,16,16);root.addView(logView,new LinearLayout.LayoutParams(-1,-2));
        setContentView(scrollView);
        updateStatus();
        appendLog("Terminal Provider test app started");
        appendLog("Shell: /system/bin/sh exists=" + new File("/system/bin/sh").exists());
        appendLog("Android API: " + Build.VERSION.SDK_INT);
        appendLog("package: " + getPackageName());
    }

    private void addAction(TerminalTheme.Palette theme, LinearLayout parent,int label,boolean primary,Runnable onClick) {
        TextView button=TerminalTheme.textMedium(this,getString(label),16,primary?theme.textOnColor:theme.text);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);button.setMinimumHeight(TerminalTheme.dp(this,52));
        TerminalTheme.padding(button,16,14,16,14);button.setBackground(TerminalTheme.rounded(this,primary?theme.accent:theme.inputBg,12));
        button.setOnClickListener(v->onClick.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=TerminalTheme.dp(this,12);parent.addView(button,p);
    }

    private void bindLocalService() {
        if (bound) {
            appendLog("service already bound");
            return;
        }
        Intent intent = new Intent(this, TerminalProviderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        appendLog("binding local service...");
    }

    private void testShell(String command) {
        if (!bound || service == null) {
            appendLog("error: bind the service first");
            return;
        }
        appendLog(">>> " + command);
        try {
            int exitCode = service.executeShell(command, "", 10000L, new ITerminalProviderCallback.Stub() {
                @Override
                public void onOutput(String content) {
                    runOnUiThread(() -> appendLog(content.trim()));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> appendLog("[ERROR] " + error));
                }

                @Override
                public void onComplete(int exitCode) {
                    runOnUiThread(() -> appendLog("[exit=" + exitCode + "]"));
                }
            });
            if (exitCode != 0) {
                appendLog("command exit code: " + exitCode);
            }
        } catch (RemoteException e) {
            appendLog("remote call failed: " + e.getMessage());
        }
    }

    private void testProviderInfo() {
        if (!bound || service == null) {
            appendLog("error: bind the service first");
            return;
        }
        try {
            String type = service.getProviderType();
            String info = service.getProviderInfo();
            boolean available = service.isAvailable();
            appendLog("ProviderType: " + type);
            appendLog("ProviderInfo: " + info);
            appendLog("Available: " + available);
        } catch (RemoteException e) {
            appendLog("remote call failed: " + e.getMessage());
        }
    }

    private void updateStatus() {
        String status = bound ? "service: bound" : "service: not bound";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            status += "\nversion: " + pi.versionName + " (" + pi.versionCode + ")";
        } catch (PackageManager.NameNotFoundException firstLaunch) {
            status += "\nversion: unknown";
        }
        statusView.setText(status);
    }

    private void appendLog(String message) {
        if (message == null || message.length() == 0) {
            return;
        }
        logBuilder.append(message).append('\n');
        logView.setText(logBuilder.toString());
        logView.post(() -> logView.scrollTo(0, logView.getHeight()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
}
