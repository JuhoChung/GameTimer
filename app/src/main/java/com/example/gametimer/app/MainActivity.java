package com.example.gametimer.app;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity {
    private static final String PREFERENCE_KEY_TIMER1_TIMEOUT = "timer1_timeout";
    private static final String PREFERENCE_KEY_TIMER2_TIMEOUT = "timer2_timeout";
    private static final String PREFERENCE_KEY_TIMER3_TIMEOUT = "timer3_timeout";

    private static final String PREFERENCE_KEY_TIMER1_CURRTIME = "timer1_currtime";
    private static final String PREFERENCE_KEY_TIMER2_CURRTIME = "timer2_currtime";
    private static final String PREFERENCE_KEY_TIMER3_CURRTIME = "timer3_currtime";

    private static final int DEFAULT_TIMER1_TIMEOUT = 60;   // 기본 60분
    private static final int DEFAULT_TIMER2_TIMEOUT = 60;   // 기본 60분
    private static final int DEFAULT_TIMER3_TIMEOUT = 60;   // 기본 60분

    private static final int TIMER1 = 1;
    private static final int TIMER2 = 2;
    private static final int TIMER3 = 3;

    private SharedPreferences mSharedPreferences;
    private static Timer mGameTimer1;
    private static Timer mGameTimer2;
    private static Timer mGameTimer3;

    private static TimerTask mGameTimer1TimeoutTask;
    private static TimerTask mGameTimer2TimeoutTask;
    private static TimerTask mGameTimer3TimeoutTask;

    private static boolean mTimer1RunFlag = false;
    private static boolean mTimer2RunFlag = false;
    private static boolean mTimer3RunFlag = false;

    // Timer별 Timeout 시간
    private static int mTimer1Timeout;
    private static int mTimer2Timeout;
    private static int mTimer3Timeout;

    // Timer별 현재진행 상태 백업 & 복구
    private static int mTimer1CurrTime;
    private static int mTimer2CurrTime;
    private static int mTimer3CurrTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Timer 관련 정보 초기화 및 복원
        initTimerData();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Timer 관련 정보 저장
        saveTimerData();

        if( mGameTimer1 != null ) {
            mGameTimer1.cancel();
            mGameTimer1 = null;
        }

        if( mGameTimer2 != null ) {
            mGameTimer2.cancel();
            mGameTimer2 = null;
        }

        if( mGameTimer3 != null ) {
            mGameTimer3.cancel();
            mGameTimer3 = null;
        }

        mGameTimer1TimeoutTask = null;
        mGameTimer2TimeoutTask = null;
        mGameTimer3TimeoutTask = null;
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
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initTimerData() {
        // Restore Timer Timeout (분)
        mTimer1Timeout = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER1_TIMEOUT, DEFAULT_TIMER1_TIMEOUT);
        mTimer2Timeout = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER2_TIMEOUT, DEFAULT_TIMER2_TIMEOUT);
        mTimer3Timeout = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER3_TIMEOUT, DEFAULT_TIMER3_TIMEOUT);

        // Restore Current Timer Value (초)
        mTimer1CurrTime = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER1_CURRTIME, DEFAULT_TIMER1_TIMEOUT*60);
        mTimer2CurrTime = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER2_CURRTIME, DEFAULT_TIMER1_TIMEOUT*60);
        mTimer3CurrTime = mSharedPreferences.getInt(PREFERENCE_KEY_TIMER3_CURRTIME, DEFAULT_TIMER1_TIMEOUT*60);
    }

    private void saveTimerData() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        // Save Timer Timeout (분)
        if( mTimer1Timeout > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER1_TIMEOUT, mTimer1Timeout);
        else
            editor.putInt(PREFERENCE_KEY_TIMER1_TIMEOUT, DEFAULT_TIMER1_TIMEOUT);

        if( mTimer2Timeout > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER2_TIMEOUT, mTimer2Timeout);
        else
            editor.putInt(PREFERENCE_KEY_TIMER2_TIMEOUT, DEFAULT_TIMER3_TIMEOUT);

        if( mTimer3Timeout > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER3_TIMEOUT, mTimer2Timeout);
        else
            editor.putInt(PREFERENCE_KEY_TIMER3_TIMEOUT, DEFAULT_TIMER3_TIMEOUT);

        // Save Current Timer (초)
        if( mTimer1CurrTime > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER1_CURRTIME, mTimer1CurrTime);
        else
            editor.putInt(PREFERENCE_KEY_TIMER1_CURRTIME, DEFAULT_TIMER1_TIMEOUT*60);

        if( mTimer2CurrTime > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER2_CURRTIME, mTimer2CurrTime);
        else
            editor.putInt(PREFERENCE_KEY_TIMER2_CURRTIME, DEFAULT_TIMER3_TIMEOUT*60);

        if( mTimer3CurrTime > 0 )
            editor.putInt(PREFERENCE_KEY_TIMER3_CURRTIME, mTimer3CurrTime);
        else
            editor.putInt(PREFERENCE_KEY_TIMER3_CURRTIME, DEFAULT_TIMER3_TIMEOUT*60);

        editor.commit();
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment implements View.OnClickListener {
        private View mName1Layout;
        private View mName2Layout;
        private View mName3Layout;

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

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            initView(rootView);
            initViewTimer();

            return rootView;
        }

        private void initView(View rootView) {
            mName1Layout = (View) rootView.findViewById(R.id.name1_layout);
            mName2Layout = (View) rootView.findViewById(R.id.name1_layout);
            mName3Layout = (View) rootView.findViewById(R.id.name1_layout);

            mName1 = (TextView) rootView.findViewById(R.id.name1);
            mName2 = (TextView) rootView.findViewById(R.id.name2);
            mName3 = (TextView) rootView.findViewById(R.id.name3);

            mStart1Button = (View) rootView.findViewById(R.id.start1);
            mStart2Button = (View) rootView.findViewById(R.id.start2);
            mStart3Button = (View) rootView.findViewById(R.id.start3);

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
            mName1.setText(R.string.name1_default);
            mName2.setText(R.string.name2_default);
            mName3.setText(R.string.name3_default);

            String timer1Timeout = String.format(getString(R.string.timer_field), mTimer1Timeout);
            String timer2Timeout = String.format(getString(R.string.timer_field), mTimer2Timeout);
            String timer3Timeout = String.format(getString(R.string.timer_field), mTimer3Timeout);
            mTimer1Time.setText(timer1Timeout);
            mTimer2Time.setText(timer2Timeout);
            mTimer3Time.setText(timer3Timeout);

            String timer1Time = String.format(getString(R.string.remain_timer_filed), mTimer1CurrTime/60, mTimer1CurrTime%60);
            String timer2Time = String.format(getString(R.string.remain_timer_filed), mTimer2CurrTime/60, mTimer2CurrTime%60);
            String timer3Time = String.format(getString(R.string.remain_timer_filed), mTimer3CurrTime/60, mTimer3CurrTime%60);
            mTimer1Curr.setText(timer1Time);
            mTimer2Curr.setText(timer2Time);
            mTimer3Curr.setText(timer3Time);
        }

        @Override
        public void onClick(View v) {
            switch( v.getId() ) {
                case R.id.start1:
                    if( mTimer1RunFlag ) {
                        mTimer1RunFlag = false;
                        ((TextView)mStart1Button).setText(R.string.timer_start);
                        stopGameTimer(TIMER1);
                    } else {
                        mTimer1RunFlag = true;
                        ((TextView)mStart1Button).setText(R.string.timer_stop);
                        initGameTimer(TIMER1);
                    }
                    break;

                case R.id.start2:
                    if( mTimer2RunFlag ) {
                        mTimer2RunFlag = false;
                        ((TextView)mStart2Button).setText(R.string.timer_start);
                        stopGameTimer(TIMER2);
                    } else {
                        mTimer2RunFlag = true;
                        ((TextView)mStart2Button).setText(R.string.timer_stop);
                        initGameTimer(TIMER2);
                    }
                    break;

                case R.id.start3:
                    if( mTimer3RunFlag ) {
                        mTimer3RunFlag = false;
                        ((TextView)mStart3Button).setText(R.string.timer_start);
                        stopGameTimer(TIMER3);
                    } else {
                        mTimer3RunFlag = true;
                        ((TextView)mStart3Button).setText(R.string.timer_stop);
                        initGameTimer(TIMER3);
                    }
                    break;

                default:
                    break;
            }
        }

        private void initGameTimer(int index) {
            switch( index ) {
                case TIMER1:
                    if( mGameTimer1TimeoutTask == null ) {
                        mGameTimer1TimeoutTask = new TimerTask() {
                            @Override
                            public void run() {
                                Message msg = handler.obtainMessage();
                                msg.what = TIMER1;
                                handler.sendMessage(msg);
                            }
                        };
                    }

                    if( mGameTimer1 == null )
                        mGameTimer1 = new Timer();
                    mGameTimer1.schedule(mGameTimer1TimeoutTask, 10, 1000);     // 1초마다
                    break;

                case TIMER2:
                    if( mGameTimer2TimeoutTask == null ) {
                        mGameTimer2TimeoutTask = new TimerTask() {
                            @Override
                            public void run() {
                                Message msg = handler.obtainMessage();
                                msg.what = TIMER2;
                                handler.sendMessage(msg);
                            }
                        };
                    }

                    if( mGameTimer2 == null )
                        mGameTimer2 = new Timer();
                    mGameTimer2.schedule(mGameTimer2TimeoutTask, 10, 1000);     // 1초마다
                    break;

                case TIMER3:
                    if( mGameTimer3TimeoutTask == null ) {
                        mGameTimer3TimeoutTask = new TimerTask() {
                            @Override
                            public void run() {
                                Message msg = handler.obtainMessage();
                                msg.what = TIMER3;
                                handler.sendMessage(msg);
                            }
                        };
                    }

                    if( mGameTimer3 == null )
                        mGameTimer3 = new Timer();
                    mGameTimer3.schedule(mGameTimer3TimeoutTask, 10, 1000);     // 1초마다
                    break;

                default:
                    break;
            }
        }

        private void stopGameTimer(int index) {
            switch( index ) {
                case TIMER1:
                    if( mGameTimer1 != null && mGameTimer1TimeoutTask != null ) {
                        mGameTimer1TimeoutTask.cancel();
                        mGameTimer1.cancel();
                        mGameTimer1 = null;
                        mGameTimer1TimeoutTask = null;
                    }
                    break;

                case TIMER2:
                    if( mGameTimer2 != null && mGameTimer2TimeoutTask != null ) {
                        mGameTimer2TimeoutTask.cancel();
                        mGameTimer2.cancel();
                        mGameTimer2 = null;
                        mGameTimer2TimeoutTask = null;
                    }
                    break;

                case TIMER3:
                    if( mGameTimer3 != null && mGameTimer3TimeoutTask != null ) {
                        mGameTimer3TimeoutTask.cancel();
                        mGameTimer3.cancel();
                        mGameTimer3 = null;
                        mGameTimer3TimeoutTask = null;
                    }
                    break;

                default:
                    break;
            }
        }

        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                if( msg.what == TIMER1 ) {
                    if( mTimer1CurrTime == 0 ) {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer1CurrTime/60, mTimer1CurrTime%60);
                        mTimer1Curr.setText(timerTime);
                        mGameTimer1.cancel();
                        mGameTimer1.purge();
                        mGameTimer1 = null;
                    } else {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer1CurrTime/60, mTimer1CurrTime%60);
                        mTimer1Curr.setText(timerTime);
                    }

                    if( mTimer1CurrTime > 0 )
                        mTimer1CurrTime --;
                } else if( msg.what == TIMER2 ) {
                    if( mTimer2CurrTime == 0 ) {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer2CurrTime/60, mTimer2CurrTime%60);
                        mTimer2Curr.setText(timerTime);
                        mGameTimer2.cancel();
                        mGameTimer2.purge();
                        mGameTimer2 = null;
                    } else {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer2CurrTime/60, mTimer2CurrTime%60);
                        mTimer2Curr.setText(timerTime);
                    }

                    if( mTimer2CurrTime > 0 )
                        mTimer2CurrTime --;
                } else if( msg.what == TIMER3 ) {
                    if( mTimer3CurrTime == 0 ) {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer3CurrTime/60, mTimer3CurrTime%60);
                        mTimer3Curr.setText(timerTime);
                        mGameTimer3.cancel();
                        mGameTimer3.purge();
                        mGameTimer3 = null;
                    } else {
                        String timerTime = String.format(getString(R.string.remain_timer_filed), mTimer3CurrTime/60, mTimer3CurrTime%60);
                        mTimer3Curr.setText(timerTime);
                    }

                    if( mTimer3CurrTime > 0 )
                        mTimer3CurrTime --;
                }
            }
        };
    }
}
