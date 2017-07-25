package com.uricul.gametimer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by uricul on 2017. 7. 13..
 */

public class PermissionUtils {
    private static final Map<String, Integer> PERMISSION_STRING_MAP = new HashMap<>();
    static {
        PERMISSION_STRING_MAP.put(Manifest.permission.RECEIVE_SMS, R.string.permission_group_sms);
        PERMISSION_STRING_MAP.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_group_storage);
    }

    public interface PermissionCallbacks {
        /**
         * If requested permissions is granted 100% successful
         * @param requestCode
         */
        void onPermissionsGranted(int requestCode);

        /**
         * Report denied permissions, if any.
         * @param requestCode
         * @param isPermanentlyDenied
         */
        void onPermissionsDenied(int requestCode, List<String> deniedPermissions, boolean isPermanentlyDenied);
    }

    private PermissionUtils() {}

    public static final String[] MUST_HAVE_PERMISSIONS = new String[]{Manifest.permission.RECEIVE_SMS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static boolean hasMustHavePermissions(@NonNull Context context) {
        return hasPermissions(context, MUST_HAVE_PERMISSIONS);
    }

    public static boolean hasPermissions(@NonNull Context context, @NonNull String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Request a set of permissions, showing rationale if the system requests it.
     *
     * @param object
     *         Activity or Fragment requesting permissions. Should implement
     *         {@link android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback}
     *         or {@code android.support.v13.app.FragmentCompat.OnRequestPermissionsResultCallback}
     * @param rationale
     *         a message explaining why the application needs this set of permissions, will be displayed if the user rejects the request the first
     *         time.
     * @param requestCode
     *         request code to track this request, must be < 256.
     * @param perms
     *         a set of permissions to be requested.
     */
    @SuppressLint("NewApi")
    public static void requestPermissions(@NonNull final Object object, @StringRes int rationale,
                                           final int requestCode, @NonNull final String... perms) {

        checkCallingObjectSuitability(object);

        // Determine if rationale should be shown (generally when the user has previously
        // denied the request);
        boolean shouldShowRationale = false;
        for (String perm : perms) {
            shouldShowRationale =
                    shouldShowRationale || shouldShowRequestPermissionRationale(object, perm);
        }

        if (shouldShowRationale) {
            new AlertDialog.Builder(getActivity(object))
                    .setCancelable(false)
                    .setMessage(rationale)
                    .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            executePermissionsRequest(object, perms, requestCode);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (object instanceof PermissionCallbacks) {
                                ((PermissionCallbacks) object).onPermissionsDenied(requestCode, Arrays.asList(perms), false);
                            }
                        }
                    })
                    .show();
        } else {
            executePermissionsRequest(object, perms, requestCode);
        }
    }

    /**
     * Handle the result of a permission request, should be called from the calling Activity's {@link android.support.v4.app.ActivityCompat
     * .OnRequestPermissionsResultCallback#onRequestPermissionsResult(int, String[], int[])} method.
     * <p>
     * If any permissions were granted or denied, the {@code object} will receive the appropriate callbacks through {@link PermissionCallbacks} and
     * methods annotated with {@link AfterPermissionGranted} will be run if appropriate.
     *
     * @param requestCode
     *         requestCode argument to permission result callback.
     * @param permissions
     *         permissions argument to permission result callback.
     * @param grantResults
     *         grantResults argument to permission result callback.
     * @param receivers
     *         an array of objects that have a method annotated with {@link AfterPermissionGranted} or implement {@link PermissionCallbacks}.
     */
    public static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                  @NonNull int[] grantResults,
                                                  @NonNull Object... receivers) {
        // Make a collection of granted and denied permissions from the request.
        ArrayList<String> granted = new ArrayList<>();
        ArrayList<String> denied = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            String perm = permissions[i];
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                granted.add(perm);
            } else {
                denied.add(perm);
            }
        }

        // iterate through all receivers
        for (Object object : receivers) {
            // If 100% successful
            if (!granted.isEmpty() && denied.isEmpty()) {
                if (object instanceof PermissionCallbacks)
                    ((PermissionCallbacks) object).onPermissionsGranted(requestCode);
            }

            // Report denied permissions, if any.
            if (!denied.isEmpty()) {
                if (object instanceof PermissionCallbacks) {
                    ((PermissionCallbacks) object).onPermissionsDenied(requestCode, denied, true);
                }
            }
        }
    }

    private static void checkCallingObjectSuitability(@Nullable Object object) {
        if (object == null) {
            throw new NullPointerException("Activity or Fragment should not be null");
        }
        // Make sure Object is an Activity or Fragment
        boolean isActivity = object instanceof android.app.Activity;
        boolean isSupportFragment = object instanceof android.support.v4.app.Fragment;
        boolean isAppFragment = object instanceof android.app.Fragment;
        boolean isMinSdkM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        if (!(isSupportFragment || isActivity || (isAppFragment && isMinSdkM))) {
            if (isAppFragment) {
                throw new IllegalArgumentException(
                        "Target SDK needs to be greater than 23 if caller is android.app.Fragment");
            } else {
                throw new IllegalArgumentException("Caller must be an Activity or a Fragment.");
            }
        }
    }

    @TargetApi(23)
    private static boolean shouldShowRequestPermissionRationale(@NonNull Object object,
                                                                @NonNull String perm) {
        if (object instanceof Activity) {
            return ActivityCompat.shouldShowRequestPermissionRationale((Activity) object, perm);
        } else if (object instanceof Fragment) {
            return ((Fragment) object).shouldShowRequestPermissionRationale(perm);
        } else if (object instanceof android.app.Fragment) {
            return ((android.app.Fragment) object).shouldShowRequestPermissionRationale(perm);
        } else {
            return false;
        }
    }

    @TargetApi(23)
    private static void executePermissionsRequest(@NonNull Object object, @NonNull String[] perms, int requestCode) {
        checkCallingObjectSuitability(object);
        if (object instanceof android.app.Activity) {
            ActivityCompat.requestPermissions((Activity) object, perms, requestCode);
        } else if (object instanceof android.support.v4.app.Fragment) {
            ((android.support.v4.app.Fragment) object).requestPermissions(perms, requestCode);
        } else if (object instanceof android.app.Fragment) {
            ((android.app.Fragment) object).requestPermissions(perms, requestCode);
        }
    }

    @TargetApi(11)
    private static Activity getActivity(@NonNull Object object) {
        if (object instanceof Activity) {
            return ((Activity) object);
        } else if (object instanceof android.support.v4.app.Fragment) {
            return ((android.support.v4.app.Fragment) object).getActivity();
        } else if (object instanceof android.app.Fragment) {
            return ((android.app.Fragment) object).getActivity();
        } else {
            return null;
        }
    }
}
