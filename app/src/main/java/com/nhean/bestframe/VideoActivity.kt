package com.nhean.bestframe

import android.app.Activity
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toFile
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.nhean.bestframe.data.IDPerson
import kotlinx.android.synthetic.main.activity_video.*
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvException
import org.opencv.core.Mat
import org.opencv.videoio.VideoCapture
import java.io.*
import kotlin.collections.ArrayList

class VideoActivity : AppCompatActivity() {

//  Processing Variable
    var video_path = ArrayList<String>()
    var people = ArrayList<String>()
    var best_image : Bitmap? = null
    var extract_thread : Thread? = null
    var start : Long = 0

//  Log File Variable
    val time_stamp = System.currentTimeMillis()
    val path = Environment.getExternalStorageDirectory().toString()+"/IDScanner"
    val sd_folder = File(path, "session_${time_stamp}")

//  IDPerson Object
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

        Log.d("PathToExternal", Environment.getExternalStorageDirectory().toString())

        // Start Video Frame Extracting Thread
        extract_thread = Thread(object:Runnable {
            override fun run() {
                for(i in 0 until video_path!!.size){
                    val matOrig = Mat()
                    var capture = VideoCapture(video_path!!.get(i))
                    val start_processing : Long = System.currentTimeMillis()

                    if (capture.isOpened()){
                        extract_text_from_frame(capture, matOrig, start_processing, i)
                    }
                    else{
                        people.add("Error Video Format-${video_path.get(i)}")
                    }
                    capture.release()
                }
                startActivityFromMainThread(people)
            }

            fun extract_text_from_frame(capture : VideoCapture, matOrig : Mat, start_processing: Long, i : Int){
                var capture_flag = true
                while (capture_flag){
                    capture.read(matOrig)
                    if (!matOrig.empty()) {
                        var assesment_time : Long = 0

//                                For Required Rotate Video
//                        Core.rotate(matOrig, matOrig, Core.ROTATE_90_CLOCKWISE)
//                        Core.rotate(matOrig, matOrig, Core.ROTATE_90_CLOCKWISE)
//                        Core.rotate(matOrig, matOrig, Core.ROTATE_90_CLOCKWISE)

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
                            Log.d("ResultSTR", resultStr+"\nDuration: ${ocr_time}ms")

                            if(!resultStr.isEmpty() && resultStr.length > MINIMUM_TEXT_LENGTH && validatedResult(resultStr)){
                                val processing_time = System.currentTimeMillis() - start_processing
                                people.add(person.toString()+"\nProcessing Time: ${processing_time/1000}s\n\n")
                                capture_flag = false

                                saveLogFile(person, processing_time, ocr_time, assesment_time, video_path.get(i))
                                Log.i("ValidatedSTR", person.toString())
                            }
                        }
                    }
                    else{
                        val processing_time = System.currentTimeMillis() - start_processing
                        people.add("Couldn't Get Information on ID Card!\nProcessing Time: ${processing_time/1000}s\n\n")
                        capture_flag = false

                        saveLogFile(person, processing_time, 0, 0, video_path.get(i))
                    }
                }
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
                    .replace("k", "")
                    .replace("\n", "")
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
                .replace(" K"," ")
                .replace(" k"," ")
                .replace("\n", ""), "")
                .trim().toUpperCase()

            if(!result!!.substringBefore("KHM").toUpperCase().replace(" ", "").contains(name_person.replace(" ", ""))){
                Log.e("Error Name: ", name_person)
                valid = false
            }
            if(name_person.length < 5){
                Log.e("Error Name: ", "Too Less Character to be a person name - ${name_person}")
                valid = false
            }

            person = IDPerson(id, name_person, dob, gender)
        }
        else {
            valid = false
        }

        return valid
    }

    fun saveLogFile(person : IDPerson?, processing_time: Long, ocr_time: Long, assesment_time: Long, file_path: String){
        val sd_main = File(path)
        var success = true

        if (!sd_main.exists()) {
            success = sd_main.mkdir()
        }
        if (success) {
            if(!sd_folder.exists()){
                success = sd_folder.mkdir()
            }
            if (success){
                val dest = File(path,"/session_${time_stamp}/${file_path.split("/").last()}.txt")
                try {
                    val text = "${person.toString()}\nProcessing Time: ${processing_time/1000}s\nVideo File Path: ${file_path}"
                    PrintWriter(dest).use { out -> out.println(text) }
                } catch (e: Exception) {Log.d("SaveLogFile", "Error Saving File: ${e}")}
            }
            else{Log.d("SaveLogFile", "Error Creating Folder Session")}
        }
        else{Log.d("SaveLogFile", "Error Creating Folder IDScanner")}
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
        private const val BEST_FRAME = 50
        private const val MINIMUM_TEXT_LENGTH = 30

        init {
            System.loadLibrary("native-lib")
        }
    }
}