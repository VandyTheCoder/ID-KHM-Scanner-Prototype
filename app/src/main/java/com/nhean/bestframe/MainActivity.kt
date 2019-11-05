package com.nhean.bestframe

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import android.os.SystemClock
import android.util.Base64
import android.widget.Chronometer
import android.widget.Toast
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import org.opencv.android.*
import org.opencv.core.*
import java.io.*
import java.lang.Integer.parseInt
import java.util.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2{

    private var id_number = ""
    private var dob = ""
    private var gender = ""
    private var name = ""

    private lateinit var chronometer : Chronometer

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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

//      Init OpenCV Camera
        mOpenCvCameraView = findViewById<CameraBridgeViewBase>(R.id.main_surface)
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)

//      Init Stop Timer
        chronometer = findViewById<Chronometer>(R.id.stop_watch_chronometer)
        chronometer.setOnChronometerTickListener(object: Chronometer.OnChronometerTickListener {
            override fun onChronometerTick(chronometerChanged:Chronometer) {
                chronometer = chronometerChanged
            }
        })
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mOpenCvCameraView!!.setCameraPermissionGranted()
                } else {
                    val message = "Camera permission was not granted"
                    Log.e(TAG, message)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Log.e(TAG, "Unexpected permission request")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        chronometer.stop()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView!!.disableView()
    }

    override fun onResume() {
        super.onResume()
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chronometer.stop()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView!!.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val mat = frame.rgba()
        val base64Img = returnGrayImg(mat.nativeObjAddr)

        if(base64Img.split(",")[1].toDouble() > BEST_IMAGE){
            val resultStr = detectTextFromBitmap(base64ToBitmap(base64Img.split(",")[0]))
            if(!resultStr.isEmpty() && resultStr.length > MINIMUM_TEXT_LENGTH && validatedResult(resultStr)){
                val intent = Intent(this, ResultActivity::class.java)

                val image = "${UUID.randomUUID()}.jpg"
                saveFile(baseContext, base64ToBitmap(base64Img.split(",")[0]), image, "$resultStr\nTresshold: ${base64Img.split(",")[1]}")

                chronometer.stop()
                Log.i("ResultText", resultStr)

                intent.putExtra("idNumber", this.id_number)
                intent.putExtra("dob", this.dob)
                intent.putExtra("gender", this.gender)
                intent.putExtra("name", this.name)
                intent.putExtra("image_name", image)
                startActivity(intent)
            }
        }
        return mat
    }

    fun base64ToBitmap(base64_encoded: String) : Bitmap{
        val decodedString = Base64.decode(base64_encoded, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size);
    }

    fun detectTextFromBitmap(bitmap : Bitmap) : String{
        val recognizer = TextRecognizer.Builder(this@MainActivity).build()
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val sparseArray = recognizer.detect(frame)
        val stringBuilder = StringBuilder()
        for (i in 0 until sparseArray.size()){
            val tx = sparseArray.get(i)
            val str = tx.getValue()
            stringBuilder.append(str)
        }
        return stringBuilder.toString()
    }

    fun validatedResult(result : String) : Boolean{
        var valid = true

        if(result.contains("khm", ignoreCase = true)){
//          Divide Result Into Two Parts
//          First part contained the ID Number
//          Second part contained the Dob, Gender, and Name
            val firstPart = result!!.substringBefore("<").substringAfter("KHM")
            val secondPart = result!!.substringAfter("<").substringBefore("KHM")

//          Validate ID Number
            var id = firstPart.dropLast(1).trim().replace(" ", "")
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
                dob = secondPart!!.replace("<","").replace(" ", "").trim().take(6)
                val num = parseInt(dob)
                this.dob = dob
            } catch (e: NumberFormatException) {
                valid = false
                Log.e("Error DOB: ", dob)
            }

//          Validate Gender
            if(secondPart.replace("<","").contains("m", ignoreCase = true)){
                this.gender = "Male"
            }
            else if(secondPart.replace("<","").contains("f", ignoreCase = true)){
                this.gender = "Female"
            }
            else {
                Log.e("Error Gender: ", "Gender Not Found")
                valid = false
            }

//          Validate Name
            val removeNumber = Regex("[0-9]")
            this.name = removeNumber.replace(result!!.substringAfter("<").substringAfter("KHM").replace("<<", " ").replace("<", "").replace("\n", ""), "").trim().toUpperCase()
            val ptrn = Pattern.compile("(\\w+)\\s+(\\w+)");
            val matcher = ptrn.matcher(this.name);
            if(!matcher.find()){
                valid = false
            }
        }
        else {
            valid = false
        }

        return valid
    }

    fun saveFile(context:Context, b:Bitmap, picName:String, resultStr: String) {
        var fos : FileOutputStream
        try
        {
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

    private external fun returnGrayImg(matAddr: Long): String

    companion object {
        private const val BEST_IMAGE = 200
        private const val MINIMUM_TEXT_LENGTH = 25
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST = 1
    }
}
