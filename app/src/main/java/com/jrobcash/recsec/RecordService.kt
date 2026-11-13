package com.jrobcash.recsec

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import android.util.Size
import java.util.concurrent.atomic.AtomicBoolean

class RecordService : Service() {
    private val channel = "recsec_recording"
    private val running = AtomicBoolean(false)
    private val handlerThread = HandlerThread("RecSecCamera")
    private lateinit var handler: Handler
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var outputUri: android.net.Uri? = null
    private var outputFd: android.os.ParcelFileDescriptor? = null
    private var recordingStarted = false
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        handlerThread.start(); handler = Handler(handlerThread.looper)
        notificationManager.createNotificationChannel(NotificationChannel(channel, "RecSec recording", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notice(message: String): Notification {
        val stopIntent = Intent(this, RecordService::class.java).setAction("STOP")
        val stop = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel).setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("RecSec").setContentText(message).setOngoing(true)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(11, notice("Preparing camera…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(11, notice("Preparing camera…"))
            handler.post { begin() }
        } catch (e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
    private fun begin() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Camera/microphone permission missing")
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: error("No rear camera")
            val characteristics = manager.getCameraCharacteristics(id)
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
            val size = sizes.firstOrNull { it.width == 1920 && it.height == 1080 } ?: sizes.filter { it.width <= 1920 && it.height <= 1080 }.maxByOrNull { it.width * it.height } ?: error("No supported video size")
            prepareRecorder(size)
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createSession(device) }
                override fun onDisconnected(device: CameraDevice) { device.close(); stopSelf() }
                override fun onError(device: CameraDevice, error: Int) { device.close(); stopSelf() }
            }, handler)
        } catch (e: Exception) { android.util.Log.e("RecSec", "Start failed", e); stopSelf() }
    }
    private fun prepareRecorder(size: Size) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "RecSec_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RecSec")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create video file")
        outputFd = contentResolver.openFileDescriptor(outputUri!!, "w") ?: error("Cannot open video file")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(size.width, size.height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(8_000_000)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFd!!.fileDescriptor)
            prepare()
        }
    }
    private fun createSession(device: CameraDevice) {
        try {
            val surface = recorder!!.surface
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(surface); set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) }.build()
                        s.setRepeatingRequest(request, null, handler)
                        recorder!!.start()
                        recordingStarted = true
                        notificationManager.notify(11, notice("Recording video — tap Stop to save"))
                    } catch (e: Exception) { android.util.Log.e("RecSec", "Record failed", e); stopSelf() }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { stopSelf() }
            }, handler)
        } catch (e: Exception) { android.util.Log.e("RecSec", "Session failed", e); stopSelf() }
    }
    override fun onDestroy() {
        // Close the camera pipeline on its dedicated thread to avoid racing callbacks.
        handler.post {
            try { session?.stopRepeating() } catch (_: Exception) {}
            try { session?.abortCaptures() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { camera?.close() } catch (_: Exception) {}
            var good = false
            try { if (recordingStarted) { recorder?.stop(); good = true } } catch (e: Exception) { android.util.Log.e("RecSec", "Stop failed", e) }
            try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
            try { outputFd?.close() } catch (_: Exception) {}
            outputUri?.let { uri ->
                try {
                    if (good) contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                    else contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            handlerThread.quitSafely()
        }
        super.onDestroy()
    }
}
