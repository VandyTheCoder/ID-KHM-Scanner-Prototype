package com.nhean.bestframe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import android.widget.Toast
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.nhean.bestframe.data.ImageOCR
import com.nhean.bestframe.data.ImageRaw
import kotlinx.android.synthetic.main.activity_video.*
import kotlinx.android.synthetic.main.progress_layout.view.*
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.*
import java.lang.Integer.parseInt
import java.util.*
import java.util.concurrent.Semaphore


class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2{
    var flag = true
    var start : Long = 0
    var sema = Semaphore(1)

    var id_number = ""
    var dob = ""
    var gender = ""
    var name_person = ""

    var imageRaw : ImageRaw? = null
    var image_name : String = ""

    private var mOpenCvCameraView: CameraBridgeViewBase? = null

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    System.loadLibrary("native-lib")
                    mOpenCvCameraView!!.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//      Asking Permission
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST
        )

//      Make Full Screen Camera
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

//      Set Content View Layout
        setContentView(R.layout.activity_main)

//      Start Timer
        start = System.currentTimeMillis()

//      Init OpenCV Camera
        mOpenCvCameraView = findViewById<CameraBridgeViewBase>(R.id.main_surface)
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

//      Start Processing Thread Thread
        Thread(object : Runnable {
            override fun run() {
                while (flag) {
                    Log.d("$TAG-Thread", "Thread is running")
                    if (imageRaw != null && imageRaw!!.image.height() > 0 && imageRaw!!.image.width() > 0) {
                        try {
                            sema.acquire()
                            var image_raw = imageRaw!!.copy()
                            sema.release()

                            var lapla_time: Long = 0

//                          Calculate Laplacain Var
                            var lapla_var = measureTimeMillis({ duration -> lapla_time = duration }) {
                                returnVarLapla(image_raw!!.image_addr).toDouble() // Native Code : Calculated Lapalcain
                            }
                            Log.i("$TAG-Lapla-Var", "${lapla_var}\n\nDuration: ${lapla_time}ms")

//                          Find The Best Frame
                            if (lapla_var > BEST_IMAGE) {
                                val imageOCR = ImageOCR(matToBitmap(image_raw!!.image), lapla_var, lapla_time)
                                var ocr_time: Long = 0

//                              OCR on Best Image
                                val resultStr = measureTimeMillis({ duration -> ocr_time = duration }) {
                                    detectTextFromBitmap(imageOCR!!.image) // Mobile Vision OCR
                                }
                                Log.i("$TAG-ResultSTR", resultStr + ",\n\nDurations: $ocr_time ms")

//                              Validate the Result from OCR
                                if (!resultStr.isEmpty() && resultStr.length > MINIMUM_TEXT_LENGTH && validatedResult(resultStr)){
                                    flag = false // Set flag to false after find the valid result string
                                    Log.i("${TAG}-Validated",resultStr + ",\n\nOCR Time: $ocr_time ms\nAssesment Time: ${imageOCR!!.assesment_time} ms")

//                                  Run Task in MainThread
                                    try {
                                        this@MainActivity.runOnUiThread(java.lang.Runnable {
                                            mOpenCvCameraView!!.disableView()
                                            mOpenCvCameraView!!.visibility = SurfaceView.GONE
                                            llProgressBar.visibility = View.VISIBLE
                                            llProgressBar.pbText.text = "Saving Image!"
                                        })
                                    }
                                    catch (e : Exception){
                                        Log.e("${TAG}-MainThread", e.toString())
                                    }

                                    // Save Image as File
                                    image_name = "${UUID.randomUUID()}.jpg"
                                    saveFile(baseContext, imageOCR!!.image!!, image_name)

                                    //Start New Activity From Thread
                                    startActivityInMainThread(
                                        id_number,
                                        dob,
                                        gender,
                                        name_person,
                                        image_name,
                                        lapla_var,
                                        imageOCR!!.assesment_time,
                                        ocr_time
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("$TAG-Error", e.toString())
                        }
                    }
                }
            }
        }).start()
    }


    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<String>,grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mOpenCvCameraView!!.setCameraPermissionGranted()
                } else {
                    val message = "Permission was not granted"
                    Log.e(TAG, message)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Log.e(TAG, "Unexpected permission request")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var id = item.itemId
        if(id == R.id.menu_video){
            startActivity(Intent(this, VideoActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        flag = true
    }

    override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView!!.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageRaw = null
        if (mOpenCvCameraView != null) mOpenCvCameraView!!.disableView()
    }

    override fun onStop() {
        super.onStop()
        imageRaw = null
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        try{
            sema.acquire()
            imageRaw = ImageRaw(frame.rgba(), frame.gray().nativeObjAddr)
            sema.release()
        }
        catch (e : Exception){
            Log.e("$TAG-OpenCV-FR", e.toString())
        }
        return frame.rgba()
    }

    private fun matToBitmap(imageMat : Mat) : Bitmap? {
        var bmp:Bitmap? = null
        try{
            bmp = Bitmap.createBitmap(imageMat.width(), imageMat.height(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(imageMat, bmp)
        }catch (e:CvException) {
            Log.d("Exception", e.toString())
        }
        return bmp
    }

    fun detectTextFromBitmap(bitmap : Bitmap?) : String{
        val stringBuilder = StringBuilder()
        try{
            val recognizer = TextRecognizer.Builder(baseContext).build()
            val frame = Frame.Builder().setBitmap(bitmap).build()
            val sparseArray = recognizer.detect(frame)
            for (i in 0 until sparseArray.size()){
                val tx = sparseArray.get(i)
                val str = tx.getValue()
                stringBuilder.append(str)
            }
            Log.i("ResultInSparseArray", sparseArray.toString())

        }catch (e: Exception){
            Log.d("ImageOCR", "ImageOCR Not Found")
        }
        return stringBuilder.toString()
    }

    fun validatedResult(result : String) : Boolean{
        var valid = true
//      Validate For Khmer ID
        if(result.contains("khm", ignoreCase = true)){
//          Divide Result Into Two Parts
//          First part contained the ID Number
//          Second part contained the Dob, Gender, and Expired Date
            val firstPart = result!!.substringBefore("<").substringAfter("KHM")
            val secondPart = result!!.substringAfter("<").substringBefore("KHM")

//          Validate ID Number
            var id = firstPart.dropLast(1).replace(" ", "")
            id = id.toLowerCase().replace("o","0")
            if(id.length<6){
                valid = false
                Log.e("Error ID: ", "ID Length Not Match")
            }
            else{
                try {
                    val num = parseInt(id)
                    this.id_number = id
                } catch (e: NumberFormatException) {
                    valid = false
                    Log.e("Error ID: ", "ID Contain Letter")
                }
            }

//          Validate Dob
            var dob = ""
            try {
                dob = secondPart!!
                    .replace("<","")
                    .replace("«","")
                    .replace(" ", "")
                    .replace("o","0")
                    .replace("O","0")
                    .replace("K", "")
                    .replace("k", "")
                    .replace("e", "")
                    .replace("z", "")
                    .replace("\n", "")
                    .trim().take(7)

                val num = parseInt(dob)
                this.dob = dob.dropLast(1)
            } catch (e: Exception) {
                valid = false
                Log.e("Error DOB: ", dob+"\n${e.toString()}")
            }

//          Validate Gender
            if(secondPart.contains("m", ignoreCase = true)){
                this.gender = "Male"
            }
            else if(secondPart.contains("f", ignoreCase = true)){
                this.gender = "Female"
            }
            else {
                Log.e("Error Gender: ", "Gender Not Found")
                valid = false
            }

//          Validate Name
            val removeNumber = Regex("[0-9]") // Remove Number From Name
            this.name_person = removeNumber.replace(result!!.substringAfter("<").substringAfter("KHM")
                .replace("<<", " ")
                .replace("<", " ")
                .replace("«"," ")
                .replace(" K"," ")
                .replace(" k"," ")
                .replace("\n", ""), "")
                .trim().toUpperCase()

            if(!result!!.substringBefore("KHM").toUpperCase().replace(" ","").contains(this.name_person.replace(" ",""))){
                Log.e("Error Name: ", "${this.name_person}")
                valid = false
            }
        }
        else {
            valid = false
        }
        return valid
    }

    fun saveFile(context:Context, b:Bitmap, picName:String) {
        var fos : FileOutputStream
        try{
            fos = context.openFileOutput(picName, Context.MODE_PRIVATE)
            b.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        catch (e: FileNotFoundException) {
            Log.d(TAG, "file not found")
            e.printStackTrace()
        }
        catch (e:IOException) {
            Log.d(TAG, "io exception")
            e.printStackTrace()
        }
    }

    fun startActivityInMainThread(id_number: String, dob: String, gender: String, name: String, image_name: String, lapla_var: Double, image_assesment_time: Long, ocr_time: Long) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object:Runnable {
            override fun run() {
                val intent = Intent(this@MainActivity, ResultActivity::class.java)
                intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }

                intent.putExtra("idNumber", id_number)
                intent.putExtra("dob", dob)
                intent.putExtra("gender", gender)
                intent.putExtra("name", name)
                intent.putExtra("image_name", image_name)
                intent.putExtra("lapla_var", lapla_var.toString())
                intent.putExtra("assesment_time", image_assesment_time.toString())
                intent.putExtra("ocr_time", ocr_time.toString())
                intent.putExtra("processing_time", (System.currentTimeMillis() - start).toString())

                startActivity(intent)
            }
        })
    }

    inline fun <T> measureTimeMillis(loggingFunction: (Long) -> Unit, function: () -> T): T {
        val startTime = System.currentTimeMillis()
        val result: T = function.invoke()
        loggingFunction.invoke(System.currentTimeMillis() - startTime)
        return result
    }

    fun saveLogFile(imgOCR : ImageOCR?, ocr_time: Long, resultSTR : String){
        val output =    "Assessments Time: ${imgOCR!!.assesment_time}ms\n"+
                        "OCR Time: ${ocr_time}ms\n"+
                        "Laplacain: ${imgOCR!!.lapla_var}"
                        "------------------------\n"+
                        "Output: \n"+
                        "$resultSTR"

        val logFileName = "Session_Log_${System.currentTimeMillis()}.txt"
    }

    private external fun returnVarLapla(matAddr: Long): String

    companion object {
        private const val TAG = "MainActivity"
        private const val BEST_IMAGE = 70
        private const val MINIMUM_TEXT_LENGTH = 25
        private const val PERMISSION_REQUEST = 1
    }
}
