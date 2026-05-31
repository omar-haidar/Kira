package dev.omar.kira;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import com.yn.shappky.ui.base.CrashApp;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class App extends CrashApp {

    private static volatile App sApp;

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
    }

    public static App i() {
        return sApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }
}
