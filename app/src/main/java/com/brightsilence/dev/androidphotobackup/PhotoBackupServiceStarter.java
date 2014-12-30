package com.brightsilence.dev.androidphotobackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 */
public class PhotoBackupServiceStarter extends BroadcastReceiver {

    PhotoBackupAlarmReceiver m_alarm = new PhotoBackupAlarmReceiver();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED") ||
            intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON")) {
            m_alarm.setAlarm( context );
        }
    }
}
