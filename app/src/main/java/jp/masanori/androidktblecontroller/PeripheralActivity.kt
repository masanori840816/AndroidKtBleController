package jp.masanori.androidktblecontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Bundle
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class PeripheralActivity : FragmentActivity() {

    private var bleManager: BluetoothManager? = null
    private var bleAdapter: BluetoothAdapter? = null
    private var bleDevice: BluetoothDevice? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private var bleGattServer: BluetoothGattServer? = null
    private var textReceivedValue: TextView? = null
    private var editUpdateValue: EditText? = null
    private var editReadValue: EditText? = null
    private final var REQUEST_NUM_BLE_ON = 1
    private var isConnected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peripheral)

        editUpdateValue = findViewById(R.id.edit_peripheral_update_value) as EditText
        editReadValue = findViewById(R.id.edit_peripheral_read_value) as EditText
        textReceivedValue = findViewById(R.id.text_peripheral_received) as TextView

        var buttonUpdate = findViewById(R.id.button_peripheral_update) as Button
        buttonUpdate.setOnClickListener{
            updateValue(editUpdateValue?.text.toString())
        }
        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager!!.adapter

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            // BluetoothがOffならインテントを表示する.
            if (bleAdapter!!.isEnabled()){
                startAdvertising()
            }
            else{
                // Intentでボタンを押すとonActivityResultが実行されるので、第二引数の番号を元に処理を行う.
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_NUM_BLE_ON)
            }
        }
        else{
            // OS ver.5.0未満はPeripheralに対応していない.
            Toast.makeText(this, R.string.peripheral_is_not_supported, Toast.LENGTH_SHORT).show()
        }
    }
    private fun startAdvertising(){
        bleAdvertiser = bleAdapter!!.bluetoothLeAdvertiser
        if(bleAdvertiser == null){
            // Peripheralモードに対応していなければ通知だけして処理終了.
            Toast.makeText(this, R.string.peripheral_is_not_supported, Toast.LENGTH_SHORT).show()
        }
        else{
            var bleGattService = BluetoothGattService(UUID.fromString(resources.getString(R.string.uuid_service)), BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // 書き込み・読み込み・Notificationの権限をCentral側に許可する.
            bleCharacteristic = BluetoothGattCharacteristic(
                    UUID.fromString(resources.getString(R.string.uuid_characteristic))
                    ,BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE
                    ,BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
            bleGattService.addCharacteristic(bleCharacteristic)

            var bleGattDescriptor = BluetoothGattDescriptor(
                    UUID.fromString(resources.getString(R.string.uuid_characteristic_config))
                    , BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ)
            bleCharacteristic?.addDescriptor(bleGattDescriptor)

            bleGattServer = bleManager?.openGattServer(this, bleGattServerCallback)
            bleGattServer?.addService(bleGattService);

            // Advertiseの設定.
            var advertiseSettingsBuilder = AdvertiseSettings.Builder()
            advertiseSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            advertiseSettingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)

            var advertiseDataBuilder = AdvertiseData.Builder()
            advertiseDataBuilder.setIncludeTxPowerLevel(false)
            advertiseDataBuilder.addServiceUuid(ParcelUuid.fromString(resources.getString(R.string.uuid_service)))

            // Advertiseの開始.
            var bluetoothLeAdvertiser = bleAdapter?.getBluetoothLeAdvertiser()
            bluetoothLeAdvertiser?.startAdvertising(advertiseSettingsBuilder.build(), advertiseDataBuilder.build()
                    , object: AdvertiseCallback(){
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings){
                }
                override fun onStartFailure(errorCode: Int){
                }
            });
        }
    }
    private final val bleGattServerCallback = object: BluetoothGattServerCallback(){
        override fun onServiceAdded(status: Int, service: BluetoothGattService){
            when(status){
                BluetoothGatt.GATT_SUCCESS -> {

                }
            }
        }
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when(newState){
                BluetoothProfile.STATE_CONNECTED ->{
                    bleDevice = device
                    isConnected = true
                }
                BluetoothProfile.STATE_DISCONNECTED ->{
                    bleDevice = null
                    isConnected = false
                }
            }
        }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic
                                                  , preparedWrite: Boolean, responseNeeded: Boolean, offset: Int
                                                  , value: ByteArray) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            // Central側から受け取った値をCharacteristicにセットしてTextViewに入れる.
            characteristic.value = value
            runOnUiThread {
                textReceivedValue?.text = characteristic.getStringValue(offset)
            }
            if(responseNeeded){
                bleGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS
                        , 0, value)
            }
        }
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor
                                              , preparedWrite: Boolean, responseNeeded: Boolean, offset: Int
                                              , value: ByteArray) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)

            if(responseNeeded){
                bleGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS
                        , 0, value)
            }
        }
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int
                                                  , characteristic: BluetoothGattCharacteristic){
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            bleCharacteristic!!.value = editReadValue.toString().toByteArray()
            bleGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS
                    , 0, bleCharacteristic!!.value)
        }
    }
    private fun updateValue(newValue: String){
        if(isConnected){
            bleCharacteristic!!.value = newValue.toByteArray()
            bleGattServer?.notifyCharacteristicChanged(bleDevice, bleCharacteristic, false)
        }
    }
}
