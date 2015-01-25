package com.brightsilence.dev.androidphotobackup;

import java.util.Locale;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class ShowLog extends ActionBarActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_log);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setPageTransformer(true, new DepthPageTransformer());
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // When changing pages, reset the action bar actions since they are dependent
                // on which page is currently active. An alternative approach is to have each
                // fragment expose actions itself (rather than the activity exposing actions),
                // but for simplicity, the activity provides the actions in this sample.
                invalidateOptionsMenu();
            }
        });

        // TODO: Text in ActionBar not correct color
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        PhotoBackupService.clearNotification(getApplicationContext());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_screen_slide, menu);

        menu.findItem(R.id.action_previous).setEnabled(mViewPager.getCurrentItem() > 0);
        menu.findItem(R.id.action_next).setEnabled(mViewPager.getCurrentItem() < (mSectionsPagerAdapter.getCount() - 1) );

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                // Go to the previous step in the wizard. If there is no previous step,
                // setCurrentItem will do nothing.
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
                return true;

            case R.id.action_next:
                // Advance to the next step in the wizard. If there is no next step, setCurrentItem
                // will do nothing.
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

        /**
         * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
         * one of the sections/tabs/pages.
         */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment retVal = null;
            switch( position )
            {
                case 0:
                    retVal = SuccessFragment.newInstance();
                    break;
                default:
                    retVal = FailFragment.newInstance();
                    break;
            }
            return retVal;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class SuccessFragment extends Fragment {
        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static SuccessFragment newInstance() {
            SuccessFragment fragment = new SuccessFragment();
            return fragment;
        }

        public SuccessFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_show_success_log, container, false);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(container.getContext());
            String noneString = getResources().getString(R.string.none);
            ((TextView) rootView.findViewById(R.id.backupResult)).setText(sharedPreferences.getString("last_backup_result",noneString));
            ((TextView) rootView.findViewById(R.id.backupFiles)).setText(sharedPreferences.getString("last_backup_files_uploaded",noneString));

            return rootView;
        }
    }

    public static class FailFragment extends Fragment {
        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static FailFragment newInstance() {
            FailFragment fragment = new FailFragment();
            return fragment;
        }

        public FailFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_show_fail_log, container, false);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(container.getContext());
            String noneString = getResources().getString(R.string.none);
            ((TextView) rootView.findViewById(R.id.failedFiles)).setText(sharedPreferences.getString("last_backup_files_failed",noneString));

            return rootView;
        }
    }
}
