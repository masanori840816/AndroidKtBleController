package jp.masanori.androidktblecontroller

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    var isPermissionAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // デバイスがBLEに対応していなければトースト表示.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        // Android6.0以降なら権限確認.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.requestBlePermission()
        }
        else{
            isPermissionAllowed = true;
        }
        var buttonCentral = findViewById(R.id.button_central) as Button
        buttonCentral!!.setOnClickListener{
            if(isPermissionAllowed){
                var intentCentral = Intent(this, CentralActivity::class.java)
                startActivity(intentCentral)
            }
            else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.requestBlePermission()
            }
        }
    }
    @TargetApi(Build.VERSION_CODES.M)
    private fun requestBlePermission() {
        // 権限が許可されていない場合はリクエスト.
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isPermissionAllowed = true
        }
        else{
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), R.string.request_num_location)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // 権限リクエストの結果を取得する.
        if (requestCode == R.string.request_num_location) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isPermissionAllowed = true
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
