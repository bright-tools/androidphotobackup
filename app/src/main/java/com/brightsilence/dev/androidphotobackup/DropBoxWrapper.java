package com.brightsilence.photobackup;

import android.content.SharedPreferences;
import android.content.Context;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.session.Session.AccessType;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

public class DropBoxWrapper {

    private static final String APP_KEY = "51a8mthvm92an7j";
    private static final String APP_SECRET = "103xmi7wfz0etn0";

    private static final String ACCOUNT_PREFS_NAME = SettingsActivity.PREFERENCES_FILE_KEY+"-DropBox";
    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    private DropboxAPI<AndroidAuthSession> mDBApi;

    private Context mContext;

    DropBoxWrapper( Context pContext )
    {
        mContext = pContext;
        AndroidAuthSession session = buildSession();
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        mDBApi.getSession().startOAuth2Authentication(mContext);
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }

    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = mContext.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setOAuth2AccessToken(secret);
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }
}
