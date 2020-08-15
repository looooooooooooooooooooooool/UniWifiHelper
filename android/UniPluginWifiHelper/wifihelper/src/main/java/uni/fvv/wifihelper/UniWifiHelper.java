package uni.fvv.wifihelper;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kongzue.wifilinker.WifiUtil;
import com.kongzue.wifilinker.interfaces.OnWifiConnectStatusChangeListener;
import com.kongzue.wifilinker.interfaces.OnWifiScanListener;
import com.kongzue.wifilinker.util.WifiAutoConnectManager;
import com.stealthcopter.networktools.PortScan;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uni.fvv.wifihelper.util.IpScanner;
import uni.fvv.wifihelper.util.ShellUtils;


public class UniWifiHelper extends WXModule {

    private WifiManager wifiManager;
    private WifiBroadcastReceiver wifiReceiver;
    WifiManager.LocalOnlyHotspotReservation mReservation;
    public static UniWifiHelper wifiHelper;
    JSCallback wifiCallback = null;
    WifiUtil wifiUtil;

    @JSMethod(uiThread = true)
    public void test(){
        Toast.makeText(mWXSDKInstance.getContext(),"test",Toast.LENGTH_LONG).show();
    }

    //初始化
    @JSMethod(uiThread = true)
    public void init(JSCallback jsCallback){
        jsCallback = SetJSCallBack(jsCallback);
        wifiHelper = this;
        wifiManager = (WifiManager) mWXSDKInstance.getContext().getApplicationContext().getSystemService(mWXSDKInstance.getContext().WIFI_SERVICE);
        wifiUtil = new WifiUtil((Activity) mWXSDKInstance.getContext());

        wifiCallback = jsCallback;
        registerNetworkConnectChangeReceiver();

    }

    //回调
    public void callback(String type,Object data){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type",type);
        jsonObject.put("data",data);
        wifiCallback.invokeAndKeepAlive(jsonObject);
    }


    //注册广播
    private void registerNetworkConnectChangeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        wifiReceiver = new WifiBroadcastReceiver();
        mWXSDKInstance.getContext().registerReceiver(wifiReceiver, filter);
    }


    //连接wifi
    @JSMethod(uiThread = true)
    public void connectWifi(JSONObject jsonObject)  {
        if(!isWifiEnable()){
            openWifi();
        }
        String ssid = SetValue(jsonObject,"ssid","");
        String password = SetValue(jsonObject,"password","");
        String inputType = SetValue(jsonObject,"type","WPA");

        WifiAutoConnectManager.WifiCipherType encType = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS;
        if(!password.equals("")){
            if(inputType.toUpperCase().equals("WPA")){
                encType = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA;
            }else{
                encType = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WEP;
            }
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiUtil.link(ssid, password, encType, new OnWifiConnectStatusChangeListener() {
                @Override
                public void onStatusChange(boolean b, int i) {
                    JSONObject cb = new JSONObject();
                    String cbType = "ERROR_DEVICE_NOT_HAVE_WIFI";
                    switch (i){
                        case -2:
                            cbType = "ERROR_CONNECT";
                            break;
                        case -3:
                            cbType = "ERROR_CONNECT_SYS_EXISTS_SAME_CONFIG";
                            break;
                        case -11:
                            cbType = "ERROR_AUTHENTICATING";
                            break;
                        case 1:
                            cbType = "CONNECTING";
                            break;
                        case 2:
                            cbType = "CONNECTED";
                            break;
                        case 3:
                            cbType = "DISCONNECTED";
                            break;
                    }
                    callback(cbType,null);
                }

                @Override
                public void onConnect(com.kongzue.wifilinker.util.WifiInfo wifiInfo) {
                    callback("CONNECTED",JSONObject.toJSONString(wifiInfo));
                }
            });
        }else{
            encType = WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA;
            wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
            disconnect();
            wifiManager.enableNetwork(getConfig(ssid,password,encType),true);
        }
    }

    public int getConfig(String ssid,String password, WifiAutoConnectManager.WifiCipherType type){
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = ssid;
        wifiConfig.status = WifiConfiguration.Status.DISABLED;
        forgetNetwork(ssid);
        // Dependent on the security type of the selected network
        // we set the security settings for the configuration
        if (type == WifiAutoConnectManager.WifiCipherType.WIFICIPHER_NOPASS) {
            // No security
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wifiConfig.allowedAuthAlgorithms.clear();
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        } else if (type == WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA) {
            //WPA/WPA2 Security
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wifiConfig.preSharedKey = "\"".concat(password).concat("\"");
        } else if (type == WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WEP) {
            // WEP Security
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

            if (getHexKey(password)) wifiConfig.wepKeys[0] = password;
            else wifiConfig.wepKeys[0] = "\"".concat(password).concat("\"");
            wifiConfig.wepTxKeyIndex = 0;
        }

        // Finally we add the new configuration to the managed list of networks
        int networkID = wifiManager.addNetwork(wifiConfig);
        return networkID;
    }

    private static boolean getHexKey(String s) {
        if (s == null) {
            return false;
        }

        int len = s.length();
        if (len != 10 && len != 26 && len != 58) {
            return false;
        }

        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                continue;
            }
            return false;
        }
        return true;
    }
    /**
     * 断开连接
     */
    @JSMethod(uiThread = false)
    public boolean disconnect() {
        return wifiManager != null && wifiManager.disconnect();
    }


    /**
     * 得到配置好的网络连接
     * @param ssid
     * @return
     */
    private WifiConfiguration isExist(String ssid) {
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {
            if (config.SSID.equals("\""+ssid+"\"")) {
                return config;
            }
        }
        return null;
    }

    /**
     * 设置静态ip地址的方法
     */
    @JSMethod(uiThread = false)
    public boolean setStaticIp(JSONObject jsonObject) {
        boolean dhcp = SetValue(jsonObject,"dhcp",true);
        String ip = SetValue(jsonObject,"ip","ip");
        int prefix = SetValue(jsonObject,"netmask",24);
        String dns1 = SetValue(jsonObject,"dns1","");
        String dns2 = SetValue(jsonObject,"dns2","");
        String gateway = SetValue(jsonObject,"gateway","");

        boolean flag=false;
        if (!wifiManager.isWifiEnabled()) {
            // wifi is disabled
            return flag;
        }
        // get the current wifi configuration
        WifiConfiguration wifiConfig = null;
        WifiInfo connectionInfo = wifiManager.getConnectionInfo();
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration conf : configuredNetworks) {
                if (conf.networkId == connectionInfo.getNetworkId()) {
                    wifiConfig = conf;
                    break;
                }
            }
        }
        if (wifiConfig == null) {
            // wifi is not connected
            return flag;
        }
        if (android.os.Build.VERSION.SDK_INT < 11) { // 如果是android2.x版本的话
            ContentResolver ctRes = mWXSDKInstance.getContext().getContentResolver();
            android.provider.Settings.System.putInt(ctRes,
                    android.provider.Settings.System.WIFI_USE_STATIC_IP, 1);
            android.provider.Settings.System.putString(ctRes,
                    android.provider.Settings.System.WIFI_STATIC_IP, ip);
            flag=true;
            return flag;
        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) { // 如果是android3.x版本及以上的话
            try {
                setIpAssignment("STATIC", wifiConfig);
                setIpAddress(InetAddress.getByName(ip), prefix, wifiConfig);
                setGateway(InetAddress.getByName(gateway), wifiConfig);
                setDNS(InetAddress.getByName(dns1), wifiConfig);
                int netId = wifiManager.updateNetwork(wifiConfig);
                boolean result =  netId!= -1; //apply the setting
                if(result){
                    boolean isDisconnected =  wifiManager.disconnect();
                    boolean configSaved = wifiManager.saveConfiguration(); //Save it
                    boolean isEnabled = wifiManager.enableNetwork(wifiConfig.networkId, true);
                    // reconnect with the new static IP
                    boolean isReconnected = wifiManager.reconnect();
                }
             /*   wifiManager.updateNetwork(wifiConfig); // apply the setting
                wifiManager.saveConfiguration(); //Save it*/
                Log.i("test","静态ip设置成功！");
                flag=true;
                return flag;
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("test","静态ip设置失败！");
                flag=false;
                return flag;
            }
        } else {//如果是android5.x版本及以上的话
            try {
                Class<?> ipAssignment = wifiConfig.getClass().getMethod("getIpAssignment").invoke(wifiConfig).getClass();
                Object staticConf = wifiConfig.getClass().getMethod("getStaticIpConfiguration").invoke(wifiConfig);

                if (dhcp) {
                    wifiConfig.getClass().getMethod("setIpAssignment", ipAssignment).invoke(wifiConfig, Enum.valueOf((Class<Enum>) ipAssignment, "DHCP"));
                    if (staticConf != null) {
                        staticConf.getClass().getMethod("clear").invoke(staticConf);
                    }
                } else {
                    wifiConfig.getClass().getMethod("setIpAssignment", ipAssignment).invoke(wifiConfig, Enum.valueOf((Class<Enum>) ipAssignment, "STATIC"));
                    if (staticConf == null) {
                        Class<?> staticConfigClass = Class.forName("android.net.StaticIpConfiguration");
                        staticConf = staticConfigClass.newInstance();
                    }
                    // STATIC IP AND MASK PREFIX
                    Constructor<?> laConstructor = LinkAddress.class.getConstructor(InetAddress.class, int.class);
                    LinkAddress linkAddress = (LinkAddress) laConstructor.newInstance(
                            InetAddress.getByName(ip),
                            prefix);
                    staticConf.getClass().getField("ipAddress").set(staticConf, linkAddress);
                    // GATEWAY
                    staticConf.getClass().getField("gateway").set(staticConf, InetAddress.getByName(gateway));
                    // DNS
                    List<InetAddress> dnsServers = (List<InetAddress>) staticConf.getClass().getField("dnsServers").get(staticConf);
                    dnsServers.clear();
                    dnsServers.add(InetAddress.getByName(dns1));
                    dnsServers.add(InetAddress.getByName(dns2)); // Google DNS as DNS2 for safety
                    // apply the new static configuration
                    wifiConfig.getClass().getMethod("setStaticIpConfiguration", staticConf.getClass()).invoke(wifiConfig, staticConf);
                }
                // apply the configuration change
                boolean result = wifiManager.updateNetwork(wifiConfig) != -1; //apply the setting
                if (result) result = wifiManager.saveConfiguration(); //Save it
                if (result){
                    wifiManager.disconnect();
                    wifiManager.reassociate();
                } // reconnect with the new static IP
                flag = result;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return flag;
    }




    private void setIpAssignment(String assign, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException {
        setEnumField(wifiConf, assign, "ipAssignment");
    }


    private void setIpAddress(InetAddress addr, int prefixLength,
                                     WifiConfiguration wifiConf) throws SecurityException,
            IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException,
            ClassNotFoundException, InstantiationException,
            InvocationTargetException {
        Object linkProperties = getField(wifiConf, "linkProperties");
        if (linkProperties == null)
            return;
        Class<?> laClass = Class.forName("android.net.LinkAddress");
        Constructor<?> laConstructor = laClass.getConstructor(new Class[]{

                InetAddress.class, int.class});
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);
        ArrayList<Object> mLinkAddresses = (ArrayList<Object>) getDeclaredField(
                linkProperties, "mLinkAddresses");
        mLinkAddresses.clear();
        mLinkAddresses.add(linkAddress);
    }

    private Object getField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    private Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }


    private void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }


    private void setGateway(InetAddress gateway, WifiConfiguration wifiConf)
            throws SecurityException,
            IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException, ClassNotFoundException,
            NoSuchMethodException, InstantiationException,
            InvocationTargetException {
        Object linkProperties = getField(wifiConf, "linkProperties");
        if (linkProperties == null)
            return;
        if (android.os.Build.VERSION.SDK_INT >= 14) { // android4.x版本
            Class<?> routeInfoClass = Class.forName("android.net.RouteInfo");
            Constructor<?> routeInfoConstructor = routeInfoClass
                    .getConstructor(new Class[]{InetAddress.class});
            Object routeInfo = routeInfoConstructor.newInstance(gateway);
            ArrayList<Object> mRoutes = (ArrayList<Object>) getDeclaredField(

                    linkProperties, "mRoutes");
            mRoutes.clear();
            mRoutes.add(routeInfo);
        } else { // android3.x版本
            ArrayList<InetAddress> mGateways = (ArrayList<InetAddress>) getDeclaredField(
                    linkProperties, "mGateways");
            //    mGateways.clear();
            mGateways.add(gateway);

        }
    }


    private void setDNS(InetAddress dns, WifiConfiguration wifiConf)

            throws SecurityException, IllegalArgumentException,

            NoSuchFieldException, IllegalAccessException {

        Object linkProperties = getField(wifiConf, "linkProperties");
        if (linkProperties == null)
            return;
        ArrayList<InetAddress> mDnses = (ArrayList<InetAddress>)
                getDeclaredField(linkProperties, "mDnses");
        mDnses.clear(); // 清除原有DNS设置（如果只想增加，不想清除，词句可省略）
        mDnses.add(dns);
        //增加新的DNS
    }


    //忘记网络
    @JSMethod(uiThread = true)
    public void forgetNetwork(String ssid){
        if(ssid == null ){
            return;
        }
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {
            if (config.SSID.equals("\""+ssid+"\"")) {
                wifiManager.removeNetwork(config.networkId);
            }
        }
    }

    /**
     * 获取WiFi列表
     * @return
     */
    @JSMethod(uiThread = true)
    public void getWifiList(JSCallback jsCallback){
        if (!checkLocation()) {
            return;
        }
        jsCallback = SetJSCallBack(jsCallback);
        JSONArray jsonArray = new JSONArray();
        if (wifiManager != null && isWifiEnable()){
            for (ScanResult res:wifiManager.getScanResults()  ) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("mac",res.BSSID);
                jsonObject.put("ssid",res.SSID);
                jsonObject.put("capabilities",res.capabilities);
                jsonObject.put("level",calculateSignalLevel(res.level));
                jsonArray.add(jsonObject);
            }
        }
        jsCallback.invoke(jsonArray);
    }
    int calculateSignalLevel(int rssi ){
        int MIN_RSSI        = -100;
        int MAX_RSSI        = -55;
        int levels          = 101;
        if (rssi <= MIN_RSSI) {
            return 0;
        } else if (rssi >= MAX_RSSI) {
            return levels - 1;
        } else {
            float inputRange = (MAX_RSSI - MIN_RSSI);
            float outputRange = (levels - 1);
            return (int)((float)(rssi - MIN_RSSI) * outputRange / inputRange);
        }
    }

    //获取wifi信息
    @JSMethod(uiThread = true)
    public void getDHCPInfo(JSCallback jsCallback){
        jsCallback = SetJSCallBack(jsCallback);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        JSONObject jsonObject = new JSONObject();
        try{
            jsonObject.put("dns1",getCorrectIPAddress(dhcpInfo.dns1));
            jsonObject.put("dns2",getCorrectIPAddress(dhcpInfo.dns1));
            jsonObject.put("gateway",getCorrectIPAddress(dhcpInfo.gateway));
            jsonObject.put("ip",getCorrectIPAddress(dhcpInfo.ipAddress));
            jsonObject.put("netmask",getCorrectIPAddress(dhcpInfo.netmask));
            jsonObject.put("server",getCorrectIPAddress(dhcpInfo.serverAddress));
        }catch (Exception e){

        }
        jsCallback.invoke(jsonObject);
    }

    //获取wifi信息
    @JSMethod(uiThread = false)
    public void getWifiInfo(JSCallback jsCallback){
        if (!checkLocation()) {
            return;
        }
        jsCallback = SetJSCallBack(jsCallback);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        jsCallback.invoke(wifiInfo);
    }

    //获取连接信息
    @JSMethod(uiThread = true)
    public void getConnectedInfo(JSCallback jsCallback) {
        jsCallback = SetJSCallBack(jsCallback);
        JSONArray jsonArray = new JSONArray();
        try {
            ShellUtils.CommandResult res =  ShellUtils.execCommand("ip neigh",false,true);
            if(res == null || res.successMsg == null || res.successMsg == ""){
                jsCallback.invoke(jsonArray);
                return;
            }
            String regEx = "(.*?)([A-Z]+)";
            Pattern pat = Pattern.compile(regEx);
            Matcher mat = pat.matcher(res.successMsg);
            while(mat.find()){
                String str = mat.group();
                String[] tempItem =  str.split(" ");
                if(tempItem.length < 5){
                    continue;
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("ip",tempItem[0]);
                jsonObject.put("device",tempItem[2]);
                jsonObject.put("mac",tempItem[4]);
                jsonObject.put("state",tempItem[5]);
                jsonArray.add(jsonObject);
            }
            jsCallback.invoke(jsonArray);
        } catch (Exception e) {
            e.printStackTrace();
            jsCallback.invoke(jsonArray);
        }
    }

    /**
     * 打开WiFi
     */
    @JSMethod(uiThread = false)
    public void openWifi(){
        if(isWifiApEnabled()){
            closeWifiAp();
        }

        if (wifiManager != null && !isWifiEnable()){
            wifiManager.setWifiEnabled(true);
        }
    }

    /**
     * 关闭WiFi
     */
    @JSMethod(uiThread = false)
    public void closeWifi(){
        if (wifiManager != null && isWifiEnable()){
            wifiManager.setWifiEnabled(false);
        }
    }

    /**
     * wifi是否打开
     * @return
     */
    @JSMethod(uiThread = false)
    public boolean isWifiEnable(){
        boolean isEnable = false;
        if (wifiManager != null){
            if (wifiManager.isWifiEnabled()){
                isEnable = true;
            }
        }
        return isEnable;
    }

    /**
     * 创建Wifi热点
     */
    @JSMethod(uiThread = true)
    public void createWifi(JSONObject jsonObject,JSCallback jsCallback) {
        jsCallback = SetJSCallBack(jsCallback);
        boolean enable = false;
        String wifiName = SetValue(jsonObject,"name","fvv");
        String password = SetValue(jsonObject,"password","");
        Boolean hidden = SetValue(jsonObject,"hide",false);

        if (wifiManager.isWifiEnabled()) {
            //如果wifi处于打开状态，则关闭wifi,
            wifiManager.setWifiEnabled(false);
        }
        closeWifiAp();
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = wifiName;
        config.preSharedKey = password;
        config.hiddenSSID = hidden;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);//开放系统认证
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        if(password != null && password != ""){
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        }
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        //通过反射调用设置热点
        try {
            Method method = wifiManager.getClass().getMethod(
                    "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            enable = (Boolean) method.invoke(wifiManager, config, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            createWifi8(jsCallback);
        }else{
            JSONObject ret = new JSONObject();
            ret.put("state",enable);
            ret.put("ssid",wifiName);
            ret.put("password",password);
            jsCallback.invoke(ret);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createWifi8(final JSCallback jsCallback){
        final JSONObject ret = new JSONObject();
        if (!checkLocation()) {
            return;
        }
        wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                mReservation = reservation;
                String ssid = reservation.getWifiConfiguration().SSID;
                String pwd = reservation.getWifiConfiguration().preSharedKey;
                ret.put("state",true);
                ret.put("ssid",ssid);
                ret.put("password",pwd);
                jsCallback.invoke(ret);
            }

            @Override
            public void onStopped() {
                callback("WIFI_HOTSPOT_STOPPED",null);
            }

            @Override
            public void onFailed(int reason) {
                ret.clear();
                ret.put("state",false);
                jsCallback.invoke(ret);
            }
        }, new Handler());
    }

    /**
     * 关闭热点
     */
    @JSMethod(uiThread = true)
    public void closeWifiAp() {
        if (isWifiApEnabled()) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(mReservation != null){
                    mReservation.close();
                }
                return;
            }
            try {
                Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
                method.setAccessible(true);
                WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
                Method method2 = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                method2.invoke(wifiManager, config, false);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查是否开启Wifi热点
     *
     * @return
     */
    @JSMethod(uiThread = false)
    public boolean isWifiApEnabled() {
        try {
            Method method = wifiManager.getClass().getMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (boolean) method.invoke(wifiManager);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }


    //端口扫描
    @JSMethod(uiThread = true)
    public void portScan(JSONObject jsonObject, JSCallback jsCallback){
        jsCallback = SetJSCallBack(jsCallback);
        String ip = SetValue(jsonObject,"ip",getHostIP());
        String port = SetValue(jsonObject,"port","");
        String type = SetValue(jsonObject,"type","tcp");
        try{
            PortScan portScan = PortScan.onAddress(ip).setTimeOutMillis(100);

            if(port == ""){
                portScan.setPortsAll();
            }else{
                portScan.setPorts(port);
            }

            if(type.toUpperCase().equals("TCP")){
                portScan.setMethodTCP();
            }else{
                portScan.setMethodUDP();
            }
            final JSONObject tempObject = new JSONObject();
            final JSCallback finalJsCallback = jsCallback;
            portScan.doScan(new PortScan.PortListener() {
                @Override
                public void onResult(int portNo, boolean open) {
                    tempObject.clear();
                    tempObject.put("type","result");
                    tempObject.put("port",portNo);
                    tempObject.put("open",open);
                    finalJsCallback.invokeAndKeepAlive(tempObject);
                }

                @Override
                public void onFinished(ArrayList<Integer> openPorts) {
                    // Stub: Finished scanning
                    try{
                        Thread.sleep(1000);
                    }catch (Exception e){

                    }
                    tempObject.clear();
                    tempObject.put("type","finished");
                    tempObject.put("ports",openPorts);
                    finalJsCallback.invoke(tempObject);
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //扫描局域网设备
    @JSMethod(uiThread = true)
    public void getLAN(JSCallback jsCallback){
        jsCallback = SetJSCallBack(jsCallback);
        IpScanner ipScanner = new IpScanner();
        final JSCallback finalJsCallback = jsCallback;
        ipScanner.setOnScanListener(new IpScanner.OnScanListener() {
            @Override
            public void scan(Map<String, String> resultMap) {
                finalJsCallback.invoke(resultMap);
            }
        });
        ipScanner.startScan();
    }


    /**
     * TODO<获取本地ip地址>
     *
     * @return String
     */
    private String getLocAddress() {
        String ipaddress = "";

        try {
            Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces();
            // 遍历所用的网络接口
            while (en.hasMoreElements()) {
                NetworkInterface networks = en.nextElement();
                // 得到每一个网络接口绑定的所有ip
                Enumeration<InetAddress> address = networks.getInetAddresses();
                // 遍历每一个接口绑定的所有ip
                while (address.hasMoreElements()) {
                    InetAddress ip = address.nextElement();
                    if (!ip.isLoopbackAddress()
                            && (ip instanceof Inet4Address)) {
                        ipaddress = ip.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("test", "获取本地ip地址失败");
            e.printStackTrace();
        }

        Log.i("test", "本机IP:" + ipaddress);
        return ipaddress;
    }
    /**
     * 获取ip地址
     * @return
     */
    @JSMethod(uiThread = false)
    public String getHostIP() {
        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return hostIp;

    }
    /**
     * TODO<获取本机IP前缀>
     *
     * @param devAddress
     *            // 本机IP地址
     * @return String
     */
    private String getLocAddrIndex(String devAddress) {
        if (!devAddress.equals("")) {
            return devAddress.substring(0, devAddress.lastIndexOf(".") + 1);
        }
        return null;
    }

    //获取本机的物理地址
    @JSMethod(uiThread = false)
    public String getHostMac() {
        WifiInfo info = wifiManager.getConnectionInfo();
        return info.getMacAddress();
    }

    /**
     * 将获取的int转为真正的ip地址
     **/
    private String getCorrectIPAddress(int iPAddress) {
        StringBuilder sb = new StringBuilder();
        sb.append(iPAddress & 0xFF).append(".");
        sb.append((iPAddress >> 8) & 0xFF).append(".");
        sb.append((iPAddress >> 16) & 0xFF).append(".");
        sb.append((iPAddress >> 24) & 0xFF);
        return sb.toString();
    }

    public JSCallback SetJSCallBack(JSCallback jsCallback){
        if(jsCallback == null){
            return new JSCallback() {
                @Override
                public void invoke(Object o) {

                }

                @Override
                public void invokeAndKeepAlive(Object o) {

                }
            };
        }
        return jsCallback;
    }

    public boolean checkLocation(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int hasLocationPermission = mWXSDKInstance.getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            Log.d("test", "location permission: " + hasLocationPermission); // 0

            if (hasLocationPermission != PackageManager.PERMISSION_GRANTED) {
                callback("NOT_ACCESS_FINE_LOCATION",null);
                ActivityCompat.requestPermissions((Activity) mWXSDKInstance.getContext(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return false;
            }
        }
        return true;
    }

    public int SetValue(JSONObject object,String key,int defaultValue){
        object = object == null?new JSONObject():object;
        return object.containsKey(key)?object.getInteger(key):defaultValue;
    }
    public String SetValue(JSONObject object,String key,String defaultValue){
        object = object == null?new JSONObject():object;
        return object.containsKey(key)?object.getString(key):defaultValue;
    }
    public Boolean SetValue(JSONObject object,String key,Boolean defaultValue){
        object = object == null?new JSONObject():object;
        return object.containsKey(key)?object.getBoolean(key):defaultValue;
    }
}
