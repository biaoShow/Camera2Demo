package com.biao.camera2demo

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SaveImage(private val byteArray: ByteArray) : Runnable {

    override fun run() {
        val startTime = System.currentTimeMillis()
        val mImageFile =
            File("${Environment.getExternalStorageDirectory()}/DCIM/$startTime.jpg");
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(mImageFile);
            fos.write(byteArray, 0, byteArray.size)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}