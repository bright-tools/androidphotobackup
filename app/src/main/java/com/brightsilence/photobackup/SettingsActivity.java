package com.brightsilence.photobackup;

import android.app.Activity;
import android.os.Bundle;
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

public class SettingsActivity extends Activity {


    private EditText passwordBox;
    private CheckBox showPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TabHost tabHost = (TabHost)findViewById(R.id.tabHost);
        tabHost.setup();

        TabSpec spec1=tabHost.newTabSpec("Destination");
        spec1.setContent(R.id.tab1);
        spec1.setIndicator("Destination");

        TabSpec spec2=tabHost.newTabSpec("Security");
        spec2.setIndicator("Security");
        spec2.setContent(R.id.tab2);

        tabHost.addTab(spec1);
        tabHost.addTab(spec2);

        passwordBox = (EditText)findViewById(R.id.editPassword);
        showPass = (CheckBox)findViewById(R.id.checkShowPass);

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
}
