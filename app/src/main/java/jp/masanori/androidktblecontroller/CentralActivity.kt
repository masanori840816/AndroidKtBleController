package jp.masanori.androidktblecontroller

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.UUID

class CentralActivity : FragmentActivity(){
    private enum class RequestNum(var num: Int){
        PERMISSION_LOCATION(0)
        , BLE_ON(1)
    }
    private var bleManager: BluetoothManager? = null
    private var bleAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleGatt: BluetoothGatt? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private final var locationAccesser = LocationAccesser()
    private var textIsConnected: TextView? = null
    private var textReceived: TextView? = null
    private var textRead: TextView? = null
    private var writeOriginalByteArray: ByteArray? = null
    private var writeByteArray: ByteArray? = null
    private var writeValueLengthFrom = 0
    private var writeValueLengthTo = 0
    private var isConntected = false
    private var isWritingData = false
    private final val SEND_VALUE_LENGTH = 20
    private var readValue: ByteArray? = null
    private var updateValue: ByteArray? = null
    private var isValueUpdated = false

    fun onGpsEnabled(){
        // 2016.03.08現在GPSを求めるのはOS ver.6.0以上のみ.
        // GPSがONになったらScan開始.
        startScanByBleScanner()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central)

        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager!!.adapter

        textIsConnected = findViewById<TextView>(R.id.text_central_isconnect)
        textReceived = findViewById<TextView>(R.id.text_central_received)

        var editTextWrite = findViewById<EditText>(R.id.edit_central_write)
        var buttonWrite = findViewById<Button>(R.id.button_central_write)
        buttonWrite.setOnClickListener {
            if(isConntected) {
                writeOriginalByteArray = editTextWrite.text.toString().toByteArray(Charsets.UTF_8)
                writeValueLengthTo = 0
                isWritingData = true
                writeText(resources.getString(R.string.ble_stop_sending_data).toByteArray(Charsets.UTF_8))
            }
        }

        textRead = findViewById<TextView>(R.id.text_central_read)
        var buttonRead = findViewById<Button>(R.id.button_central_read)
        buttonRead.setOnClickListener{
            if(isConntected){
                readText()
            }

        }
        // OS ver.6.0以降なら権限確認.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestBlePermission()
        }
        else{
            // BluetoothがOnかを確認.
            requestBleOn()
        }
        registerReceiver(broadcastReceiver, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED))

        readValue = emptyArray<Byte>().toByteArray()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        // Intentでユーザーがボタンを押したら実行.
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            RequestNum.BLE_ON.num -> {
                if(bleAdapter!!.isEnabled()){
                    // BLEが使用可能ならスキャン開始.
                    scanNewDevice()
                }
            }
            locationAccesser.REQUEST_NUM_LOCATION -> {
                if(resultCode == Activity.RESULT_OK){
                    onGpsEnabled()
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // 権限リクエストの結果を取得する.
        when(requestCode){
            RequestNum.PERMISSION_LOCATION.num -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 権限が付与されたらBluetoothがOnかを確認.
                    requestBleOn()
                }
            }
            else ->{
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }
    override fun onPause(){
        super.onPause()
        unregisterReceiver(broadcastReceiver)
    }
    override fun onResume(){
        super.onResume()
        registerReceiver(broadcastReceiver, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED))
        if(bleAdapter!!.isEnabled
            && ! isConntected){
            bleGatt = null
            scanNewDevice()
        }
    }
    override fun onDestroy(){
        super.onDestroy()

        // reset.
        if(bleAdapter!!.isEnabled) {
            bleScanner!!.stopScan(bleScanCallback)
            // 接続中のデバイスがあれば切断して閉じる.
            if (!bleManager!!.getConnectedDevices(BluetoothProfile.GATT).isEmpty()) {
                bleGatt!!.disconnect()
                bleGatt!!.close()
            }
            bleGatt = null
        }
        isConntected = false
        textIsConnected?.text = resources.getString(R.string.ble_status_disconnected)
    }
    @TargetApi(Build.VERSION_CODES.M)
    private fun requestBlePermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 権限が付与されたらBluetoothがOnかを確認.
            requestBleOn()
        }
        else{
            // 権限が付与されていない場合はリクエスト.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), RequestNum.PERMISSION_LOCATION.num)
        }
    }
    private fun requestBleOn(){
        // BluetoothがOffならインテントを表示する.
        if(bleAdapter!!.isEnabled) {
            scanNewDevice()
        }
        else{
            // Intentでボタンを押すとonActivityResultが実行されるので、第二引数の番号を元に処理を行う.
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), RequestNum.BLE_ON.num)
        }
    }
    private final val bleGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, stateNum: Int) {
            // 接続状況が変化したら実行.
            when(stateNum){
                BluetoothProfile.STATE_CONNECTED ->{
                    // 接続に成功したらサービスを検索する.
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED ->{
                    // 接続が切れたらGATTを空にする.
                    bleGatt!!.close()
                    isConntected = false
                    runOnUiThread {
                        textIsConnected?.text = resources.getString(R.string.ble_status_disconnected)
                    }
                }
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Serviceが見つかったら実行.
            when(status){
                BluetoothGatt.GATT_SUCCESS ->{
                    // UUIDが同じかどうかを確認する.
                    var _bleService = gatt.getService(UUID.fromString(getString(R.string.uuid_service)))

                    // 指定したUUIDを持つCharacteristicを確認する.
                    bleCharacteristic = _bleService.getCharacteristic(UUID.fromString(getString(R.string.uuid_characteristic))) as BluetoothGattCharacteristic
                    bleCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                    // Service, CharacteristicのUUIDが同じならBluetoothGattを更新する.
                    bleGatt = gatt;

                    // キャラクタリスティックが見つかったら、Notificationをリクエスト.
                    bleGatt!!.setCharacteristicNotification(bleCharacteristic, true)

                    // Characteristic の Notificationを有効化する.
                    var _bleDescriptor: BluetoothGattDescriptor = bleCharacteristic!!.getDescriptor(UUID.fromString(getString(R.string.uuid_characteristic_config)))

                    _bleDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bleGatt!!.writeDescriptor(_bleDescriptor)
                    // 接続が終わったらScanを止める.
                    bleScanner!!.stopScan(bleScanCallback!!)
                    isConntected = true
                    runOnUiThread {
                        textIsConnected?.text = resources.getString(R.string.ble_status_connected)
                    }
                }
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // キャラクタリスティックのUUIDをチェック(getUuidの結果が全て小文字で帰ってくるのでUpperCaseに変換)
            if (getString(R.string.uuid_characteristic).equals(characteristic.getUuid().toString().toUpperCase())){
                // Peripheralで値が更新されたらNotificationを受ける.
                // メインスレッドでTextViewに値をセットする.
                /*runOnUiThread {
                    textReceived!!.text = characteristic.getStringValue(0)
                }*/
                updateValue = emptyArray<Byte>().toByteArray()
                // Peripheralから値を読み込んだらByteArray型で追加.
                updateValue = updateValue!!.plus(characteristic.value)
                isValueUpdated = true
                // 送信完了の文字列が届くまで読み込みリクエストを送る.
                readText()
            }
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int){
            super.onCharacteristicWrite(gatt, characteristic, status)
            // 20byteごとに分割して送信.
            writeValueLengthFrom = writeValueLengthTo

            if(! isWritingData){
                return
            }
            if(writeOriginalByteArray!!.size <= writeValueLengthFrom){
                // すべて送信したら完了通知用の文字列を送信する.
                writeText(resources.getString(R.string.ble_stop_sending_data).toByteArray(Charsets.UTF_8))
                isWritingData = false
                return
            }
            if(writeOriginalByteArray!!.size <= (writeValueLengthTo + SEND_VALUE_LENGTH)){
                writeValueLengthTo = writeOriginalByteArray!!.size
            }
            else{
                writeValueLengthTo += SEND_VALUE_LENGTH
            }
            var i = writeValueLengthFrom
            var t = 0
            writeByteArray = ByteArray(writeValueLengthTo - writeValueLengthFrom)

            while(i < writeValueLengthTo){
                writeByteArray!![t] = writeOriginalByteArray!![i]
                i++
                t++
            }
            writeText(writeByteArray!!)
        }
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int){
            super.onCharacteristicRead(gatt, characteristic, status)
            if (getString(R.string.uuid_characteristic).equals(characteristic.uuid.toString().toUpperCase())){
                if(characteristic.getStringValue(0).equals(resources.getString(R.string.ble_stop_sending_data))){
                    runOnUiThread {
                        // 送信完了の文字列を受け取ったらUIスレッドでTextViewに値をセットする.
                        if(isValueUpdated){
                            textReceived!!.text = updateValue?.toString(Charsets.UTF_8)
                            updateValue = emptyArray<Byte>().toByteArray()
                            isValueUpdated = false
                        }
                        else{
                            textRead!!.text = readValue?.toString(Charsets.UTF_8)
                            readValue = emptyArray<Byte>().toByteArray()
                        }
                    }
                }
                else{
                    // Peripheralから値を読み込んだらByteArray型で追加.
                    if(isValueUpdated){
                        updateValue = updateValue!!.plus(characteristic.value)
                    }
                    else{
                        readValue = readValue!!.plus(characteristic.value)
                    }

                    // 送信完了の文字列が届くまで読み込みリクエストを送る.
                    readText()
                }

            }
        }
    }
    private final val bleScanCallback: ScanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // スキャン中に見つかったデバイスに接続を試みる.第三引数には接続後に呼ばれるBluetoothGattCallbackを指定する.
            result.device.connectGatt(applicationContext, false, bleGattCallback)
        }
        override fun onScanFailed(intErrorCode: Int){
            super.onScanFailed(intErrorCode)
        }
    };
    private var broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context : Context?, intent : Intent?){
            when(intent!!.action){
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    bleGatt = null
                    scanNewDevice()
                }
            }
        }
    }
    private fun scanNewDevice(){
        //
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            locationAccesser.checkIsGpsOn(this)
        }
        // OS ver.5.0以上ならBluetoothLeScannerを使用する.
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            startScanByBleScanner()
        }
        else{
            // OS ver. 4.3, 4.4.
            bleAdapter!!.startLeScan({
                bluetoothDevice, i, bytes -> run{
                    runOnUiThread {
                        bleGatt = bluetoothDevice.connectGatt(applicationContext, false, bleGattCallback) as BluetoothGatt
                    }
                }
            })
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startScanByBleScanner(){
        bleScanner = bleAdapter!!.getBluetoothLeScanner()

        var filter: ScanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(getString(R.string.uuid_service))))
                .build()

        var setting: ScanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
        // デバイスの検出.
        bleScanner!!.startScan(listOf(filter), setting, bleScanCallback)
    }
    private fun writeText(sendValue: ByteArray){
        // 1台以上接続されていれば書き込みリクエストを送る.
        if(bleManager!!.getConnectedDevices(BluetoothProfile.GATT).isEmpty()){
           return
        }
        bleCharacteristic!!.value = sendValue
        bleGatt!!.writeCharacteristic(bleCharacteristic)
    }
    private fun readText(){
        // 1台以上接続されていれば書き込みリクエストを送る.
        if(bleManager!!.getConnectedDevices(BluetoothProfile.GATT).isEmpty()){
            return
        }
        bleGatt!!.readCharacteristic(bleCharacteristic)
    }
}
