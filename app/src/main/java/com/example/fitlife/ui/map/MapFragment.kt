package com.example.fitlife.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.GeoLocation
import com.example.fitlife.data.model.LocationType
import com.example.fitlife.utils.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var etSearch: EditText
    private lateinit var btnMyLocation: View
    private lateinit var rvLocations: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var tvLocationCount: TextView
    private lateinit var fabAddLocation: FloatingActionButton

    private lateinit var locationAdapter: LocationAdapter
    private lateinit var sessionManager: SessionManager

    private val locationRepository by lazy {
        (requireActivity().application as FitLifeApplication).locationRepository
    }

    private var currentLatLng: LatLng? = null

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initViews(view)
        setupRecyclerView()
        setupBottomSheet(view)
        setupClickListeners()
        setupMap()
        loadLocations()
    }

    private fun initViews(view: View) {
        etSearch = view.findViewById(R.id.etSearch)
        btnMyLocation = view.findViewById(R.id.btnMyLocation)
        rvLocations = view.findViewById(R.id.rvLocations)
        llEmptyState = view.findViewById(R.id.llEmptyState)
        tvLocationCount = view.findViewById(R.id.tvLocationCount)
        fabAddLocation = view.findViewById(R.id.fabAddLocation)
    }

    private fun setupRecyclerView() {
        locationAdapter = LocationAdapter(
            onLocationClick = { location -> moveToLocation(location) },
            onNavigateClick = { location -> navigateToLocation(location) },
            onDeleteClick = { location -> showDeleteConfirmation(location) }
        )

        rvLocations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = locationAdapter
        }
    }

    private fun setupBottomSheet(view: View) {
        val bottomSheet = view.findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupClickListeners() {
        btnMyLocation.setOnClickListener {
            checkLocationPermissionAndMoveToCurrentLocation()
        }

        fabAddLocation.setOnClickListener {
            showAddLocationDialog()
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Set default location (can be changed based on user preference)
        val defaultLocation = LatLng(-34.0, 151.0) // Sydney as default
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        // Enable zoom controls
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false // We use our own button

        // Check location permission
        checkLocationPermissionAndEnableMyLocation()

        // Set map click listener for adding locations
        map.setOnMapLongClickListener { latLng ->
            showAddLocationDialogWithCoordinates(latLng)
        }
    }

    private fun checkLocationPermissionAndEnableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationPermissionAndMoveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            moveToCurrentLocation()
        } else {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        try {
            if (::map.isInitialized) {
                map.isMyLocationEnabled = true
                moveToCurrentLocation()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun moveToCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLatLng = LatLng(it.latitude, it.longitude)
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 15f)
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun loadLocations() {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) return

        viewLifecycleOwner.lifecycleScope.launch {
            locationRepository.getLocationsByUserId(userId).collectLatest { locations ->
                if (locations.isEmpty()) {
                    showEmptyState()
                } else {
                    showLocationsList()
                    locationAdapter.submitList(locations)
                    tvLocationCount.text = getString(R.string.locations_count_format, locations.size)

                    // Add markers to map
                    if (::map.isInitialized) {
                        map.clear()
                        locations.forEach { location ->
                            addMarkerForLocation(location)
                        }
                    }
                }
            }
        }
    }

    private fun addMarkerForLocation(location: GeoLocation) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(location.name)
                .snippet(location.locationType.displayName)
        )
    }

    private fun showEmptyState() {
        rvLocations.visibility = View.GONE
        llEmptyState.visibility = View.VISIBLE
    }

    private fun showLocationsList() {
        rvLocations.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE
    }

    private fun moveToLocation(location: GeoLocation) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun navigateToLocation(location: GeoLocation) {
        val uri = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to web maps
            val webUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}"
            )
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun showDeleteConfirmation(location: GeoLocation) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Location")
            .setMessage("Are you sure you want to delete '${location.name}'?")
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteLocation(location)
            }
            .show()
    }

    private fun deleteLocation(location: GeoLocation) {
        viewLifecycleOwner.lifecycleScope.launch {
            locationRepository.deleteLocation(location)
            Toast.makeText(requireContext(), getString(R.string.location_deleted), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddLocationDialog() {
        currentLatLng?.let {
            showAddLocationDialogWithCoordinates(it)
        } ?: run {
            Toast.makeText(
                requireContext(),
                "Please enable location or long-press on map to add a location",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showAddLocationDialogWithCoordinates(latLng: LatLng) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_location, null)

        val etLocationName = dialogView.findViewById<TextInputEditText>(R.id.etLocationName)
        val etAddress = dialogView.findViewById<TextInputEditText>(R.id.etAddress)
        val spinnerType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.spinnerLocationType)

        // Setup location type dropdown
        val locationTypes = LocationType.values().map { getLocationTypeDisplay(it) }
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, locationTypes)
        spinnerType.setAdapter(typeAdapter)
        spinnerType.setText(locationTypes[0], false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_location))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etLocationName.text?.toString()?.trim() ?: ""
                val address = etAddress.text?.toString()?.trim() ?: ""
                val typeIndex = locationTypes.indexOf(spinnerType.text.toString())
                val type = if (typeIndex >= 0) LocationType.values()[typeIndex] else LocationType.OTHER

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.please_enter_location_name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                saveLocation(name, address, type, latLng)
            }
            .show()
    }

    private fun saveLocation(name: String, address: String, type: LocationType, latLng: LatLng) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) return

        val location = GeoLocation(
            userId = userId,
            name = name,
            address = address,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            locationType = type
        )

        viewLifecycleOwner.lifecycleScope.launch {
            locationRepository.insertLocation(location)
            Toast.makeText(requireContext(), getString(R.string.location_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationTypeDisplay(type: LocationType): String {
        return when (type) {
            LocationType.GYM -> getString(R.string.gym)
            LocationType.YOGA_STUDIO -> getString(R.string.yoga_studio)
            LocationType.PARK -> getString(R.string.park)
            LocationType.HOME -> getString(R.string.home)
            LocationType.POOL -> getString(R.string.swimming_pool)
            LocationType.OTHER -> getString(R.string.other)
        }
    }
}
