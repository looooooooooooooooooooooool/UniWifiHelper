<template>
	<view class="content">
		<p>Fvv Wifi Helper</p>
		<button  @click="init">初始化</button>
		<button  @click="getHost">获取本机ip，mac</button>
		<button  @click="createWifi">开启热点</button>
		<button  @click="closeAp">关闭热点</button>
		<button  @click="scanWifi">获取wifi列表</button>
		<button  @click="connectWifi">连接wifi</button> 
		<button  @click="staticIP">静态ip</button>
		<button  @click="getLAN">获取局域网设备</button>
		<button  @click="portScan">端口扫描</button>
	</view>
</template>

<script>
	import permision from "@/static/permission.js" 
	const FvvUniWifiHelper = uni.requireNativePlugin("Fvv-UniWifiHelper");
	export default {
		data() {
			return {
				 
			}
		},
		onLoad() {

		},
		methods: { 
			async getPermission(){
				var result = await permision.requestAndroidPermission("android.permission.ACCESS_FINE_LOCATION")
				var strStatus
				if (result == 1) {
					strStatus = "已获得授权"
				} else if (result == 0) {
					strStatus = "未获得授权"
				} else {
					strStatus = "被永久拒绝权限"
				}
				uni.showModal({
					content: permisionID + strStatus,
					showCancel: false
				});
			},
			init(){
				FvvUniWifiHelper.init(res => {
					console.log(res) 
				})
				this.getPermission()
			},
			getHost(){
				console.log(FvvUniWifiHelper.getHostIP())
				console.log(FvvUniWifiHelper.getHostMac())
			},
			createWifi(){ 
				console.log(FvvUniWifiHelper.createWifi({
					name:"fvv",
					password:"hellofvv"
				},res => {
					console.log(res)
				}))
			},
			closeAp(){
				FvvUniWifiHelper.closeWifiAp()
			},
			scanWifi(){ 
				FvvUniWifiHelper.getWifiList(res => {
					console.log(res)
				})
			},
			connectWifi(){
				FvvUniWifiHelper.connectWifi({
				    ssid:"fvv", 
				});
			}, 
			staticIP(){
				console.log(FvvUniWifiHelper.setStaticIp({
				    dhcp:false,
				    ip:"192.168.0.168",
				    dns1:"192.168.0.1",
				    gateway:"192.168.0.1"
				}));
			},
			getLAN(){
				FvvUniWifiHelper.getLAN(res => {
					console.log(res)
				})
			},
			portScan(){
				FvvUniWifiHelper.portScan({
					ip:"14.215.177.39",
					port:"80,3306"
				},res => {
					console.log(res)
				})
			}
		}
	}
</script>

<style>
	.content {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}

	 
</style>
