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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Implements an Android Service which actually does the work of backing up the media files
    based on the setting entered by the user.

    The service works by scanning all the media stores on the local device and checking for
    corresponding files on the cloud storage service.  Lists of directory contents are cached
    ( @see mExistingFiles ) in order to reduce (as compared to checking for the existence of
    each file individually) the number of cloud API accesses.  A list of files which need to be
    backed up is built ( @see mFilesToUpload ).  Once the comparison is complete, mFilesToUpload
    is iterated and the files specified are backed up.
 */
public class PhotoBackupService extends IntentService {

    public static final String TAG = "PhotoBackup::PhotoBackupService";
    public static final String BACKUP_FN_EXT = ".zip";
    public static final int m_notificationId = 111;

    private DropBoxWrapper            mDropBoxWrapper = null;

    /** Map of all of the directories that have been checked for existence (or created) and the files within them
     *  Map key is the (cloud storage) directory name
     *  Map value is a set containing the (unqualified) filenames within the directory */
    private Map<String, Set<String>> mExistingFiles;

    /** Map of all of the files that need to be uploaded based on a comparison of the files on the
     *   local device and those on the cloud storage
     *  Map key is the qualified local filename
     *  Map value is the directory qualified target name on the cloud storage
     */
    private Map<String, String> mFilesToUpload;

    /** Top-level directory on the cloud storage service for backed up files.  Actual files
     *   will likely be in sub-directories under this, corresponding to the storage structure
     *   on the local device.
     */
    private String mTargetDir;

    private SharedPreferences mSharedPreferences;

    public PhotoBackupService()
    {
        super("PhotoBackupService");
    }

    /** Check to see if there's network connectivity
     *   Thanks to: http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts#answer-4009133 */
    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /** Update the stored time at which a backup was last performed. */
    private void updateLastBackupTime()
    {
        SharedPreferences.Editor preferenceEditor = mSharedPreferences.edit();

        preferenceEditor.putLong("last_backup_time",System.currentTimeMillis());
        preferenceEditor.apply();
    }

    /** Handles the backing up of all files pointed to by the supplied database cursor
     *
     *  @param cursor Cursor to be iterated. */
    private void handleFiles( Cursor cursor )
    {
        final int displayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
        final int bucketDisplayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
        final int dateModColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);

        do {
            String bucketName = cursor.getString(bucketDisplayNameColIdx);
            String bucketTargetDir = mTargetDir + "/" + bucketName;

            /* Check to see if the directory has already been checked & if not, attempt to create it. */
            if (!mExistingFiles.containsKey( bucketTargetDir ))
            {
                Log.d(TAG, "Not yet checked directory "+bucketTargetDir);
                mDropBoxWrapper.createDir( bucketTargetDir );

                // If directory creation failed, then getFilesInDir will yield a NULL set

                Set<String> fileSet = mDropBoxWrapper.getFilesInDir(bucketTargetDir,BACKUP_FN_EXT);
                if( fileSet != null ) {
                    mExistingFiles.put(bucketTargetDir, fileSet);
                }
            }
            else
            {
                Log.d(TAG, "Already checked directory "+bucketTargetDir);
            }

            if (!mExistingFiles.containsKey(bucketName)) {

                String mediaFileName = cursor.getString(displayNameColIdx);
                String mediaModified = cursor.getString(dateModColIdx);

                String targetMediaFileName = mediaFileName + "." + mediaModified + BACKUP_FN_EXT;

                String fileSrc = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));

                Log.d(TAG, "File Source: " + fileSrc);

                handleIndividualFile(fileSrc, bucketTargetDir, targetMediaFileName);
            }
            else
            {
                Log.d(TAG,"Failed to create bucket directory for: "+bucketName);
            }
        } while (cursor.moveToNext());
    }

    /** Handles the backing up of a particular file
     *  @param fileSrc           The path to the local file on the Android device
     *  @param targetPath        The target directory name
     *  @param targetName        The target file name
     */
    private void handleIndividualFile(String fileSrc, String targetPath, String targetName) {
        Set<String> existingFiles = mExistingFiles.get(targetPath);

        if( existingFiles != null ) {
            if (!existingFiles.contains(targetName)) {

                Log.d(TAG, "File doesn't already exist - adding to upload queue");
                mFilesToUpload.put(fileSrc, targetPath + "/" + targetName );

            } else {
                Log.d(TAG, "File exists");
            }
        } else
        {
            Log.d(TAG, "File list null for "+targetPath);
        }
    }

    /** Method to examine media stores on both internal and external storage and backup the files
        (via the handleFiles method) */
    private void backupContent()
    {
        ContentResolver contentResolver = getContentResolver();
        mExistingFiles = new HashMap<String, Set<String>>();
        mFilesToUpload = new HashMap<String, String>();

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
                handleFiles(cursor);
            }

            Log.d(TAG,"Finished storage examination");

            cursor.close();
        }
        uploadFiles();
    }

    void uploadFiles()
    {
        for( Map.Entry<String, String> entry : mFilesToUpload.entrySet() ) {
            try {
                Log.d(TAG, "Uploading "+entry.getKey()+" to "+entry.getValue());

                ZipInputStream zipStream = new ZipInputStream(new FileInputStream(entry.getKey()),
                        entry.getKey(),
                        mSharedPreferences.getString("password_text", ""),
                        mSharedPreferences.getString("zip_encryption_type", ""));
                mDropBoxWrapper.upload(entry.getValue(), zipStream);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String resultString = "";
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        Log.d(TAG,"onHandleIntent");

        if( mSharedPreferences.getString( "password_text", "").length() == 0 ) {

            resultString = getResources().getString(R.string.no_password_set);

        } else if( !mSharedPreferences.getBoolean("enable_dropbox_checkbox", false ) ) {

            resultString = getResources().getString(R.string.dropbox_not_enabled);
        }
        else if( !mSharedPreferences.getBoolean("connection_to_dropbox", false ) ) {

            resultString = getResources().getString(R.string.dropbox_not_connected);
        }
        else if ( !isOnline() )
        {
            resultString = getResources().getString(R.string.no_internet_connection);
        }
        else
        {
            resultString = startBackup();
        }

        doNotification(resultString);
        updateLastBackupTime();

        /* Don't need these any longer - memory can be released */
        mFilesToUpload = null;
        mExistingFiles = null;
    }

    private String startBackup()
    {
        String resultString = "";
        mDropBoxWrapper = new DropBoxWrapper( getApplicationContext() );

        if( mDropBoxWrapper.isConnected() )
        {
            Log.d(TAG,"DropBox connected");
            mTargetDir = mSharedPreferences.getString("dropbox_target_dir","" );
            Log.d(TAG,"Creating directory "+mTargetDir);
            if( mDropBoxWrapper.createDir( mTargetDir ))
            {
                backupContent();
                // TODO: Complete, but maybe with errors
                resultString = getResources().getString(R.string.backup_completed);
            } else
            {
                Log.d(TAG,"Top-level directory creation failed");
                resultString = getResources().getString(R.string.top_level_dir_create_failed);

            }
        }
        else
        {
            Log.d(TAG,"DropBox not connected");
            resultString = getResources().getString(R.string.dropbox_not_connected);
        }
        return resultString;
    }

    private void doNotification( String notificationText )
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
