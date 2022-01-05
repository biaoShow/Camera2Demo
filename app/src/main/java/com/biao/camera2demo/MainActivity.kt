package com.biao.camera2demo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var cameraDevice: CameraDevice
    private val TAG = MainActivity::class.simpleName
    private lateinit var tvCamera: TextureView
    private lateinit var clLayout: ConstraintLayout
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var handlerThread: HandlerThread
    private lateinit var bgHandler: Handler

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
    )

    //图像宽高
    private var width: Int = 1280
    private var height: Int = 720

//    companion object {
//        private var PHOTO_ORITATION: SparseIntArray = SparseIntArray()
//
//        init {
//            PHOTO_ORITATION.append(Surface.ROTATION_0, 90)
//            PHOTO_ORITATION.append(Surface.ROTATION_90, 0)
//            PHOTO_ORITATION.append(Surface.ROTATION_180, 270);
//            PHOTO_ORITATION.append(Surface.ROTATION_270, 180);
//        }
//    }

    private val TextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable")
            openCamera(0)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "onSurfaceTextureDestroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            Log.d(TAG, "onSurfaceTextureUpdated")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()
        initView()
        initData()
    }

    private fun requestPermissions() {
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, permissions, 0x01)
            }
        }
    }


    private fun initView() {
        tvCamera = findViewById(R.id.tv_camera)
        clLayout = findViewById(R.id.cl_layout)

        //预览角度（适合横屏，竖屏需要自行调整）
        tvCamera.rotation = 180F
    }

    private fun initData() {
        handlerThread = HandlerThread("bg")
        handlerThread.start()
        bgHandler = Handler(handlerThread.looper)
    }

    override fun onResume() {
        super.onResume()
        if (tvCamera.isAvailable) {
            openCamera(0)
        } else {
            tvCamera.surfaceTextureListener = TextureListener
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

        //查看支持的分辨率
        val cameraCharacteristics: CameraCharacteristics =
            cameraManager.getCameraCharacteristics(cameraID.toString())
        val streamConfigurationMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//        val sizes: Array<Size> = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)
        val sizes: Array<Size> = streamConfigurationMap!!.getOutputSizes(ImageFormat.JPEG)
        Log.d(TAG, "摄像头支持的分辨率：")
        for (size in sizes) {
            Log.d(TAG, "width = ${size.width}, height = ${size.height}")
        }

        //设置图片分辨率
//        val maxSize = Collections.max(sizes,CompareSizeByArea())
        width = sizes[0].width
        height = sizes[0].height
        Log.i(TAG, "分辨率：width = $width, height = $height")

        //最大支持人脸个数
        val faceDetectCount =
            cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT)    //同时检测到人脸的数量
        //支持人脸识别模式
        //0不支持人脸识别，1可支持到返回人脸坐标和置信度，2人脸坐标、分数、地标、人脸ID等详细人脸信息
        val faceDetectModes =
            cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)  //人脸检测的模式

        //人脸识别支持判断
        if (faceDetectCount == null || faceDetectCount <= 0 || faceDetectModes == null ||
            (faceDetectModes.size == 1 && faceDetectModes[0] == 0)
        ) {
            Toast.makeText(this, "当前摄像头不支持人脸识别！！", Toast.LENGTH_LONG).show()
        }

        //获取是否支持闪关灯
        val flashAvailable: Boolean? =
            cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        if (flashAvailable == null || flashAvailable == false) {
            Toast.makeText(this, "设备不支持闪光灯", Toast.LENGTH_LONG).show()
        }

        //根据父布局大小，设置TextureView大小,跟相机像素保持相等比例，避免预览变形
        val layoutParams = tvCamera.layoutParams
        val min = clLayout.width.coerceAtMost(clLayout.height)
        layoutParams.width = min * width / height
        layoutParams.height = min
        tvCamera.layoutParams = layoutParams
        Log.i(TAG, "预览宽高：width = ${min * width / height}, height = $min")

        //初始化拍照使用对象
        //设置图片大小
        mImageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
        mImageReader.setOnImageAvailableListener(
            { reader ->
                Log.i(TAG, "Image Available!")
                val image: Image = reader.acquireNextImage()
                val byteBuffer = image.planes[0].buffer
                val byteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(byteArray)
                image.close()//不close导致下次拍照报错
                //子线程保存图片
                bgHandler.post(SaveImage(byteArray))
            }, null
        )

        //打开相机
        cameraManager.openCamera(cameraID.toString(), object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "onOpened：打开成功！！")
                cameraDevice = camera
                startPreview(camera)
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

    lateinit var captureRequestBuilder: CaptureRequest.Builder
    lateinit var mImageReader: ImageReader;

    //监听ImageReader的事件，当有图像流数据可用时会回调onImageAvailable方法，它的参数就是预览帧数据，可以对这帧数据进行处理
    val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            Log.i(TAG, "onCaptureStarted")
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            super.onCaptureProgressed(session, request, partialResult)
            Log.i(TAG, "onCaptureProgressed")
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            Log.i(TAG, "onCaptureCompleted")
            val faces = result.get(CaptureResult.STATISTICS_FACES)
            Log.e(TAG, "faceNum = ${faces?.size}")
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            Log.e(TAG, "onCaptureFailed")
        }
    }

    private val stateCallback = object : CameraCaptureSession.StateCallback() {
        //摄像头完成配置，可以处理Capture请求了。
        override fun onConfigured(session: CameraCaptureSession) {
            Log.i(TAG, "onConfigured")
            cameraCaptureSession = session
            //根据传入的 CaptureRequest 对象开始一个无限循环的捕捉图像的请求。第二个参数 listener 为捕捉图像的回调
            session.setRepeatingRequest(
                captureRequestBuilder.build(),
                captureCallback,
                null
            )
        }

        //摄像头正在处理请求
        override fun onActive(session: CameraCaptureSession) {
            super.onActive(session)
            Log.i(TAG, "onActive")
        }

        //请求队列中为空，准备着接受下一个请求
        override fun onCaptureQueueEmpty(session: CameraCaptureSession) {
            super.onCaptureQueueEmpty(session)
            Log.i(TAG, "onCaptureQueueEmpty")
        }

        //会话被关闭
        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            Log.i(TAG, "onClosed")
        }

        //摄像头处于就绪状态，当前没有请求需要处理
        override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            Log.i(TAG, "onReady")
        }

        //Surface准备就绪
        override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) {
            super.onSurfacePrepared(session, surface)
            Log.i(TAG, "onSurfacePrepared")
        }

        //摄像头配置失败
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "onConfigureFailed")
            Toast.makeText(this@MainActivity, "摄像头配置失败！！", Toast.LENGTH_LONG).show()
        }
    }

    private fun startPreview(cameraDevice: CameraDevice) {
        try {
            val surfaceTexture = tvCamera.surfaceTexture
            //设置预览大小
            surfaceTexture?.setDefaultBufferSize(width, height)
            //描述了一次操作请求，拍照、预览等操作都需要先传入CaptureRequest参数，
            // 具体的参数控制也是通过CameraRequest的成员变量来设置
            captureRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surface = Surface(surfaceTexture)//创建一个新的Capture请求
            // 给此次请求添加一个Surface对象作为图像的输出目标
            captureRequestBuilder.addTarget(surface)

            // 自动曝光
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            // 自动对焦
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            // 白平衡（可以调节不同场景：日光、阴天日光、荧光灯、白炽灯等）
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO
            )

//            //闪光灯模式控制
//            captureRequestBuilder.set(
//                CaptureRequest.FLASH_MODE,
//                CaptureRequest.FLASH_MODE_SINGLE
//            )
            //降噪
//            captureRequestBuilder.set(
//                CaptureRequest.NOISE_REDUCTION_MODE,
//                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
//            )
//            //色差矫正
//            captureRequestBuilder.set(
//                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
//                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
//            )
//            //色差矫正
//            captureRequestBuilder.set(
//                CaptureRequest.COLOR_CORRECTION_MODE,
//                CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
//            )
            //色差矫正
//            captureRequestBuilder.set(
//                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
//                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
//            )


            //人脸信息特征返回模式/程度
            captureRequestBuilder.set(
                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE
            )

            // 创建CaptureSession会话。
            // 第一个参数 outputs 是一个 List 数组，相机会把捕捉到的图片数据传递给该参数中的 Surface 。
            // 第二个参数 StateCallback 是创建会话的状态回调。
            // 第三个参数描述了 StateCallback 被调用时所在的线程
            cameraDevice.createCaptureSession(
                listOf(surface, mImageReader.surface),
                stateCallback,
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    fun take(view: View) {
        //拍照
        captureRequestBuilder.addTarget(mImageReader.surface)
        //设置图片方向（适合横屏，竖屏需要自行调整）
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 180)
        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())

        //闪光灯模式控制
        captureRequestBuilder.set(
            CaptureRequest.FLASH_MODE,
            CaptureRequest.FLASH_MODE_SINGLE
        )

        cameraCaptureSession.capture(captureRequestBuilder.build(), null, null)
    }

    override fun onStop() {
        super.onStop()
        cameraCaptureSession.close()
        cameraDevice.close()
    }

    fun start(view: View) {

    }

    fun stop(view: View) {

    }
}