package com.example.gametimer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.LruCache;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Message;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity implements ActionBar.TabListener {
    // Preference Key
    private static final String[] PREFERENCE_KEY_NAME = { "timer1_name", "timer2_name", "timer3_name" };
    private static final String[] PREFERENCE_KEY_TIMER_TIMEOUT = { "timer1_timeout", "timer2_timeout", "timer3_timeout" };
    private static final String[] PREFERENCE_KEY_TIMER_CURRTIME = { "timer1_currtime", "timer2_currtime", "timer3_currtime" };
    private static final String[] PREFERENCE_KEY_TIMER_RUNNING_FLAG = { "timer1_running_flag", "timer2_running_flag", "timer3_running_flag" };
    private static final String[] PREFERENCE_KEY_TIMER_START_TIME = { "timer1_start_time", "timer2_start_time", "timer3_start_time" };

    // Profile Photo File Name
    private static final String DIRECTORY_NAME = "/GameTimer/";
    private static final String[] PROFILE_PHOTO_FILE_NAME = { "timer1_photo.jpg", "timer2_photo.jpg", "timer3_photo.jpg" };

    // Intent Key
    public static final String KEY_CONTROL_TIMER = "controlTimer";
    public static final String KEY_TIMER_INDEX = "timerIndex";
    public static final String[] CONTROL_CMD = { "Start", "Stop" };

    private static final int REQUEST_CODE_PICK_PICTURE = 0;

    private static final int[] DEFAULT_TIMER_TIMEOUT = { 60, 60, 60 };      // 기본 60분

    private static final int SECONDS_PER_MINUTE = 60;

    private static final int NOTIFICATION_DURATION = 10; // 10분

    private static final int[] TIMER_INDEX = { 0, 1, 2 };

    private static final int MAX_TIMER_VALUE = 9;   // 99분 ~ 30분
    private static final int MIN_TIMER_VALUE = 0;
    private static final int MIN_TIMER = 30;        // 30분이 최소값

    private static final int NOTIFICATION_TIMER_PERIOD = 0;

    private static final String simpleDateFormat = "yyyy.MM.dd";
    private static final String detailDateFormat = "HH:mm:ss";

    private static MainActivity mContext;

    private static boolean isTimerDataRecovery = false;

    private static ViewPager mViewPager;
    private static TabsPagerAdapter mPagerAdapter;
    private ActionBar mActionBar;

    private static SharedPreferences mSharedPreferences;
    private static Timer[] mGameTimer;

    private static TimerTask[] mGameTimerTimeoutTask;

    private static boolean[] mTimerRunFlag;

    private static String[] mTimerName;

    // Timer별 Timeout 시간
    private static int[] mTimerTimeout;

    // Timer별 현재진행 상태 백업 & 복구
    private static int[] mTimerCurrTime;

    private static InputMethodManager mInputMethodManager;

    private static NotificationManager mNotificationManager;
    private static int mNotificationCounter;

    private static LogDBAdapter mDBAdapter;

    private SMSControlTimer mSmsControlTimer;

    // Bitmap Memory Cache From Google LruCache
    private static LruCache<String, Bitmap> mMemoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("GameTimer", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mInputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationCounter = 0;

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

        // Log DB
        mDBAdapter = new LogDBAdapter(mContext);
        try {
            mDBAdapter.open();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mViewPager = (ViewPager) findViewById(R.id.main_pager);
        mPagerAdapter = new TabsPagerAdapter(getSupportFragmentManager());

        mViewPager.setAdapter(mPagerAdapter);

        // Timer 관련 정보 초기화 및 복원
        initTimerName();
        initTimerData();
        initTimerStatus();
        isTimerDataRecovery = true;

        setupTabs();
        //if (savedInstanceState == null) {
        //    getSupportFragmentManager().beginTransaction()
        //            .add(R.id.container, new PlaceholderFragment())
        //            .commit();
        //}

        /**
         * On Swiping The ViewPager Make Respective Tab Selected
         */
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                mActionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d("GameTimer", "onNewIntent");
        super.onNewIntent(intent);

        String cmd = intent.getStringExtra(KEY_CONTROL_TIMER);
        if( cmd == null ) {
            Log.d("GameTimer", "onNewIntent from Notification");
            mNotificationCounter = 0;    // Notification으로 진입시 Counter 초기화
        } else {
            Log.d("GameTimer", "onNewIntent from SMS Control[" + cmd + "]");

            int timerIndex = intent.getIntExtra(KEY_TIMER_INDEX, -1);
            if( cmd.equals(CONTROL_CMD[0]) && isValidTimerIndex(timerIndex) ) {          // Start Timer Command
                mViewPager.setCurrentItem(0);

                if (!mTimerRunFlag[timerIndex]) {
                    mSmsControlTimer.onControlTimerBySMS(timerIndex);
                } else {
                    Log.d("GameTimer", "SMS Control[" + cmd + "] Ignore");
                }
            } else if( cmd.equals(CONTROL_CMD[1]) && isValidTimerIndex(timerIndex) ) {   // Stop TImer Command
                mViewPager.setCurrentItem(0);

                if (mTimerRunFlag[timerIndex]) {
                    mSmsControlTimer.onControlTimerBySMS(timerIndex);
                } else {
                    Log.d("GameTimer", "SMS Control[" + cmd + "] Ignore");
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d("GameTimer", "onDestroy");
        super.onDestroy();
        // Timer 관련 정보 저장
        saveTimerData();

        clearTimer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            mViewPager.setCurrentItem(2);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, getString(R.string.back_press_ignore_msg), Toast.LENGTH_SHORT).show();
    }

    private void setupTabs() {
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        mActionBar.setDisplayShowTitleEnabled(true);

        Tab tab1 = mActionBar
                .newTab()
                .setText(R.string.tab1_title)
                .setIcon(R.drawable.tab1_ico)
                .setTag(getString(R.string.tab1_tag))
                .setTabListener(this);

        mActionBar.addTab(tab1);

        Tab tab2 = mActionBar
                .newTab()
                .setText(R.string.tab2_title)
                .setIcon(R.drawable.tab2_ico)
                .setTag(getString(R.string.tab2_tag))
                .setTabListener(this);

        mActionBar.addTab(tab2);

        Tab tab3 = mActionBar
                .newTab()
                .setText(R.string.tab3_title)
                .setIcon(R.drawable.tab3_ico)
                .setTag(getString(R.string.tab3_tag))
                .setTabListener(this);

        mActionBar.addTab(tab3);
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction fragmentTransaction) {
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction fragmentTransaction) {

    }

    private void initTimerData() {
        // Restore Timer Timeout (분)
        mTimerTimeout = new int[TIMER_INDEX.length];    // 초기화
        mTimerCurrTime = new int[TIMER_INDEX.length];   // 초기화

        mGameTimer = new Timer[TIMER_INDEX.length];
        mGameTimerTimeoutTask = new TimerTask[TIMER_INDEX.length];

        for( int i = 0; i < TIMER_INDEX.length; i++ ) {
            // Restore Timer Timeout (분)
            mTimerTimeout[i] = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER_TIMEOUT[i], DEFAULT_TIMER_TIMEOUT[i]);

            // Restore Current Timer Value (초)
            mTimerCurrTime[i] = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER_CURRTIME[i], DEFAULT_TIMER_TIMEOUT[i] * SECONDS_PER_MINUTE);
        }
    }

    private static void resetTimerData(boolean initData) {
        // 초기값으로
        if( initData ) {
            for( int i = 0; i < TIMER_INDEX.length; i++ )
                mTimerTimeout[i] = DEFAULT_TIMER_TIMEOUT[i];
        }

        // Reset Current Timer Value (초)
        for( int i = 0; i < TIMER_INDEX.length; i++ )
            mTimerCurrTime[i] = mTimerTimeout[i] * SECONDS_PER_MINUTE;
    }

    private static void saveTimerData() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        // Save Timer Timeout (분)
        for( int i = 0; i < TIMER_INDEX.length; i++ ) {
            // Save Timer Timeout (분)
            if( mTimerTimeout[i] > 0 )
                editor.putInt(PREFERENCE_KEY_TIMER_TIMEOUT[i], mTimerTimeout[i]);
            else
                editor.putInt(PREFERENCE_KEY_TIMER_TIMEOUT[i], DEFAULT_TIMER_TIMEOUT[i]);

            // Save Current Timer (초)
            if( mTimerCurrTime[i] > 0 )
                editor.putInt(PREFERENCE_KEY_TIMER_CURRTIME[i], mTimerCurrTime[i]);
            else
                editor.putInt(PREFERENCE_KEY_TIMER_CURRTIME[i], DEFAULT_TIMER_TIMEOUT[i] * SECONDS_PER_MINUTE);
        }

        editor.commit();
    }

    private static void saveTimerData(int index) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if( isValidTimerIndex(index) ) {
            editor.putInt(PREFERENCE_KEY_TIMER_CURRTIME[index], mTimerCurrTime[index]);
            editor.commit();
        } else {
            Log.d("GameTimer", "saveTimerData : Invalid Timer Index[" + String.valueOf(index) + "]" );
        }
    }

    private void initTimerName() {
        // Restore Timer Name
        mTimerName = new String[TIMER_INDEX.length];    // 초기화
        for( int i = 0; i < TIMER_INDEX.length; i++ )
            mTimerName[i] = mSharedPreferences.getString(PREFERENCE_KEY_NAME[i], getDefaultTimerName(i));
    }

    private static void saveTimerName(int index, String name) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if( isValidTimerIndex(index) ) {
            mTimerName[index] = name;
            editor.putString(PREFERENCE_KEY_NAME[index], mTimerName[index]);
            editor.commit();
        } else {
            Log.d("GameTimer", "saveTimerData : Invalid Timer Index[" + String.valueOf(index) + "]" );
        }
    }

    private static void saveTimerTime(int index, int timeValue) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if( isValidTimerIndex(index) ) {
            int diff = (timeValue - mTimerTimeout[index]) * SECONDS_PER_MINUTE;
            mTimerCurrTime[index] += diff;
            if( mTimerCurrTime[index] < 0 )
                mTimerCurrTime[index] = 0;
            editor.putInt(PREFERENCE_KEY_TIMER_CURRTIME[index], mTimerCurrTime[index]);

            mTimerTimeout[index] = timeValue;
            editor.putInt(PREFERENCE_KEY_TIMER_TIMEOUT[index], mTimerTimeout[index]);
            editor.commit();
        }
    }

    private void initTimerStatus() {
        // Restore Timer Status
        long startTime;
        long currTime = (new Date().getTime())/1000;    // 초단위로

        mTimerRunFlag = new boolean[TIMER_INDEX.length];    // 초기화
        for( int i = 0; i < TIMER_INDEX.length; i++ ) {
            mTimerRunFlag[i] = mSharedPreferences.getBoolean(PREFERENCE_KEY_TIMER_RUNNING_FLAG[i], false);

            if (mTimerRunFlag[i]) {
                startTime = mSharedPreferences.getLong(PREFERENCE_KEY_TIMER_START_TIME[i], currTime);

                if ((currTime - startTime) > 0) {
                    mTimerCurrTime[i] -= (currTime - startTime);
                    if (mTimerCurrTime[i] < 0)
                        mTimerCurrTime[i] = 0;
                }
            }
        }
    }

    private static void saveTimerStatus(int index) {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        if( isValidTimerIndex(index) ) {
            editor.putBoolean(PREFERENCE_KEY_TIMER_RUNNING_FLAG[index], mTimerRunFlag[index]);
            editor.putLong(PREFERENCE_KEY_TIMER_START_TIME[index], (new Date().getTime()) / 1000);
            editor.commit();
        } else {
            Log.d("GameTimer", "saveTimerStatus : Invalid Timer Index[" + String.valueOf(index) + "]" );
        }
    }

    private static void clearTimer() {
        for( int i = 0; i < TIMER_INDEX.length; i++ ) {
            if( mGameTimer[i] != null ) {
                if( mGameTimerTimeoutTask[i] != null )
                    mGameTimerTimeoutTask[i].cancel();
                mGameTimer[i].cancel();
                mGameTimer[i] = null;
                mGameTimerTimeoutTask = null;
            }
        }
    }

    private static String getTimerName(int index) {
        if( isValidTimerIndex(index) ) {
            return mTimerName[index];
        } else {
            Log.d("GameTimer", "getTimerName : Invalid Timer Index[" + String.valueOf(index) + "]" );
            return null;
        }
    }

    private String getDefaultTimerName(int index) {
        String defaultTimerName = null;

        if( index == TIMER_INDEX[0] )
            defaultTimerName = getString(R.string.name1_default);
        else if( index == TIMER_INDEX[1])
            defaultTimerName = getString(R.string.name2_default);
        else if( index == TIMER_INDEX[2])
            defaultTimerName = getString(R.string.name3_default);

        return defaultTimerName;
    }

    private static boolean isValidTimerIndex(int index) {
        boolean result = false;

        if( (TIMER_INDEX[0] <= index) && (index <= TIMER_INDEX[TIMER_INDEX.length-1]) )
            result = true;

        return result;
    }

    public interface SMSControlTimer {
        public void onControlTimerBySMS(int timerIndex);
    }

    /**
     * ViewPager Adapter
     */
    public class TabsPagerAdapter extends FragmentPagerAdapter {
        public TabsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int index) {
            switch( index ) {
                case 0:
                    Fragment fragment = new MainTimerFragment();
                    try {
                        mSmsControlTimer = (SMSControlTimer) fragment;
                    } catch( ClassCastException e ) {
                        Log.d("GameTimer", "TabsPagerAdapter: SMSControlTimer Not Implement" + e.getMessage() );
                        throw new ClassCastException(fragment.toString() + "must implement SMSControlTimer");
                    }
                    return fragment;

                case 1:
                    return new TimerSettingFragment();

                case 2:
                    return new SettingFragment();
            }

            return null;
        }

        @Override
        public int getCount() {
            return 3;               // Tab 개수
        }

        @Override
        public int getItemPosition(Object object) {
            if( object instanceof MainTimerFragment ) {
                Log.d("GameTimer", "TabsPagerAdapter : " + ((MainTimerFragment)object).getTag());
                return POSITION_NONE;
            }
            else if( object instanceof TimerSettingFragment ) {
                Log.d("GameTimer", "TabsPagerAdapter : " + ((TimerSettingFragment)object).getTag());
                return POSITION_UNCHANGED;
            } else {
                Log.d("GameTimer", "TabsPagerAdapter : " + ((SettingFragment)object).getTag());
                return POSITION_UNCHANGED;
            }
        }
    }

    /**
     *  LruCache : Memory Cache for Bitmap Image
     */
    public static void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if( getBitmapFromMemCache(key) == null ) {
            Log.d("GameTimer", "addBitmapToMemoryCache : " + key);
            mMemoryCache.put(key, bitmap);
        } else {
            Log.d("GameTimer", "addBitmapToMemoryCache : (Update) " + key);
            mMemoryCache.remove(key);
            mMemoryCache.put(key, bitmap);
        }
    }

    public static void clearBitmapFromMemCache(String key) {
        if( getBitmapFromMemCache(key) != null ) {
            Log.d("GameTimer", "clearBitmapFromMemCache : " + key);
            mMemoryCache.remove(key);
        }
    }

    public static Bitmap getBitmapFromMemCache(String key) {
        Log.d("GameTimer", "getBitmapFromMemCache : " + key);
        return mMemoryCache.get(key);
    }

    /**
     * A MainTimer Fragment
     */
    public static class MainTimerFragment extends Fragment implements View.OnClickListener, SMSControlTimer {
        private View mName1Layout;
        private View mName2Layout;
        private View mName3Layout;

        private ImageView mPhoto1;
        private ImageView mPhoto2;
        private ImageView mPhoto3;

        private TextView mName1;
        private TextView mName2;
        private TextView mName3;

        private View mStart1Button;
        private View mStart2Button;
        private View mStart3Button;

        private TextView mTimer1Time;
        private TextView mTimer2Time;
        private TextView mTimer3Time;

        private TextView mTimer1Curr;
        private TextView mTimer2Curr;
        private TextView mTimer3Curr;

        public MainTimerFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Log.d("GameTimer", "MainTimerFragment onCreateView");
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            initView(rootView);
            initViewTimer();

            return rootView;
        }

        @Override
        public void onDetach() {
            super.onDetach();
        }

        @Override
        public void onControlTimerBySMS(int timerIndex) {
            toggleGameTimer(timerIndex);
        }

        private void initView(View rootView) {
            mName1Layout = rootView.findViewById(R.id.name1_layout);
            mName2Layout = rootView.findViewById(R.id.name1_layout);
            mName3Layout = rootView.findViewById(R.id.name1_layout);

            mPhoto1 = (ImageView) rootView.findViewById(R.id.photo1);
            mPhoto2 = (ImageView) rootView.findViewById(R.id.photo2);
            mPhoto3 = (ImageView) rootView.findViewById(R.id.photo3);

            mName1 = (TextView) rootView.findViewById(R.id.name1);
            mName2 = (TextView) rootView.findViewById(R.id.name2);
            mName3 = (TextView) rootView.findViewById(R.id.name3);

            mStart1Button = rootView.findViewById(R.id.start1);
            mStart2Button = rootView.findViewById(R.id.start2);
            mStart3Button = rootView.findViewById(R.id.start3);

            mTimer1Time = (TextView) rootView.findViewById(R.id.timer1);
            mTimer2Time = (TextView) rootView.findViewById(R.id.timer2);
            mTimer3Time = (TextView) rootView.findViewById(R.id.timer3);

            mTimer1Curr = (TextView) rootView.findViewById(R.id.remaintimer1);
            mTimer2Curr = (TextView) rootView.findViewById(R.id.remaintimer2);
            mTimer3Curr = (TextView) rootView.findViewById(R.id.remaintimer3);

            mStart1Button.setOnClickListener(this);
            mStart2Button.setOnClickListener(this);
            mStart3Button.setOnClickListener(this);
        }

        private void initViewTimer() {
            PhotoBitmapLoadTask task1 = new PhotoBitmapLoadTask();
            task1.execute(TIMER_INDEX[0]);

            PhotoBitmapLoadTask task2 = new PhotoBitmapLoadTask();
            task2.execute(TIMER_INDEX[1]);

            PhotoBitmapLoadTask task3 = new PhotoBitmapLoadTask();
            task3.execute(TIMER_INDEX[2]);

            mName1.setText(mTimerName[TIMER_INDEX[0]]);
            mName2.setText(mTimerName[TIMER_INDEX[1]]);
            mName3.setText(mTimerName[TIMER_INDEX[2]]);

            String timer1Timeout = String.format(getString(R.string.timer_field), mTimerTimeout[TIMER_INDEX[0]]);
            String timer2Timeout = String.format(getString(R.string.timer_field), mTimerTimeout[TIMER_INDEX[1]]);
            String timer3Timeout = String.format(getString(R.string.timer_field), mTimerTimeout[TIMER_INDEX[2]]);
            mTimer1Time.setText(timer1Timeout);
            mTimer2Time.setText(timer2Timeout);
            mTimer3Time.setText(timer3Timeout);

            String timer1Time = String.format(getString(R.string.remain_timer_filed), mTimerCurrTime[TIMER_INDEX[0]]/SECONDS_PER_MINUTE, mTimerCurrTime[TIMER_INDEX[0]]%SECONDS_PER_MINUTE);
            String timer2Time = String.format(getString(R.string.remain_timer_filed), mTimerCurrTime[TIMER_INDEX[1]]/SECONDS_PER_MINUTE, mTimerCurrTime[TIMER_INDEX[1]]%SECONDS_PER_MINUTE);
            String timer3Time = String.format(getString(R.string.remain_timer_filed), mTimerCurrTime[TIMER_INDEX[2]]/SECONDS_PER_MINUTE, mTimerCurrTime[TIMER_INDEX[2]]%SECONDS_PER_MINUTE);
            mTimer1Curr.setText(timer1Time);
            mTimer2Curr.setText(timer2Time);
            mTimer3Curr.setText(timer3Time);

            // Timer Status 설정
            initTimerButton();
            if( isTimerDataRecovery ) {
                initTimerStatus();
                isTimerDataRecovery = false;
            }
        }

        private void initTimerButton() {
            if(  mTimerRunFlag[TIMER_INDEX[0]] ) {
                ((TextView)mStart1Button).setText(R.string.timer_stop);
                mStart1Button.setBackgroundResource(R.drawable.stop_btn);
            } else {
                ((TextView)mStart1Button).setText(R.string.timer_start);
                mStart1Button.setBackgroundResource(R.drawable.start_btn);
            }

            if(  mTimerRunFlag[TIMER_INDEX[1]] ) {
                ((TextView)mStart2Button).setText(R.string.timer_stop);
                mStart2Button.setBackgroundResource(R.drawable.stop_btn);
            } else {
                ((TextView)mStart2Button).setText(R.string.timer_start);
                mStart2Button.setBackgroundResource(R.drawable.start_btn);
            }

            if(  mTimerRunFlag[TIMER_INDEX[2]] ) {
                ((TextView)mStart3Button).setText(R.string.timer_stop);
                mStart3Button.setBackgroundResource(R.drawable.stop_btn);
            } else {
                ((TextView)mStart3Button).setText(R.string.timer_start);
                mStart3Button.setBackgroundResource(R.drawable.start_btn);
            }
        }

        private void initTimerStatus() {
            for( int i = 0; i < TIMER_INDEX.length; i++ ) {
                if( mTimerRunFlag[i] )
                    initGameTimer(i);
            }
        }

        @Override
        public void onClick(View v) {
            switch( v.getId() ) {
                case R.id.start1:
                    toggleGameTimer(TIMER_INDEX[0]);
                    break;

                case R.id.start2:
                    toggleGameTimer(TIMER_INDEX[1]);
                    break;

                case R.id.start3:
                    toggleGameTimer(TIMER_INDEX[2]);
                    break;

                default:
                    break;
            }
        }

        private void toggleGameTimer(int index) {
            if( index == TIMER_INDEX[0] ) {
                if( mTimerRunFlag[index] ) {
                    mTimerRunFlag[index] = false;
                    ((TextView)mStart1Button).setText(R.string.timer_start);
                    mStart1Button.setBackgroundResource(R.drawable.start_btn);
                    stopGameTimer(index);
                    saveTimerData(index);
                } else {
                    mTimerRunFlag[index] = true;
                    ((TextView)mStart1Button).setText(R.string.timer_stop);
                    mStart1Button.setBackgroundResource(R.drawable.stop_btn);
                    initGameTimer(index);
                }
                saveTimerStatus(index);
            } else if( index == TIMER_INDEX[1] ) {
                if( mTimerRunFlag[index] ) {
                    mTimerRunFlag[index] = false;
                    ((TextView)mStart2Button).setText(R.string.timer_start);
                    mStart2Button.setBackgroundResource(R.drawable.start_btn);
                    stopGameTimer(index);
                    saveTimerData(index);
                } else {
                    mTimerRunFlag[index] = true;
                    ((TextView)mStart2Button).setText(R.string.timer_stop);
                    mStart2Button.setBackgroundResource(R.drawable.stop_btn);
                    initGameTimer(index);
                }
                saveTimerStatus(index);
            } else if( index == TIMER_INDEX[2] ) {
                if( mTimerRunFlag[index] ) {
                    mTimerRunFlag[index] = false;
                    ((TextView)mStart3Button).setText(R.string.timer_start);
                    mStart3Button.setBackgroundResource(R.drawable.start_btn);
                    stopGameTimer(index);
                    saveTimerData(index);
                } else {
                    mTimerRunFlag[index] = true;
                    ((TextView)mStart3Button).setText(R.string.timer_stop);
                    mStart3Button.setBackgroundResource(R.drawable.stop_btn);
                    initGameTimer(index);
                }
                saveTimerStatus(index);
            } else {
                Log.d("GameTimer", "toggleGameTimer : Invalid Timer Index[" + String.valueOf(index) + "]" );
            }
        }

        private void initGameTimer(final int index) {
            if( isValidTimerIndex(index) ) {
                if( mGameTimerTimeoutTask[index] == null ) {
                    mGameTimerTimeoutTask[index] = new TimerTask() {
                        @Override
                        public void run() {
                            //Log.d("GameTimer", "TIMER[" + String.valueOf(index) + "] SendMessage");
                            Message msg = handler.obtainMessage();
                            msg.what = index;
                            handler.sendMessage(msg);

                        }
                    };
                }

                if( mGameTimer[index] == null )
                    mGameTimer[index] = new Timer();

                mGameTimer[index].scheduleAtFixedRate(mGameTimerTimeoutTask[index], 1000, 1000);     // 1초마다
            } else {
                Log.d("GameTimer", "initGameTimer : Invalid Timer Index[" + String.valueOf(index) + "]" );
            }
        }

        private void stopGameTimer(int index) {
            if( isValidTimerIndex(index) ) {
                if( mGameTimer[index] != null && mGameTimerTimeoutTask[index] != null ) {
                    mGameTimerTimeoutTask[index].cancel();
                    mGameTimer[index].cancel();
                    mGameTimer[index] = null;
                    mGameTimerTimeoutTask[index] = null;

                    saveLogToDB(index);
                }
            } else {
                Log.d("GameTimer", "stopGameTimer : Invalid Timer Index[" + String.valueOf(index) + "]" );
            }
        }

        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                if( isValidTimerIndex(msg.what) ) {
                    int timerIndex = msg.what;
                    //Log.d("GameTimer", "TIMER[" + String.valueOf(timerIndex) + "] RcvdMessage");

                    if( mTimerCurrTime[timerIndex] >= 0 ) {
                        mTimerCurrTime[timerIndex]--;

                        // Notification
                        if( isNotified(timerIndex) )
                            addNotification(timerIndex);
                    }

                    // Timer UI 갱신
                    if( isAdded() ) {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimerCurrTime[timerIndex]/60, mTimerCurrTime[timerIndex]%60);
                        if( timerIndex == TIMER_INDEX[0])
                            mTimer1Curr.setText(timerTime);
                        else if( timerIndex == TIMER_INDEX[1])
                            mTimer2Curr.setText(timerTime);
                        else if( timerIndex == TIMER_INDEX[2])
                            mTimer3Curr.setText(timerTime);
                    }

                    if( mTimerCurrTime[timerIndex] == 0 )
                        toggleGameTimer(timerIndex);
                }
            }
        };

        private boolean isNotified(int index) {
            boolean result = false;

            long timerRunningTime = getTimerRunningTime(index);
            if( timerRunningTime > 0 && (timerRunningTime % (NOTIFICATION_DURATION*SECONDS_PER_MINUTE) == 0) )
                result = true;

            return result;
        }

        private long getTimerRunningTime(int index) {
            long startTime = 0;
            long currTime = (new Date().getTime())/1000;    // 초단위로

            if( isValidTimerIndex(index) ) {
                startTime = mSharedPreferences.getLong(PREFERENCE_KEY_TIMER_START_TIME[index], currTime);
            } else {
                Log.d("GameTimer", "getTimerRunningTime : Invalid Timer Index[" + String.valueOf(index) + "]" );
                startTime = currTime;
            }

            return (currTime - startTime);
        }

        private void addNotification(int index) {
            Intent intent = new Intent(mContext, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            String contentTitle = getTimerName(index);
            String contentText = String.format(getString(R.string.notification_msg), getTimerRunningTime(index)/SECONDS_PER_MINUTE);

            Bitmap icon = ((BitmapDrawable) mContext.getResources().getDrawable(R.drawable.gametimer)).getBitmap();
            Notification notification = new NotificationCompat.Builder(mContext)
                                        .setContentTitle(contentTitle)
                                        .setContentText(contentText)
                                        .setSmallIcon(R.drawable.gametimer)
                                        .setLargeIcon(icon)
                                        .setTicker(getString(R.string.app_name))
                                        .setAutoCancel(true)
                                        .setVibrate(new long[] {300, 200, 100, 100, 100, 100, 300, 200})
                                        .setNumber(++mNotificationCounter)
                                        .setLights(Color.BLUE, 1000, 1000)
                                        .setWhen(new Date().getTime())
                                        .setContentIntent(pendingIntent)
                                        .build();

            //notification.tickerText = getString(R.string.app_name);
            //notification.icon = R.drawable.gametimer;
            //notification.when = new Date().getTime();
            //notification.number = ++mNotificationCounter;
            //notification.vibrate = new long[] {300, 200, 100, 100, 100, 100, 300, 200};
            //notification.ledARGB = Color.BLUE;
            //notification.ledOnMS = 1000;
            //notification.ledOffMS = 1000;
            //notification.flags |= (notification.FLAG_AUTO_CANCEL | notification.FLAG_SHOW_LIGHTS);
            //notification.setLatestEventInfo(mContext, contentTitle, contentText, pendingIntent);

            // 화면 켜기
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =  powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "MY TAG");
            wakeLock.acquire(5000);

            mNotificationManager.notify(NOTIFICATION_TIMER_PERIOD, notification);

            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View toastLayout = inflater.inflate(R.layout.toast_notification, null);
            TextView toastTitle = (TextView) toastLayout.findViewById(R.id.toast_title);
            TextView toastText = (TextView) toastLayout.findViewById(R.id.toast_text);

            toastTitle.setText(contentTitle);
            toastText.setText(contentText);

            Toast toast = new Toast(mContext);
            toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            toast.setDuration(Toast.LENGTH_LONG);
            toast.setView(toastLayout);
            toast.show();
        }

        private void saveLogToDB(int index) {
            long currTime = new Date().getTime();

            if( isValidTimerIndex(index) ) {
                mDBAdapter.createLog(index,
                        mTimerName[index],
                        convertTimeToDate(currTime, simpleDateFormat),
                        convertTimeToDate(((mSharedPreferences.getLong(PREFERENCE_KEY_TIMER_START_TIME[index], (currTime / 1000))) * 1000), detailDateFormat),
                        convertTimeToDate(currTime, detailDateFormat)
                );
            } else {
                Log.d("GameTimer", "saveLogToDB : Invalid Timer Index[" + String.valueOf(index) + "]" );
            }
        }

        class PhotoBitmapLoadTask extends AsyncTask<Integer, Void, Bitmap> {
            private int timerIndex;

            // Decode Image in Background
            @Override
            protected Bitmap doInBackground(Integer... params) {
                final Bitmap photoBitmap;

                timerIndex = params[0];
                if (isValidTimerIndex(timerIndex)) {
                    Bitmap cachePhotoBitmap = getBitmapFromMemCache(PROFILE_PHOTO_FILE_NAME[timerIndex]);
                    if (cachePhotoBitmap == null) {
                        String fileDirectory = Environment.getExternalStorageDirectory().getPath() + DIRECTORY_NAME;

                        File directory = new File(fileDirectory);
                        if (directory.exists()) {
                            String filePath = fileDirectory + PROFILE_PHOTO_FILE_NAME[timerIndex];
                            if (isExistFile(filePath)) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inPreferredConfig = Bitmap.Config.RGB_565;
                                options.inJustDecodeBounds = false;

                                photoBitmap = BitmapFactory.decodeFile(filePath, options);
                                addBitmapToMemoryCache(PROFILE_PHOTO_FILE_NAME[timerIndex], photoBitmap);
                            } else {
                                photoBitmap = null;
                            }
                        } else {
                            photoBitmap = null;
                        }
                    } else {
                        photoBitmap = cachePhotoBitmap;
                    }
                } else {
                    photoBitmap = null;
                }

                return photoBitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    if (timerIndex == TIMER_INDEX[0]) {
                        mPhoto1.setImageBitmap(bitmap);
                    } else if (timerIndex == TIMER_INDEX[1]) {
                        mPhoto2.setImageBitmap(bitmap);
                    } else if (timerIndex == TIMER_INDEX[2]) {
                        mPhoto3.setImageBitmap(bitmap);
                    }
                }
            }
        }
    }

    /**
     * A Timer Settting Fragment
     */
    public static class TimerSettingFragment extends Fragment {
        private ArrayAdapter<CharSequence> mNameArrayAdapterSpinner;
        private ArrayAdapter<CharSequence> mTimeArrayAdapterSpinner;
        private Spinner mNameSpinner;
        private Spinner mTimeSpinner;
        private EditText mTimerNameEdit;
        private int mNameTimerIndex;
        private String mOldTimerName;
        private Button mSaveNameBtn;
        private NumberPicker mTimerTensNumberPicker;
        private NumberPicker mTimerNumberPicker;
        private int mTimeTimerIndex;
        private Button mSaveTimeBtn;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                Bundle savedInstanceState) {
            Log.d("GameTimer", "TimerSettingFragment onCreateView");
            View rootView = inflater.inflate(R.layout.fragment_timer_setting, container, false);
            initView(rootView);

            return rootView;
        }

        private void initView(View rootView) {
            View resetTimer = rootView.findViewById(R.id.reset_timer);
            resetTimer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
                    ab.setTitle(R.string.reset_timer);
                    ab.setMessage(R.string.reset_timer_confirm_msg);
                    ab.setCancelable(false);

                    ab.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(mContext, getString(R.string.reset_timer_msg), Toast.LENGTH_SHORT).show();
                            clearTimer();
                            resetTimerData(false);
                            saveTimerData();

                            mNameSpinner.setSelection(0);
                            mTimeSpinner.setSelection(0);

                            mPagerAdapter.notifyDataSetChanged();
                            mViewPager.invalidate();
                        }
                    });

                    ab.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Do Nothing
                        }
                    });
                    ab.show();
                }
            });

            mTimerNameEdit = (EditText) rootView.findViewById(R.id.name_input);
            mSaveNameBtn = (Button) rootView.findViewById(R.id.input_confirm_btn);
            mSaveNameBtn.setEnabled(false);
            mSaveNameBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String inputName = mTimerNameEdit.getText().toString();

                    if( inputName != null && inputName.length() > 0 ) {
                        saveTimerName(mNameTimerIndex, inputName);
                        mOldTimerName = inputName;
                        mSaveNameBtn.setEnabled(false);
                        Toast.makeText(mContext, "[" + inputName + "] " + getString(R.string.setting_name_save_msg), Toast.LENGTH_SHORT).show();

                        mPagerAdapter.notifyDataSetChanged();
                        mViewPager.invalidate();
                    }
                }
            });

            mNameSpinner = (Spinner) rootView.findViewById(R.id.name_setting_spinner);
            mNameSpinner.setPrompt(getString(R.string.setting_name_spinner_title));

            mNameArrayAdapterSpinner = ArrayAdapter.createFromResource(mContext, R.array.setting_name_spinner_list, android.R.layout.simple_spinner_dropdown_item);
            mNameSpinner.setAdapter(mNameArrayAdapterSpinner);

            mNameSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if( isValidTimerIndex(position) ) {
                        mNameTimerIndex = position;
                        mTimerNameEdit.setText(mTimerName[position]);
                        mOldTimerName = mTimerName[position];
                        mTimerNameEdit.setSelection(mTimerNameEdit.length());
                        mSaveNameBtn.setEnabled(false);
                    } else {
                        Log.d("GameTimer", "mNameSpinner : Invalid Timer Index[" + String.valueOf(position) + "]" );
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            mTimerNameEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String inputName = s.toString();

                    if( inputName != null && inputName.length() > 0 && !inputName.equals(mOldTimerName) )
                        mSaveNameBtn.setEnabled(true);
                    else
                        mSaveNameBtn.setEnabled(false);
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            mTimerNameEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if( !hasFocus ) {
                        Log.d("GameTimer", "mTimerNameEdit lost focus");
                        hideKeyboard(v);
                    } else {
                        Log.d("GameTimer", "mTimerNameEdit get focus");
                    }
                }
            });

            mTimeSpinner = (Spinner) rootView.findViewById(R.id.timer_setting_spinner);
            mTimeSpinner.setPrompt(getString(R.string.setting_timer_spinner_title));

            mTimeArrayAdapterSpinner = ArrayAdapter.createFromResource(mContext, R.array.setting_name_spinner_list, android.R.layout.simple_spinner_dropdown_item);
            mTimeSpinner.setAdapter(mTimeArrayAdapterSpinner);

            mTimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if( isValidTimerIndex(position) ) {
                        mTimeTimerIndex = position;
                        setTimeSetting(mTimerTimeout[position]);
                    } else {
                        Log.d("GameTimer", "mNameSpinner : Invalid Timer Index[" + String.valueOf(position) + "]" );
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            mTimerTensNumberPicker = (NumberPicker) rootView.findViewById(R.id.timer_setting_1);
            mTimerNumberPicker = (NumberPicker) rootView.findViewById(R.id.timer_setting_2);
            mTimerTensNumberPicker.setMaxValue(MAX_TIMER_VALUE);
            mTimerTensNumberPicker.setMinValue(MIN_TIMER_VALUE);
            mTimerNumberPicker.setMaxValue(MAX_TIMER_VALUE);
            mTimerNumberPicker.setMinValue(MIN_TIMER_VALUE);

            mSaveTimeBtn = (Button) rootView.findViewById(R.id.timer_confirm_btn);
            mSaveTimeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int valueTensTime = mTimerTensNumberPicker.getValue();
                    int valueTime = mTimerNumberPicker.getValue();

                    int realValueTime = valueTensTime*10 + valueTime;

                    if( realValueTime >= MIN_TIMER ) {
                        saveTimerTime(mTimeTimerIndex, realValueTime);
                        Toast.makeText(mContext, "[" + realValueTime + "] " + getString(R.string.setting_timer_save_msg), Toast.LENGTH_SHORT).show();

                        mPagerAdapter.notifyDataSetChanged();
                        mViewPager.invalidate();
                    } else {
                        String timerMinValueMsg = String.format(getString(R.string.setting_timer_min_value_msg), MIN_TIMER);
                        Toast.makeText(mContext, timerMinValueMsg, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // 입력외 영역을 터치하면 Keybord를 내림
            View timerSettingLayout = rootView.findViewById(R.id.timer_setting_root);
            timerSettingLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d("GameTimer", "timerSettingLayout is touched");
                    hideKeyboard(mTimerNameEdit);
                    return false;
                }
            });
        }

        private void setTimeSetting(int timerValue) {
            if( timerValue > 0 ) {
                mTimerTensNumberPicker.setValue(timerValue/10); // 10의 자리
                mTimerNumberPicker.setValue(timerValue%10);     // 1의 자리
            }
        }

        private void hideKeyboard(View view) {
            mInputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * A Setting Fragment
     */
    public static class SettingFragment extends Fragment {
        private ArrayAdapter<CharSequence> mPhotoArrayAdapterSpinner;
        private Spinner mPhotoSpinner;
        private ImageView mPhoto;
        private int mPhotoIndex;
        private Button mPhotoSaveBtn;
        private Button mPhotoDeleteBtn;
        private Dialog mLoadLogSelect;
        private CharSequence[] mLoadLogItems;
        private boolean[] mLoadLogItemsStates;
        private TextView mLogView;
        private TableLayout mLogTable;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Log.d("GameTimer", "SettingFragment onCreateView");
            View rootView = inflater.inflate(R.layout.fragment_setting, container, false);
            initView(rootView);

            return rootView;
        }

        private void initView(View rootView) {
            View initTimer = rootView.findViewById(R.id.init_timer);
            initTimer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
                    ab.setTitle(R.string.init_timer);
                    ab.setMessage(R.string.init_timer_confirm_msg);
                    ab.setCancelable(false);

                    ab.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(mContext, getString(R.string.init_timer_msg), Toast.LENGTH_SHORT).show();
                            clearTimer();
                            resetTimerData(true);
                            saveTimerData();

                            saveTimerName(TIMER_INDEX[0], getString(R.string.name1_default));
                            saveTimerName(TIMER_INDEX[1], getString(R.string.name2_default));
                            saveTimerName(TIMER_INDEX[2], getString(R.string.name3_default));

                            mPagerAdapter.notifyDataSetChanged();
                            mViewPager.invalidate();
                        }
                    });

                    ab.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Do Nothing
                        }
                    });
                    ab.show();
                }
            });

            mPhotoSaveBtn = (Button) rootView.findViewById(R.id.save_photo_btn);
            mPhotoSaveBtn.setEnabled(false);
            mPhotoSaveBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isValidTimerIndex(mPhotoIndex)) {
                        Drawable photo = mPhoto.getDrawable();
                        Bitmap phtoBitmap = ((BitmapDrawable) photo).getBitmap();

                        String fileDirectory = Environment.getExternalStorageDirectory().getPath() + DIRECTORY_NAME;
                        File directory = new File(fileDirectory);
                        if (directory != null && directory.exists() == false) {
                            directory.mkdirs();
                        }

                        String filePath = fileDirectory + PROFILE_PHOTO_FILE_NAME[mPhotoIndex];

                        try {
                            FileOutputStream saveFile = new FileOutputStream(filePath, false);
                            phtoBitmap.compress(Bitmap.CompressFormat.JPEG, 70, saveFile);
                            saveFile.close();

                            addBitmapToMemoryCache(PROFILE_PHOTO_FILE_NAME[mPhotoIndex], phtoBitmap);   // Update Cache

                            Toast.makeText(mContext, getString(R.string.setting_photo_save_msg), Toast.LENGTH_SHORT).show();

                            Intent medaiScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            File scanFile = new File(filePath);
                            Uri contentUri = Uri.fromFile(scanFile);
                            medaiScanIntent.setData(contentUri);
                            mContext.sendBroadcast(medaiScanIntent);

                            mPhotoSaveBtn.setEnabled(false);
                            mPhotoDeleteBtn.setEnabled(true);
                        } catch (Exception e) {
                            Log.d("GameTimer", "mPhotoSaveBtn Save FileOutputStream Error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            });

            mPhotoDeleteBtn = (Button) rootView.findViewById(R.id.delete_photo_btn);
            mPhotoDeleteBtn.setEnabled(false);
            mPhotoDeleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String fileDirectory = Environment.getExternalStorageDirectory().getPath() + DIRECTORY_NAME;
                    File directory = new File(fileDirectory);
                    if (directory.exists()) {
                        String filePath = fileDirectory + PROFILE_PHOTO_FILE_NAME[mPhotoIndex];

                        File targetFile = new File(filePath);
                        if (targetFile.exists()) {
                            if (targetFile.delete()) {
                                Log.d("GameTimer", "mPhotoDeleteBtn Delete Photo : " + PROFILE_PHOTO_FILE_NAME[mPhotoIndex]);
                                mPhotoDeleteBtn.setEnabled(false);
                                clearBitmapFromMemCache(PROFILE_PHOTO_FILE_NAME[mPhotoIndex]);
                                mPhoto.setImageResource(R.drawable.photo_add);  // 사진을 지운 경우 Default 이미지를 설정해줌
                            } else {
                                Log.d("GameTimer", "mPhotoDeleteBtn Delete Photo Error : " + PROFILE_PHOTO_FILE_NAME[mPhotoIndex]);
                            }
                        }
                    }

                }
            });

            mPhotoSpinner = (Spinner) rootView.findViewById(R.id.photo_setting_spinner);
            mPhotoSpinner.setPrompt(getString(R.string.setting_name_spinner_title));

            mPhotoArrayAdapterSpinner = ArrayAdapter.createFromResource(mContext, R.array.setting_name_spinner_list, android.R.layout.simple_spinner_dropdown_item);
            mPhotoSpinner.setAdapter(mPhotoArrayAdapterSpinner);

            mPhotoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isValidTimerIndex(position)) {
                        mPhotoIndex = position;
                        mPhotoSaveBtn.setEnabled(false);

                        BitmapLoadTask task = new BitmapLoadTask();
                        task.execute(mPhotoIndex);
                    } else {
                        Log.d("GameTimer", "mPhotoSpinner : Invalid Timer Index[" + String.valueOf(position) + "]");
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            mPhoto = (ImageView) rootView.findViewById(R.id.photo);
            mPhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
                    intent.setData(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    startActivityForResult(intent, REQUEST_CODE_PICK_PICTURE);
                }
            });

            //mLogView = (TextView) rootView.findViewById(R.id.log_text);
            mLogTable = (TableLayout) rootView.findViewById(R.id.log_table);

            mLoadLogItems = new CharSequence[TIMER_INDEX.length];
            mLoadLogItemsStates = new boolean[TIMER_INDEX.length];
            for (int i = 0; i < TIMER_INDEX.length; i++) {
                mLoadLogItems[i] = mTimerName[i];
                mLoadLogItemsStates[i] = true;
            }

            AlertDialog.Builder loadLogSelectBuilder = new AlertDialog.Builder(mContext);
            loadLogSelectBuilder.setTitle(R.string.load_log_by_index_title);
            loadLogSelectBuilder.setMultiChoiceItems(mLoadLogItems, mLoadLogItemsStates, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    Log.d("GameTimer", "loadLogSelectByName[" + mLoadLogItems[which] + "]-" + isChecked);
                }
            });

            loadLogSelectBuilder.setPositiveButton(getString(R.string.load_log), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    boolean noSelectFlag = true;
                    int index = 0;
                    for (boolean state : mLoadLogItemsStates) {
                        Log.d("GameTimer", "loadLogSelectByName Confirm[" + mLoadLogItems[index++] + "]-" + state);

                        if (state)
                            noSelectFlag = false;
                    }

                    if (noSelectFlag) {
                        Toast.makeText(mContext, getString(R.string.load_log_select_by_index_error_msg), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        Cursor cursor = mDBAdapter.fetchLogByTimerIndex(mLoadLogItemsStates);
                        showLog(cursor);
                    } catch (SQLException e) {
                        Log.d("GameTimer", "loadLogSelectByName DB Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
            mLoadLogSelect = loadLogSelectBuilder.create();

            final View loadLogSelectByName = rootView.findViewById(R.id.load_log_select_by_name_btn);
            loadLogSelectByName.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mLoadLogSelect.show();
                }
            });

            final View loadLogSelectByDate = rootView.findViewById(R.id.load_log_select_by_date_btn);
            loadLogSelectByDate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCalendarPopup(mContext);
                }
            });

            View deleteAllLog = rootView.findViewById(R.id.delete_all_log_btn);
            deleteAllLog.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder ab = new AlertDialog.Builder(mContext);
                    ab.setTitle(R.string.delete_all_log_title);
                    ab.setMessage(R.string.delete_all_log_confirm_msg);
                    ab.setCancelable(false);

                    ab.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(mContext, getString(R.string.delete_all_log_msg), Toast.LENGTH_SHORT).show();
                            mDBAdapter.deleteAllLog();
                            //mLogView.setText("");
                            mLogTable.removeAllViewsInLayout();
                        }
                    });

                    ab.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Do Nothing
                        }
                    });
                    ab.show();
                }
            });

        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if( requestCode == REQUEST_CODE_PICK_PICTURE ) {
                if( (resultCode == Activity.RESULT_OK) && (data != null) ) {
                    Uri pictureUri = data.getData();
                    Log.d("GameTimer", "Pick Picture(" + pictureUri.toString() + ")");

                    setPictureBG(pictureUri);
                }
            }
        }

        private void showCalendarPopup(Activity context) {
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View calendarLayout = layoutInflater.inflate(R.layout.calendar_popup, null, false);

            final PopupWindow popupWindow = new PopupWindow(calendarLayout, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            popupWindow.setContentView(calendarLayout);
            popupWindow.setOutsideTouchable(false);

            final CalendarView calendarView = (CalendarView) calendarLayout.findViewById(R.id.calendar_view);

            View prevMonthButton = calendarLayout.findViewById(R.id.prev_month_btn);
            prevMonthButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    long currMilliTime = calendarView.getDate();

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(currMilliTime);

                    int currYear = calendar.get(Calendar.YEAR);
                    int currMonth = calendar.get(Calendar.MONTH) + 1;   // Month는 0 ~ 11이므로 1을 더해준다
                    int currDay = calendar.get(Calendar.DAY_OF_MONTH);

                    if( currMonth == 1 ) {
                        currYear--;
                        currMonth = 12;
                    } else {
                        currMonth--;
                    }

                    Log.d("GameTimer", "showCalendarPopup : prevMonthButton(" + currYear + "/" + currMonth + "/" + currDay + ")");

                    calendar.set(Calendar.YEAR, currYear);
                    calendar.set(Calendar.MONTH, currMonth - 1);    // Month는 0 ~ 11이므로 설정해줄때는 1을 빼준다
                    calendar.set(Calendar.DAY_OF_MONTH, currDay);

                    long setMilliTime = calendar.getTimeInMillis();

                    calendarView.setDate(setMilliTime, true, true);
                }
            });

            View nextMonthButton = calendarLayout.findViewById(R.id.next_month_btn);
            nextMonthButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    long currMilliTime = calendarView.getDate();

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(currMilliTime);

                    int currYear = calendar.get(Calendar.YEAR);
                    int currMonth = calendar.get(Calendar.MONTH) + 1;   // Month는 0 ~ 11이므로 읽어들일때는 1을 더해준다
                    int currDay = calendar.get(Calendar.DAY_OF_MONTH);

                    if( currMonth == 12 ) {
                        currYear++;
                        currMonth = 1;
                    } else {
                        currMonth++;
                    }

                    Log.d("GameTimer", "showCalendarPopup : nextMonthButton(" + currYear + "/" + currMonth + "/" + currDay + ")");

                    calendar.set(Calendar.YEAR, currYear);
                    calendar.set(Calendar.MONTH, currMonth - 1);    // Month는 0 ~ 11이므로 설정해줄때는 1을 빼준다
                    calendar.set(Calendar.DAY_OF_MONTH, currDay);

                    long setMilliTime = calendar.getTimeInMillis();

                    calendarView.setDate(setMilliTime, true, true);
                }
            });

            View confirmButton = calendarLayout.findViewById(R.id.confirm_btn);
            confirmButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();

                    long currMilliTime = calendarView.getDate();

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(currMilliTime);

                    int currYear = calendar.get(Calendar.YEAR);
                    int currMonth = calendar.get(Calendar.MONTH) + 1;   // Month는 0 ~ 11이므로 1을 더해준다
                    int currDay = calendar.get(Calendar.DAY_OF_MONTH);

                    try {
                        Log.d("GameTimer", "showCalendarPopup : confirmButton(" + currYear + "/" + currMonth + "/" + currDay + ")");
                        String date = null;
                        Cursor cursor = mDBAdapter.fetchLogByDate(date);

                        showLog(cursor);
                    } catch (SQLException e) {
                        Log.d("GameTimer", "loadLogSelectByDate DB Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });

            popupWindow.showAtLocation(calendarLayout, Gravity.TOP, 0, 0);
        }

        private void showLog(Cursor cursor) {
            if( cursor != null && cursor.moveToFirst() ) {
                String logItems = "";
                int timerIndex;
                String timerName, date, startTime, stopTime;

                mLogTable.removeAllViewsInLayout();

                while (!cursor.isAfterLast()) {
                    timerIndex = cursor.getInt(1);
                    timerName = cursor.getString(2);
                    date = cursor.getString(3);
                    startTime = cursor.getString(4);
                    stopTime = cursor.getString(5);
                    logItems = logItems + String.valueOf(timerIndex) + " " + timerName + " " + date + " " + startTime + " " + stopTime + " " + calDuration(startTime, stopTime) + "\n";

                    TableRow newRow = new TableRow(mContext);
                    newRow.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                    newRow.setBackgroundColor(getResources().getColor(R.color.white));
                    newRow.setPadding(1, 1, 1, 1);

                    addLogData(newRow, String.valueOf(timerIndex));
                    addLogData(newRow, timerName);
                    addLogData(newRow, date);
                    addLogData(newRow, startTime);
                    addLogData(newRow, stopTime);
                    addLogData(newRow, calDuration(startTime, stopTime));

                    mLogTable.addView(newRow, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                    cursor.moveToNext();
                }

                //mLogView.setText(logItems);
            } else {
                //mLogView.setText(getString(R.string.load_log_empty_msg));
                mLogTable.removeAllViewsInLayout();
            }
        }

        private void addLogData(TableRow logRow, String data) {
            TextView textIndex = new TextView(mContext);
            textIndex.setText(data);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            textIndex.setLayoutParams(layoutParams);
            textIndex.setPadding(5,0,0,0);
            textIndex.setBackgroundColor(getResources().getColor(R.color.log_text_background));

            logRow.addView(textIndex, new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void setPictureBG(Uri pictureUri) {
            int columnIndex;
            String imagePath;
            final String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor imageCursor = mContext.getContentResolver().query(pictureUri, filePathColumn, null, null, null);

            if( imageCursor != null ) {
                imageCursor.moveToFirst();

                columnIndex = imageCursor.getColumnIndex(filePathColumn[0]);
                imagePath = imageCursor.getString(columnIndex);

                imageCursor.close();

                if( isExistFile(imagePath) ) {
                    Log.d("GameTimer", "setPictureBG(" + imagePath + ")");
                    // View의 사이즈를 구함
                    int width = mPhoto.getWidth();
                    int height = mPhoto.getHeight();

                    // 읽어들일 이미지의 사이즈를 구함
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imagePath, options);

                    // View 사이즈에 가장 근접하는 이미지의 리스케일 사이즈를 구함. 리스케일의 사이즈는 짝수로 지정한다. (이미지의 손실을 최소화하기 위함)
                    float widthScale = options.outWidth / width;
                    float heightScale = options.outHeight / height;
                    float scale = widthScale > heightScale ? widthScale : heightScale;

                    if( scale >= 8 ) {
                        options.inSampleSize = 8;
                    } else if( scale >= 6 ) {
                        options.inSampleSize = 6;
                    } else if( scale >= 4 ) {
                        options.inSampleSize = 4;
                    } else if( scale >= 2 ) {
                        options.inSampleSize = 2;
                    } else {
                        options.inSampleSize = 1;
                    }

                    options.inJustDecodeBounds = false;

                    Bitmap pictureBitmap = BitmapFactory.decodeFile(imagePath, options);

                    Bitmap resizedPicture;
                    if( (pictureBitmap.getHeight() > height) || (pictureBitmap.getWidth() > width) ) {
                        if( (pictureBitmap.getHeight() > height) && (pictureBitmap.getWidth() > width) ) {
                            if( (pictureBitmap.getHeight() - height) > (pictureBitmap.getWidth() - width) ) {
                                resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, (pictureBitmap.getWidth() * height) / pictureBitmap.getHeight(), height, true);
                            }
                            else {
                                resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, width, (pictureBitmap.getHeight() * width) / pictureBitmap.getWidth(), true);
                            }
                        }
                        else if ( pictureBitmap.getHeight() > height ) {
                            resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, (pictureBitmap.getWidth() * height) / pictureBitmap.getHeight(), height, true);
                        }
                        else {
                            resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, width, (pictureBitmap.getHeight() * width) / pictureBitmap.getWidth(), true);
                        }
                    }
                    else {
                        if ( pictureBitmap.getHeight() > pictureBitmap.getWidth() ) {
                            resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, (pictureBitmap.getWidth() * height) / pictureBitmap.getHeight(), height, true);
                        }
                        else {
                            resizedPicture = Bitmap.createScaledBitmap(pictureBitmap, width, (pictureBitmap.getHeight() * width) / pictureBitmap.getWidth(), true);
                        }
                    }

                    pictureBitmap.recycle();	// Free Original Bitmap

                    mPhoto.setImageBitmap(resizedPicture);
                    mPhotoSaveBtn.setEnabled(true);
                    mPhotoDeleteBtn.setEnabled(false);
                }
            }
        }

        class BitmapLoadTask extends AsyncTask<Integer, Void, Bitmap> {

            // Decode Image in Background
            @Override
            protected Bitmap doInBackground(Integer... params) {
                final Bitmap photoBitmap;

                int timerIndex = params[0];
                if( isValidTimerIndex(timerIndex) ) {
                    Bitmap cachePhotoBitmap = getBitmapFromMemCache(PROFILE_PHOTO_FILE_NAME[timerIndex]);
                    if( cachePhotoBitmap == null ) {
                        String fileDirectory = Environment.getExternalStorageDirectory().getPath() + DIRECTORY_NAME;

                        File directory = new File(fileDirectory);
                        if( directory.exists()  ) {
                            String filePath = fileDirectory + PROFILE_PHOTO_FILE_NAME[timerIndex];
                            if (isExistFile(filePath)) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inPreferredConfig = Bitmap.Config.RGB_565;
                                options.inJustDecodeBounds = false;

                                photoBitmap = BitmapFactory.decodeFile(filePath, options);
                                addBitmapToMemoryCache(PROFILE_PHOTO_FILE_NAME[timerIndex], photoBitmap);
                            } else {
                                photoBitmap = null;
                            }
                        } else {
                            photoBitmap = null;
                        }
                    } else {
                        photoBitmap = cachePhotoBitmap;
                    }
                } else {
                    photoBitmap = null;
                }

                return photoBitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    mPhoto.setImageResource(R.drawable.photo_add);  // 사진이 없는 경우 Default 이미지를 설정해줌
                    mPhotoDeleteBtn.setEnabled(false);
                }
                else {
                    mPhoto.setImageBitmap(bitmap);
                    mPhotoDeleteBtn.setEnabled(true);
                }
            }
        }
    }

    static public boolean isExistFile(String file){
        if( (file == null) || (file.length() == 0) )
            return false;

        File targetFile = new File(file);
        if( targetFile.exists() )
            return true;

        return false;
    }

    public static String calDuration(String start, String stop) {
        Date startTime, stopTime;
        DateFormat inputFormat = new SimpleDateFormat(detailDateFormat);

        if( start != null && stop != null ) {
            try {
                startTime = inputFormat.parse(start);
                stopTime = inputFormat.parse(stop);

                long duration = (stopTime.getTime() / 1000) - (startTime.getTime() / 1000);
                return (String.valueOf(duration/SECONDS_PER_MINUTE) + ":" + String.valueOf(duration%SECONDS_PER_MINUTE));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return "";
    }

    public static String convertTimeToDate(long milliseconds, String format) {
        if( format != null ) {
            DateFormat dateFormat = new SimpleDateFormat(format);

            try {
                Date date = new Date();
                date.setTime(milliseconds);

                return dateFormat.format(date);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static int getTimerNumber() {
        return TIMER_INDEX.length;
    }
}
