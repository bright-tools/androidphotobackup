package com.brightsilence.photobackup;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
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

public class SettingsActivity extends Activity {

    public static final String PREFERENCES_FILE_KEY = "PhotoBackupPrefsFile";

    private EditText passwordBox;
    private CheckBox showPass;
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

        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        tabHost.addTab(spec3);
    }

    private void loadSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        passwordBox.setText( sharedPref.getString("encryptionPassword","") );
    }

    private void saveSettings()
    {
        SharedPreferences sharedPref = getSharedPreferences(PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("encryptionPassword", passwordBox.getText().toString());
        // TODO: if the password changes, need to re-upload all files using the new password?
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

        loadSettings();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveSettings();
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

    public void onRunNowClick(View v) {
        // TODO: Move this out so that it's no running in the on..Click callback
        ContentResolver contentResolver = getContentResolver();
        for( int i = 0; i < 2 ; i++ ) {
            Uri src;
            if( i == 0 ) {
                src = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
                logView.append("Examining internal media storage");
            }else {
                src = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                logView.append("Examining external media storage");
            }
            Cursor cursor = contentResolver.query(src, null, null, null, null);
            int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            if( cursor.moveToFirst() ) {

                logView.append(cursor.getString(column_index));
            }else{
                logView.append("Didn't find any media");
            }
        }
    }
}
