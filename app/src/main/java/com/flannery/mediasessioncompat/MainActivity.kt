package com.flannery.mediasessioncompat

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.flannery.mediasessioncompat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var mRoundAnimator: ValueAnimator? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        MusicService.startMySelf(this)
        binding.btnPlay.setOnClickListener {
            MusicService.play(this@MainActivity)
            startAnim()
        }
        binding.btnStop.setOnClickListener {
            MusicService.stop(this@MainActivity)
            stopAnim()
        }
    }

    private fun startAnim() {
        stopAnim()
        val max = MusicService.MAX_PROGRESS.toInt()
        mRoundAnimator = ValueAnimator.ofInt(0, max)
        mRoundAnimator?.interpolator = LinearInterpolator()
        mRoundAnimator?.addUpdateListener {
            binding.seekBar.progress = it.animatedValue as Int
            MusicService.setProgress(this@MainActivity, binding.seekBar.progress)
        }
        mRoundAnimator?.duration = 15000
        mRoundAnimator?.repeatCount = 10
        mRoundAnimator?.start()
    }

    private fun stopAnim() {
        mRoundAnimator?.end()
    }
}