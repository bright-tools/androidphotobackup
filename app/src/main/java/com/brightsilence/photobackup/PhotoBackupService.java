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

package com.brightsilence.photobackup;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveApi.ContentsResult;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveFolder.DriveFileResult;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.util.Zip4jConstants;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Date;
import java.io.File;
import java.io.FileInputStream;

public class PhotoBackupService extends IntentService {

    public static final String TAG = "PhotoBackup::PhotoBackupService";

    private GoogleApiClient apiClient;

    public PhotoBackupService() {
        super("PhotoBackupService");

        Log.d(TAG, "PhotoBackupService starting\n");

        apiClient = null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        ConnectionResult connectResult;

        if( apiClient == null ) {
            apiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .build();
        }

        if( !apiClient.isConnected() ) {
            connectResult = apiClient.blockingConnect();
        }

        if( apiClient.isConnected() ) {
            Status s = Drive.DriveApi.requestSync(apiClient).await();

            if (s.isSuccess()) {

                String driveFolderId = intent.getStringExtra("driveFolderId");

                // TODO: Validate this folder
                DriveFolder rootFolder = Drive.DriveApi.getFolder(apiClient, DriveId.decodeFromString(driveFolderId));

                // TODO: Move this out so that it's no running in the on..Click callback
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

                        final int displayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                        final int bucketDisplayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        final int dateModColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);
                        ZipParameters zipParameters = null;

                        HashMap<String, DriveFolder> dirsExisting = new HashMap<String, DriveFolder>();

                        do {
                            String bucketName = cursor.getString(bucketDisplayNameColIdx);
                            if (!dirsExisting.containsKey(bucketName)) {
                                Log.d(TAG, "New bucket: " + bucketName + "\n");
                                DriveFolder driveFolder = checkForDriveFolder(bucketName, rootFolder);
                                if (driveFolder == null) {
                                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                            .setTitle(bucketName).build();
                                    Log.d(TAG, "Creating New Drive Folder for bucket '" + bucketName + "'\n");
                                    DriveFolder.DriveFolderResult result = rootFolder.createFolder(apiClient, changeSet).await();
                                    if (result.getStatus().isSuccess()) {
                                        Log.d(TAG, "Success\n");
                                        driveFolder = result.getDriveFolder();
                                    } else {
                                        Log.d(TAG, "Failed!\n");
                                    }
                                } else {
                                    Log.d(TAG, "Directory for bucket exists\n");
                                }
                                if (driveFolder != null) {
                                    dirsExisting.put(bucketName, driveFolder);
                                }
                            }

                            String mediaFileName = cursor.getString(displayNameColIdx);
                            String mediaModified = cursor.getString(dateModColIdx);
                            String targetMediaFileName = mediaFileName + "." + mediaModified + ".zip";

                            Log.d(TAG, "Found media: " + mediaFileName + "(" + bucketName + "), modified " + mediaModified + "\n");
                            if (dirsExisting.containsKey(bucketName)) {
                                DriveFolder containingFolder = dirsExisting.get(bucketName);
                                if (!checkForDriveFile(targetMediaFileName, containingFolder, Long.valueOf(mediaModified))) {
                                    Log.d(TAG, "File not found on Drive");
                                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                            .setTitle(targetMediaFileName)
                                            .setMimeType("application/zip").build();
                                    ContentsResult result = Drive.DriveApi.newContents(apiClient).await();
                                    if (result.getStatus().isSuccess()) {
                                        OutputStream outputStream = result.getContents().getOutputStream();

                                        if (zipParameters == null) {
                                            SharedPreferences sharedPref = getSharedPreferences(SettingsActivity.PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);

                                            zipParameters = new ZipParameters();
                                            zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
                                            zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
                                            zipParameters.setEncryptFiles(true);
                                            zipParameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
                                            zipParameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
                                            // TODO: If password not set, don't run backup?
                                            zipParameters.setPassword(sharedPref.getString("encryptionPassword", ""));
                                            //                                    parameters.setSourceExternalStream(true);
                                            //                                    parameters.setFileNameInZip(mediaFileName);
                                        }

                                        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
                                        try {
                                            String fileSrc = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                                            Log.d(TAG, "Data: " + fileSrc);
                                            zipOutputStream.putNextEntry(new File(fileSrc), zipParameters);

                                            FileInputStream inputStream = new FileInputStream(fileSrc);
                                            byte[] readBuff = new byte[4096];
                                            int readLen = -1;

                                            //Read the file content and write it to the OutputStream
                                            while ((readLen = inputStream.read(readBuff)) != -1) {
                                                zipOutputStream.write(readBuff, 0, readLen);
                                            }

                                            inputStream.close();

                                            zipOutputStream.closeEntry();
                                            zipOutputStream.finish();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        try {
                                            zipOutputStream.close();
                                            outputStream.close();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        DriveFileResult fileResult = containingFolder.createFile(apiClient, changeSet, result.getContents()).await();
                                    } else {
                                        Log.d(TAG, "Failed to create new contents");
                                    }
                                } else {
                                    Log.d(TAG, "File found on Drive");
                                }
                            }
                        } while (cursor.moveToNext());

                    } else {
                        Log.d(TAG, "Didn't find any media\n");
                    }
                    cursor.close();
                }
            } else
            {
                Log.d(TAG, "Sync to drive failed\n");
            }
        } else {
            Log.d(TAG, "Failed to connect to Drive\n");
        }
    }

    private boolean checkForDriveFile( String fileName, DriveFolder folderId, long modificationTime ) {
        boolean retVal = false;

        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, fileName ))
                .addFilter(Filters.eq(SearchableField.TRASHED, false))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE,"application/zip"))
                .build();
        // Invoke the query synchronously
        DriveApi.MetadataBufferResult result =
                folderId.queryChildren(apiClient, query).await();

        if( result.getStatus().isSuccess() ) {
            Log.d(TAG,"checkForDriveFile returned "+result.getMetadataBuffer().getCount());
            if( result.getMetadataBuffer().getCount() > 0 ) {
                Log.d(TAG,"File exists");

                Metadata md = result.getMetadataBuffer().get(0);
                Log.d(TAG,"Is Trashed: "+md.isTrashed());
//                Date modDate = md.getModifiedDate();
                // getTime returns milliseconds - modificationTime is in seconds
//                long modTime = modDate.getTime() / 1000;
//                if( modTime == modificationTime ){
                    retVal = true;
//                    Log.d(TAG,"Time matches");
//                } else {
//                    Log.d(TAG,"Time does not match");
//                }
            }
            result.getMetadataBuffer().close();
        }

        return retVal;
    }

    private DriveFolder checkForDriveFolder( String folderName, DriveFolder parentFolder )
    {
        DriveFolder retVal = null;
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, folderName))
                .addFilter(Filters.eq(SearchableField.TRASHED, false))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE,DriveFolder.MIME_TYPE))
                .build();
        // Invoke the query synchronously
        DriveApi.MetadataBufferResult result =
                parentFolder.queryChildren(apiClient, query).await();

        if( result.getStatus().isSuccess() ) {
            Log.d(TAG,"checkForDriveFolder returned "+result.getMetadataBuffer().getCount());
            if( result.getMetadataBuffer().getCount() > 0 ) {
                retVal = Drive.DriveApi.getFolder(apiClient, result.getMetadataBuffer().get(0).getDriveId());
            }
            result.getMetadataBuffer().close();
        }

        return retVal;
    }
}
