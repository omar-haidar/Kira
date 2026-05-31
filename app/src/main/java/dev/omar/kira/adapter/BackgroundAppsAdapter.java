package dev.omar.kira.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import dev.omar.kira.R;
import dev.omar.kira.databinding.ItemBinding;
import dev.omar.kira.model.AppModel;

import java.util.List;

public class BackgroundAppsAdapter extends BaseAdapter {

    private final AppCompatActivity context;
    private final List<AppModel> data;
    
    private final int density;
    
    private OnAppActionListener actionListener;

    // Listener interface for app actions
    public interface OnAppActionListener {
        void onKillApp(String packageName);
    }

    public BackgroundAppsAdapter(AppCompatActivity context, List<AppModel> data) {
        this.context = context;
        this.data = data;
        
        this.density = (int) context.getResources().getDisplayMetrics().density;

        // Retrieve colors from resources
        //this.selectedColor = ContextCompat.getColor(context, R.color.list_item_selected_color);
        //this.defaultColor = ContextCompat.getColor(context, R.color.list_item_default_color);
    }

    public void setOnAppActionListener(OnAppActionListener listener) {
        this.actionListener = listener;
    }

    // Creates a circular button background with ripple effect
    private Drawable createButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, R.color.button_background_color));
        background.setCornerRadius(density * 360);

        return new RippleDrawable(
                new ColorStateList(new int[][]{{}}, new int[]{ContextCompat.getColor(context, R.color.button_ripple_color)}),
                background,
                null
        );
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public AppModel getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    // Creates and binds the view for each list item
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemBinding itemBinding;

        if (convertView == null) {
            itemBinding = ItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            convertView = itemBinding.getRoot();
            convertView.setTag(itemBinding);
        } else {
            itemBinding = (ItemBinding) convertView.getTag();
        }

        AppModel app = getItem(position);

        itemBinding.appName.setText(app.getAppName());
        itemBinding.appPkg.setText(app.getPackageName());
        itemBinding.appRam.setText(app.getAppRam());
        itemBinding.appIcon.setImageDrawable(app.getAppIcon());

        // Configure the kill button appearance
        itemBinding.imageview2.setBackground(createButtonBackground());
        itemBinding.imageview2.setElevation(density * 2);
        itemBinding.imageview2.setClickable(true);
        itemBinding.imageview2.setFocusable(true);

        final String pkg = app.getPackageName();

        // Set kill button click listener only if necessary (avoids re-binding on scroll)
        Object tag = itemBinding.imageview2.getTag();
        if (!(tag instanceof String) || !tag.equals(pkg)) {
            itemBinding.imageview2.setOnClickListener(v -> {
                if (actionListener != null) {
                    AppModel currentApp = getItem(position);
                    if (currentApp != null) {
                        actionListener.onKillApp(currentApp.getPackageName());
                    }
                }
            });
            itemBinding.imageview2.setTag(pkg);
        }

        // Update background and button visibility based on selection
        itemBinding.linear1.setSelected(app.isSelected()); 
        itemBinding.imageview2.setVisibility(app.isSelected() ? View.GONE : View.VISIBLE);
        if (app.isProtected()) {
            itemBinding.getRoot().setAlpha(0.4f); 
            itemBinding.imageview2.setVisibility(View.GONE);
            itemBinding.linear1.setSelected(false);
        } else {
            itemBinding.getRoot().setAlpha(1.0f); 
            itemBinding.imageview2.setVisibility(app.isSelected() ? View.GONE : View.VISIBLE);
            itemBinding.linear1.setSelected(app.isSelected());
        }
        return convertView;
    }
}

