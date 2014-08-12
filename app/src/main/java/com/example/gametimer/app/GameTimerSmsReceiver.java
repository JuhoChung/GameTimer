package com.example.gametimer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * SMS 구조 : 게임타이머 시작/끝 TimerID(1/2/3)
 */
public class GameTimerSmsReceiver extends BroadcastReceiver {
    static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";
    static final String GAMETIMER_TOKEN = "게임타이머";
    static final String GAMETIMER_START_TOKEN = "시작";
    static final String GAMETIMER_STOP_TOKEN = "끝";

    public void onReceive(Context context, Intent intent) {
        if( intent.getAction().equals(ACTION) ) {
            Bundle bundle = intent.getExtras();

            if( bundle != null ) {
                Object[] pdusObj = (Object[])bundle.get("pdus");
                if( pdusObj != null ) {
                    SmsMessage[] messages = new SmsMessage[pdusObj.length];

                    for( int i = 0; i < messages.length; i++ ) {
                        messages[i] = SmsMessage.createFromPdu((byte[])pdusObj[i]);

                        SmsMessage message = messages[i];

                        try {
                            if( message != null ) {
                                //Log.d("GameTimerSmsReceiver", "SMS[" + message.getDisplayOriginatingAddress() + "][" + message.getDisplayMessageBody() + "]");

                                parseSmsMessage(context, message);
                            }
                        } catch (Exception e) { }
                    }
                }
            }
        }
    }

    private void parseSmsMessage(Context context, SmsMessage message) {
        String address = message.getOriginatingAddress();
        String messageBody = message.getMessageBody();

        if( (messageBody != null) && (messageBody.contains(GAMETIMER_TOKEN)) ) {
            //Log.d("GameTimerSmsReceiver", "parseSmsMessage[" + messageBody + "]");
            String[] tokens = messageBody.split(" ");

            if( tokens.length == 3 ) {   // "게임타이어 시작/끝 1/2/3"
                int timerIndex = (Integer.parseInt(tokens[2]) - 1);
                if( (0 <= timerIndex) && (timerIndex < MainActivity.getTimerNumber()) ) {
                    Intent intent = new Intent(context, MainActivity.class);

                    if( tokens[1].equals(GAMETIMER_START_TOKEN) ) {
                        intent.putExtra(MainActivity.KEY_CONTROL_TIMER, MainActivity.CONTROL_CMD[0]);   // Start Command
                    } else if( tokens[1].equals(GAMETIMER_STOP_TOKEN) ) {
                        intent.putExtra(MainActivity.KEY_CONTROL_TIMER, MainActivity.CONTROL_CMD[1]);   // Stop Command
                    } else
                        return;     // 잘못된 명령인 경우는 무시

                    Log.d("GameTimerSmsReceiver", "parseSmsMessage[" + tokens[1] + "][" + tokens[2] + "]");
                    intent.putExtra(MainActivity.KEY_TIMER_INDEX, timerIndex);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        }
    }
}