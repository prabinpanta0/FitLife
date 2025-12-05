package com.example.fitlife.ui.splash

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.fitlife.MainActivity
import com.example.fitlife.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen (for Android 12+)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animate logo and text
        val logo = findViewById<ImageView>(R.id.ivLogo)
        val appName = findViewById<TextView>(R.id.tvAppName)
        val tagline = findViewById<TextView>(R.id.tvTagline)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        logo.startAnimation(fadeIn)
        appName.startAnimation(slideUp)
        tagline.startAnimation(slideUp)

        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashOnScreen = false
            val intent = Intent(this, MainActivity::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeCustomAnimation(
                    this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            finish()
        }, 2000)
    }
}
