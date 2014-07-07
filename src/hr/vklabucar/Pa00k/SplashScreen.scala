package hr.vklabucar.Pa00k

import android.app.{AlertDialog, Activity}
import android.os.Bundle
import android.widget._
import android.content._
import android.bluetooth.{BluetoothSocket, BluetoothDevice, BluetoothAdapter}
import android.content.DialogInterface.OnClickListener
import android.widget.AdapterView.OnItemClickListener
import android.view.View
import android.util.Log


class SplashScreen extends Activity {

  val TAG: String = "Splash"

  var BTadapter: BluetoothAdapter = null
  var mReceiver: BroadcastReceiver = null
  val REQUEST_ENABLE_BT = 1
  var mArrayAdapter: ArrayAdapter[String] = null
  var title: TextView = null
  var scanButton: Button = null
  var socket: BluetoothSocket = null


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")

    setContentView(R.layout.device_list)

    title = findViewById(R.id.title_new_devices).asInstanceOf[TextView]

    BTadapter = BluetoothAdapter.getDefaultAdapter
    checkIfBTAvailable(getApplicationContext)
    turnBTOn()

    mArrayAdapter = new ArrayAdapter[String](this, R.layout.device_name)
    val newDevicesListView: ListView = findViewById(R.id.new_devices).asInstanceOf[ListView]
    newDevicesListView.setAdapter(mArrayAdapter)
    newDevicesListView.setOnItemClickListener( new OnItemClickListener {
      override def onItemClick(av: AdapterView[_], v: View, p3: Int, p4: Long): Unit = {
        // Cancel discovery because it's costly and we're about to connect
        BTadapter.cancelDiscovery()

        // Get the device MAC address, which is the last 17 chars in the View
        val info: String  = v.asInstanceOf[TextView].getText.toString
        if(info != "No devices.") {
          val address: String = info.split("\\n")(1)

          Log.d(TAG, " onItemClick -> address = " + address)

          // Create the result Intent and include the MAC address
          val intent: Intent = new Intent("hr.vklabucar.MAIN")
          intent.putExtra("device_address", address)

          // Set result and finish this Activity
          SplashScreen.this.startActivity(intent)
          SplashScreen.this.finish()
        }
      }
    })

    scanButton = findViewById(R.id.scan).asInstanceOf[Button]
    scanButton.setOnClickListener(new View.OnClickListener {
      def onClick(v: View): Unit = {
        Log.d(TAG, " Scan pressed!")

        doDiscovery()
      }
    })

    mReceiver = new BroadcastReceiver() {
      def onReceive( context: Context,  intent: Intent): Unit = {

        val action: String = intent.getAction

         action match {
          case BluetoothDevice.ACTION_FOUND =>

            // Get the BluetoothDevice object from the Intent
            val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            // Add the name and address to an array adapter to show in a ListView
            val deviceString: String = device.getName + "\n" + device.getAddress
            Log.d("TAG", "BroadcastReceiver found a new device: " + device.getName + "  " + device.getAddress)

            mArrayAdapter.add(deviceString)

          case BluetoothAdapter.ACTION_DISCOVERY_FINISHED =>

            Log.d(TAG, "BroadcastReceiver: Discovery finished.")
            title.setText("Select a Pa00k to drive :)")

            if(mArrayAdapter.getCount == 0) mArrayAdapter.add("No devices.")
            findViewById(R.id.progress).setVisibility(View.INVISIBLE)
            scanButton.setText("Scan")
         }
      }
    }

    val filterAF: IntentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND)
    val filterADF: IntentFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

    registerReceiver(mReceiver, filterAF)
    registerReceiver(mReceiver, filterADF)

    doDiscovery()
  }

  def checkIfBTAvailable(context: Context): Unit = {
    if(BTadapter == null) {
      val alertDialogBuilder: AlertDialog.Builder = new AlertDialog.Builder(context)
      alertDialogBuilder.setTitle("Error")
      alertDialogBuilder
        .setMessage("Your devide doesn't support Bluetooth!")
        .setCancelable(false)
        .setPositiveButton("Ok :(",new OnClickListener {
        override def onClick(p1: DialogInterface, p2: Int): Unit = SplashScreen.this.finish()
      })

      val alertDialog: AlertDialog = alertDialogBuilder.create()

      alertDialog.show()
    }
  }

  def turnBTOn(): Unit = {
    if (!BTadapter.isEnabled) {
      val enableBtIntent: Intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }
  }

  def doDiscovery() = {

    if (BTadapter.isDiscovering){
      BTadapter.cancelDiscovery()
      Log.d(TAG, "Scanning stopped.")
    }
    else if (BTadapter.isEnabled){
      Log.d(TAG, "Scanning...")
      findViewById(R.id.progress).setVisibility(View.VISIBLE)
      title.setText("Scanning for Pa00ks...")
      scanButton.setText("Stop")
      mArrayAdapter.clear()
      BTadapter.startDiscovery()
    }

  }

  override def onDestroy() {
    super.onDestroy()

    // Make sure we're not doing discovery anymore
    if (BTadapter != null) {
      BTadapter.cancelDiscovery()
    }

    // Unregister broadcast listeners
    this.unregisterReceiver(mReceiver)
  }

  override def onBackPressed() {finish()}

}
