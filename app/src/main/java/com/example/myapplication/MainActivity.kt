package com.example.myapplication

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private var moreBottomSheet: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.navigation_home -> selectedFragment = HomeFragment()
                R.id.navigation_cpu -> selectedFragment = CpuFragment()
                R.id.navigation_gpu -> selectedFragment = GpuFragment()
                R.id.navigation_ram -> selectedFragment = RamFragment()
                R.id.navigation_more -> {
                    showMoreBottomSheet()
                    return@setOnItemSelectedListener false
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, selectedFragment).commit()
            }
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.navigation_home
        }
    }

    private fun showMoreBottomSheet() {
        if (moreBottomSheet == null) {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_more, null)
            moreBottomSheet = BottomSheetDialog(this)
            moreBottomSheet?.setContentView(bottomSheetView)

            bottomSheetView.findViewById<LinearLayout>(R.id.option_storage).setOnClickListener {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, StorageFragment()).commit()
                moreBottomSheet?.dismiss()
            }

            bottomSheetView.findViewById<LinearLayout>(R.id.option_battery).setOnClickListener {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, BatteryFragment()).commit()
                moreBottomSheet?.dismiss()
            }
        }
        moreBottomSheet?.show()
    }
}
