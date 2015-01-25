package com.example.coocon.telme;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity {
    private WakeLock wakeLock;
    private boolean iswakeLock = true;// 是否常亮
    private TelephonyManager mTtelephonyManager;

    private  PhoneCallListener mPhoneCallListener;


    public static String [] strList = new String[]{
            "first", "second", "third", "fourth", "fifth"
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }



        Receiver myReceiver = new Receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.MY_BROADCAST");

        registerReceiver(myReceiver, filter);

        mTtelephonyManager =  (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mTtelephonyManager.listen(mPhoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);





        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }




    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        private Button btnOK;
        private Button btnCancel;
        private Switch btnSwitch;
        private EditText myText;
        private ListView listView;
        private GridView gridView;

        private  List<String> listData = new ArrayList<String>();

        public List<String> getListData() {
            return listData;
        }

        public  Boolean addListData(String msg){
            Boolean res = listData.add(msg);
            return res;

        }
        public PlaceholderFragment() {

        }




        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            final Context context = this.getActivity().getApplicationContext();


            listView =  (ListView) rootView.findViewById(R.id.listView);

            //设置listview的数据适配器
            //listView.setAdapter(new ArrayAdapter(context, android.R.layout.simple_list_item_1, strList ));

            gridView = (GridView) rootView.findViewById(R.id.gridView);
            gridView.setAdapter(
                new ArrayAdapter(context, android.R.layout.simple_gallery_item, listData)
            );
            myText = (EditText) rootView.findViewById(R.id.txtTel);
            btnOK = (Button) rootView.findViewById(R.id.btnOK);
            btnOK.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    String str = myText.getText().toString();
                    if (str.isEmpty()) {
                        return ;
                    }
                    myText.getText().clear();
                    addListData(str);
                }
            });

            btnSwitch = (Switch) rootView.findViewById(R.id.btnSwitch);
            btnSwitch.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (b) {
                        MqttService.actionStart(context);
                    }
                    else {
                        MqttService.actionStop(context);

                    }
                }
            });



            MqttService.actionStart(context);

            return rootView;
        }



    }
        @Override
       protected void onResume() {
        // TODO Auto-generated method stub
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, "DPA");

        if (iswakeLock) {
            wakeLock.acquire();
        }
        super.onResume();

    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (wakeLock != null) {
            wakeLock.release();
        }
        //android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 拨打电话的功能
     * TODO：需要增加一个队列，如果已经在拨打电话中，需要等待一会儿
     *
     * @param num
     * @param ctx
     */
    public static void callNumber(String num, Context ctx){
        String str = "15210838121";
        if (num != ""  ) {
            str = num;
        }
        Intent phoneIntent = new Intent(
                Intent.ACTION_CALL, Uri.parse("tel:" + str));
        ctx.startActivity(phoneIntent);

    }





    public class PhoneCallListener extends PhoneStateListener {
        private boolean bphonecalling = false;

        @Override
        public void onCallStateChanged(int state, String incomingnumber) {
            // seems the incoming number is this call back always ""
            if (TelephonyManager.CALL_STATE_OFFHOOK == state) {
                bphonecalling = true;
            } else if (TelephonyManager.CALL_STATE_IDLE == state
                    && bphonecalling) {
                if (mTtelephonyManager != null) {
                    mTtelephonyManager.listen(mPhoneCallListener,
                            PhoneStateListener.LISTEN_NONE);
                }
                bphonecalling = false;

                Intent i = getPackageManager().getLaunchIntentForPackage(
                        getPackageName());
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);

            }

            super.onCallStateChanged(state, incomingnumber);
        }
    }

}


