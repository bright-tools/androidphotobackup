package com.brightsilence.dev.androidphotobackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 */
public class PhotoBackupServiceStarter extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, PhotoBackupService.class);
        context.startService(i);
    }
}
