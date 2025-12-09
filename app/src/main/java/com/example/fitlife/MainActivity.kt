package com.example.fitlife

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.fitlife.databinding.ActivityMainBinding
import com.example.fitlife.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val PREFS_NAME = "fitlife_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    // Permission launcher for multiple permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled, continue with app
        checkAuthState()
    }

    // Top-level destinations (no back button shown)
    private val topLevelDestinations = setOf(
        R.id.HomeFragment,
        R.id.RoutinesFragment,
        R.id.ChecklistFragment,
        R.id.MapFragment,
        R.id.ProfileFragment,
        R.id.LoginFragment,
        R.id.RegisterFragment
    )

    // Destinations where bottom nav should be hidden
    private val hideBottomNavDestinations = setOf(
        R.id.LoginFragment,
        R.id.RegisterFragment,
        R.id.AddRoutineFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavView.setupWithNavController(navController)

        // Check if first launch and request permissions
        if (isFirstLaunch()) {
            showPermissionExplanationDialog()
        } else {
            // Check if user is logged in and navigate accordingly
            checkAuthState()
        }

        // Listen for navigation changes to show/hide bottom nav and FAB
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Hide bottom nav for auth screens
            if (destination.id in hideBottomNavDestinations) {
                binding.bottomNavView.visibility = View.GONE
                binding.fab.visibility = View.GONE
                supportActionBar?.hide()
            } else {
                binding.bottomNavView.visibility = View.VISIBLE
                supportActionBar?.show()

                // Show FAB only on Home and Routines screens
                if (destination.id == R.id.HomeFragment || destination.id == R.id.RoutinesFragment) {
                    binding.fab.visibility = View.VISIBLE
                } else {
                    binding.fab.visibility = View.GONE
                }
            }
        }

        // FAB click - add new routine
        binding.fab.setOnClickListener {
            when (navController.currentDestination?.id) {
                R.id.HomeFragment -> navController.navigate(R.id.action_home_to_addRoutine)
                R.id.RoutinesFragment -> navController.navigate(R.id.action_routines_to_addRoutine)
            }
        }
    }

    private fun checkAuthState() {
        if (sessionManager.isLoggedIn()) {
            // User is logged in, navigate to home using the proper action
            val currentDest = navController.currentDestination?.id
            if (currentDest == R.id.LoginFragment) {
                navController.navigate(R.id.action_LoginFragment_to_HomeFragment)
            }
        }
        // If not logged in, start destination (LoginFragment) will be shown
    }

    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    private fun setFirstLaunchComplete() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("FitLife needs some permissions to provide the best experience:\n\n" +
                    "• Location - To show nearby gyms on the map\n" +
                    "• Camera - To set your profile picture\n" +
                    "• SMS & Contacts - To share equipment lists\n" +
                    "• Notifications - To remind you about workouts")
            .setPositiveButton("Continue") { _, _ ->
                requestAppPermissions()
            }
            .setNegativeButton("Skip") { _, _ ->
                setFirstLaunchComplete()
                checkAuthState()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestAppPermissions() {
        setFirstLaunchComplete()
        
        val permissionsToRequest = mutableListOf<String>()
        
        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        // SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        
        // Contacts permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAuthState()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
