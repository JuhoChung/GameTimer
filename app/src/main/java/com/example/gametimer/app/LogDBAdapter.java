package com.example.gametimer.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.sql.SQLException;

/**
 * Created by 정주호 on 2014-07-07.
 */

public class LogDBAdapter {
    public static final String KEY_ID = "_id";
    public static final String KEY_INDEX = "timer_index";
    public static final String KEY_NAME = "name";
    public static final String KEY_DATE = "date";
    public static final String KEY_START_TIME = "start_time";
    public static final String KEY_STOP_TIME = "stop_time";

    public static final String[] ALL_COLUMS = { KEY_ID, KEY_INDEX, KEY_NAME, KEY_DATE, KEY_START_TIME, KEY_STOP_TIME};

    private static LogDatabaseHelper mDBHelper;
    private static SQLiteDatabase mDB;

    /**
     *  Database Creation SQL Statement
     */
    private static final String DATABASE_NAME = "gametimer";
    private static final String DATABASE_TABLE =  "logs";
    private static final int DATABASE_VERSION = 1;
    private final Context mContext;

    private static class LogDatabaseHelper extends SQLiteOpenHelper {
        LogDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DATABASE_TABLE + " ( " +
                       KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                       KEY_INDEX + " INTEGER, " +
                       KEY_NAME + " TEXT, " +
                       KEY_DATE + " TEXT, " +
                       KEY_START_TIME + " TEXT, " +
                       KEY_STOP_TIME + " TEXT" +
                       " );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d("GameTimer DB", "Upgrading DB from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
            onCreate(db);
        }
    }

    public LogDBAdapter(Context context) {
        this.mContext = context;
    }

    public LogDBAdapter open() throws SQLException {
        mDBHelper = new LogDatabaseHelper(mContext);
        mDB = mDBHelper.getWritableDatabase();

        return this;
    }

    public void close() {
        mDBHelper.close();
    }

    public long createLog(int index, String name, String date, String startTime, String stopTime) {
        if( mDB == null ) {
            Log.d("GameTimer DB", "createLog Error : DB is null");

            return -1;
        }

        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_INDEX, index);
        initialValues.put(KEY_NAME, name);
        initialValues.put(KEY_DATE, date);
        initialValues.put(KEY_START_TIME, startTime);
        initialValues.put(KEY_STOP_TIME, stopTime);

        return mDB.insert(DATABASE_TABLE, null, initialValues);
    }

    public boolean deleteLog(long rowId) {
        Log.d("GameTimer DB", "Delete called : value = " + rowId);

        if( mDB == null ) {
            Log.d("GameTimer DB", "deleteLog Error : DB is null");

            return false;
        }

        return mDB.delete(DATABASE_TABLE, KEY_ID + "=" + rowId, null) > 0;
    }

    public boolean deleteAllLog() {
        Log.d("GameTimer DB", "Delete All Log");

        if( mDB == null ) {
            Log.d("GameTimer DB", "deleteAllLog Error : DB is null");

            return false;
        }

        return mDB.delete(DATABASE_TABLE, null, null) > 0;
    }

    public Cursor fetchAllLogs() throws SQLException {
        Log.d("GameTimer DB", "Fetch All Log");

        if( mDB == null ) {
            Log.d("GameTimer DB", "fetchAllLogs Error : DB is null");

            return null;
        }

        return mDB.query(DATABASE_TABLE, ALL_COLUMS, null, null, null, null, null);
    }

    public Cursor fetchLogByTimerIndex(boolean[] timerIndexStates) throws SQLException {
        int index = 0;
        for( boolean state : timerIndexStates ) {
            Log.d("GameTimer DB", "fetchLog called TimerIndex[" + (index++) + "]-" + state );
        }

        if( mDB == null ) {
            Log.d("GameTimer DB", "fetchLog Error : DB is null");

            return null;
        }

        String where = "";
        for( int i = 0; i < timerIndexStates.length; i++ ) {
            if (timerIndexStates[i]) {
                if (where.length() > 0)
                    where += " OR ";

                where += KEY_INDEX + "=" + i;
            }
        }

        Cursor cursor = mDB.query(true, DATABASE_TABLE,
                                  ALL_COLUMS,
                                  where,
                                  null,
                                  null,
                                  null,
                                  null,
                                  null);

        if( cursor != null )
            cursor.moveToFirst();

        return cursor;
    }

    public Cursor fetchLogByDate(String date) throws  SQLException {
        Log.d("GameTimer DB", "fetchLogByDate : " + date);

        if( mDB == null ) {
            Log.d("GameTimer DB", "fetchLogByDate Error : DB is null");

            return null;
        }

        String prevDate = MainActivity.getDiffDate(date, 7, false);
        String nextDate = MainActivity.getDiffDate(date, 7, true);

        //String where = KEY_DATE + "=" + "'" + date + "'";
        String where = KEY_DATE + ">" + "'" + prevDate + "'" + " AND " + KEY_DATE + "<" + "'" + nextDate + "'";

        Cursor cursor = mDB.query(true, DATABASE_TABLE,
                ALL_COLUMS,
                where,
                null,
                null,
                null,
                null,
                null);

        if( cursor != null )
            cursor.moveToFirst();

        return cursor;
    }
}
