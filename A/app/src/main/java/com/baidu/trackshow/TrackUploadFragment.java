package com.baidu.trackshow;

import android.annotation.SuppressLint;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;
import com.baidu.mapapi.utils.CoordinateConverter.CoordType;
import com.baidu.trace.OnStartTraceListener;
import com.baidu.trace.OnStopTraceListener;
import com.baidu.trace.TraceLocation;
import com.baidu.trackutils.DateUtils;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Chronometer;
import android.widget.Chronometer.OnChronometerTickListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.baidu.trackshow.TrackQueryFragment;
/**
 * 轨迹追踪
 */
@SuppressLint("NewApi")
public class TrackUploadFragment extends Fragment {

    private Chronometer jishiqi;

    private View btnStartTrace = null;

    private View btnStopTrace = null;

    protected TextView tvEntityName = null;

    /**
     * 开启轨迹服务监听器
     */
    protected static OnStartTraceListener startTraceListener = null;

    /**
     * 停止轨迹服务监听器
     */
    protected static OnStopTraceListener stopTraceListener = null;

    /**
     * 采集周期（单位 : 秒）
     */
    private int gatherInterval = 5;

    /**
     * 打包周期（单位 : 秒）
     */
    private int packInterval = 15;

    /**
     * 图标
     */
    private static BitmapDescriptor realtimeBitmap;

    private static Overlay overlay = null;

    // 覆盖物
    protected static OverlayOptions overlayOptions;
    // 路线覆盖物
    private static PolylineOptions polyline = null;

    private static List<LatLng> pointList = new ArrayList<LatLng>();

    protected boolean isTraceStart = false;

    private Intent serviceIntent = null;

    /**
     * 刷新地图线程(获取实时点)
     */
    protected RefreshThread refreshThread = null;

    protected static MapStatusUpdate msUpdate = null;

    private View view = null;

    private LayoutInflater mInflater = null;

    protected static boolean isInUploadFragment = true;

    private static boolean isRegister = false;

    protected static PowerManager pm = null;

    protected static WakeLock wakeLock = null;

    private PowerReceiver powerReceiver = new PowerReceiver();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_trackupload, container, false);

        mInflater = inflater;

        // 初始化
        init();

        // 初始化监听器
        initListener();

        // 设置采集周期
        setInterval();

        // 设置http请求协议类型
        setRequestType();

        return view;
    }

    /**
     * 初始化
     */
    private void init() {
        jishiqi = (Chronometer) view.findViewById(R.id.jishiqi);

        btnStartTrace =  view.findViewById(R.id.btn_startTrace);

        btnStopTrace = view.findViewById(R.id.btn_stopTrace);

        tvEntityName = (TextView) view.findViewById(R.id.tv_entityName);

        tvEntityName.setText(" entityName : " + MainActivity.entityName + " ");

        jishiqi.setBase(SystemClock.elapsedRealtime());  //设置起始时间 ，这里是从0开始
        jishiqi.setFormat("%s");

        //这里是计时器的监听器，可以在里面添加比如计时到多少事件提示什么文本等事件
        jishiqi.setOnChronometerTickListener(new OnChronometerTickListener() {

            @Override
            public void onChronometerTick(Chronometer chronometer) {
                // TODO Auto-generated method stub

            }
        });



        btnStartTrace.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub

                Toast.makeText(getActivity(), "正在开启轨迹服务，请稍候", Toast.LENGTH_SHORT).show();
                startTrace();
                btnStartTrace.setVisibility(View.INVISIBLE);
                btnStopTrace.setVisibility(View.VISIBLE);
                jishiqi.setBase(SystemClock.elapsedRealtime());
                jishiqi.start();

                if (!isRegister) {
                    if (null == pm) {
                        pm = (PowerManager) MainActivity.mContext.getSystemService(Context.POWER_SERVICE);
                    }
                    if (null == wakeLock) {
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "track upload");
                    }
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_SCREEN_OFF);
                    filter.addAction(Intent.ACTION_SCREEN_ON);
                    MainActivity.mContext.registerReceiver(powerReceiver, filter);
                    isRegister = true;
                }

            }
        });

        btnStopTrace.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub

                //Toast.makeText(getActivity(), "正在停止轨迹服务，请稍候", Toast.LENGTH_SHORT).show();
                stopTrace();
                btnStopTrace.setVisibility(View.INVISIBLE);
                btnStartTrace.setVisibility(View.VISIBLE);
                jishiqi.stop();
                btnStartTrace.setEnabled(false);

                checkSpeed();

                if (isRegister) {
                    try {
                        MainActivity.mContext.unregisterReceiver(powerReceiver);
                        isRegister = false;
                    } catch (Exception e) {
                        // TODO: handle
                    }

                }
            }
        });

    }

    public void checkSpeed(){
        //判断是否打卡成功
        //TrackQueryFragment temp = new TrackQueryFragment();
        //temp.queryDistance(1,null);
        double count=Seconds(jishiqi);
        float doubleOMG = Float.parseFloat("1350");
        double speed = doubleOMG/count;

        if(speed < 1.0 || speed > 3.0)
        {
            Toast.makeText(getActivity(), "打卡失败！", Toast.LENGTH_SHORT).show();
            //String tmp = String.valueOf(speed);//or
            //Toast.makeText(getActivity(),tmp, Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(getActivity(), "打卡成功！", Toast.LENGTH_SHORT).show();
           // String tmp = String.valueOf(speed);//or
            // Toast.makeText(getActivity(),tmp, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *
     * @param cmt  Chronometer控件
     * @return 小时+分钟+秒数  的所有秒数
     */
    public  static double Seconds(Chronometer cmt) {
        double totalss = 0.0;
        String string = cmt.getText().toString();

        if(string.length()==7){
            String[] split = string.split(":");
            String string2 = split[0];
            double hour = Integer.parseInt(string2);
            double Hours = hour*3600;
            String string3 = split[1];
            double min = Integer.parseInt(string3);
            double Mins = min*60;
            double  SS =Integer.parseInt(split[2]);
            totalss = Hours+Mins+SS;
            return totalss;
        }
        else if(string.length()==5){
            String[] split = string.split(":");
            String string3 = split[0];
            double min = Integer.parseInt(string3);
            double Mins = min*60;
            double  SS = Integer.parseInt(split[1]);
            totalss = Mins+SS;
            return totalss;
        }else {
            double SS = Integer.parseInt(string);
            totalss = SS;
            return totalss;
        }
    }

    public void startMonitorService() {
        serviceIntent = new Intent(MainActivity.mContext,
                com.baidu.trackshow.MonitorService.class);
        MainActivity.mContext.startService(serviceIntent);
    }

    /**
     * 初始化监听器
     */
    private void initListener() {
        // 初始化开启轨迹服务监听器
        initOnStartTraceListener();

        // 初始化停止轨迹服务监听器
        initOnStopTraceListener();
    }

    /**
     * 开启轨迹服务
     */
    private void startTrace() {
        // 通过轨迹服务客户端client开启轨迹服务
        MainActivity.client.startTrace(MainActivity.trace, startTraceListener);

        if (!MonitorService.isRunning) {
            // 开启监听service
            MonitorService.isCheck = true;
            MonitorService.isRunning = true;
            startMonitorService();
        }
    }

    /**
     * 停止轨迹服务
     */
    private void stopTrace() {

        // 停止监听service
        MonitorService.isCheck = false;
        MonitorService.isRunning = false;

        // 通过轨迹服务客户端client停止轨迹服务

        MainActivity.client.stopTrace(MainActivity.trace, stopTraceListener);

        if (null != serviceIntent) {
            MainActivity.mContext.stopService(serviceIntent);
        }
    }

    /**
     * 设置采集周期和打包周期
     */
    private void setInterval() {
        MainActivity.client.setInterval(gatherInterval, packInterval);
    }

    /**
     * 设置请求协议
     */
    protected static void setRequestType() {
        int type = 0;
        MainActivity.client.setProtocolType(type);
    }

    /**
     * 查询实时轨迹
     */
    private void queryRealtimeLoc() {
        MainActivity.client.queryRealtimeLoc(MainActivity.serviceId, MainActivity.entityListener);
    }

    /**
     * 查询entityList
     */
    @SuppressWarnings("unused")
    private void queryEntityList() {
        // // entity标识列表（多个entityName，以英文逗号"," 分割）
        String entityNames = MainActivity.entityName;
        // 属性名称（格式为 : "key1=value1,key2=value2,....."）
        String columnKey = "key1=value1,key2=value2";
        // 返回结果的类型（0 : 返回全部结果，1 : 只返回entityName的列表）
        int returnType = 0;
        // 活跃时间（指定该字段时，返回从该时间点之后仍有位置变动的entity的实时点集合）
        // int activeTime = (int) (System.currentTimeMillis() / 1000 - 30);
        int activeTime = 0;
        // 分页大小
        int pageSize = 10;
        // 分页索引
        int pageIndex = 1;

        MainActivity.client.queryEntityList(MainActivity.serviceId, entityNames, columnKey, returnType, activeTime,
                pageSize,
                pageIndex, MainActivity.entityListener);
    }

    /**
     * 初始化OnStartTraceListener
     */
    private void initOnStartTraceListener() {
        // 初始化startTraceListener
        startTraceListener = new OnStartTraceListener() {

            // 开启轨迹服务回调接口（arg0 : 消息编码，arg1 : 消息内容，详情查看类参考）
            public void onTraceCallback(int arg0, String arg1) {
                // TODO Auto-generated method stub
                showMessage("开启轨迹服务回调接口消息 [消息编码 : " + arg0 + "，消息内容 : " + arg1 + "]", Integer.valueOf(arg0));
                if (0 == arg0 || 10006 == arg0 || 10008 == arg0 || 10009 == arg0) {
                    isTraceStart = true;
                    // startRefreshThread(true);
                }
            }

            // 轨迹服务推送接口（用于接收服务端推送消息，arg0 : 消息类型，arg1 : 消息内容，详情查看类参考）
            public void onTracePushCallback(byte arg0, String arg1) {
                // TODO Auto-generated method stub
                if (0x03 == arg0 || 0x04 == arg0) {
                    try {
                        JSONObject dataJson = new JSONObject(arg1);
                        if (null != dataJson) {
                            String mPerson = dataJson.getString("monitored_person");
                            String action = dataJson.getInt("action") == 1 ? "进入" : "离开";
                            String date = DateUtils.getDate(dataJson.getInt("time"));
                            long fenceId = dataJson.getLong("fence_id");
                            showMessage("监控对象[" + mPerson + "]于" + date + " [" + action + "][" + fenceId + "号]围栏",
                                    null);
                        }

                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        showMessage("轨迹服务推送接口消息 [消息类型 : " + arg0 + "，消息内容 : " + arg1 + "]", null);
                    }
                } else {
                    showMessage("轨迹服务推送接口消息 [消息类型 : " + arg0 + "，消息内容 : " + arg1 + "]", null);
                }
            }

        };
    }

    /**
     * 初始化OnStopTraceListener
     */
    private void initOnStopTraceListener() {
        // 初始化stopTraceListener
        stopTraceListener = new OnStopTraceListener() {

            // 轨迹服务停止成功
            public void onStopTraceSuccess() {
                // TODO Auto-generated method stub
                //showMessage("停止轨迹服务成功", Integer.valueOf(1));
                isTraceStart = false;
                startRefreshThread(false);
                MainActivity.client.onDestroy();
            }

            // 轨迹服务停止失败（arg0 : 错误编码，arg1 : 消息内容，详情查看类参考）
            public void onStopTraceFailed(int arg0, String arg1) {
                // TODO Auto-generated method stub
                showMessage("停止轨迹服务接口消息 [错误编码 : " + arg0 + "，消息内容 : " + arg1 + "]", null);
                startRefreshThread(false);
            }
        };
    }

    protected class RefreshThread extends Thread {

        protected boolean refresh = true;

        @Override
        public void run() {
            // TODO Auto-generated method stub
            Looper.prepare();
            while (refresh) {
                // 查询实时位置
                queryRealtimeLoc();

                try {
                    Thread.sleep(gatherInterval * 1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    System.out.println("线程休眠失败");
                }
            }
            Looper.loop();
        }
    }

    /**
     * 显示实时轨迹
     *
     * @param location
     */
    protected void showRealtimeTrack(TraceLocation location) {

        if (null == refreshThread || !refreshThread.refresh) {
            return;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        if (Math.abs(latitude - 0.0) < 0.000001 && Math.abs(longitude - 0.0) < 0.000001) {
            showMessage("当前查询无轨迹点", null);

        } else {

            LatLng latLng = new LatLng(latitude, longitude);

            if (1 == location.getCoordType()) {
                LatLng sourceLatLng = latLng;
                CoordinateConverter converter = new
                        CoordinateConverter();
                converter.from(CoordType.GPS);
                converter.coord(sourceLatLng);
                latLng = converter.convert();
            }

            pointList.add(latLng);

            if (isInUploadFragment) {
                // 绘制实时点
                drawRealtimePoint(latLng);
            }

        }

    }

    /**
     * 绘制实时点
     *
     * @param point
     */
    private void drawRealtimePoint(LatLng point) {

        if (null != overlay) {
            overlay.remove();
        }

        MapStatus mMapStatus = new MapStatus.Builder().target(point).zoom(19).build();

        msUpdate = MapStatusUpdateFactory.newMapStatus(mMapStatus);

        if (null == realtimeBitmap) {
            realtimeBitmap = BitmapDescriptorFactory
                    .fromResource(R.mipmap.icon_geo);
        }

        overlayOptions = new MarkerOptions().position(point)
                .icon(realtimeBitmap).zIndex(9).draggable(true);

        if (pointList.size() >= 2 && pointList.size() <= 10000) {
            // 添加路线（轨迹）
            polyline = new PolylineOptions().width(10)
                    .color(Color.RED).points(pointList);
        }

        addMarker();

    }

    /**
     * 添加地图覆盖物
     */
    protected static void addMarker() {

        if (null != msUpdate) {
            MainActivity.mBaiduMap.setMapStatus(msUpdate);
        }

        // 路线覆盖物
        if (null != polyline) {
            MainActivity.mBaiduMap.addOverlay(polyline);
        }

        // 实时点覆盖物
        if (null != overlayOptions) {
            overlay = MainActivity.mBaiduMap.addOverlay(overlayOptions);
        }

    }

    protected void startRefreshThread(boolean isStart) {
        if (null == refreshThread) {
            refreshThread = new RefreshThread();
        }
        refreshThread.refresh = isStart;
        if (isStart) {
            if (!refreshThread.isAlive()) {
                refreshThread.start();
            }
        } else {
            refreshThread = null;
        }
    }

    private void showMessage(final String message, final Integer errorNo) {

        new Handler(MainActivity.mContext.getMainLooper()).post(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.mContext, message, Toast.LENGTH_LONG).show();
/*
                if (null != errorNo) {
                    if (0 == errorNo.intValue() || 10006 == errorNo.intValue() || 10008 == errorNo.intValue()
                            || 10009 == errorNo.intValue()) {
                        btnStartTrace.setBackgroundColor(Color.rgb(0x99, 0xcc, 0xff));
                        btnStartTrace.setTextColor(Color.rgb(0x00, 0x00, 0xd8));
                    } else if (1 == errorNo.intValue() || 10004 == errorNo.intValue()) {
                        btnStartTrace.setBackgroundColor(Color.rgb(0xff, 0xff, 0xff));
                        btnStartTrace.setTextColor(Color.rgb(0x00, 0x00, 0x00));
                    }
                }*/
            }
        });

    }
}
