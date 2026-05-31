package dev.omar.kira.ui.main;

import android.os.Bundle;
import dev.omar.kira.ui.base.BaseActivity;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new MainFragment(), "MainFragment")
                    .commit();
        }
    }
}
