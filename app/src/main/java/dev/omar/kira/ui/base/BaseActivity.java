package dev.omar.kira.ui.base;

import android.content.Intent;
import android.net.Uri;
import android.util.TypedValue;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    protected int resolveThemeColor(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    // Open URL in browser
    protected void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Error when open url cause : " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
    }
}
