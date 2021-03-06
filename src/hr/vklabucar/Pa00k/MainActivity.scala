package hr.vklabucar.Pa00k

import android.app.Activity
import android.os.Bundle
import android.view.{KeyEvent, MotionEvent, View}
import android.util.Log
import android.view.View.{OnClickListener, OnTouchListener}
import android.hardware.{Sensor, SensorEvent, SensorEventListener, SensorManager}
import android.content.Context.SENSOR_SERVICE
import android.content.{IntentFilter, Context, BroadcastReceiver, Intent}
import scala.actors.Actor
import android.bluetooth.{BluetoothAdapter, BluetoothSocket, BluetoothDevice}
import java.util.UUID
import java.io.IOException
import android.widget.{Toast, Button, ToggleButton}
import scala.language.postfixOps


case class Point(var x: Int, var y: Int)

class MainActivity extends Activity with OnTouchListener with SensorEventListener {

  val TAG: String = "MainActivity"
  val sleep = 120
  var BTadapter: BluetoothAdapter = null
  var connected: Boolean = true
  var mReceiver: BroadcastReceiver = null

  // finger positions
  var p1 = Point(0, 0)
  var p2 = Point(0, 0)
  var centre1 = Point(0, 0)
  var centre2 = Point(0, 0)
  var relCentre1 = Point(0, 0)
  var relCentre2 = Point(0, 0)

  //values for hexapod
  var x1: Double = 0
  var y1: Double = 0
  var x2: Double = 0
  var y2: Double = 0


  // acceleration low pass filter
  var ax = 0d
  var ax_old = 0d
  var ax_ev = 0d
  var ax_ev_old = 0d

  var ay = 0d
  var ay_old = 0d
  var ay_ev = 0d
  var ay_ev_old = 0d


  // alpha for the circles
  var a1 = 0
  var a2 = 0

  // hexapod params and limits
  var height = 3
  var stride = 1
  val hMax = 5
  val hMin = 1
  val sMax = 3
  val sMin = 1

  var ctrls = new Array[ToggleButton](2)
  var toast: Toast = null

  var sensorManager: SensorManager = null
  var accelerometer: Sensor = null
  var drawingLinearLayout: DrawingLinearLayout = null

  override def onResume() = {
    super.onResume()
    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    Log.d(TAG, "onResume")
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    val b_up = findViewById(R.id.ctrl1).asInstanceOf[Button]
    val b_down = findViewById(R.id.ctrl2).asInstanceOf[Button]

    ctrls(0) = findViewById(R.id.ctrl3).asInstanceOf[ToggleButton]
    ctrls(1) = findViewById(R.id.ctrl4).asInstanceOf[ToggleButton]

    b_up.setOnClickListener(new OnClickListener {
      override def onClick(b: View): Unit = {
        Log.d(TAG, s"UP: $height")

        if (height < hMax) height += 1

        makeToast()
      }
    })

    b_down.setOnClickListener(new OnClickListener {
      override def onClick(b: View): Unit = {
        Log.d(TAG, s"DOWN: $height")

        if (height > hMin) height -= 1

        makeToast()
      }
    })

    BTadapter = BluetoothAdapter.getDefaultAdapter

    val intent = getIntent
    val address = intent.getStringExtra("device_address")

    sensorManager = getSystemService(SENSOR_SERVICE).asInstanceOf[SensorManager]
    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


    findViewById(R.id.gestureOverlayView1).setOnTouchListener(this)
    findViewById(R.id.gestureOverlayView2).setOnTouchListener(this)
    drawingLinearLayout = findViewById(R.id.drawingLinearLayout).asInstanceOf[DrawingLinearLayout]

    mReceiver = new BroadcastReceiver() {
      def onReceive(context: Context, intent: Intent): Unit = {

        val action: String = intent.getAction
        Log.d(TAG, "Intent: " + intent.toString)
        action match {
          case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED | BluetoothDevice.ACTION_ACL_DISCONNECTED =>

            startActivity(new Intent("hr.vklabucar.SPLASH"))
            MainActivity.this.finish()

          case BluetoothDevice.ACTION_UUID | BluetoothDevice.EXTRA_UUID =>
            Log.d(TAG, "UUID intents: " + intent.getExtras.get("android.bluetooth.device.extra.DEVICE") + " " + intent.getExtras.get("android.bluetooth.device.extra.UUID"))


        }
      }
    }

    val filterDREQ: IntentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
    val filterD: IntentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    val f11 = new IntentFilter(BluetoothDevice.ACTION_UUID)
    val f22 = new IntentFilter(BluetoothDevice.EXTRA_UUID)

    registerReceiver(mReceiver, filterDREQ)
    registerReceiver(mReceiver, filterD)
    registerReceiver(mReceiver, f11)
    registerReceiver(mReceiver, f22)

    val sendActor = new ConnectAndSendActor(address)
    sendActor.start()

    Log.d(TAG, "onCreate")
  }

  override def onPause() = {
    super.onPause()
    try {
      this.unregisterReceiver(mReceiver)
    } catch {
      case iae: IllegalArgumentException =>
        Log.d(TAG, "IllegalArgumentException: mReceiver already unregistered. ")
    }
    connected = false
    sensorManager.unregisterListener(this)
    Log.d(TAG, "onPause")
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_VOLUME_UP =>
        Log.d(TAG, s"VOL UP: $stride")

        if (stride < sMax) stride += 1
        makeToast()

      case KeyEvent.KEYCODE_VOLUME_DOWN =>
        Log.d(TAG, s"VOL DOWN: $stride")

        if (stride > sMin) stride -= 1
        makeToast()

      case KeyEvent.KEYCODE_BACK =>
        connected = false
        startActivity(new Intent("hr.vklabucar.SPLASH"))
        finish()

      case _ =>

    }

    true
  }

  def makeToast(): Unit = {
    if (toast != null) toast.cancel()
    toast = Toast.makeText(MainActivity.this, s"HEIGHT: $height, STRIDE: $stride", Toast.LENGTH_SHORT)
    toast.show()
  }

  def onTouch(view: View, event: MotionEvent): Boolean = {
    val x = event.getRawX.toInt
    val y = event.getRawY.toInt
    val relY = event.getY.toInt

    event.getAction match {
      case MotionEvent.ACTION_DOWN =>

        view.getId match {
          case R.id.gestureOverlayView1 =>
            centre1.x = x
            centre1.y = y
            relCentre1.x = x
            relCentre1.y = relY
            a1 = 255
            drawingLinearLayout.drawCircle(a1, a2, relCentre1, relCentre2)

          case R.id.gestureOverlayView2 =>
            centre2.x = x
            centre2.y = y
            relCentre2.x = x
            relCentre2.y = relY
            a2 = 255
            drawingLinearLayout.drawCircle(a1, a2, relCentre1, relCentre2)
        }
        Log.d("onTouch", s"ACTION DOWN centre1: $centre1, centre2: $centre2")
        return true
      case MotionEvent.ACTION_MOVE =>
        view.getId match {
          case R.id.gestureOverlayView1 =>
            p1.x = x; p1.y = y
            // set x1 and y1
            x1 = genPair(p1, centre1)._1
            x1 *= 100
            y1 = genPair(p1, centre1)._2
            y1 *= 100
          case R.id.gestureOverlayView2 =>
            p2.x = x; p2.y = y
            //set x2 and y2
            x2 = genPair(p2, centre2)._1
            x2 *= 100
            y2 = genPair(p2, centre2)._2
            y2 *= 100

        }
        //Log.d("onTouch", s"ACTION MOVE p1: ${genPair(p1, centre1)}, p2: ${genPair(p2, centre2)}")

      case MotionEvent.ACTION_UP =>

        view.getId match {
          case R.id.gestureOverlayView1 =>
            p1.x = centre1.x
            p1.y = centre1.y
            a1 = 0
            drawingLinearLayout.hideCircle(a1, a2)
            x1 = 0; y1 = 0
          case R.id.gestureOverlayView2 =>
            p2.x = centre2.x
            p2.y = centre2.y
            a2 = 0
            drawingLinearLayout.hideCircle(a1, a2)
            x2 = 0; y2 = 0
        }
        Log.d("onTouch", s"ACTION_UP p1: $p1, p2: $p2")

      case _ => Log.d("onTouch", "CAUGHT UNEXPECTED ACTION: " + event.toString)
    }
    false
  }

  override def onSensorChanged(event: SensorEvent) = {

    if (event.sensor.getType == Sensor.TYPE_ACCELEROMETER) {

      // x signal through low pass
      ax_ev = event.values(0)
      ax = 0.82 * ax + 0.09 * ax_ev_old + 0.09 * ax_ev
      ax match {
        case x if x > 4 => ax = 4
        case x if x < -4 => ax = -4
        case _ =>
      }
      ax_ev_old = ax_ev
      ax_old = ax

      // y signal through low poass
      ay_ev = event.values(1)
      ay = 0.82 * ay + 0.09 * ay_ev_old + 0.09 * ay_ev
      ay match {
        case y if y > 4 => ay = 4
        case y if y < -4 => ay = -4
        case _ =>
      }
      ay_ev_old = ay_ev
      ay_old = ay

      // Log.d("ACC", genAcc)
    }
  }

  def genPair(p1: Point, p2: Point)(implicit r: Int = 110): (Double, Double) = {
    var x: Float = (p1.x.toFloat - p2.x) / r
    var y: Float = (p2.y - p1.y.toFloat) / r

    if (x > 1) x = 1f else if (x < -1) x = -1f
    if (y > 1) y = 1f else if (y < -1) y = -1f

    ((math floor x * 100) / 100, (math floor y * 100) / 100)
  }

  override def onAccuracyChanged(sensor: Sensor, acuracy: Int) = {}

  def genCtrls: Array[Byte] = {
    val array = new Array[Byte](4)

    array(0) = height.toByte
    array(1) = stride.toByte

    for (i <- 2 to 3) {
      if (ctrls(i - 2).isChecked) array(i) = 1.toByte
      else array(i) = 0.toByte
    }

    array
  }

  def genAcc: String = s"x: ${(math floor ax * 100) / 100} y: ${(math floor ay * 100) / 100}"

  def genAccBytes: Array[Byte] = Array((math floor ax * 10).toInt.toByte, (math floor ay * 10).toInt.toByte)

  private class ConnectAndSendActor(MAC: String) extends Actor {

    val device: BluetoothDevice = BTadapter.getRemoteDevice(MAC)
    val socket: BluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))


    Log.d(TAG, " ConnectAndSend Actor created.")

    def act() = {
      BTadapter.cancelDiscovery()
      Log.d(TAG, " ConnectAndSend Actor started.")

      if (socket == null) {
        Log.d(TAG, " Socket is null !   exiting ....")
        exit()
      }

      try {
        socket.connect()
        connected = true
        val output = socket.getOutputStream

        while (connected) {
          Thread.sleep(sleep)

          output.write('P'.toByte) // start of the packet
          // coordinates
          output.write(Array(x1.toByte, y1.toByte, x2.toByte, y2.toByte))
          // mode controls
          output.write(genCtrls)
          // send acceleration (only x & y)
          output.write(genAccBytes)

          //Log.d(TAG, s"x1: ${x1.toByte}, y1: ${y1.toByte} x2: ${x2.toByte}, y2: ${y2.toByte}, "+ genCtrls.toString + genAccBytes.toString )

        }

      } catch {
        case ioe: IOException =>
          Log.d(TAG, s" Connection died. \n ${ioe.getMessage}")
          socket.close()
      }

      socket.close()
    }
  }

}
