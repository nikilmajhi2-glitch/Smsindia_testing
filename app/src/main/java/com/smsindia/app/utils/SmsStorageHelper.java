import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.List;

public class SmsStorageHelper {

    public static class SmsMessageData {
        public String id;
        public String address;
        public String body;
        public long date;

        public SmsMessageData(String id, String address, String body, long date) {
            this.id = id;
            this.address = address;
            this.body = body;
            this.date = date;
        }
    }

    public static List<SmsMessageData> getInboxMessages(Context context) {
        List<SmsMessageData> messages = new ArrayList<>();
        Uri inboxUri = Telephony.Sms.Inbox.CONTENT_URI;

        Cursor cursor = context.getContentResolver().query(inboxUri,
                new String[]{Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                null, null, Telephony.Sms.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));

                messages.add(new SmsMessageData(id, address, body, date));
            }
            cursor.close();
        }
        return messages;
    }
}