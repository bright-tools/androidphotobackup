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
import android.app.Service;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
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

public class PhotoBackupService extends IntentService {

    public static final String TAG = "PhotoBackup::PhotoBackupService";

    private DropBoxWrapper            mDropBoxWrapper = null;

    public PhotoBackupService()
    {
        super("PhotoBackupService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG,"Intent");
        mDropBoxWrapper = new DropBoxWrapper( getApplicationContext() );
        if( mDropBoxWrapper.isConnected() )
        {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

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

                        do {
                            String bucketName = cursor.getString(bucketDisplayNameColIdx);
                            // TODO: Optimise this so that we're not calling create dir for directories which we already created.
                            if( mDropBoxWrapper.createDir( targetDir + "/" + bucketName )) {
                                String mediaFileName = cursor.getString(displayNameColIdx);
                                String mediaModified = cursor.getString(dateModColIdx);
                                String targetMediaFileName = mediaFileName + "." + mediaModified + ".zip";

                                String fileSrc = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                                Log.d(TAG, "File Source: " + fileSrc);

                                try {
                                    // TODO: Error if no password set?
                                    ZipInputStream zipStream = new ZipInputStream(new FileInputStream(fileSrc),
                                                                                  fileSrc,
                                                                                  sharedPreferences.getString("password_text", ""),
                                                                                  sharedPreferences.getString("zip_encryption_type",""));
                                    mDropBoxWrapper.upload( targetDir + "/" + bucketName + "/" + targetMediaFileName, zipStream );

                                } catch (Exception e) {
                                    e.printStackTrace();
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
                }
            } else
            {
                Log.d(TAG,"Top-level directory creation failed");
            }
        }
        else
        {
            Log.d(TAG,"DropBox not connected");
        }
    }
}
