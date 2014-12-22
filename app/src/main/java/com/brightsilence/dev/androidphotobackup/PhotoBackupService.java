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

public class PhotoBackupService extends Service {

    public static final String TAG = "PhotoBackup::PhotoBackupService";

    private Looper                    mServiceLooper;
    private PhotoBackupServiceHandler mServiceHandler;
    private final IBinder             mBinder = new PhotoBackupServiceBinder();
    private DropBoxWrapper            mDropBoxWrapper = null;

    public class PhotoBackupServiceBinder extends Binder {
        PhotoBackupService getService() {
            return PhotoBackupService.this;
        }
    }

    // Handler that receives messages from the thread
    private final class PhotoBackupServiceHandler extends Handler {
        public PhotoBackupServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new PhotoBackupServiceHandler(mServiceLooper);

        mDropBoxWrapper = new DropBoxWrapper( getApplicationContext() );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }
}
