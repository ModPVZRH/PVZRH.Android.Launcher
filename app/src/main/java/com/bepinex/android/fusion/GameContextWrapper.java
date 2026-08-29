package com.bepinex.android.fusion;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.view.Display;

import androidx.annotation.Nullable;

import java.io.File;

/** Wraps Context to redirect resource and data paths for the UnityPlayer constructor. */
public class GameContextWrapper extends ContextWrapper {
    Context fusionContext;
    Context appContext;

    public GameContextWrapper(Context gameContext, Context fusionContext, Context appContext) {
        super(gameContext);
        this.fusionContext = fusionContext;
        // Do not mutate ApplicationInfo. Unity keeps this Context for the
        // render thread and later calls getPackageCodePath() on it.
        this.appContext = appContext.getApplicationContext();
    }

    @Override
    public Resources getResources() {
        return super.getResources();
    }

    @Override
    public Resources.Theme getTheme() {
        return super.getTheme();
    }

    @Override
    public android.content.res.AssetManager getAssets() {
        return super.getAssets();
    }

    @Override
    public String getPackageName() {
        return super.getPackageName();
    }

    @Override
    public String getOpPackageName() {
        return super.getOpPackageName();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return this.fusionContext.getSharedPreferences(name, mode);
    }

    public boolean deleteSharedPreferences(String name) {
        return this.fusionContext.deleteSharedPreferences(name);
    }

    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        return this.fusionContext.moveSharedPreferencesFrom(sourceContext, name);
    }

    @Override
    public File getFilesDir() {
        return this.fusionContext.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return this.fusionContext.getCacheDir();
    }

    @Nullable
    @Override
    public File getExternalCacheDir() {
        return this.fusionContext.getExternalCacheDir();
    }

    @Override
    public File[] getExternalCacheDirs() {
        return this.fusionContext.getExternalCacheDirs();
    }

    @Override
    public File getExternalFilesDir(String type) {
        return this.fusionContext.getExternalFilesDir(type);
    }

    @Override
    public Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return this.fusionContext.getDisplay();
        }
        return null;
    }

    @Override
    public Object getSystemService(String name) {
        return this.fusionContext.getSystemService(name);
    }

    @Override
    public Context getBaseContext() {
        return super.getBaseContext();
    }

    @Override
    public Context getApplicationContext() {
        return appContext;
    }

    @Override
    public File getObbDir() {
        return null;
    }

    @Override
    public File[] getObbDirs() {
        return this.fusionContext.getObbDirs();
    }
}
