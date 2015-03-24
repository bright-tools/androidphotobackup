package com.brightsilence.dev.androidphotobackup;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import java.text.DateFormat;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class PhotoBackupSettingsActivity extends PreferenceActivity {

    public static final String TAG = "APB::SettingsActivity";
    public PhotoBackupPreferenceChanged m_prefsListener;

    public static final String PREFERENCES_FILE_KEY = "PhotoBackupPrefsFile";

    private static PhotoBackupAlarmReceiver m_alarm = new PhotoBackupAlarmReceiver();
    private static int m_dropBoxFragmentId = -1;
    private static int m_generalFragmentId = -1;
    private static int m_securityFragmentId = -1;

    private DropBoxWrapper m_dropBoxWrapper = null;
    private String m_lastPassword;

    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;

    private class PhotoBackupSettingsMenuListener implements Toolbar.OnMenuItemClickListener
    {
        PhotoBackupSettingsActivity mActivity;

        public PhotoBackupSettingsMenuListener( PhotoBackupSettingsActivity pActivity )
        {
            mActivity= pActivity;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            mActivity.showAbout();
            return true;
        }
    };

    @Override
    @TargetApi(Build.VERSION_CODES.KITKAT)
    protected boolean isValidFragment(String fragmentName) {
        return GeneralPreferenceFragment.class.getName().equals(fragmentName) ||
               SecurityPreferenceFragment.class.getName().equals(fragmentName) ||
               DropboxPreferenceFragment.class.getName().equals(fragmentName);
    }

    private EditTextPreference getPasswordTextPref()
    {
        EditTextPreference passwordPref = null;
        PreferenceFragment prefFragment = null;

        if( m_securityFragmentId != -1 )
        {
            prefFragment = (PreferenceFragment)getFragmentManager().findFragmentById(m_securityFragmentId);
        }

        if( prefFragment == null ) {
            passwordPref = (EditTextPreference)findPreference("password_text");
        } else {
            passwordPref = (EditTextPreference)prefFragment.findPreference("password_text");
        }
        if( passwordPref == null ) {
            Log.d(TAG,"getPasswordTextPref()::Failed to find preference");
        }

        return passwordPref;
    }

    private SwitchPreference getDailyBackupPref()
    {
        SwitchPreference backupPref = null;
        PreferenceFragment prefFragment = null;

        if( m_generalFragmentId != -1 ) {
            prefFragment = (PreferenceFragment) getFragmentManager().findFragmentById(m_generalFragmentId);
        }

        if( prefFragment == null ) {
            backupPref = (SwitchPreference)findPreference("enable_daily_backup");
        } else {
            backupPref = (SwitchPreference)prefFragment.findPreference("enable_daily_backup");
        }
        if( backupPref == null ) {
            Log.d(TAG,"getDailyBackupPref()::Failed to find preference");
        }

        return backupPref;
    }

    private SwitchPreference getConnectionToDropBoxPref()
    {
        SwitchPreference connectedPref = null;
        PreferenceFragment prefFragment = null;

        if( m_dropBoxFragmentId != -1 ) {
            prefFragment = (PreferenceFragment) getFragmentManager().findFragmentById(m_dropBoxFragmentId);
        }

        if( prefFragment == null ) {
            connectedPref = (SwitchPreference)findPreference("connection_to_dropbox");
        } else {
            connectedPref = (SwitchPreference)prefFragment.findPreference("connection_to_dropbox");
        }
        if( connectedPref == null ) {
            Log.d(TAG,"getConnectionToDropBoxPref()::Failed to find preference");
        }

        return connectedPref;
    }

    private void checkDisableConnectionToDropBoxPref()
    {
        if( m_dropBoxWrapper != null ) {
            if (!m_dropBoxWrapper.isConnected()) {
                SwitchPreference connectedPref = getConnectionToDropBoxPref();

                if (connectedPref != null) {
                    connectedPref.setChecked(false);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(m_prefsListener);

        if( m_dropBoxWrapper != null ) {
            m_dropBoxWrapper.onResume();

            checkDisableConnectionToDropBoxPref();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(m_prefsListener);
    }

    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        LinearLayout content = (LinearLayout) root.getChildAt(0);
        LinearLayout toolbarContainer = (LinearLayout) View.inflate(this, R.layout.layout_settings, null);

        root.removeAllViews();
        toolbarContainer.addView(content);
        root.addView(toolbarContainer);

        mToolbar = (Toolbar) toolbarContainer.findViewById(R.id.toolbar);
        mToolbar.setLogo(R.drawable.ic_launcher);
        mToolbar.setTitle(R.string.app_name);
        mToolbar.inflateMenu(R.menu.menu_settings);
        mToolbar.setOnMenuItemClickListener(new PhotoBackupSettingsMenuListener(this));

        // For some reason, even though the text color is in the theme, AppCompat doesn't seem
        // to pick it up
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            mToolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        }

        m_prefsListener = new PhotoBackupPreferenceChanged(this);
    }

    public void showDisclaimer() {
        View messageView = getLayoutInflater().inflate(R.layout.disclaimer, null, false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                SharedPreferences.Editor prefEd = PreferenceManager.getDefaultSharedPreferences(PhotoBackupSettingsActivity.this).edit();
                prefEd.putInt("disclaimer_accepted_version", BuildConfig.VERSION_CODE);
                prefEd.apply();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                PhotoBackupSettingsActivity.this.finish();
            }
        });
        builder.setTitle(getResources().getString(R.string.important_information));
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    public void showAbout() {
        View messageView = getLayoutInflater().inflate(R.layout.about, null, false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setCancelable(true);
        builder.setTitle(getResources().getString(R.string.about) + " " + getResources().getString(R.string.app_name));
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupSimplePreferencesScreen();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if( sharedPreferences.getBoolean("enable_dropbox_checkbox", false ) &&
            sharedPreferences.getBoolean("connection_to_dropbox", false ) ) {
            createDropBoxWrapper(false);
            checkDisableConnectionToDropBoxPref();
        }

        m_lastPassword = sharedPreferences.getString("password_text","");
        setLastBackupTime();

        if( sharedPreferences.getInt("disclaimer_accepted_version", 0 ) < 1 )
        {
            showDisclaimer();
        }

        updateAlarm();
    }

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        addPreferencesFromResource(R.xml.pref_container);

        // Add 'general' preferences, and a corresponding header.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_general);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_general);

        // Add 'security' preferences, and a corresponding header.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_security);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_security);

        // Add 'dropbox' preferences, and a corresponding header.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_dropbox);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_dropbox);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPasswordPreference(findPreference("show_password_checkbox"));
        findPreference("show_last_log").setOnPreferenceClickListener(new PhotoBackupPreferenceClick());

        bindPreferenceSummaryToLongValue(findPreference("backup_trigger_time"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    private static Preference.OnPreferenceChangeListener sTogglePassword = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            EditTextPreference pref = (EditTextPreference)(preference.getPreferenceManager().findPreference("password_text"));

            int inputType = InputType.TYPE_CLASS_TEXT;

            if( stringValue.equals("true"))
            {
                Log.d(TAG,"Setting password visible");
                inputType |= InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            }
            else
            {
                Log.d(TAG,"Setting password hidden");
                inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
            }
            pref.getEditText().setInputType(inputType);

            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private static void bindPreferenceSummaryToLongValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getLong(preference.getKey(), 0));
    }

    private static void bindPasswordPreference(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sTogglePassword);

        // Trigger the listener immediately with the preference's
        // current value.
        sTogglePassword.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getBoolean(preference.getKey(), false));
    }


    private void updateAlarm() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences( this );
        boolean alarmEnabled = sharedPreferences.getBoolean("enable_daily_backup", false);

        if( alarmEnabled )
        {
            m_alarm.setAlarm( this );
        }
        else
        {
            m_alarm.cancelAlarm(this);
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            m_generalFragmentId = getId();

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToLongValue(findPreference("backup_trigger_time"));
            findPreference("show_last_log").setOnPreferenceClickListener(new PhotoBackupPreferenceClick());
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SecurityPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_security);

            m_securityFragmentId = getId();

            bindPasswordPreference(findPreference("show_password_checkbox"));

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            //bindPreferenceSummaryToValue(findPreference("show_password_checkbox"));
            //bindPreferenceSummaryToValue(findPreference("example_list"));
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DropboxPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_dropbox);

            m_dropBoxFragmentId = getId();

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            //bindPreferenceSummaryToValue(findPreference("example_text"));
            //bindPreferenceSummaryToValue(findPreference("example_list"));
        }
    }

    public static class PhotoBackupPreferenceClick implements Preference.OnPreferenceClickListener{
        @Override
        public boolean onPreferenceClick(Preference arg0) {
            if( arg0.getKey().equals( "show_last_log" ))
            {
                Log.d(TAG,"Show last log received");
                Intent intent = new Intent(arg0.getContext(), ShowLog.class);
                arg0.getContext().startActivity(intent);
            }
            return true;
        }
    }

    public class PhotoBackupPreferenceChanged implements SharedPreferences.OnSharedPreferenceChangeListener {
        public static final String TAG = "APB::PreferenceChanged";

        private PreferenceActivity m_parent;

        public PhotoBackupPreferenceChanged( PreferenceActivity parent )
        {
            super();
            m_parent = parent;
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Log.d(TAG, "onSharedPreferenceChanged : " + key);
            if( key.equals("connection_to_dropbox") )
            {
                if( sharedPreferences.getBoolean( key, false ) )
                {
                    createDropBoxWrapper();
                }
                else
                {
                    destroyDropBoxWrapper();
                }
            } else if( key.equals("enable_dropbox_checkbox")) {
                if( sharedPreferences.getBoolean( key, true ))
                {
                    if( sharedPreferences.getBoolean("connection_to_dropbox", false)) {
                        createDropBoxWrapper();
                    }
                }
            }
            else if( key.equals("enable_daily_backup") ||
                     key.equals("backup_trigger_time" ))
            {
                if( sharedPreferences.getString( "password_text", "").length() == 0 )
                {
                    disableBackupsPwIsEmpty();
                }
                else if( sharedPreferences.getBoolean("enable_dropbox_checkbox", false ) &&
                        sharedPreferences.getBoolean("connection_to_dropbox", false ) ) {
                    updateAlarm();
                }
                else
                {
                    disableBackupsDestUnconfigured();
                }
            }
            else if( key.equals("password_text")) {
                FragmentManager fragmentManager = m_parent.getFragmentManager();
                String newPassword = sharedPreferences.getString( key, "" );

                if( newPassword.length() == 0 )
                {
                    disableBackupsPwIsEmpty();
                    m_lastPassword = newPassword;
                }
                // Is the password being changed, as opposed to initially set?
                else if( ! m_lastPassword.equals("") )
                {
                    Log.d(TAG,"Password change: "+m_lastPassword+" to "+newPassword);
                    if(! m_lastPassword.equals( newPassword )) {
                        ChangePasswordDialogFragment changePasswordDialog = new ChangePasswordDialogFragment();
                        changePasswordDialog.show(fragmentManager, "ChangePasswordDialogFragment");
                        // TODO: Allow user to set the re-upload option
                    }
                }
                else
                {
                    m_lastPassword = newPassword;
                }
            }
            else if( key.equals( "zip_encryption_type" ))
            {
                warnEncryptionChanged();
            }
            else if( key.equals("last_backup_time"))
            {
                setLastBackupTime();
            }
        }
    }

    private void setLastBackupTime()
    {
        SwitchPreference dailyBackupPref = getDailyBackupPref();

        if( dailyBackupPref != null ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            Long lastBackupTime = sharedPreferences.getLong("last_backup_time", 0);

            String lastBackupString = getResources().getString(R.string.last_run)+": ";

            if (lastBackupTime > 0) {
                lastBackupString += DateFormat.getDateTimeInstance().format(lastBackupTime);
            } else {
                lastBackupString += getResources().getString(R.string.never);
            }

            dailyBackupPref.setSummary(lastBackupString);
        }
    }

    private void createDropBoxWrapper( ) {
        createDropBoxWrapper( true );
    }

    private void createDropBoxWrapper( boolean autoConnect ) {
        if( m_dropBoxWrapper == null ) {
            m_dropBoxWrapper = new DropBoxWrapper(this);
        }
        if( autoConnect ) {
            if (!m_dropBoxWrapper.isConnected()) {
                m_dropBoxWrapper.connect();
            }
        }
    }

    private void destroyDropBoxWrapper()
    {
        if( m_dropBoxWrapper != null )
        {
            m_dropBoxWrapper.clearKeys();
            m_dropBoxWrapper = null;
        }
    }

    private void acceptPassword()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        m_lastPassword = sharedPreferences.getString("password_text", "");
    }

    private void resetPassword()
    {
        EditTextPreference pref = getPasswordTextPref();
        if( pref != null ) {
            Log.d(TAG,"Resetting password");
            pref.setText( m_lastPassword);
        } else
        {
            Log.d(TAG,"Couldn't get password text preference");
        }
    }

    private void disableBackups()
    {
        SwitchPreference backupPref = getDailyBackupPref();
        if( backupPref != null )
        {
            backupPref.setChecked(false);
        }
    }

    private void disableBackupsDestUnconfigured()
    {
        CharSequence text = getResources().getString(R.string.dropbox_not_connected_no_backups);

        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();

        disableBackups();
    }

    private void disableBackupsPwIsEmpty()
    {
        CharSequence text = getResources().getString(R.string.no_password_set_no_backups);

        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();

        disableBackups();
    }

    private void warnEncryptionChanged()
    {
        CharSequence text = getResources().getString(R.string.new_password_effect);

        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }

    public class ChangePasswordDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.change_password_warning)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            acceptPassword();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            resetPassword();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
