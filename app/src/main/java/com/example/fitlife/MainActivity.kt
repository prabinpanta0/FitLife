package com.example.fitlife

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.fitlife.databinding.ActivityMainBinding
import com.example.fitlife.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sessionManager: SessionManager

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

        navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavView.setupWithNavController(navController)

        // Check if user is logged in and navigate accordingly
        checkAuthState()

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
            // User is logged in, navigate to home
            if (navController.currentDestination?.id == R.id.LoginFragment) {
                navController.navigate(R.id.action_LoginFragment_to_HomeFragment)
            }
        }
        // If not logged in, start destination (LoginFragment) will be shown
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
