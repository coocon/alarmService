package com.example.coocon.telme;

/**
 * Created by coocon on 15/1/23.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.json.JSONObject;

/*
 * PushService that does all of the work.
 * Most of the logic is borrowed from KeepAliveService.
 * http://code.google.com/p/android-random/source/browse/trunk/TestKeepAlive/src/org/devtcg/demo/keepalive/KeepAliveService.java?r=219
 */
public class MqttService extends Service
{
    // this is the log tag
    public static final String		TAG = "MqttService";

    // the IP address, where your MQTT broker is running.
    // the port at which the broker is running.
    // Let's not use the MQTT persistence.

    // We don't need to remember any state between the connections, so we use a clean start.
    private static boolean			MQTT_CLEAN_START          = true;
    // Let's set the internal keep alive for MQTT to 15 mins. I haven't tested this value much. It could probably be increased.
    private static short			MQTT_KEEP_ALIVE           = 60 * 15;
    // Set quality of services to 0 (at most once delivery), since we don't want push notifications
    // arrive more than once. However, this means that some messages might get lost (delivery is not guaranteed)
    private static int[]			MQTT_QUALITIES_OF_SERVICE = { 0 } ;
    private static int				MQTT_QUALITY_OF_SERVICE   = 0;
    // The broker should not retain any messages.
    private static boolean			MQTT_RETAINED_PUBLISH     = false;

    // MQTT client ID, which is given the broker. In this example, I also use this for the topic header.
    // You can use this to run push notifications for multiple apps with one MQTT broker.
    public static String			MQTT_CLIENT_ID = "coocon";

    // These are the actions for the service (name are descriptive enough)
    private static final String		ACTION_START = MQTT_CLIENT_ID + ".START";
    private static final String		ACTION_STOP = MQTT_CLIENT_ID + ".STOP";
    private static final String		ACTION_KEEPALIVE = MQTT_CLIENT_ID + ".KEEP_ALIVE";
    private static final String		ACTION_RECONNECT = MQTT_CLIENT_ID + ".RECONNECT";

    // Connection log for the push service. Good for debugging.

    // Connectivity manager to determining, when the phone loses connection
    private ConnectivityManager		mConnMan;
    // Notification manager to displaying arrived push notifications
    private NotificationManager		notificationManager;

    // Whether or not the service has been started.
    private boolean 				mStarted;

    // This the application level keep-alive interval, that is used by the AlarmManager
    // to keep the connection active, even when the device goes to sleep.

    // Retry intervals, when the connection is lost.
    private static final long		INITIAL_RETRY_INTERVAL = 1000 * 10;
    private static final long		MAXIMUM_RETRY_INTERVAL = 1000 * 60 * 30;

    // Preferences instance
    private SharedPreferences 		mPrefs;
    // We store in the preferences, whether or not the service has been started
    public static final String		PREF_STARTED = "isStarted";
    // We also store the deviceID (target)
    public static final String		PREF_DEVICE_ID = "deviceID";
    // We store the last retry interval
    public static final String		PREF_RETRY = "retryInterval";

    // Notification title
    public static String			NOTIF_TITLE = "PushService";
    // Notification id
    private static final int		NOTIF_CONNECTED = 0;

    // This is the instance of an MQTT connection.

    private long					mStartTime;

    public static final String ICON_NAME = "icon";//icon res name





    //private final static String CONNECTION_STRING = "tcp://alarm.coocons.com:1883";
    private final static String PORT = ":1883";
    private final static String PROTOCOL = "tcp://";
    public static String HOST = "alarm.coocons.com";



    private final static short KEEP_ALIVE = 30;//低耗网络，但是又需要及时获取数据，心跳30s
    private final static int  QOS = 1;

    private  String CLIENT_ID = "hello,world";

    private MqttClient client =  null;
    private String TOPIC = "/client/alarm";



    // Static method to start the service
    public static void actionStart(Context ctx, String host) {
        HOST = host;
        System.out.println("set host:" + HOST);

        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_START);
        ctx.startService(i);

    }

    // Static method to stop the service
    public static void actionStop(Context ctx) {
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    // Static method to send a keep alive message
    public static void actionPing(Context ctx) {
        Intent i = new Intent(ctx, MqttService.class);
        i.setAction(ACTION_KEEPALIVE);
        ctx.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();


        mStartTime = System.currentTimeMillis();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Get instances of preferences, connectivity manager and notification manager
        mPrefs = getSharedPreferences(TAG, MODE_PRIVATE);
        mConnMan = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);

        TelephonyManager tm = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        CLIENT_ID = tm.getDeviceId();

		/* If our process was reaped by the system for any reason we need
		 * to restore our state with merely a call to onCreate.  We record
		 * the last "started" value and restore it here if necessary. */
        handleCrashedService();

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog() //打印logcat，当然也可以定位到dropbox，通过文件保存相应的log
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects() //探测SQLite数据库操作
                .penaltyLog() //打印logcat
                .penaltyDeath()
                .build());


    }

    // This method does any necessary clean-up need in case the server has been destroyed by the system
    // and then restarted
    private void handleCrashedService() {
        if (wasStarted() == true) {
            log("Handling crashed service...");
            stop();
            // Do a clean start
            start();
        }
    }

    @Override
    public void onDestroy() {
        log("Service destroyed (started=" + mStarted + ")");

        // Stop the services, if it has been started
        if (mStarted == true) {
            stop();
        }


    }

    @Override
    public  int  onStartCommand (Intent intent,int flags, int startId) {

        //super.onStart(intent, startId);
        log("Service started with intent=" + intent);

        // Do an appropriate action based on the intent.
        if (intent.getAction().equals(ACTION_STOP) == true) {
            stop();
            stopSelf();
        } else if (intent.getAction().equals(ACTION_START) == true) {
            start();
        } else if (intent.getAction().equals(ACTION_RECONNECT) == true) {

            if (isNetworkAvailable()) {
                reconnectIfNecessary();
            }
        }
        return START_REDELIVER_INTENT;
    }



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // log helper function
    private void log(String message) {
        log(message, null);
    }
    private void log(String message, Throwable e) {
        if (e != null) {
            Log.e(TAG, message, e);

        } else {
            Log.i(TAG, message);
        }

    }

    // Reads whether or not the service has been started from the preferences
    private boolean wasStarted() {
        return mPrefs.getBoolean(PREF_STARTED, false);
    }

    // Sets whether or not the services has been started in the preferences.
    private void setStarted(boolean started) {
        mPrefs.edit().putBoolean(PREF_STARTED, started).commit();
        mStarted = started;
    }

    private synchronized void start() {
        log("Starting service...");


        // Establish an MQTT connection
        connect();
        // Register a connectivity listener
        registerReceiver(mConnectivityChanged, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private synchronized void stop() {
        // Do nothing, if the service is not running.
        if (mStarted == false) {
            Log.w(TAG, "Attempt to stop connection not active.");
            return;
        }

        // Save stopped state in the preferences
        setStarted(false);

        // Remove the connectivity receiver
        unregisterReceiver(mConnectivityChanged);
        // Any existing reconnect timers should be removed, since we explicitly stopping the service.
        try {
            client.disconnect();

        } catch (MqttException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }






    public void connect( ) {

        try {
            String CONNECTION_STRING = PROTOCOL + HOST + PORT;

            if (client == null) {
                System.out.print(CONNECTION_STRING);
                client = new MqttClient(CONNECTION_STRING,CLIENT_ID, null);
            }
            else {
                String server = client.getServerURI();
                if (!server.equals(CONNECTION_STRING) ) {

                    client = new MqttClient(CONNECTION_STRING,CLIENT_ID, null);
                }

            }


            SimpleCallbackHandler callback = new SimpleCallbackHandler();


            client.setCallback(callback);
            MqttConnectOptions conOptions = new MqttConnectOptions();
            conOptions.setCleanSession(false);
            conOptions.setKeepAliveInterval(KEEP_ALIVE);
            client.connect(conOptions);

            client.subscribe(TOPIC, 0);

            setStarted(true);

        } catch (Exception e) {
            e.printStackTrace();

        }



    }



    private synchronized void reconnectIfNecessary() {
        System.out.println("we try to reconnect");
        connect();

    }








    // This receiver listeners for network changes and updates the MQTT connection
    // accordingly
    private BroadcastReceiver mConnectivityChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get network info

            ConnectivityManager connectivityManager =  (ConnectivityManager)  context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean hasConnectivity = false;
            if (connectivityManager!=null) {
                NetworkInfo [] networkInfos = connectivityManager.getAllNetworkInfo();
                for (int i = 0; i < networkInfos.length; i++) {
                    State state = networkInfos[i].getState();
                    if (NetworkInfo.State.CONNECTED==state) {
                        System.out.println("\n------------> Network is ok");
                        hasConnectivity =  true;
                        break;
                    }
                }
            }

            if (hasConnectivity) {
                    //TODO: see this
                	reconnectIfNecessary();
            }
        }
    };


    public void sendTel(String tel) {
        Intent intent = new Intent("android.intent.action.MY_BROADCAST");
        intent.putExtra("tel",tel);
        sendBroadcast(intent);
    }

    // Display the topbar notification
    private void showNotification(String text) {
        System.out.println(text);
        Intent intent = new Intent(this, MqttService.class);
        Context ctx = this.getApplicationContext();
        PendingIntent  m_PendingIntent = PendingIntent.getActivity(
                ctx, 0, intent, 0
        );

        int iconResID = ctx.getResources().getIdentifier(ICON_NAME,"drawable", ctx.getPackageName());
        Notification notification = new NotificationCompat.Builder(ctx)
                .setContentTitle("51cache")
                .setContentText(text)
                //.setDefaults(Notification.DEFAULT_ALL) //设置默认铃声，震动等
                .setSmallIcon(iconResID)
                .setContentIntent(m_PendingIntent)
                .setAutoCancel(true)
                        //    .setLargeIcon(aBitmap)
                .build();
        int id = (int) System.currentTimeMillis();

        notificationManager.notify(id, notification);

    }

    // Check if we are online
    private boolean isNetworkAvailable() {
        NetworkInfo info = mConnMan.getActiveNetworkInfo();
        if (info == null) {
            return false;
        }
        return info.isConnected();
    }





    /**
     *  实现回调类 可以实现事件监听，从而多次回调
     * @author coocon
     *
     */


    class SimpleCallbackHandler implements MqttCallback {


        @Override
        public void connectionLost(Throwable t) {
            System.out.println("Connection lost!");
            // sendUpdate(getInfo("log", "connection lost"), true);
            try{
                connect();
            } catch(Exception e) {
                e.printStackTrace();
            }
            // code to reconnect to the broker would go here if desired
        }

        @Override
        public void deliveryComplete(MqttDeliveryToken token) {
            try {
                System.out.println("Pub complete" + new String(token.getMessage().getPayload()));
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void messageArrived(MqttTopic topic, MqttMessage message) throws Exception {
            String msg = new String(message.getPayload());
            String tpk = topic.getName();

            System.out.println(tpk);

            JSONObject obj = new JSONObject();

            if (tpk.equals(TOPIC)) {
                obj.put("message", msg);
                obj.put("topic", tpk);
            }
            if (obj.has("topic")) {
                System.out.println(obj.toString());
                showNotification(msg);
                sendTel(msg);
            }

        }

    }

}