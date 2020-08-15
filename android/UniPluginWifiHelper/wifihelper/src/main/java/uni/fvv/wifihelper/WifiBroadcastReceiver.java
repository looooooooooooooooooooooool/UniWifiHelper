package uni.fvv.wifihelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

//监听wifi状态广播接收器
public class WifiBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            //wifi开关变化
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            switch (state) {
                case WifiManager.WIFI_STATE_DISABLED: {
                    //wifi关闭
                    //Log.e("=====", "已经关闭");
                    UniWifiHelper.wifiHelper.callback("WIFI_STATE_DISABLED",null);
                    break;
                }
                case WifiManager.WIFI_STATE_DISABLING: {
                    //wifi正在关闭
                    //Log.e("=====", "正在关闭");
                    UniWifiHelper.wifiHelper.callback("WIFI_STATE_DISABLING",null);
                    break;
                }
                case WifiManager.WIFI_STATE_ENABLED: {
                    //wifi已经打开
                    //Log.e("=====", "已经打开"); ;
                    UniWifiHelper.wifiHelper.callback("WIFI_STATE_ENABLED",null);
                    break;
                }
                case WifiManager.WIFI_STATE_ENABLING: {
                    //wifi正在打开
                    //Log.e("=====", "正在打开");
                    UniWifiHelper.wifiHelper.callback("WIFI_STATE_ENABLING",null);
                    break;
                }
                case WifiManager.WIFI_STATE_UNKNOWN: {
                    //未知
                    //Log.e("=====", "未知状态");
                    UniWifiHelper.wifiHelper.callback("WIFI_STATE_UNKNOWN",null);
                    break;
                }
            }
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
            //监听wifi列表变化
            //Log.e("=====", "wifi列表发生变化");
            UniWifiHelper.wifiHelper.callback("SCAN_RESULTS_AVAILABLE_ACTION",null);
        } else if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                //监听wifi连接状态
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                //Log.e("=====", "--NetworkInfo--" + info.toString());
                if (NetworkInfo.State.DISCONNECTED == info.getState()) {//wifi没连接上
                    //Log.e("=====", "wifi没连接上");
                    UniWifiHelper.wifiHelper.callback("DISCONNECTED",info);
                } else if (NetworkInfo.State.CONNECTED == info.getState()) {//wifi连接上了
                    //Log.e("=====", "wifi以连接");
                    UniWifiHelper.wifiHelper.callback("CONNECTED",info);
                } else if (NetworkInfo.State.CONNECTING == info.getState()) {//正在连接
                    //Log.e("=====", "wifi正在连接");
                    UniWifiHelper.wifiHelper.callback("CONNECTING",info);
                }
            } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int linkWifiResult = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
                if (linkWifiResult == WifiManager.ERROR_AUTHENTICATING) {
                    //Log.e("=====", "wifi密码错误");
                    UniWifiHelper.wifiHelper.callback("ERROR_AUTHENTICATING",null);
                }

            }
        }
    }
}
