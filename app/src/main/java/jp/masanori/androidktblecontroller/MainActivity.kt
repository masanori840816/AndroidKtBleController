package jp.masanori.androidktblecontroller

import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // デバイスがBLEに対応していなければトースト表示.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }

        var buttonCentral = findViewById<Button>(R.id.button_central)
        buttonCentral.setOnClickListener{
            var intentCentral = Intent(this, CentralActivity::class.java)
            startActivity(intentCentral)
        }
        var buttonPeripheral = findViewById<Button>(R.id.button_peripheral)
        buttonPeripheral.setOnClickListener{
            var intentPeripheral = Intent(this, PeripheralActivity::class.java)
            startActivity(intentPeripheral)
        }
    }

}
