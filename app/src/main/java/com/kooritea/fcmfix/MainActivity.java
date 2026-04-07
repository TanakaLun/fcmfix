package com.kooritea.fcmfix;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.kooritea.fcmfix.util.IceboxUtils;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private AppListAdapter appListAdapter;
    private static XposedService xposedService;
    private LinearProgressIndicator progressBar;
    private RecyclerView recyclerView;
    private View searchBarContainer;
    private TextInputEditText searchEditText;

    private final Set<String> allowList = new HashSet<>();
    private final JSONObject config = new JSONObject();
    
    private final List<AppInfo> mAppListFull = new ArrayList<>();
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        progressBar = findViewById(R.id.progress_bar);
        recyclerView = findViewById(R.id.recycler_view);
        searchBarContainer = findViewById(R.id.search_bar_container);
        searchEditText = findViewById(R.id.search_edit_text);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appListAdapter != null) appListAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        initXposedService();
        checkPermissions();

        appListAdapter = new AppListAdapter();
        recyclerView.setAdapter(appListAdapter);
        appListAdapter.refreshList();
    }

    private void checkPermissions() {
        try {
            if (ContextCompat.checkSelfPermission(this, IceboxUtils.SDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{IceboxUtils.SDK_PERMISSION}, IceboxUtils.REQUEST_CODE);
            }
        } catch (Throwable ignored) {}
    }

    private void initXposedService() {
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(@NonNull XposedService service) {
                    xposedService = service;
                    runOnUiThread(() -> {
                        loadConfigFromRemotePreferences();
                        if (appListAdapter != null) appListAdapter.refreshList();
                    });
                }

                @Override
                public void onServiceDied(@NonNull XposedService service) {
                    if (xposedService == service) xposedService = null;
                }
            });
        } catch (Throwable e) {
            Log.e("FCMFix", "Xposed binding failed", e);
        }
    }

    private void loadConfigFromRemotePreferences() {
        if (xposedService == null) return;
        try {
            SharedPreferences pref = xposedService.getRemotePreferences("config");
            this.allowList.clear();
            this.allowList.addAll(pref.getStringSet("allowList", new HashSet<>()));
            this.config.put("allowList", new JSONArray(this.allowList));
            this.config.put("disableAutoCleanNotification", pref.getBoolean("disableAutoCleanNotification", false));
            this.config.put("includeIceBoxDisableApp", pref.getBoolean("includeIceBoxDisableApp", false));
            this.config.put("noResponseNotification", pref.getBoolean("noResponseNotification", false));
        } catch (Throwable e) {
            Log.e("FCMFix", "Load config error", e);
        }
    }

        private void updateConfig() {
        try {
            if (xposedService == null) {
                throw new IllegalStateException("XposedService 未连接，无法写入远程配置");
            }
            SharedPreferences pref = xposedService.getRemotePreferences("config");
            this.config.put("allowList", new JSONArray(this.allowList));
            
            boolean saved = pref.edit()
                    .putBoolean("init", true)
                    .putStringSet("allowList", new HashSet<>(this.allowList))
                    .putBoolean("disableAutoCleanNotification", this.config.optBoolean("disableAutoCleanNotification"))
                    .putBoolean("includeIceBoxDisableApp", this.config.optBoolean("includeIceBoxDisableApp"))
                    .putBoolean("noResponseNotification", this.config.optBoolean("noResponseNotification"))
                    .commit();

            if (!saved) {
                throw new IllegalStateException("配置写入失败");
            }

            this.sendBroadcast(new Intent("com.kooritea.fcmfix.update.config"));
        } catch (Throwable e) {
            Log.e("updateConfig", e.toString());
            new MaterialAlertDialogBuilder(this)
                    .setTitle("保存失败")
                    .setMessage(e.getMessage())
                    .setPositiveButton("确定", null)
                    .show();
        }
    }


    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private final List<AppInfo> mAppList = new ArrayList<>();
        private boolean isRefreshing = false;

        @SuppressLint("NotifyDataSetChanged")
        public void refreshList() {
            if (isRefreshing) return;
            isRefreshing = true;
            progressBar.setVisibility(View.VISIBLE);

            executorService.execute(() -> {
                List<AppInfo> _allow = new ArrayList<>(), _notAllow = new ArrayList<>(), _noFcm = new ArrayList<>();
                PackageManager pm = getPackageManager();
                List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS);
                
                for (PackageInfo pi : installedPackages) {
                    AppInfo info = new AppInfo(pi, pm);
                    boolean hasFcm = false;
                    if (pi.receivers != null) {
                        for (ActivityInfo r : pi.receivers) {
                            if (r.name.contains("FirebaseInstanceIdReceiver") || r.name.contains("AppMeasurementReceiver")) {
                                hasFcm = true; break;
                            }
                        }
                    }
                    info.includeFcm = hasFcm;
                    if (allowList.contains(info.packageName)) {
                        info.isAllow = true;
                        _allow.add(info);
                    } else if (hasFcm) {
                        _notAllow.add(info);
                    } else {
                        _noFcm.add(info);
                    }
                }

                Comparator<AppInfo> nameComparator = (a, b) -> Collator.getInstance().compare(a.name, b.name);
                _allow.sort(nameComparator);
                _notAllow.sort(nameComparator);
                _noFcm.sort(nameComparator);

                List<AppInfo> fullList = new ArrayList<>();
                fullList.addAll(_allow);
                fullList.addAll(_notAllow);
                fullList.addAll(_noFcm);

                new Handler(Looper.getMainLooper()).post(() -> {
                    mAppListFull.clear();
                    mAppListFull.addAll(fullList);
                    
                    filter(searchEditText.getText().toString());
                    
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    searchBarContainer.setVisibility(View.VISIBLE);
                    isRefreshing = false;
                });
            });
        }

        @SuppressLint("NotifyDataSetChanged")
        public void filter(String query) {
            String pattern = query.toLowerCase().trim();
            mAppList.clear();
            if (pattern.isEmpty()) {
                mAppList.addAll(mAppListFull);
            } else {
                for (AppInfo info : mAppListFull) {
                    if (info.name.toLowerCase().contains(pattern) || 
                        info.packageName.toLowerCase().contains(pattern)) {
                        mAppList.add(info);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.app_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = mAppList.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.name.setText(app.name);
            holder.packageName.setText(app.packageName);
            holder.includeFcm.setVisibility(app.includeFcm ? View.VISIBLE : View.GONE);
            holder.isAllow.setOnCheckedChangeListener(null);
            holder.isAllow.setChecked(app.isAllow);
            
            holder.itemView.setOnClickListener(v -> {
                app.isAllow = !app.isAllow;
                if (app.isAllow) allowList.add(app.packageName);
                else allowList.remove(app.packageName);
                updateConfig();
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return mAppList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, packageName, includeFcm;
            MaterialCheckBox isAllow;
            ViewHolder(View view) {
                super(view);
                icon = view.findViewById(R.id.icon);
                name = view.findViewById(R.id.name);
                packageName = view.findViewById(R.id.packageName);
                includeFcm = view.findViewById(R.id.includeFcm);
                isAllow = view.findViewById(R.id.isAllow);
            }
        }
    }

    private static class AppInfo {
        String name, packageName;
        Drawable icon;
        boolean isAllow = false, includeFcm = false;
        AppInfo(PackageInfo pi, PackageManager pm) {
            this.name = pi.applicationInfo.loadLabel(pm).toString();
            this.packageName = pi.packageName;
            this.icon = pi.applicationInfo.loadIcon(pm);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem autoClean = menu.findItem(R.id.menu_disable_autoclean);
        if (autoClean != null) autoClean.setChecked(config.optBoolean("disableAutoCleanNotification"));
        MenuItem icebox = menu.findItem(R.id.menu_allow_icebox);
        if (icebox != null) icebox.setChecked(config.optBoolean("includeIceBoxDisableApp"));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_disable_autoclean) {
            toggleConfig("disableAutoCleanNotification", item);
        } else if (id == R.id.menu_allow_icebox) {
            toggleConfig("includeIceBoxDisableApp", item);
        } else if (id == R.id.menu_select_all_fcm) {
            selectAllFcm();
        } else if (id == R.id.menu_fcm_diag) {
            openFcmDiagnostics();
        }
        return true;
    }

    private void toggleConfig(String key, MenuItem item) {
        try {
            boolean newState = !item.isChecked();
            config.put(key, newState);
            item.setChecked(newState);
            updateConfig();
        } catch (JSONException ignored) {}
    }

    private void selectAllFcm() {
        if (appListAdapter == null) return;
        for (AppInfo info : mAppListFull) {
            if (info.includeFcm) {
                info.isAllow = true;
                allowList.add(info.packageName);
            }
        }
        updateConfig();
        appListAdapter.filter(searchEditText.getText().toString());
    }

    private void openFcmDiagnostics() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.google.android.gms", "com.google.android.gms.gcm.GcmDiagnostics"));
            startActivity(intent);
        } catch (Exception e) {
            Log.e("FCMFix", "Cannot open diagnostics", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
