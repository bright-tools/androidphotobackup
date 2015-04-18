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

import android.content.SharedPreferences;
import android.content.Context;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** This class wraps & abstracts the DropBox API into a few high-level functions which are
    used by Android Photo Backup
 */
public class DropBoxWrapper {

    /** DropBox application key, taken from the DropBox Application Console,
        https://www.dropbox.com/developers/apps
        Needs to be kept in sync with the value in AndroidManifest.xml */
    private static final String APP_KEY = "jr5gypgxq80yuin";

    /** DropBox application secret, taken from the DropBox Application Console. */
    private static final String APP_SECRET = "txdlkunfq2qhdcf";

    /** Preferences name for DropBox specific preferences */
    private static final String ACCOUNT_PREFS_NAME = PhotoBackupSettingsActivity.PREFERENCES_FILE_KEY+"-DropBox";

    /** Preference key for the DropBox access key */
    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";

    /** Preference key for the DropBox access secret */
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    /** Maximum number of child file entries that can be returned by the metadata DropBox API method */
    public final static int MAX_DROPBOX_FILES = 25000;

    public static final String TAG = "APB::DropBoxWrapper";

    /** Number of times to retry uploading a file chunk before giving up */
    public static final int MAX_RETRIES = 3;

    private DropboxAPI<AndroidAuthSession> mDBApi;

    private Context mContext;

    DropBoxWrapper( Context pContext )
    {
        mContext = pContext;
        AndroidAuthSession session = buildSession();
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
    }

    boolean isConnected()
    {
        return mDBApi.getSession().isLinked();
    }

    void connect()
    {
        mDBApi.getSession().startOAuth2Authentication(mContext);
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }

    public Set<String> getFilesInDir(String dirName, String query)
    {
        Set<String> retVal = null;

        if( isConnected() ) {
            try {
                Log.d(TAG, "Getting files in: " + dirName);

                DropboxAPI.Entry entries = mDBApi.metadata("/" + dirName, MAX_DROPBOX_FILES, null, true, null);

                // Move list of files into hashset
                retVal = new HashSet<String>();
                for( DropboxAPI.Entry entry : entries.contents )
                {
                    retVal.add( entry.fileName() );
                }
            } catch( DropboxException e )
            {
                e.printStackTrace();
            }
        }
        return retVal;
    }

    public boolean fileExists(String fileName)
    {
        boolean retVal = false;
        if( isConnected() ) {
            try {
                DropboxAPI.Entry existingEntry = mDBApi.metadata("/" + fileName, 1, null, false, null);
                if ((!existingEntry.isDir) && (!existingEntry.isDeleted)) {
                    retVal = true;
                }
            } catch( DropboxException e )
            {
                e.printStackTrace();
            }
        }
        return retVal;
    }

    public boolean createDir(String dirName)
    {
        boolean retVal = false;

        if( isConnected() )
        {
            if( dirName.length() > 0 ) {
                try {
                    DropboxAPI.Entry existingEntry = mDBApi.metadata("/" + dirName , 1, null, false, null);
                    if(existingEntry.isDir && (!existingEntry.isDeleted))
                    {
                        Log.d(TAG, "Folder exists : " + dirName);
                        retVal = true;
                    }
                } catch( DropboxException e )
                {
                    Log.d(TAG,e.toString());
                }
                if( retVal == false )
                {
                    try {
                        mDBApi.createFolder("/" + dirName);
                        retVal = true;
                    } catch( DropboxException e )
                    {
                        Log.d(TAG,e.toString());
                    }
                }
            }
            else
            {
                /* No directory specified, nothing to create ... report success */
                retVal = true;
            }
        }

        return retVal;
    }

    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = mContext.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0)
        {
            Log.d(TAG,"No key found");
            return;
        }

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            Log.d(TAG,"OAuth 2 token found");
            session.setOAuth2AccessToken(secret);
        } else {
            // Still support using old OAuth 1 tokens.
            Log.d(TAG,"OAuth 1 keypair found");
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }

    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = mContext.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.apply();
            Log.d(TAG,"Stored OAuth 2 token");
        }
        else {
            // Store the OAuth 1 access token, if there is one.  This is only necessary if
            // you're still using OAuth 1.
            AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
            if (oauth1AccessToken != null) {
                SharedPreferences prefs = mContext.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
                edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
                edit.apply();
                Log.d(TAG, "Stored OAuth 1 keypair");
            }
        }
    }

    void clearKeys() {
        SharedPreferences prefs = mContext.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    boolean upload( String fileName, InputStream in ) throws IOException, DropboxException
    {
        boolean allOk = true;

        // TODO: Check that there's enough quota

        DropboxAPI.Entry uploadedFileMetadata;
        try {
            DropboxAPI.ChunkedUploader uploader = mDBApi.getChunkedUploader(in);
            int retryCounter = 0;
            while(!uploader.isComplete()) {
                try {
                    uploader.upload();
                } catch (DropboxException e) {
                    if (retryCounter > MAX_RETRIES)
                    {
                        e.printStackTrace();
                        allOk = false;
                        break;  // Give up after a while.
                    }
                    retryCounter++;
                    // Maybe wait a few seconds before retrying?
                }
            }
            if( allOk ) {
                uploadedFileMetadata = uploader.finish("/"+fileName, null);
                Log.d(TAG, "Uploaded " + uploadedFileMetadata.fileName() + ", size: " + uploadedFileMetadata.size);
            } else {
                Log.d(TAG, "Upload of "+fileName+" failed");

            }
        } finally {
            in.close();
        }
        return allOk;
    }

    void onResume() {
        AndroidAuthSession session = mDBApi.getSession();

        if( !session.isLinked() ) {
            // The next part must be inserted in the onResume() method of the
            // activity from which session.startAuthentication() was called, so
            // that Dropbox authentication completes properly.
            if (session.authenticationSuccessful()) {
                try {
                    Log.d(TAG, "onResume() Authentication successful");

                    // Mandatory call to complete the auth
                    session.finishAuthentication();

                    // Store it locally in our app for later use
                    storeAuth(session);
                } catch (IllegalStateException e) {
                    Log.i(TAG, "Error authenticating", e);
                }
            }
        }
    }
}
