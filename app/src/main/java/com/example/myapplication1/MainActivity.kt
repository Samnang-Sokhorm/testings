package com.example.myapplication1

import android.R.attr.x
import android.R.attr.y
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication1.AppConfig.SERVER_BASE_URL
import io.socket.client.IO
import io.socket.client.Socket
import kotlin.math.abs
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View.X
import android.view.View.Y
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var socket: Socket

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 20L // 50 times/

    private lateinit var txtServerStatus: TextView
    private lateinit var switchAuto: Switch
    private lateinit var switchBox1: Switch
    private lateinit var switchBox2: Switch

    private var AUTO = 1
    private var BOX1 = 1
    private var BOX2 = 0

    private var blockSwitchEvent = false

    // =========================
    // JOYSTICK VALUES
    // =========================
    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickZ = 0f

    // =========================
    // BUTTON VALUES
    // =========================
    private var lbButton = 0
    private var rbButton = 0
    private var ltButton = 0
    private var rtButton = 0

    private var m1Button = 0
    private var m2Button = 0

    private var aButton = 0
    private var xButton = 0
    private var bButton = 0

    // Y level selector:
    // first push = 1
    // second push = 2
    // third push = 3
    // fourth push = 1
    private var yLevel = 1
    private var yButtonAlreadyStarted = false

    // DPAD values
    private var f1Button = 0
    private var b1Button = 0
    private var r1Button = 0
    private var l1Button = 0

    // DPAD HAT debounce
    private var dpadUpWasPressed = false
    private var dpadDownWasPressed = false
    private var dpadRightWasPressed = false
    private var dpadLeftWasPressed = false

    // =========================
    // LOOPS
    // =========================
    private val sendLoop = object : Runnable {
        override fun run() {
            updateAndSendControllerState()
            handler.postDelayed(this, updateInterval)
        }
    }

    private val renderLoop = object : Runnable {
        override fun run() {
            gameView.postInvalidate()
            handler.postDelayed(this, 16L) // about 60 FPS
        }
    }

    // =========================
    // ACTIVITY START
    // =========================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)

        txtServerStatus = findViewById(R.id.txtServerStatus)
        switchAuto = findViewById(R.id.switchAuto)
        switchBox1 = findViewById(R.id.switchBox1)
        switchBox2 = findViewById(R.id.switchBox2)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        txtServerStatus = findViewById(R.id.txtServerStatus)
        switchAuto = findViewById(R.id.switchAuto)
        switchBox1 = findViewById(R.id.switchBox1)
        switchBox2 = findViewById(R.id.switchBox2)

// default state when app opens
        AUTO = 1
        BOX1 = 1
        BOX2 = 0

        setServerStatus(false)
        updateBoxSwitchUI()

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            AUTO = if (isChecked) 1 else 0
            sendControllerData()
        }

        switchBox1.setOnCheckedChangeListener { _, isChecked ->
            if (blockSwitchEvent) return@setOnCheckedChangeListener

            if (isChecked) {
                BOX1 = 1
                BOX2 = 0
            } else {
                // prevent both BOX1 and BOX2 from OFF
                if (BOX2 == 0) {
                    BOX1 = 1
                }
            }

            updateBoxSwitchUI()
            sendControllerData()
        }

        switchBox2.setOnCheckedChangeListener { _, isChecked ->
            if (blockSwitchEvent) return@setOnCheckedChangeListener

            if (isChecked) {
                BOX1 = 0
                BOX2 = 1
            } else {
                // prevent both BOX1 and BOX2 from OFF
                if (BOX1 == 0) {
                    BOX2 = 1
                }
            }

            updateBoxSwitchUI()
            sendControllerData()
        }

        setupSocket()
        startDataSendLoop()
        startRenderLoop()
    }
    private fun setServerStatus(active: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = 40f

        if (active) {
            txtServerStatus.text = "ACTIVE"
            bg.setColor(Color.parseColor("#22C55E")) // green
        } else {
            txtServerStatus.text = "OFFLINE"
            bg.setColor(Color.parseColor("#EF4444")) // red
        }

        txtServerStatus.background = bg
    }

    private fun updateBoxSwitchUI() {
        blockSwitchEvent = true

        switchAuto.isChecked = AUTO == 1
        switchBox1.isChecked = BOX1 == 1
        switchBox2.isChecked = BOX2 == 1

        blockSwitchEvent = false
    }
    // =========================
    // SOCKET.IO
    // =========================
    private fun setupSocket() {
        try {
            socket = IO.socket(SERVER_BASE_URL)

            socket.on(Socket.EVENT_CONNECT) {
                Log.d("SocketIO", "Connected to server")

                runOnUiThread {
                    setServerStatus(true)
                }

                sendJoystickDataToServer()
            }

            socket.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketIO", "Disconnected from server")

                runOnUiThread {
                    setServerStatus(false)
                }
            }

            socket.on(Socket.EVENT_CONNECT_ERROR) {
                Log.e("SocketIO", "Connect error")

                runOnUiThread {
                    setServerStatus(false)
                }
            }

            socket.on("server_ack") { args ->
                if (args.isNotEmpty()) {
                    Log.d("SocketIO", "ACK: ${args[0]}")
                }
            }

            socket.on("server_state") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject

                    AUTO = data.optInt("AUTO", AUTO)
                    BOX1 = data.optInt("BOX1", BOX1)
                    BOX2 = data.optInt("BOX2", BOX2)

                    runOnUiThread {
                        setServerStatus(true)
                        updateBoxSwitchUI()
                    }
                }
            }

            socket.on("teensy_data") { args ->
                if (args.isNotEmpty()) {
                    Log.d("TeensyData", args[0].toString())
                }
            }

            socket.connect()

        } catch (e: Exception) {
            Log.e("SocketIO", "Socket setup error", e)

            runOnUiThread {
                setServerStatus(false)
            }
        }
    }

    // =========================
    // JOYSTICK / DPAD AXIS
    // =========================
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onGenericMotionEvent(event)
        }

        joystickX = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_X))
        joystickY = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Y))

        // Some controllers use AXIS_Z, some use AXIS_RZ for right stick.
        val zAxis = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Z))
        val rzAxis = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_RZ))

        joystickZ = if (abs(rzAxis) > abs(zAxis)) {
            rzAxis
        } else {
            zAxis
        }

        // D-pad / HAT axis
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        // UP: hatY = -1
        if (hatY < -0.5f) {
            if (!dpadUpWasPressed) {
                dpadUpWasPressed = true
                f1Button = 1 - f1Button
                Log.e("DPAD_HAT_DEBUG", "F1 toggle = $f1Button")
            }
        } else {
            dpadUpWasPressed = false
        }

        // DOWN: hatY = 1
        if (hatY > 0.5f) {
            if (!dpadDownWasPressed) {
                dpadDownWasPressed = true
                b1Button = 1 - b1Button
                Log.e("DPAD_HAT_DEBUG", "B1 toggle = $b1Button")
            }
        } else {
            dpadDownWasPressed = false
        }

        // RIGHT: hatX = 1
        if (hatX > 0.5f) {
            if (!dpadRightWasPressed) {
                dpadRightWasPressed = true
                r1Button = 1 - r1Button
                Log.e("DPAD_HAT_DEBUG", "R1 toggle = $r1Button")
            }
        } else {
            dpadRightWasPressed = false
        }

        // LEFT: hatX = -1
        if (hatX < -0.5f) {
            if (!dpadLeftWasPressed) {
                dpadLeftWasPressed = true
                l1Button = 1 - l1Button
                Log.e("DPAD_HAT_DEBUG", "L1 toggle = $l1Button")
            }
        } else {
            dpadLeftWasPressed = false
        }

        updateAndSendControllerState()
        return true
    }

    private fun applyDeadZone(value: Float): Float {
        return if (abs(value) < 0.08f) {
            0f
        } else {
            value
        }
    }

    // =========================
    // BUTTON EVENTS
    // =========================
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.e(
            "KEY_LOGGER",
            "keyCode=${event.keyCode}, " +
                    "name=${KeyEvent.keyCodeToString(event.keyCode)}, " +
                    "action=${event.action}, repeat=${event.repeatCount}"
        )

        when (event.keyCode) {

            // =========================
            // LB push button
            // Sends 1 on press, 0 on release.
            // Arduino stores the LB path.
            // =========================
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    lbButton = 1
                    Log.d("LB Button", "LB Pressed")
                    updateAndSendControllerState()
                } else if (event.action == KeyEvent.ACTION_UP) {
                    lbButton = 0
                    Log.d("LB Button", "LB Released")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // RB push button
            // Sends 1 on press, 0 on release.
            // Arduino stores the RB path.
            // =========================
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    rbButton = 1
                    Log.d("RB Button", "RB Pressed")
                    updateAndSendControllerState()
                } else if (event.action == KeyEvent.ACTION_UP) {
                    rbButton = 0
                    Log.d("RB Button", "RB Released")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // LT hold
            // =========================
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    ltButton = 1
                    Log.d("LT Button", "LT Pressed")
                    updateAndSendControllerState()
                } else if (event.action == KeyEvent.ACTION_UP) {
                    ltButton = 0
                    Log.d("LT Button", "LT Released")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // RT hold
            // =========================
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    rtButton = 1
                    Log.d("RT Button", "RT Pressed")
                    updateAndSendControllerState()
                } else if (event.action == KeyEvent.ACTION_UP) {
                    rtButton = 0
                    Log.d("RT Button", "RT Released")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // X toggle
            // First press: X = 1
            // Second press: X = 0
            // =========================
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    xButton = if (xButton == 0) 1 else 0
                    Log.d("X Button", "X state = $xButton")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // Y level selector
            // first push = 1
            // second push = 2
            // third push = 3
            // fourth push = 1
            // =========================
            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (!yButtonAlreadyStarted) {
                        yLevel = 1
                        yButtonAlreadyStarted = true
                    } else {
                        yLevel++
                        if (yLevel > 3) {
                            yLevel = 1
                        }
                    }

                    Log.d("Y Button", "Y level = $yLevel")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // A toggle
            // =========================
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    aButton = if (aButton == 0) 1 else 0
                    Log.d("A Button", "A state = $aButton")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // B toggle
            // =========================
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    bButton = if (bButton == 0) 1 else 0
                    Log.d("B Button", "B state = $bButton")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // Thumb buttons
            // =========================
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    m1Button = if (m1Button == 0) 1 else 0
                    Log.d("M1 Button", "M1 state = $m1Button")
                    updateAndSendControllerState()
                }
                return true
            }

            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    m2Button = if (m2Button == 0) 1 else 0
                    Log.d("M2 Button", "M2 state = $m2Button")
                    updateAndSendControllerState()
                }
                return true
            }

            // =========================
            // DPAD physical key fallback
            // =========================
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    f1Button = 1 - f1Button
                    Log.e("DPAD_DEBUG", "F1 = $f1Button")
                    updateAndSendControllerState()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    b1Button = 1 - b1Button
                    Log.e("DPAD_DEBUG", "B1 = $b1Button")
                    updateAndSendControllerState()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    r1Button = 1 - r1Button
                    Log.e("DPAD_DEBUG", "R1 = $r1Button")
                    updateAndSendControllerState()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    l1Button = 1 - l1Button
                    Log.e("DPAD_DEBUG", "L1 = $l1Button")
                    updateAndSendControllerState()
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    // =========================
    // UPDATE VIEW + SEND SERVER
    // =========================
    private fun updateAndSendControllerState() {
        gameView.updateControllerState(
            joystickX,
            joystickY,
            joystickZ,
            lbButton,
            rbButton,
            ltButton,
            rtButton,
            m1Button,
            m2Button,
            xButton,
            yLevel,
            aButton,
            bButton,
            f1Button,
            b1Button,
            l1Button,
            r1Button
        )

        sendJoystickDataToServer()
    }

    private fun sendJoystickDataToServer() {
        if (!::socket.isInitialized || !socket.connected()) {
            Log.e("SocketIO", "Socket not connected")

            runOnUiThread {
                setServerStatus(false)
            }

            return
        }

        val json = JSONObject().apply {
            put("x", joystickX)
            put("y", -joystickY)
            put("z", joystickZ)

            put("LB", lbButton)
            put("RB", rbButton)
            put("LT", ltButton)
            put("RT", rtButton)

            put("M1", m1Button)
            put("M2", m2Button)

            put("X1", xButton)
            put("Y1", yLevel)
            put("A1", aButton)
            put("B1", bButton)

            put("F1", f1Button)
            put("Back1", b1Button)
            put("L1", l1Button)
            put("R1", r1Button)

            // new values
            put("AUTO", AUTO)
            put("BOX1", BOX1)
            put("BOX2", BOX2)
        }

        Log.d("SocketIO", "Emit controller_data: $json")
        socket.emit("controller_data", json)
    }

    private fun sendControllerData() {
        val json = JSONObject()

        // joystick data
        json.put("x", x)
        json.put("y", y)
        json.put("z", z)

        // old buttons
        json.put("A", A)
        json.put("B", B)
        json.put("X", X)
        json.put("Y", Y)

        json.put("LB", LB)
        json.put("RB", RB)
        json.put("LT", LT)
        json.put("RT", RT)

        // new auto and box switches
        json.put("AUTO", AUTO)
        json.put("BOX1", BOX1)
        json.put("BOX2", BOX2)

        socket.emit("controller_data", json)
    }
    // =========================
    // LOOPS
    // =========================
    private fun startDataSendLoop() {
        handler.post(sendLoop)
    }

    private fun startRenderLoop() {
        handler.post(renderLoop)
    }

    // =========================
    // CONFIG CHANGE
    // =========================
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Orientation", "Landscape mode")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("Orientation", "Portrait mode")
        }
    }


    // =========================
    // DESTROY
    // =========================
    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(sendLoop)
        handler.removeCallbacks(renderLoop)

        if (::socket.isInitialized) {
            socket.disconnect()
            socket.off()
        }
    }
}