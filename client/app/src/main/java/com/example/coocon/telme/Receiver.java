package com.example.coocon.telme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by coocon on 15/1/24.
 */
public  class  Receiver  extends BroadcastReceiver {

    private static final String TAG = "Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Get network info
        String tel = intent.getStringExtra("tel");

        MainActivity.callNumber(tel, context);


       // new MainActivity().showMessage(tel);
    }

}