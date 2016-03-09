package jp.masanori.androidktblecontroller

import android.annotation.TargetApi
import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.UUID

class CentralActivity : FragmentActivity() {
    private var bleManager: BluetoothManager? = null
    private var bleAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleGatt: BluetoothGatt? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private final var locationAccesser = LocationAccesser()
    private var textReceived: TextView? = null
    private final var REQUEST_NUM_BLE_ON = 1

    fun onGpsEnabled(){
        // 2016.03.08現在GPSを求めるのはAndroid6.0以上のみ.
        startScanByBleScanner()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central)

        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager!!.adapter

        textReceived = findViewById(R.id.text_received) as TextView

        var editTextSend = findViewById(R.id.edittext_send) as EditText
        var buttonSendText = findViewById(R.id.button_send_text) as Button
        buttonSendText!!.setOnClickListener {
            sendText(editTextSend.text.toString())
        }
        // BluetoothがOffならインテントを表示する.
        if(bleAdapter!!.isEnabled) {
            scanNewDevice()
        }
        else{
            // Intentでボタンを押すとonActivityResultが実行されるので、第二引数の番号を元に処理を行う.
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_NUM_BLE_ON)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        // Intentでユーザーがボタンを押したら実行.
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            REQUEST_NUM_BLE_ON -> {
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
    override fun onPause(){
        super.onPause()
        Log.d("BLE", "OnPause")
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
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // キャラクタリスティックのUUIDをチェック(getUuidの結果が全て小文字で帰ってくるのでUpperCaseに変換)
            if (getString(R.string.uuid_characteristic).equals(characteristic.getUuid().toString().toUpperCase())){
                // Peripheralで値が更新されたらNotificationを受ける.
                // メインスレッドでTextViewに値をセットする.
                runOnUiThread {
                    textReceived!!.text = characteristic.getStringValue(0)
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
                        bleGatt = bluetoothDevice.connectGatt(getApplicationContext(), false, bleGattCallback) as BluetoothGatt
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
        bleScanner!!.startScan(listOf(filter!!), setting!!, bleScanCallback)
    }
    private fun sendText(sendValue: String){
        // 1台以上接続されていれば書き込みリクエストを送る.
        if(bleManager!!.getConnectedDevices(BluetoothProfile.GATT).isEmpty()){
           return
        }
        bleCharacteristic!!.value = sendValue.toByteArray()
        bleGatt!!.writeCharacteristic(bleCharacteristic)
    }
}
