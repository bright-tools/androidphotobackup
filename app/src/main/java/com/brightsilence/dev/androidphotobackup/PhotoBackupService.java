/*

Copyright 2014 John Bailey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

 */

package com.brightsilence.dev.androidphotobackup;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.util.Zip4jConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public class PhotoBackupService extends IntentService {

    public static final String TAG = "PhotoBackup::PhotoBackupService";
    public static final int m_notificationId = 111;

    private DropBoxWrapper            mDropBoxWrapper = null;

    public PhotoBackupService()
    {
        super("PhotoBackupService");
    }

    private void updateLastBackupTime()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor preferenceEditor = sharedPreferences.edit();

        preferenceEditor.putLong("last_backup_time",System.currentTimeMillis());
        preferenceEditor.apply();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String resultString = "";
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        Log.d(TAG,"onHandleIntent");

        if( sharedPreferences.getBoolean("enable_dropbox_checkbox", false ) &&
            sharedPreferences.getBoolean("connection_to_dropbox", false ) ) {

            mDropBoxWrapper = new DropBoxWrapper( getApplicationContext() );

            if( mDropBoxWrapper.isConnected() )
            {

                Log.d(TAG,"DropBox connected");
                String targetDir = sharedPreferences.getString("dropbox_target_dir","" );
                Log.d(TAG,"Creating directory "+targetDir);
                if( mDropBoxWrapper.createDir( targetDir ))
                {
                    ContentResolver contentResolver = getContentResolver();
                    for (int i = 0; i < 2; i++) {
                        Uri src;
                        if (i == 0) {
                            src = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
                            Log.d(TAG, "Examining internal media storage\n");
                        } else {
                            src = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            Log.d(TAG, "Examining external media storage\n");
                        }
                        Cursor cursor = contentResolver.query(src, null, null, null, null);

                        if (cursor.moveToFirst()) {

                            // TODO: Add "Replace all" setting

                            final int displayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                            final int bucketDisplayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                            final int dateModColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);

                            /* Map of all of the directories that have been checked for existence (or created) */
                            Set<String> existingDirs = new HashSet<String>();

                            do {
                                String bucketName = cursor.getString(bucketDisplayNameColIdx);

                                /* Check to see if the directory has already been checked & if not, attempt to create it. */
                                if( existingDirs.contains( bucketName ) ||
                                    ( mDropBoxWrapper.createDir( targetDir + "/" + bucketName ) &&
                                      existingDirs.add( bucketName ) )) {

                                    String mediaFileName = cursor.getString(displayNameColIdx);
                                    String mediaModified = cursor.getString(dateModColIdx);
                                    String targetMediaFileName = mediaFileName + "." + mediaModified + ".zip";

                                    String fileSrc = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                                    Log.d(TAG, "File Source: " + fileSrc);

                                    String targetPathAndName = targetDir + "/" + bucketName + "/" + targetMediaFileName;
                                    if( !mDropBoxWrapper.fileExists( targetPathAndName )) {
                                        Log.d(TAG,"File doesn't already exist");
                                        try {
                                            // TODO: Error if no password set?
                                            ZipInputStream zipStream = new ZipInputStream(new FileInputStream(fileSrc),
                                                    fileSrc,
                                                    sharedPreferences.getString("password_text", ""),
                                                    sharedPreferences.getString("zip_encryption_type", ""));
                                            mDropBoxWrapper.upload(targetPathAndName, zipStream);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        Log.d(TAG,"File exists");
                                    }
                                }
                                else
                                {
                                    Log.d(TAG,"Failed to create bucket directory for: "+bucketName);
                                }
                            } while (cursor.moveToNext());
                        }

                        Log.d(TAG,"Finished storage examination");

                        cursor.close();
                        // TODO: Complete, but maybe with errors
                        resultString = "Backup complete";
                    }
                } else
                {
                    Log.d(TAG,"Top-level directory creation failed");
                    resultString = "Couldn't create container directory";
                }
            }
            else
            {
                Log.d(TAG,"DropBox not connected");
                resultString = "Dropbox not connected";
            }
        }
        else
        {
            resultString = "Dropbox not enabled";
        }

        doNotification(resultString);
        updateLastBackupTime();
    }

    void doNotification( String notificationText )
    {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Android Photo Backup Complete")
                        .setContentText(notificationText);

        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, PhotoBackupSettingsActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(PhotoBackupSettingsActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // mId allows you to update the notification later on.
        mNotificationManager.notify(m_notificationId, mBuilder.build());
    }
}
