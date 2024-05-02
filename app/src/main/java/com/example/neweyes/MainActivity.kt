package com.example.neweyes
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lango.record.AndroidAudioRecorder
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var volumeChangeReceiver: VolumeChangeReceiver
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var capReq: CaptureRequest.Builder
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Set connection timeout
        .readTimeout(60, TimeUnit.SECONDS)    // Set read timeout
        .writeTimeout(60, TimeUnit.SECONDS)   // Set write timeout
        .build()
    private lateinit var textToSpeech: TextToSpeech
    var engRes = "{\"text\":\"Where\"}"
    var response: String = ""
    var responseBody1 = ""
    var correct: Boolean by mutableStateOf(false)
    var content = ""
    var res: String = ""
    private lateinit var audioManager: AudioManager
    private var extractedText = ""

    private val recorder by lazy {
        AndroidAudioRecorder(applicationContext)
    }
    private var audioFile: File? = null


    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        super.onDestroy()
        handlerThread.quitSafely()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //for audio
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0
        )
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeChangeReceiver = VolumeChangeReceiver()
        val filter = IntentFilter()
        filter.addAction("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeChangeReceiver, filter)



        handlerThread = HandlerThread("CameraBackground")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        getPermissions()
        textToSpeech = TextToSpeech(this, this)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Text-to-Speech language not supported.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecording() {
        try {
            Toast.makeText(applicationContext, "Start Speaking", Toast.LENGTH_SHORT).show()
            File(cacheDir, "sample.mp3").also {
                recorder.start(it)
                audioFile = it
                response = ""   // Reset the response variable
                engRes = " "     // Reset the engRes variable
                content = ""
                correct = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
//            Log.d("tag","&$>+e.printStackTrace(")
        }
    }

    fun stopRecording() {
        try {

            recorder.stop()
            Log.d("tag","!!>>")
            res = ""
            res = audioFile?.let { transcribeAudio(it) }.toString()
            if(res.isNotEmpty()){
                val jsonObject = JSONObject(res)
                extractedText = jsonObject.optString("text", "DefaultText")
                takePicture( )

                Toast.makeText(applicationContext, extractedText, Toast.LENGTH_SHORT).show()

//                val sub: Unit = findViewById<TextView?>(R.id.subtitles).setText(extractedText)
//                engRes = chatTranslateToEnglish(res)
            }
            else{
                Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
            }

        }catch(e:Exception){
            Log.d("tag","()"+e.printStackTrace())
        }
    }

    @SuppressLint("SuspiciousIndentation")
    fun chatTranslateToEnglish(inputText: String): String {
        Thread {
            val value= "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"
            val apiUrl = "https://api.openai.com/v1/chat/completions"
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestData = JSONObject()
            requestData.put("model", "gpt-3.5-turbo")
            val messages = JSONArray()
            val systemMessage = JSONObject()
            systemMessage.put("role", "system")
            systemMessage.put(
                "content",
                "Translate the text to english and do not reply anything else strictly"
            )
            val userMessage = JSONObject()
            userMessage.put("role", "user")
            userMessage.put("content", inputText)  // Include the inputText in the conversation

            messages.put(systemMessage)
            messages.put(userMessage)

            requestData.put("messages", messages)

            val requestBody = RequestBody.create(jsonMediaType, requestData.toString())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)

            try {
                responseBody1 = client.newCall(request.build()).execute().use { response ->
                    response.body?.string() ?: ""
                }
                val jsonResponse = JsonParser.parseString(responseBody1).asJsonObject
                content = jsonResponse.getAsJsonArray("choices")
                    .firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    ?: ""

                println("#> Response Content: $content")
//                println("#> Response: $responseBody1")
                correct = true
            } catch (e: IOException) {
                e.printStackTrace()
                println("!> Request failed with exception: ${e.message}")
            }
        }.start()

        while (content == "") {
        }
        return content;

    }

    fun transcribeAudio(audioFile: File): Any? {
        val audioRequestBody = audioFile.asRequestBody("audio/*".toMediaType())
        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioRequestBody)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", "en")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header(
                "Authorization",
                "Bearer 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"
            )
            .post(formBody)

        Thread {
            try {
                response = client.newCall(request.build()).execute().use { response ->
                    response.body?.string() ?: ""
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                correct = true
            }
        }.start()
        while (response == "") {
        }
        correct = true
        println("&>" + response)
        return response;
    }

    // Function to convert text to speech
    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun takePicture1() {
        try {
            // Capture a bitmap from the TextureView
            val bitmap = textureView.bitmap ?: return

            // Encode the bitmap to Base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            // Prepare payload for OpenAI request
            val payload = JSONObject().apply {
                put("model", "gpt-4-turbo")

                val messages = JSONArray().apply {
                    val message = JSONObject().apply {
                        put("role", "user")

                        val content = JSONArray().apply {
                            val text = JSONObject().apply {
                                put("type", "text")
                                put("text", "What’s in this image?")
                            }
                            put(text)

                            val imageUrl = JSONObject().apply {
                                put("type", "image_url")

                                val imageUrlObject = JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                }
                                put("image_url", imageUrlObject)
                            }
                            put(imageUrl)
                        }
                        put("content", content)
                    }
                    put(message)
                }
                put("messages", messages)
                put("max_tokens", 500)
            }

            // Make HTTP POST request to OpenAI API
            val value = "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"
            val client = OkHttpClient()

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = RequestBody.create(mediaType, payload.toString())

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $value")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("OpenAI Request", "Failed: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    Log.d("<! OpenAI Response", "Response: $responseBody")
                    // Process response as needed
                }
            })

        } catch (e: Exception) {
            Log.e("<!!Image Processing", "Error processing image: ${e.message}")
        }
    }


    private fun takePicture() {
        try {
            val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
            Log.d("takePicture", ">! Starting to capture and process image")
            val bitmap = textureView.bitmap ?: return
            Log.d("takePicture", "Bitmap captured successfully")
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            runOnUiThread {
                subtitlesTextView.text = "Photo taken. Processing..."
            }
            Log.d("takePicture", "Bitmap encoded to Base64")
            val payload = JSONObject().apply {
                put("model", "gpt-4-turbo")

                val messages = JSONArray().apply {
                    val message = JSONObject().apply {
                        put("role", "user")

                        val content = JSONArray().apply {
                            val text = JSONObject().apply {
                                put("type", "text")
                                put("text", extractedText)
                            }

                            put(text)

                            val imageUrl = JSONObject().apply {
                                put("type", "image_url")

                                val imageUrlObject = JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                }
                                put("image_url", imageUrlObject)
                            }
                            put(imageUrl)
                        }
                        put("content", content)
                    }
                    put(message)
                }
                put("messages", messages)
                put("max_tokens", 300)
            }

            val value = "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"
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS) // Set connection timeout
                .readTimeout(60, TimeUnit.SECONDS)    // Set read timeout
                .writeTimeout(60, TimeUnit.SECONDS)   // Set write timeout
                .build()

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = RequestBody.create(mediaType, payload.toString())

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $value")
                .post(requestBody)
                .build()

            Log.d("takePicture", "Sending HTTP request to OpenAI API")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(">! OpenAI Request", "Failed: ${e.message}")

                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    responseBody?.let { json ->
                        try {
                            val jsonResponse = JSONObject(json)
                            val choicesArray = jsonResponse.getJSONArray("choices")

                            if (choicesArray.length() > 0) {
                                val firstChoice = choicesArray.getJSONObject(0)
                                val messageObject = firstChoice.getJSONObject("message")
                                val assistantResponse = messageObject.getString("content")

                                runOnUiThread {
                                    subtitlesTextView.text = assistantResponse
                                    // Speak the response
                                    speakText(assistantResponse)
                                }
                            } else {
                                runOnUiThread {
                                    subtitlesTextView.text = "No response content available"
                                }
                            }
                        } catch (e: JSONException) {
                            Log.e("JSON Parsing", "Error parsing JSON: ${e.message}")
                            runOnUiThread {
                                subtitlesTextView.text = "Error parsing response"
                            }
                        }
                    } ?: run {
                        runOnUiThread {
                            subtitlesTextView.text = "No response received"
                        }
                    }
                }

            })

        } catch (e: Exception) {
            Log.e("Image Processing", "Error processing image: ${e.message}")
        }
    }

    private inner class VolumeChangeReceiver : BroadcastReceiver() {
        private var lastVolume = 0

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                // Check if volume is increased or decreased
                if (currentVolume > lastVolume) {
                    // Volume increased, executeVolumeUpCommand()
                    executeVolumeUpCommand()
                } else if (currentVolume < lastVolume) {
                    // Volume decreased, executeVolumeDownCommand()
                    executeVolumeDownCommand()
                }
                lastVolume = currentVolume
            }
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun executeVolumeUpCommand() {

        findViewById<TextView?>(R.id.subtitles).setText(" ")
        val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
        val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.start)
        val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
        subtitlesTextView.background = backgroundLayerDrawable
        if (isNetworkAvailable(this)) {
            startRecording()
        }
        else{
            Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeVolumeDownCommand() {
        val subtitlesTextView = findViewById<TextView>(R.id.subtitles)
        subtitlesTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
        val curvedBackgroundDrawable = ContextCompat.getDrawable(this, R.drawable.curved_background)
        val backgroundLayerDrawable = LayerDrawable(arrayOf(curvedBackgroundDrawable))
        subtitlesTextView.background = backgroundLayerDrawable
        if (isNetworkAvailable(this)) {
            stopRecording()
        }
        else{
            Toast.makeText(applicationContext, "No internet", Toast.LENGTH_SHORT).show()
        }
    }



    fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return true
    }

    private fun openCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            cameraManager.openCamera(cameraIds[0], object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val surface = Surface(textureView.surfaceTexture)
                    capReq.addTarget(surface)
                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                // Capture session configured successfully
                                cameraCaptureSession = session
                                cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                                val delayMillis = 5000L
                                Handler().postDelayed({
//                                    takePicture()
                                }, delayMillis)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e("Camera", "Capture session configuration failed")
                            }
                        },
                        null
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.e("Camera", "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("Camera", "Camera error: $error")
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.e("Camera Access Exception", "Error accessing camera: ${e.message}")
        }
    }

    private fun getPermissions() {
        val permissionsList = mutableListOf<String>()

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.CAMERA)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsList.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsList.toTypedArray(), 101)
            }
        } catch (e: Exception) {
            Log.e("Permission Exception", "Error requesting permissions: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if (it != PackageManager.PERMISSION_GRANTED) {
                getPermissions()
            }
        }
    }
}









