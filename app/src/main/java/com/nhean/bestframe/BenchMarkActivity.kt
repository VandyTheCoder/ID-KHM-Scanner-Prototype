package com.nhean.bestframe

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_bench_mark.*

class BenchMarkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bench_mark)

        val bundle :Bundle ?=intent.extras
        if (bundle!=null){
            val people = bundle.getStringArrayList("people")
            val processing_time = bundle.getString("processing_time")

            result_txt.text = people.toString()+"\n\nTotal Time: ${processing_time}s"
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, VideoActivity::class.java))
    }
}