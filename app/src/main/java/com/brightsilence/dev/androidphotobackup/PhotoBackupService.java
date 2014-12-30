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
            Log.d(TAG,"DropBox connected");
        }
        else
        {
            Log.d(TAG,"DropBox not connected");
        }
    }
}
