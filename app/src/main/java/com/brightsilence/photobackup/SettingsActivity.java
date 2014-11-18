package com.brightsilence.photobackup;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
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
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentResolver;
import android.net.Uri;

import android.util.Log;

import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.DialogInterface;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.*;
import com.google.android.gms.drive.*;
import com.google.android.gms.common.GooglePlayServicesUtil;
import android.app.Dialog;
import android.support.v4.app.*;

import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.widget.Toast;

public class SettingsActivity extends FragmentActivity  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {

    public static final String PREFERENCES_FILE_KEY = "PhotoBackupPrefsFile";

    public static final String TAG = "PhotoBackup::SettingsActivity";

    public static final String DEFAULT_DRIVE_FOLDER = "(none)";

    private EditText passwordBox;
    private CheckBox showPass;
    private TextView driveStatus;
    private TextView driveFolder;
    private EditText logView;

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

    private void loadSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        passwordBox.setText( sharedPref.getString("encryptionPassword","") );
        driveFolder.setText( sharedPref.getString("driveFolder",DEFAULT_DRIVE_FOLDER) );
    }

    private void saveSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        // TODO: if the password changes, need to re-upload all files using the new password?
        editor.putString("encryptionPassword", passwordBox.getText().toString());
        editor.putString("driveFolder", driveFolder.getText().toString());
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
        driveFolder = (TextView)findViewById(R.id.textCurrentDriveFolder);

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

        Log.d(TAG, "XX" + GooglePlayServicesUtil.isGooglePlayServicesAvailable(this));

        loadSettings();
        setupApis();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            apiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        saveSettings();
        apiClient.disconnect();
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

        String driveFolderStr = driveFolder.getText().toString();

        OpenFileActivityBuilder openFileBuilder = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[] { DriveFolder.MIME_TYPE });

        if( !driveFolderStr.equals( DEFAULT_DRIVE_FOLDER )) {
            openFileBuilder.setActivityStartFolder( DriveId.decodeFromString( driveFolderStr ));
        }

        IntentSender intentSender = openFileBuilder.build(apiClient);
        try {
            startIntentSenderForResult(
                    intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }

    public void onRunNowClick(View v) {
        // TODO: Move this out so that it's no running in the on..Click callback
        ContentResolver contentResolver = getContentResolver();
        for( int i = 0; i < 2 ; i++ ) {
            Uri src;
            if( i == 0 ) {
                src = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
                logView.append("Examining internal media storage\n");
            } else {
                src = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                logView.append("Examining external media storage\n");
            }
            Cursor cursor = contentResolver.query(src, null, null, null, null);
            int displayNameColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            int dateModColIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);
            if( cursor.moveToFirst() ) {
                do {
                    logView.append("Found media: " + cursor.getString(displayNameColIdx) + ", modified "+cursor.getString(dateModColIdx)+"\n");
                } while( cursor.moveToNext() );
            }else{
                logView.append("Didn't find any media\n");
            }
        }
    }

    /**
     * Shows a toast message.
     */
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }


    private GoogleApiClient apiClient;
    private boolean mResolvingError = false;
    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final int REQUEST_CODE_OPENER = 1002;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";

    public void setupApis()
    {
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        driveStatus.setText("Connected");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        driveStatus.setText("Suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d( "XX", "YY" + result );
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                apiClient.connect();
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
                    if (!apiClient.isConnecting() &&
                            !apiClient.isConnected()) {
                        apiClient.connect();
                    }
                }
                break;
            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    DriveId driveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    driveFolder.setText( driveId.toString() );
                    showMessage("Selected folder's ID: " + driveId);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

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
