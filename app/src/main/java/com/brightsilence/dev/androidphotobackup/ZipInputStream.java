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

import android.util.Log;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

public class ZipInputStream  extends FilterInputStream {
    ZipParameters zipParameters = null;
    ZipOutputStream zipOutputStream;
    private int outputStreamReadPoint;
    private ByteArrayOutputStream byteOutputStream;
    private byte[] byteArray;
    private int    byteArraySize;
    private boolean zipOutputStreamFinished;

    public static final String TAG = "PhotoBackup::ZipInputStream";


    private static final int bufferSize = 1024 * 1024;
    private static final int chunkSize = 1024 * 10;

    public ZipInputStream(InputStream in, String fileName, String pass, String encyptionMethod) throws ZipException {
        super(in);

        zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        zipParameters.setEncryptFiles(true);

        String encParts[] = encyptionMethod.split(":");

        zipParameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);

        if( encParts[1].equals("128") )
        {
            Log.d(TAG,"Encryption Strength 128-bit");
            zipParameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_128);
        }
        else
        {
            Log.d(TAG,"Encryption Strength 256-bit");
            zipParameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
        }
        // TODO: If password not set, don't run backup?
        zipParameters.setPassword(pass);
        //                                    parameters.setSourceExternalStream(true);
        //                                    parameters.setFileNameInZip(mediaFileName);

        byteOutputStream = new ByteArrayOutputStream(bufferSize);

        zipOutputStream = new ZipOutputStream(byteOutputStream);
        zipOutputStream.putNextEntry(new File(fileName), zipParameters);
        zipOutputStreamFinished = false;
        byteArraySize = 0;

        Log.d(TAG,"New zip file: "+fileName);

    }

    private void updateByteArray() throws IOException, ZipException
    {
        byte[] readBuff = new byte[chunkSize];
        int readLen = -1;

        Log.d(TAG,"Available: "+byteArraySize + " (ptr: "+outputStreamReadPoint+")");

        if( !zipOutputStreamFinished) {
            // Read the file content and write it to the OutputStream
            readLen = in.read(readBuff);
            Log.d(TAG, "Read from input stream: " + readLen);
            if (readLen != -1) {
                Log.d(TAG, "Writing to zipOutputStream");
                zipOutputStream.write(readBuff, 0, readLen);
            } else {
                Log.d(TAG, "Closing zipOutputStream");
                zipOutputStream.closeEntry();
                zipOutputStream.finish();
                zipOutputStreamFinished = true;
            }
        }

        byteArray = byteOutputStream.toByteArray();
        byteArraySize = byteOutputStream.size();
        byteOutputStream.reset();
        outputStreamReadPoint = 0;
        Log.d(TAG,"Available Now: "+byteArraySize + " (ptr: "+outputStreamReadPoint+")");
    }

    public void close() throws IOException
    {
        in.close();
    }
    public synchronized void mark(int readlimit)
    {
        throw new UnsupportedOperationException();
    }

    public boolean markSupported()
    {
        return false;
    }

    public int available()
    {
        Log.d(TAG,"available()");

        return byteArraySize - outputStreamReadPoint;
    }

    public int read() throws IOException {
        Log.d(TAG,"read()");
        int b = -1;
        if( outputStreamReadPoint >= byteArraySize ) {
            try {
                updateByteArray();
            }
            catch( ZipException e )
            {
                e.printStackTrace();
            }
        }
        if( outputStreamReadPoint < byteArraySize ) {
            b = byteArray[ outputStreamReadPoint++ ];
        }
        return b;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        Log.d(TAG,"read([])");
        int bytes = -1;
        if( outputStreamReadPoint >= byteArraySize ) {
            try {
                updateByteArray();
            }
            catch( ZipException e )
            {
                e.printStackTrace();
            }
        }
        if( outputStreamReadPoint < byteArraySize ) {
            bytes = Math.min(len, byteArraySize-outputStreamReadPoint);
            System.arraycopy(byteArray, outputStreamReadPoint, b, off, bytes);
            outputStreamReadPoint+= bytes;
        }
        Log.d(TAG,"read([]) returning "+bytes);
        return bytes;
    }

    public synchronized void reset()
    {
        throw new UnsupportedOperationException();
    }
    public long skip(long n)
    {
        throw new UnsupportedOperationException();
    }

}
