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

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.Arrays;

import android.util.Log;

import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.DialogInterface;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.*;
import com.google.android.gms.drive.*;
import com.google.android.gms.common.GooglePlayServicesUtil;
import android.app.Dialog;
import android.support.v4.app.*;

import android.content.IntentSender;
import android.widget.Toast;
import com.google.android.gms.drive.DriveResource.MetadataResult;
import com.google.android.gms.drive.Metadata;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.gson.GsonFactory;
import android.accounts.AccountManager;

public class SettingsActivity extends FragmentActivity  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {

    public static final String PREFERENCES_FILE_KEY = "PhotoBackupPrefsFile";
    public static final String TAG = "PhotoBackup::SettingsActivity";
    public static final String DEFAULT_DRIVE_FOLDER = "(none)";
    public static final String DEFAULT_DRIVE_ACCOUNT = "(none)";
    public static final String APPLICATION_NAME = "Android Photo Backup";

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final int REQUEST_CODE_OPENER = 1002;
    private static final int COMPLETE_AUTHORIZATION_REQUEST_CODE = 1003;
    private static final int REQUEST_ACCOUNT_PICKER = 1004;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";

    private String   m_driveFolderId;
    private String   driveAccountName = null;

    private EditText passwordBox;
    private CheckBox showPass;
    private TextView driveStatus;
    private TextView driveFolderText;
    private TextView driveAccountText;
    private EditText logView;
    private Button   driveFolderSelectButton;

    private boolean mResolvingError = false;
    private GoogleApiClient m_apiClient = null;
    private com.google.api.services.drive.Drive m_drvSvc = null;
    com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential m_googleActCrd = null;

    private void setupTabs( )
    {
        TabHost tabHost = (TabHost)findViewById(R.id.tabHost);
        tabHost.setup();

        TabSpec spec1=tabHost.newTabSpec("Destination");
        spec1.setContent(R.id.tab1);
        spec1.setIndicator("Destination");

        TabSpec spec2=tabHost.newTabSpec("Security");
        spec2.setIndicator("Security");
        spec2.setContent(R.id.tab2);

        TabSpec spec3=tabHost.newTabSpec("Timing");
        spec3.setIndicator("Timing");
        spec3.setContent(R.id.tab3);

        TabSpec spec4=tabHost.newTabSpec("About");
        spec4.setIndicator("About");
        spec4.setContent(R.id.tab4);

        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        tabHost.addTab(spec3);
        tabHost.addTab(spec4);
    }

    private void setDriveAccountName( String pNewName )
    {
        driveAccountName = pNewName;
        driveAccountText.setText( driveAccountName );
        if( ! driveAccountName.equals(DEFAULT_DRIVE_ACCOUNT) ) {
            setupApis(true);
        }
    }

    private void loadSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        passwordBox.setText(sharedPref.getString("encryptionPassword", ""));
        driveFolderText.setText(sharedPref.getString("driveFolder", DEFAULT_DRIVE_FOLDER));
        m_driveFolderId = sharedPref.getString("m_driveFolderId","");
        setDriveAccountName(sharedPref.getString("driveAccountName", DEFAULT_DRIVE_ACCOUNT));
    }

    private void saveSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        // TODO: if the password changes, need to re-upload all files using the new password?
        editor.putString("encryptionPassword", passwordBox.getText().toString());
        editor.putString("driveFolder", driveFolderText.getText().toString());
        editor.putString("m_driveFolderId", m_driveFolderId);
        editor.putString("driveAccountName", driveAccountName);
        editor.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        setupTabs();

        passwordBox = (EditText)findViewById(R.id.editPassword);
        showPass = (CheckBox)findViewById(R.id.checkShowPass);
        logView = (EditText)findViewById(R.id.logView);
        driveStatus = (TextView)findViewById(R.id.textDriveStatus);
        driveFolderText = (TextView)findViewById(R.id.textCurrentDriveFolder);
        driveAccountText = (TextView)findViewById(R.id.textCurrentDriveAccount);
        driveFolderSelectButton = (Button)findViewById(R.id.buttonSelectFolder);
        driveFolderSelectButton.setEnabled(false);

        showPass.setOnCheckedChangeListener(new OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
                if(showPass.isChecked()) {
                    passwordBox.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                else {
                    passwordBox.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }});

        Log.d(TAG, "Are play services available: " + GooglePlayServicesUtil.isGooglePlayServicesAvailable(this));

        loadSettings();
        setupApis();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        saveSettings();
        if( m_apiClient != null ) {
            m_apiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // TODO: Make activity tab bar scrollable?

    public void onChooseDriveFolderClick(View v) {

        OpenFileActivityBuilder openFileBuilder = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[] { DriveFolder.MIME_TYPE });

        if( !m_driveFolderId.isEmpty() ) {
            openFileBuilder.setActivityStartFolder( DriveId.decodeFromString(m_driveFolderId));
        }

        IntentSender intentSender = openFileBuilder.build(m_apiClient);
        try {
            startIntentSenderForResult(
                    intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }

    public void onChooseDriveAccountClick(View v) {
        startActivityForResult(m_googleActCrd.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
    }

    public void onRunNowClick(View v) {
        Intent intent = new Intent(this, PhotoBackupService.class);
        intent.putExtra("driveFolderId", m_driveFolderId);
        startService(intent);
    }

    /**
     * Shows a toast message.
     */
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    public void setupApis() {
        setupApis(false);
    }

    public void setupApis( boolean pForce )
    {
        if( pForce ) {
            m_apiClient = null;
            m_drvSvc = null;
        }
        if( m_apiClient == null ) {
            if( ! driveAccountName.equals( DEFAULT_DRIVE_ACCOUNT )) {
                // When getting:
                //   googleplayservicesutil﹕ internal error occurred. please see logs for detailed information
                // in the log, worth checking that they certificate's fingerprint is registered with
                // the Developer Console

                Log.d(TAG,"Creating new Drive API for "+driveAccountName);
                m_apiClient = new GoogleApiClient.Builder(this)
                        .addApi(Drive.API)
                        .addScope(Drive.SCOPE_FILE)
                        .setAccountName(driveAccountName)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();
            }
        }

        if( m_apiClient != null ) {
            if( !( m_apiClient.isConnected() )) {
                Log.d(TAG,"Trying to connect Drive API");
                m_apiClient.connect();
            }
        }

        if( m_googleActCrd == null ) {
            Log.d(TAG,"Creating new Account Credential");
            m_googleActCrd = com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
                    .usingOAuth2(this, Arrays.asList(com.google.api.services.drive.DriveScopes.DRIVE_FILE));
        }

        if( m_drvSvc == null ) {
            if( m_googleActCrd != null ) {
                if (! driveAccountName.equals(DEFAULT_DRIVE_ACCOUNT)) {
                    Log.d(TAG,"Selecting credentials for account: "+driveAccountName);
                    m_googleActCrd.setSelectedAccountName(driveAccountName);
                    Log.d(TAG,"Creating new JSON Drive API");
                    m_drvSvc = new com.google.api.services.drive.Drive.Builder(
                            AndroidHttp.newCompatibleTransport(), new GsonFactory(), m_googleActCrd).
                            setApplicationName( APPLICATION_NAME ).
                            build();
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        driveFolderSelectButton.setEnabled(true);
        driveStatus.setText("Connected");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        driveFolderSelectButton.setEnabled(false);
        driveStatus.setText("Suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                m_apiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {

            case REQUEST_RESOLVE_ERROR:
                mResolvingError = false;
                if (resultCode == RESULT_OK) {
                    // Make sure the app is not already connected or attempting to connect
                    if (!m_apiClient.isConnecting() &&
                            !m_apiClient.isConnected()) {
                        m_apiClient.connect();
                    }
                }
                break;
            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    final DriveId driveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);

                    // Store for later
                    m_driveFolderId = driveId.encodeToString();

                    new Thread(new Runnable() {
                        public void run() {
                            DriveFolder driveFolder = Drive.DriveApi.getFolder(m_apiClient, driveId);
                            driveFolder.getMetadata(m_apiClient).setResultCallback(metadataCallback);
                            try {
                                m_drvSvc.files().get(driveId.getResourceId()).execute();
                                // Try to perform a Drive API request, for instance:
                                // File file = service.files().insert(body, mediaContent).execute();
                            } catch (UserRecoverableAuthIOException e) {
                                startActivityForResult(e.getIntent(), COMPLETE_AUTHORIZATION_REQUEST_CODE);
                            } catch (IOException t )
                            {
                                Log.d(TAG,"XX",t.getCause());
                            }
                        }
                    }).start();

                }
                break;
            case COMPLETE_AUTHORIZATION_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG,"Auth OK");
                    // App is authorized, you can go back to sending the API request
                } else {
                    Log.d(TAG,"Auth Fail");
                    // User denied access, show him the account chooser again
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        // Different to the current account name?
                        if( ! accountName.equals( driveAccountName )) {
                            setDriveAccountName( accountName );
                        }
                        Log.d(TAG, "Account Selected:" + accountName);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    final private ResultCallback<MetadataResult> metadataCallback = new
            ResultCallback<MetadataResult>() {
                @Override
                public void onResult(MetadataResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Problem while trying to fetch metadata");
                        return;
                    }
                    Metadata metadata = result.getMetadata();
                    driveFolderText.setText( metadata.getTitle() );
                }
            };

    // The rest of this code is all about building the error dialog

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }



    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((SettingsActivity)getActivity()).onDialogDismissed();
        }
    }
}
