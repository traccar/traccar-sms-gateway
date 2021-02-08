package org.traccar.gateway.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
if (message.length() > 70) {
    ArrayList<String> msgs = sms.divideMessage(message);
    ArrayList<PendingIntent> sentIntents =  new ArrayList<PendingIntent>();
    for(int i = 0;i<msgs.size();i++){
        sentIntents.add(sentPI);
    }
    sms.sendMultipartTextMessage(phoneNumber, null, msgs, sentIntents, null);
} else {
    sms.sendTextMessage(phoneNumber, null, message, sentPI, deliverPI);
}