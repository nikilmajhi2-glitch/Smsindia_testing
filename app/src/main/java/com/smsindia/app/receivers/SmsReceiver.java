package com.smsindia.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder messageBody = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            sender = smsMessage.getDisplayOriginatingAddress();
            messageBody.append(smsMessage.getMessageBody());
        }

        // Example: Show a toast with sender and message
        Toast.makeText(context, "SMS from " + sender + ": " + messageBody.toString(), Toast.LENGTH_LONG).show();

        // You can add your own logic here to save or process the message
    }
}