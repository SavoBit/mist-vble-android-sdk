package com.mist.sample.background.utils;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import com.mist.android.AppMode;
import com.mist.android.BatteryUsage;
import com.mist.android.MSTCentralManager;
import com.mist.android.MSTCentralManagerIndoorOnlyListener;
import com.mist.android.MSTOrgCredentialsCallback;
import com.mist.android.MSTOrgCredentialsManager;
import com.mist.android.model.AppModeParams;
import com.mist.sample.background.app.MainApplication;
import com.mist.sample.background.model.OrgData;

import java.lang.ref.WeakReference;
import java.util.Date;

/**
 * Created by anubhava on 02/04/18.
 */

/**
 * This is the interactor class which will interact with Mist SDK for
 * Enrollment
 * starting Mist SDK
 * stopping Mist SDK
 * Reconnection
 * Setting Mode
 */
public class MistManager implements MSTOrgCredentialsCallback {

    private static final String TAG = MistManager.class.getSimpleName();

    private static WeakReference<Application> mApp;
    private static MistManager mistManager;
    private String sdkToken;
    private String envType;
    private OrgData orgData;
    private MSTCentralManagerIndoorOnlyListener indoorOnlyListener;
    private AppMode appMode = AppMode.FOREGROUND;
    private MSTOrgCredentialsManager mstOrgCredentialsManager;
    private volatile MSTCentralManager mstCentralManager;

    private MistManager() {
    }

    /**
     * Custructor for creating singleton instance of the interactor class
     *
     * @param mainApplication application instance needed by Mist SDK
     * @return
     */
    public static MistManager newInstance(MainApplication mainApplication) {
        mApp = new WeakReference<Application>(mainApplication);
        if (mistManager == null) {
            mistManager = new MistManager();
        }
        return mistManager;
    }

    /**
     * This method will enroll the device and start the Mist SDK on successful enrollment, if we already have the deatil of enrollment response detail we can just start the SDK with those details
     *
     * @param sdkToken           Token used for enrollment
     * @param indoorOnlyListener listener on which callback for location,map,notification can be heard
     * @param appMode            mode of the app (Background,Foreground)
     */
    public void init(String sdkToken, MSTCentralManagerIndoorOnlyListener indoorOnlyListener, AppMode appMode) {
        if (sdkToken != null && !sdkToken.isEmpty()) {
            this.sdkToken = sdkToken;
            this.envType = String.valueOf(sdkToken.charAt(0));
            this.indoorOnlyListener = indoorOnlyListener;
            if (appMode != null) {
                this.appMode = appMode;
            }
            orgData = SharedPrefUtils.readConfig(mApp.get(), sdkToken);
            if (orgData == null || orgData.getSdkSecret() == null || orgData.getSdkSecret().isEmpty()) {
                if (mstOrgCredentialsManager == null) {
                    mstOrgCredentialsManager = new MSTOrgCredentialsManager(mApp.get(), this);
                }
                mstOrgCredentialsManager.enrollDeviceWithToken(sdkToken);

            } else {
                connect(indoorOnlyListener, appMode);
            }
        } else {
            Log.d(TAG, "Empty SDK Token");
        }
    }

    /**
     * This method is used to start the Mist SDk
     *
     * @param indoorOnlyListener listener on which callback for location,map,notification can be heard
     * @param appMode            mode of the app (Background,Foreground)
     */
    private synchronized void connect(MSTCentralManagerIndoorOnlyListener indoorOnlyListener, AppMode appMode) {
        if (mstCentralManager == null) {
            mstCentralManager = new MSTCentralManager(mApp.get(),
                    orgData.getOrgId(),
                    orgData.getSdkSecret(),
                    indoorOnlyListener);
            try{
                mstCentralManager.setEnvironment(Utils.getEnvironment(envType));
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
                return;
            }
            if (appMode.equals(AppMode.FOREGROUND)) {
                setAppMode(new AppModeParams(AppMode.FOREGROUND,
                        BatteryUsage.HIGH_BATTERY_USAGE_HIGH_ACCURACY,
                        true,
                        0.5,
                        1));
            } else {
                ///
                setAppMode(new AppModeParams(AppMode.BACKGROUND,
                        BatteryUsage.LOW_BATTERY_USAGE_LOW_ACCURACY,
                        true,
                        0.5,
                        1));
            }
            mstCentralManager.start();
        } else {
            reconnect();
        }
    }

    /**
     * @param appModeParams params to let SDK know about the scanning frequency and the state of the app (background or foreground)
     *                      call this method to switch the mode when app changes the mode between foreground and background
     */
    public void setAppMode(AppModeParams appModeParams) {
        if (this.mstCentralManager != null) {
            this.mstCentralManager.setAppMode(appModeParams);
            this.appMode = appModeParams.getAppMode();
        }
    }

    /**
     * This is the callback method which will receive the following information from the Mist SDK enrollment call
     *
     * @param orgName   name of the token used for the enrollment
     * @param orgID     organization id
     * @param sdkSecret secret needed to start the Mist SDK
     * @param error     error message if any
     * @param envType   envType which will be used to set the environment
     */
    @Override
    public void onReceivedSecret(String orgName, String orgID, String sdkSecret, String error, String envType) {
        if (!TextUtils.isEmpty(sdkSecret) && !TextUtils.isEmpty(orgID) && !TextUtils.isEmpty(sdkSecret)) {
            saveConfig(orgName, orgID, sdkSecret, envType);
            connect(indoorOnlyListener, appMode);
        } else {
            if (!Utils.isEmptyString(error)) {
                if (indoorOnlyListener != null) {
                    indoorOnlyListener.onMistErrorReceived(error, new Date());
                }
            }
        }
    }

    /**
     * This method is saving the following details so that we can use it again for starting Mist SDK without need for enrollment again
     *
     * @param orgName   name of the token used for the enrollment
     * @param orgID     organization id
     * @param sdkSecret secret needed to start the Mist SDK
     * @param envType   envType which will be used to set the environment
     */
    private void saveConfig(String orgName, String orgID, String sdkSecret, String envType) {
        orgData = new OrgData(orgName, orgID, sdkSecret, envType);
        SharedPrefUtils.saveConfig(mApp.get(), orgData, sdkToken);
    }


    /**
     * This method will stop the Mist SDK
     */
    public void disconnect() {
        if (mstCentralManager != null) {
            mstCentralManager.stop();
        }
    }

    /**
     * This method will reconnect he Mist SDK
     */
    private synchronized void reconnect() {
        if (mstCentralManager != null) {
            disconnect();
            mstCentralManager.setMSTCentralManagerIndoorOnlyListener(indoorOnlyListener);
            mstCentralManager.start();
        }
    }

    /**
     * This method will clear/destroy the Mist SDK instance
     */
    public synchronized void destroy() {
        if (mstCentralManager != null) {
            mstCentralManager.stop();
            mstCentralManager = null;
        }
    }
}
