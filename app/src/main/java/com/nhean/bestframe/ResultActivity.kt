package com.nhean.bestframe

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_result.*
import android.os.Environment
import java.io.*
import java.lang.Double.parseDouble

class ResultActivity : AppCompatActivity() {

    val time_stamp = System.currentTimeMillis()
    val path = Environment.getExternalStorageDirectory().toString()+"/IDScanner"
    val sd_folder = File(path, "session_${time_stamp}")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        copyFile("shape_predictor_68_face_landmarks.dat")
        copyFile("dlib_face_recognition_resnet_model_v1.dat")

        val bundle :Bundle ?=intent.extras
        if (bundle!=null){
            val idNumber = bundle.getString("idNumber")
            val birthDate = bundle.getString("dob")
            val gender = bundle.getString("gender")
            val name = bundle.getString("name")

            val image = bundle.getString("image_name")
            val bmp = loadBitmap(baseContext, image.toString())
            image_view.setImageBitmap(bmp)

            val lapla_var = bundle.getString("lapla_var")
            val assesment_time = bundle.getString("assesment_time")
            val ocr_time = bundle.getString("ocr_time")
            val processing_time = bundle.getString("processing_time")

            val resultOpt = "Resolution: ${bmp!!.width}x${bmp!!.height}\n\n"+
                            "ID: ${idNumber} \n" +
                            "Birth Date: ${birthDate}\n" +
                            "Gender: ${gender}\n" +
                            "Name: ${name}\n\n\n" +
                            "Laplacain Var: ${lapla_var}\n"+
                            "Assesment Time: ${assesment_time} ms\n" +
                            "OCR Time: ${ocr_time} ms\n" +
                            "Processing TIme: ${parseDouble(processing_time!!)} ms\n\n" +
                            "Load Shape Detector: ${returnFaceDetection()}"

            result_txt.text = resultOpt
        }

        saveAllLogToFile()
    }

    fun saveAllLogToFile(){
        val sd_main = File(path)
        var is_folder_created = true

        if(!sd_main.exists()){
            is_folder_created = sd_main.mkdir()
        }

        if(is_folder_created){
            if(!sd_folder.exists()){
                is_folder_created = sd_folder.mkdir()
            }
            if(is_folder_created){
                val dest = File(sd_folder,"log.txt")
                dest.createNewFile()
                Log.i("Log-File-Path", dest.absolutePath)
                try {
                    val cmd = "logcat -d -f " + dest.getAbsolutePath()
                    Runtime.getRuntime().exec(cmd)
                } catch (e: Exception) {Log.d("SaveLogFile", "Error Saving File: ${e}")}
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, MainActivity::class.java))
    }

    fun loadBitmap(context: Context, picName:String): Bitmap? {
        var b:Bitmap? = null
        var fis: FileInputStream
        try
        {
            fis = context.openFileInput(picName)
            b = BitmapFactory.decodeStream(fis)
        }
        catch (e: FileNotFoundException) {
            Log.d("FileNotFound", "file not found")
            e.printStackTrace()
        }
        catch (e: IOException) {
            Log.d("FileNotFound", "io exception")
            e.printStackTrace()
        }
        return b
    }

    private fun copyFile(filename:String) {
        val baseDir = Environment.getExternalStorageDirectory().getPath()
        val pathDir = baseDir + File.separator + filename
        val assetManager = this.getAssets()
        var inputStream:InputStream?
        var outputStream:OutputStream?
        try
        {
            inputStream = assetManager.open(filename)
            outputStream = FileOutputStream(pathDir)
            val buffer = ByteArray(1024)
            inputStream.use { input ->
                outputStream.use { fileOut ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0)
                            break
                        fileOut.write(buffer, 0, read)
                    }
                    fileOut.flush()
                    fileOut.close()
                }
            }
            inputStream.close()
        }
        catch (e:Exception) {
        }
    }

    private external fun returnFaceDetection(): String

    companion object{
        init {
            System.loadLibrary("native-lib")
        }
    }
}