package com.brightsilence.photobackup;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
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

import java.util.HashMap;
import java.util.Date;

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

                    HashMap dirsExisting = new HashMap<String, DriveFolder>();

                    do {
                        String bucketName = cursor.getString(bucketDisplayNameColIdx);
                        if (!dirsExisting.containsKey(bucketName)) {
                            Log.d(TAG, "New bucket: " + bucketName + "\n");
                            DriveFolder driveFolder = checkForDriveFolder(bucketName, rootFolder);
                            if (driveFolder == null) {
                                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                        .setTitle(bucketName).build();
                                Log.d(TAG, "Creating New Drive Folder for bucket '"+bucketName+"'\n");
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
                                dirsExisting.put(bucketName,driveFolder);
                            }
                        }

                        String mediaFileName = cursor.getString(displayNameColIdx);
                        String targetMediaFileName = mediaFileName+".zip";
                        String mediaModified = cursor.getString(dateModColIdx);

                        Log.d(TAG, "Found media: " + mediaFileName + "(" + bucketName + "), modified " + mediaModified + "\n");
                        if (dirsExisting.containsKey(bucketName)) {
                            DriveFolder containingFolder = (DriveFolder)(dirsExisting.get( bucketName ));
                            if( !checkForDriveFile( targetMediaFileName, containingFolder, Long.valueOf( mediaModified ).longValue() )) {
                                Log.d(TAG, "File not found on Drive");
                                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                        .setTitle(targetMediaFileName)
                                        .setMimeType("application/zip").build();
                                ContentsResult result = Drive.DriveApi.newContents(apiClient).await();
                                if( result.getStatus().isSuccess() )
                                {
//                                    OutputStream outputStream = result.getContents().getOutputStream();
                                    DriveFileResult fileResult = containingFolder.createFile(apiClient, changeSet, result.getContents() ).await();

                                    // TODO: Do backup
                                }
                                else {
                                    Log.d(TAG,"Failed to create new contents");
                                }
                            } else {
                                Log.d(TAG, "File found on Drive");
                            }
                        }
                    } while (cursor.moveToNext());

                    cursor.close();
                } else {
                    Log.d(TAG, "Didn't find any media\n");
                }
            }
        } else {
            Log.d(TAG, "Failed to connect to Drive\n");
        }
    }

    private boolean checkForDriveFile( String fileName, DriveFolder folderId, long modificationTime ) {
        boolean retVal = false;

        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, fileName ))
                .addFilter(Filters.eq(SearchableField.MIME_TYPE,"application/zip"))
                .build();
        // Invoke the query synchronously
        DriveApi.MetadataBufferResult result =
                folderId.queryChildren(apiClient, query).await();

        if( result.getStatus().isSuccess() ) {
            Log.d(TAG,"checkForDriveFile returned "+result.getMetadataBuffer().getCount());
            if( result.getMetadataBuffer().getCount() > 0 ) {
                Metadata md = result.getMetadataBuffer().get(0);
                Date modDate = md.getModifiedDate();
                // getTime returns milliseconds - modificationTime is in seconds
                long modTime = modDate.getTime() / 1000;
                if( modTime == modificationTime ){
                    retVal = true;
                    Log.d(TAG,"File exists & time matches");
                } else {
                    Log.d(TAG,"File exists but time does not match");
                }
            }
        }

        return retVal;
    }

    private DriveFolder checkForDriveFolder( String folderName, DriveFolder parentFolder )
    {
        DriveFolder retVal = null;
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, folderName))
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
        }

        return retVal;
    }
}
