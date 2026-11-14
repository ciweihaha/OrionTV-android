package com.ciweihaha.oriontv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.ciweihaha.oriontv.databinding.ActivityMainBinding
import com.ciweihaha.oriontv.ui.home.HomeFragment

/**
 * Launcher activity. Hosts the Leanback home browse fragment.
 * The 48dp safe margin (T9) lives in the home rows / detail layouts,
 * not here, so the Leanback headers keep their standard TV placement.
 */
class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, HomeFragment())
                .commit()
        }
    }
}
