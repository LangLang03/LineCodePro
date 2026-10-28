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
            appendLog("服务已绑定");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            updateStatus();
            appendLog("服务已断开");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        TerminalTheme.apply(dark);
        getWindow().setStatusBarColor(TerminalTheme.BG);getWindow().setNavigationBarColor(TerminalTheme.BG);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scrollView = new ScrollView(this);scrollView.setBackgroundColor(TerminalTheme.BG);scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TerminalTheme.padding(root,28,28,28,32);scrollView.addView(root);
        TextView title=TerminalTheme.textMedium(this,getString(R.string.app_name),26,TerminalTheme.TEXT);
        root.addView(title);
        statusView=TerminalTheme.text(this,"",14,TerminalTheme.TEXT_SECONDARY,android.graphics.Typeface.NORMAL);
        TerminalTheme.padding(statusView,0,16,0,28);root.addView(statusView);
        addAction(root,R.string.bind_service, true,this::bindLocalService);
        addAction(root,R.string.test_identity,false,()->testShell("whoami"));
        addAction(root,R.string.test_files,false,()->testShell("ls /"));
        addAction(root,R.string.provider_info,false,this::testProviderInfo);
        LinearLayout logHeader=new LinearLayout(this);logHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TerminalTheme.padding(logHeader,0,24,0,12);
        logHeader.addView(TerminalTheme.textMedium(this,getString(R.string.logs),16,TerminalTheme.TEXT),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=TerminalTheme.text(this,getString(R.string.clear_logs),14,TerminalTheme.TEXT_SECONDARY,android.graphics.Typeface.NORMAL);
        clear.setMinimumHeight(TerminalTheme.dp(this,48));clear.setGravity(android.view.Gravity.CENTER_VERTICAL);
        clear.setOnClickListener(v->{logBuilder.setLength(0);logView.setText("");});logHeader.addView(clear);root.addView(logHeader);
        logView=TerminalTheme.text(this,"",13,TerminalTheme.TEXT,android.graphics.Typeface.NORMAL);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);logView.setTextIsSelectable(true);
        logView.setLineSpacing(TerminalTheme.dp(this,6),1);logView.setBackground(TerminalTheme.rounded(this,TerminalTheme.INPUT_BG,12));
        TerminalTheme.padding(logView,16,16,16,16);root.addView(logView,new LinearLayout.LayoutParams(-1,-2));
        setContentView(scrollView);
        updateStatus();
        appendLog("Terminal Provider 测试 APP 已启动");
        appendLog("Shell: /system/bin/sh exists=" + new File("/system/bin/sh").exists());
        appendLog("Android API: " + Build.VERSION.SDK_INT);
        appendLog("包名: " + getPackageName());
    }

    private void addAction(LinearLayout parent,int label,boolean primary,Runnable onClick) {
        TextView button=TerminalTheme.textMedium(this,getString(label),16,primary?TerminalTheme.TEXT_ON_COLOR:TerminalTheme.TEXT);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);button.setMinimumHeight(TerminalTheme.dp(this,52));
        TerminalTheme.padding(button,16,14,16,14);button.setBackground(TerminalTheme.rounded(this,primary?TerminalTheme.ACCENT:TerminalTheme.INPUT_BG,12));
        button.setOnClickListener(v->onClick.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=TerminalTheme.dp(this,12);parent.addView(button,p);
    }

    private void bindLocalService() {
        if (bound) {
            appendLog("服务已绑定，无需重复绑定");
            return;
        }
        Intent intent = new Intent(this, TerminalProviderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        appendLog("正在绑定本地服务...");
    }

    private void testShell(String command) {
        if (!bound || service == null) {
            appendLog("错误: 服务未绑定，请先绑定");
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
                appendLog("命令退出码: " + exitCode);
            }
        } catch (RemoteException e) {
            appendLog("远程调用失败: " + e.getMessage());
        }
    }

    private void testProviderInfo() {
        if (!bound || service == null) {
            appendLog("错误: 服务未绑定，请先绑定");
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
            appendLog("远程调用失败: " + e.getMessage());
        }
    }

    private void updateStatus() {
        String status = bound ? "服务状态: 已绑定" : "服务状态: 未绑定";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            status += "\n版本: " + pi.versionName + " (" + pi.versionCode + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
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
