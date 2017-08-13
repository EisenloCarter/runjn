package com.baidu.trackshow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapView;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.LocationMode;
import com.baidu.trace.OnEntityListener;
import com.baidu.trace.Trace;
import com.baidu.trace.TraceLocation;
import com.baidu.trackshow.UserInformation;

@SuppressLint("NewApi")
public class MainActivity extends FragmentActivity implements OnClickListener {

    /**
     * 轨迹服务
     */
    protected static Trace trace = null;

    /**
     * entity标识
     */
    protected static String entityName = null;

    /**
     * 鹰眼服务ID，开发者创建的鹰眼服务对应的服务ID
     */
    protected static long serviceId = 125574;

    /**
     * 轨迹服务类型（0 : 不建立socket长连接， 1 : 建立socket长连接但不上传位置数据，2 : 建立socket长连接并上传位置数据）
     */
    private int traceType = 2;

    /**
     * 轨迹服务客户端
     */
    protected static LBSTraceClient client = null;

    /**
     * Entity监听器
     */
    protected static OnEntityListener entityListener = null;

    private Button btnTrackUpload;
    private Button btnTrackQuery;

    protected static MapView bmapView = null;
    protected static BaiduMap mBaiduMap = null;

    /**
     * 用于对Fragment进行管理
     */
    private FragmentManager fragmentManager;

    private TrackUploadFragment mTrackUploadFragment;

    private TrackQueryFragment mTrackQueryFragment;

    protected static Context mContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SDKInitializer.initialize(getApplicationContext());

        requestWindowFeature(Window.FEATURE_NO_TITLE);


        Window window = this.getWindow();
     //取消设置透明状态栏,使 ContentView 内容不再覆盖状态栏
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

     //需要设置这个 flag 才能调用 setStatusBarColor 来设置状态栏颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
     //设置状态栏颜色
        window.setStatusBarColor(getResources().getColor(R.color.colorPrimary));

        ViewGroup mContentView = (ViewGroup) this.findViewById(Window.ID_ANDROID_CONTENT);
        View mChildView = mContentView.getChildAt(0);
        if (mChildView != null) {
            //注意不是设置 ContentView 的 FitsSystemWindows, 而是设置 ContentView 的第一个子 View . 预留出系统 View 的空间.
            mChildView.setFitsSystemWindows(true);
        }


        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        // 初始化轨迹服务客户端
        client = new LBSTraceClient(mContext);

        // 设置定位模式
        client.setLocationMode(LocationMode.High_Accuracy);

        // 初始化entity标识
        entityName = "myTrace";

        // 初始化轨迹服务
        trace = new Trace(getApplicationContext(), serviceId, entityName, traceType);

        // 初始化EntityListener
        initOnEntityListener();

        // 初始化组件
        initComponent();

    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();

        // 设置默认的Fragment
        setDefaultFragment();

    }

    /**
     * 初始化组件
     */
    private void initComponent() {
        // 初始化控件
        btnTrackUpload = (Button) findViewById(R.id.btn_trackUpload);
        btnTrackQuery = (Button) findViewById(R.id.btn_trackQuery);

        btnTrackUpload.setOnClickListener(this);
        btnTrackQuery.setOnClickListener(this);

        findViewById(R.id.btn_myinfo).setOnClickListener(this);



        fragmentManager = getSupportFragmentManager();

        bmapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = bmapView.getMap();
        bmapView.showZoomControls(false);

    }

    /**
     * 设置默认的Fragment
    */
    private void setDefaultFragment() {
        handlerButtonClick(R.id.btn_trackUpload);
    }

    /**
     * 点击事件
     */
    public void onClick(View v) {
        // TODO Auto-generated method stub
        handlerButtonClick(v.getId());
    }

    /**
     * 初始化OnEntityListener
     */
    private void initOnEntityListener() {
        entityListener = new OnEntityListener() {

            // 请求失败回调接口
            @Override
            public void onRequestFailedCallback(String arg0) {
                // TODO Auto-generated method stub
                // TrackApplication.showMessage("entity请求失败回调接口消息 : " + arg0);
                System.out.println("entity请求失败回调接口消息 : " + arg0);
            }

            // 添加entity回调接口
            public void onAddEntityCallback(String arg0) {
                // TODO Auto-generated method stub
                TrackApplication.showMessage("添加entity回调接口消息 : " + arg0);
            }

            // 查询entity列表回调接口
            @Override
            public void onQueryEntityListCallback(String message) {
                // TODO Auto-generated method stub
                System.out.println("entityList回调消息 : " + message);
            }

            @Override
            public void onReceiveLocation(TraceLocation location) {
                // TODO Auto-generated method stub
                if (mTrackUploadFragment != null) {
                    mTrackUploadFragment.showRealtimeTrack(location);
                    // System.out.println("获取到实时位置:" + location.toString());
                }
            }

        };
    }

    /**
     * 处理tab点击事件
     *
     * @param id
     */
    private void handlerButtonClick(int id) {
        // 开启Fragment事务
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        // 隐藏Fragment
        hideFragments(transaction);

        switch (id) {

            case R.id.btn_trackQuery:

                TrackUploadFragment.isInUploadFragment = false;

                if (mTrackQueryFragment == null) {
                    mTrackQueryFragment = new TrackQueryFragment();
                    transaction.add(R.id.fragment_content, mTrackQueryFragment);
                } else {
                    transaction.show(mTrackQueryFragment);
                }
                if (null != mTrackUploadFragment) {
                    mTrackUploadFragment.startRefreshThread(false);
                }
                mTrackQueryFragment.addMarker();
                mBaiduMap.setOnMapClickListener(null);
                break;

            case R.id.btn_trackUpload:

                TrackUploadFragment.isInUploadFragment = true;

                if (mTrackUploadFragment == null) {
                    mTrackUploadFragment = new TrackUploadFragment();
                    transaction.add(R.id.fragment_content, mTrackUploadFragment);
                } else {
                    transaction.show(mTrackUploadFragment);
                }

                mTrackUploadFragment.startRefreshThread(true);
                TrackUploadFragment.addMarker();
                mBaiduMap.setOnMapClickListener(null);
                break;
            case R.id.btn_myinfo:

                Intent intent = new Intent(this,UserInformation.class);
                startActivity(intent);
                break;
        }
        // 事务提交
        transaction.commit();

    }

    /**
     * 隐藏Fragment
     */
    private void hideFragments(FragmentTransaction transaction) {

        if (mTrackQueryFragment != null) {
            transaction.hide(mTrackQueryFragment);
        }
        if (mTrackUploadFragment != null) {
            transaction.hide(mTrackUploadFragment);
        }
        // 清空地图覆盖物
        mBaiduMap.clear();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        TrackUploadFragment.isInUploadFragment = false;
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        client.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 获取设备IMEI码
     *
     * @param context
     * @return
     */
    protected static String getImei(Context context) {
        String mImei = "NULL";
        try {
            mImei = ((TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        } catch (Exception e) {
            System.out.println("获取IMEI码失败");
            mImei = "NULL";
        }
        return mImei;
    }

}
