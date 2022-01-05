package com.biao.camera2demo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat

class VideoActivity : AppCompatActivity() {
    private val TAG = VideoActivity::class.java.simpleName
    private lateinit var tvVideo: TextureView
    private lateinit var btnVideo: Button
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private var mediaRecorder: MediaRecorder? = null
    private var isRecordingVideo = false

    private lateinit var handlerThread: HandlerThread
    private lateinit var bgHandler: Handler

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable")
            openCamera(0)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//            Log.d(TAG, "onSurfaceTextureUpdated")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        initView()
        initData()
    }

    private fun initView() {
        tvVideo = findViewById(R.id.tv_video)
        btnVideo = findViewById(R.id.btn_video)

        //预览角度（适合横屏，竖屏需要自行调整）
        tvVideo.rotation = 180F
    }

    private fun initData() {
        mediaRecorder = MediaRecorder()

        handlerThread = HandlerThread("bg")
        handlerThread.start()
        bgHandler = Handler(handlerThread.looper)
    }

    override fun onResume() {
        super.onResume()
        if (tvVideo.isAvailable) {
            openCamera(0)
        } else {
            tvVideo.surfaceTextureListener = textureListener
        }
    }

    private fun openCamera(cameraID: Int) {
        //判断是否有相机权限
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "未授权，请到设置页面手动授权！", Toast.LENGTH_SHORT).show()
            return
        }

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //查看是否存在摄像头
        val cameraIdList = cameraManager.cameraIdList
        Log.i(TAG, "支持打开的摄像头个数：${cameraIdList.size}")
        if (cameraIdList.size <= 0 || cameraID > cameraIdList.size - 1) {
            Toast.makeText(this, "未检测到摄像头！", Toast.LENGTH_SHORT).show()
            return
        }

        //打开相机
        cameraManager.openCamera(cameraID.toString(), object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "onOpened：打开成功！！")
                cameraDevice = camera
                startPreview()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.i(TAG, "onDisconnected")
                camera.close()
            }

            override fun onClosed(camera: CameraDevice) {
                super.onClosed(camera)
                Log.i(TAG, "onClosed")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "onError code: $error")
                camera.close()
            }

        }, null)
    }

    private fun startPreview() {
        if (cameraDevice == null) return

        closePreviewSession()

        val surfaceTexture = tvVideo.surfaceTexture
//        surfaceTexture!!.setDefaultBufferSize(1280, 720)
        previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

        val surface = Surface(surfaceTexture)//创建一个新的Capture请求
        // 给此次请求添加一个Surface对象作为图像的输出目标
        previewRequestBuilder.addTarget(surface)

        createCaptureSession(listOf(surface))

//        cameraDevice!!.createCaptureSession(
//            listOf(surface),
//            object : CameraCaptureSession.StateCallback() {
//                override fun onConfigured(session: CameraCaptureSession) {
//                    Log.i(TAG, "onConfigured")
//                    this@VideoActivity.session = session
//                    session.setRepeatingRequest(previewRequestBuilder.build(), null, bgHandler)
//                }
//
//                override fun onConfigureFailed(session: CameraCaptureSession) {
//                    Log.i(TAG, "onConfigureFailed")
//                }
//            },
//            null
//        )
    }

    private fun startRecordingVideo() {
        if (cameraDevice == null || !tvVideo.isAvailable || mediaRecorder == null) return

        closePreviewSession()

        mediaRecorder?.apply {
            setOrientationHint(180)//录制时评方向（适合横屏，竖屏需要自行调整）
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile("/sdcard/${System.currentTimeMillis()}.mp4")
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(1280, 720)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        val texture = tvVideo.surfaceTexture!!.apply {
//            setDefaultBufferSize(1280, 720)
        }
        val previewSurface = Surface(texture)
        val recorderSurface = mediaRecorder!!.surface
        val surfaces = ArrayList<Surface>().apply {
            add(previewSurface)
            add(recorderSurface)
        }
        previewRequestBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            }

        createCaptureSession(surfaces)

        mediaRecorder?.start()
    }

    private fun createCaptureSession(surfaces: List<Surface>) {
        cameraDevice?.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.i(TAG, "onConfigured video")
                    this@VideoActivity.session = session
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, bgHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.i(TAG, "onConfigureFailed video")
                }

            }, null
        )
    }

    private fun stopRecordingVideo() {
        mediaRecorder?.apply {
            stop()
            reset()
        }
        startPreview()
    }

    private fun closePreviewSession() {
        session?.close()
        session = null
    }


    fun video(view: View) {
        if (!isRecordingVideo) {
            btnVideo.text = "stop"
            startRecordingVideo()
            isRecordingVideo = true
        } else {
            btnVideo.text = "start"
            stopRecordingVideo()
            isRecordingVideo = false
        }
    }

    private fun closeCamera() {
        closePreviewSession()
        cameraDevice?.close()
        cameraDevice = null
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }
}