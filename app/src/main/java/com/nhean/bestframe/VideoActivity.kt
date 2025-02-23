package com.nhean.bestframe

import android.app.Activity
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.nhean.bestframe.data.IDPerson
import kotlinx.android.synthetic.main.activity_video.*
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvException
import org.opencv.core.Mat
import org.opencv.videoio.VideoCapture
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.collections.ArrayList

class VideoActivity : AppCompatActivity() {

    var video_path = ArrayList<String>()
    var people = ArrayList<String>()
    var flag = true
    var best_image : Bitmap? = null
    var extract_thread : Thread? = null
    var start : Long = 0

    var person : IDPerson? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_video)

        select_video_btn.setOnClickListener() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.setType("video/*")

            startActivityForResult(intent, VIDEO_SELECTED_CODE)
        }

        // Start Video Frame Extracting Thread
        extract_thread = Thread(object:Runnable {
            override fun run() {
                for(i in 0 until video_path!!.size){
                    val matOrig = Mat()
                    val capture = VideoCapture(video_path!!.get(i))
                    val start_processing : Long = System.currentTimeMillis()

                    if (capture.isOpened()){
                        var capture_flag = true
                        while (capture_flag){
                            capture.read(matOrig)
                            if (!matOrig.empty()) {
                                var assesment_time : Long = 0

//                                Core.rotate(matOrig, matOrig, Core.ROTATE_90_CLOCKWISE);
//                                Core.rotate(matOrig, matOrig, Core.ROTATE_90_CLOCKWISE);
                                val varLapla = measureTimeMillis({duration -> assesment_time = duration}){
                                    returnVarLapla(matOrig.nativeObjAddr).toDouble()
                                }
                                Log.i("Laplacian-Var", varLapla.toString()+"\n Duration: $assesment_time ms")

                                if(varLapla > BEST_FRAME){
                                    var ocr_time : Long = 0
                                    best_image = matToBitmap(matOrig) // Convert Mat to Bitmap ImageOCR Android
                                    val resultStr = measureTimeMillis({duration -> ocr_time = duration}){
                                        detectTextFromBitmap(best_image) // Mobile Vision OCR
                                    }
                                    Log.d("ResultSTR", resultStr)

                                    if(!resultStr.isEmpty() && resultStr.length > MINIMUM_TEXT_LENGTH && validatedResult(resultStr)){
                                        val processing_time = System.currentTimeMillis() - start_processing

                                        Log.i("ValidatedSTR", person.toString())

                                        people.add(person.toString()+"\nProcessing Time: ${processing_time/1000}s")
                                        capture_flag = false
                                    }
                                }
                            }
                            else{
                                val processing_time = System.currentTimeMillis() - start_processing
                                people.add("Couldn't Get Information on ID Card!\nProcessing Time: ${processing_time/1000}s")
                                capture_flag = false
                            }
                        }
                    }
                }
                startActivityFromMainThread(people)
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == VIDEO_SELECTED_CODE){
            val clipData = data!!.getClipData()
            if (clipData != null)
            {
                for (i in 0 until clipData.getItemCount()){
                    val videoItemPath = getPath(clipData.getItemAt(i).uri)
                    Log.i("Video-File", videoItemPath)
                    video_path.add(videoItemPath.toString())
                }
            }
            Log.i("Video-Path", video_path.toString())

            llProgressBar.visibility = View.VISIBLE
            select_video_btn.visibility = View.GONE
            extract_thread?.start()
            start = System.currentTimeMillis()
        }
    }

    fun getPath(uri: Uri?):String? {
        var docId = DocumentsContract.getDocumentId(uri)
        var split = docId.split(":")
        var selection = "_id=?"
        var selectionArgs = arrayOf(split[1])
        var column = "_data"
        val projection = arrayOf(column)
        var content_uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val cursor = getContentResolver().query(content_uri, projection, selection, selectionArgs, null)
        if (cursor != null){
            cursor.moveToFirst()
            val column_index = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(column_index)
        }
        return null
    }

    private fun matToBitmap(imageMat : Mat) : Bitmap? {
        var bmp:Bitmap? = null
        try{
            bmp = Bitmap.createBitmap(imageMat.cols(), imageMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(imageMat, bmp)

        }catch (e: CvException) {
            Log.d("Exception", e.toString())
        }
        return bmp
    }

    fun detectTextFromBitmap(best_image : Bitmap?) : String{
        val stringBuilder = StringBuilder()
        try{
            val recognizer = TextRecognizer.Builder(baseContext).build()
            val frame = Frame.Builder().setBitmap(best_image).build()
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
            var id = firstPart.dropLast(1).trim().replace(" ", "")
            id = id.toLowerCase().replace("o","0")
            if(id.length<6){
                valid = false
                Log.e("Error ID: ", "ID Length Not Match")
            }
            else{
                try {
                    val num = Integer.parseInt(id)
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
                    .trim().take(7)
                val num = Integer.parseInt(dob)
                dob = dob.dropLast(1)
            } catch (e: NumberFormatException) {
                valid = false
                Log.e("Error DOB: ", dob)
            }

//          Validate Gender
            var gender = ""
            if(secondPart.contains("m", ignoreCase = true)){
                gender = "Male"
            }
            else if(secondPart.contains("f", ignoreCase = true)){
                gender = "Female"
            }
            else {
                Log.e("Error Gender: ", "Gender Not Found")
                valid = false
            }

//          Validate Name
            val removeNumber = Regex("[0-9]") // Remove Number From Name
            var name_person = removeNumber.replace(result!!.substringAfter("<").substringAfter("KHM")
                .replace("<<", " ")
                .replace("<", " ")
                .replace("«"," ")
                .replace(" K "," ")
                .replace("\n", ""), "")
                .trim().toUpperCase()

            if(!result!!.substringBefore("KHM").toUpperCase().contains(name_person)){
                Log.e("Error Name: ", name_person)
                valid = false
            }

            person = IDPerson(id, name_person, dob, gender)
        }
        else {
            valid = false
        }

        return valid
    }

    fun saveFile(context: Context, b:Bitmap, picName:String) {
        var fos : FileOutputStream
        try
        {
            fos = context.openFileOutput(picName, Context.MODE_PRIVATE)
            b.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        catch (e: FileNotFoundException) {
            Log.d("IO", "file not found")
            e.printStackTrace()
        }
        catch (e: IOException) {
            Log.d("IO", "io exception")
            e.printStackTrace()
        }
    }

    fun startActivityFromMainThread(id_number: String, dob: String, gender: String, name: String, image_name: String, assesment_time : Long, ocr_time : Long) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object:Runnable {
            override fun run() {
                val intent = Intent(this@VideoActivity, ResultActivity::class.java)
                intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }

                intent.putExtra("idNumber", id_number)
                intent.putExtra("dob", dob)
                intent.putExtra("gender", gender)
                intent.putExtra("name", name)
                intent.putExtra("image_name", image_name)
                intent.putExtra("assesment_time", assesment_time.toString())
                intent.putExtra("ocr_time", ocr_time.toString())
                intent.putExtra("processing_time", (System.currentTimeMillis() - start).toString())

                startActivity(intent)
            }
        })
    }

    fun startActivityFromMainThread(people : ArrayList<String>){
        val handler = Handler(Looper.getMainLooper())
        handler.post(object:Runnable {
            override fun run() {
                val intent = Intent(this@VideoActivity, BenchMarkActivity::class.java)
                intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                intent.putExtra("people", people)
                intent.putExtra("processing_time", ((System.currentTimeMillis() - start)/1000).toString())
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

    private external fun returnVarLapla(matAddr: Long): String

    companion object{
        private const val VIDEO_SELECTED_CODE = 10
        private const val BEST_FRAME = 150
        private const val MINIMUM_TEXT_LENGTH = 30

        init {
            System.loadLibrary("native-lib")
        }
    }
}