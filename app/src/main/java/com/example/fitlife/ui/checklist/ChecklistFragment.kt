package com.example.fitlife.ui.checklist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.FitLifeApplication
import com.example.fitlife.R
import com.example.fitlife.data.model.Equipment
import com.example.fitlife.data.model.EquipmentCategory
import com.example.fitlife.utils.PermissionManager
import com.example.fitlife.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChecklistFragment : Fragment() {

    private lateinit var cardStatus: MaterialCardView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSubtitle: TextView
    private lateinit var progressChecklist: CircularProgressIndicator
    private lateinit var btnSendSms: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var rvChecklist: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var btnGoToRoutines: MaterialButton

    private lateinit var checklistAdapter: ChecklistCategoryAdapter
    private lateinit var sessionManager: SessionManager
    
    // Current phone number edit text reference for contact picker
    private var currentPhoneEditText: TextInputEditText? = null

    private val workoutRepository by lazy {
        (requireActivity().application as FitLifeApplication).workoutRepository
    }

    // Track checked items locally (in a real app, this could be persisted)
    private val checkedItems = mutableSetOf<Long>()
    private var allEquipment: List<Equipment> = emptyList()

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true
        if (smsGranted) {
            showSmsDialog()
        } else {
            handleSmsPermissionDenied()
        }
    }

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        if (contactsGranted) {
            launchContactPicker()
        } else {
            handleContactsPermissionDenied()
        }
    }

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { contactUri ->
            getPhoneNumberFromContact(contactUri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_checklist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadEquipment()
    }

    private fun initViews(view: View) {
        cardStatus = view.findViewById(R.id.cardStatus)
        tvStatusTitle = view.findViewById(R.id.tvStatusTitle)
        tvStatusSubtitle = view.findViewById(R.id.tvStatusSubtitle)
        progressChecklist = view.findViewById(R.id.progressChecklist)
        btnSendSms = view.findViewById(R.id.btnSendSms)
        btnShare = view.findViewById(R.id.btnShare)
        rvChecklist = view.findViewById(R.id.rvChecklist)
        llEmptyState = view.findViewById(R.id.llEmptyState)
        btnGoToRoutines = view.findViewById(R.id.btnGoToRoutines)
    }

    private fun setupRecyclerView() {
        checklistAdapter = ChecklistCategoryAdapter(
            onItemChecked = { equipment, isChecked ->
                if (isChecked) {
                    checkedItems.add(equipment.id)
                } else {
                    checkedItems.remove(equipment.id)
                }
                updateProgress()
                refreshCategories()
            },
            onItemDeleted = { equipment ->
                showDeleteConfirmation(equipment)
            }
        )

        rvChecklist.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = checklistAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        btnSendSms.setOnClickListener {
            if (allEquipment.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.no_equipment_to_send), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkSmsPermissionAndSend()
        }

        btnShare.setOnClickListener {
            if (allEquipment.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.no_equipment_to_share), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareChecklist()
        }

        btnGoToRoutines.setOnClickListener {
            // Navigate to routines using Navigation component
            findNavController().navigate(R.id.action_checklist_to_routines)
        }
    }

    private fun loadEquipment() {
        val userId = sessionManager.getCurrentUserId()
        if (userId == -1L) return

        viewLifecycleOwner.lifecycleScope.launch {
            workoutRepository.getAllEquipmentForUser(userId).collectLatest { equipment ->
                allEquipment = equipment
                if (equipment.isEmpty()) {
                    showEmptyState()
                } else {
                    showEquipmentList()
                    refreshCategories()
                }
                updateProgress()
            }
        }
    }

    private fun refreshCategories() {
        val categories = allEquipment
            .groupBy { it.category }
            .map { (category, items) ->
                ChecklistCategory(
                    category = category,
                    items = items,
                    checkedItems = checkedItems
                )
            }
            .sortedBy { it.category.ordinal }

        checklistAdapter.submitList(categories)
    }

    private fun updateProgress() {
        val totalItems = allEquipment.size
        val checkedCount = checkedItems.count { id -> allEquipment.any { it.id == id } }

        val progress = if (totalItems > 0) {
            (checkedCount * 100) / totalItems
        } else {
            0
        }

        progressChecklist.progress = progress
        tvStatusSubtitle.text = resources.getQuantityString(R.plurals.items_count, checkedCount, checkedCount)
            .plus(" / ${resources.getQuantityString(R.plurals.items_count, totalItems, totalItems)}")

        tvStatusTitle.text = when {
            totalItems == 0 -> getString(R.string.checklist_empty)
            checkedCount == totalItems -> getString(R.string.all_equipment_ready)
            checkedCount > totalItems / 2 -> getString(R.string.all_equipment_ready)
            else -> getString(R.string.equipment_checklist)
        }
    }

    private fun showEmptyState() {
        rvChecklist.visibility = View.GONE
        llEmptyState.visibility = View.VISIBLE
    }

    private fun showEquipmentList() {
        rvChecklist.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE
    }

    private fun showDeleteConfirmation(equipment: Equipment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Equipment")
            .setMessage("Remove '${equipment.name}' from checklist?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteEquipment(equipment)
            }
            .show()
    }

    private fun deleteEquipment(equipment: Equipment) {
        viewLifecycleOwner.lifecycleScope.launch {
            workoutRepository.deleteEquipment(equipment)
            checkedItems.remove(equipment.id)
            Toast.makeText(requireContext(), getString(R.string.equipment_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSmsPermissionAndSend() {
        when {
            PermissionManager.hasPermissions(requireContext(), PermissionManager.SMS_PERMISSION) -> {
                showSmsDialog()
            }
            PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.SMS_PERMISSION) -> {
                PermissionManager.showRationaleDialog(
                    context = requireContext(),
                    title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.SMS),
                    message = PermissionManager.getRationaleMessage(PermissionManager.PermissionType.SMS),
                    onPositiveClick = {
                        requestSmsPermission.launch(PermissionManager.SMS_PERMISSION)
                    }
                )
            }
            else -> {
                requestSmsPermission.launch(PermissionManager.SMS_PERMISSION)
            }
        }
    }

    private fun handleSmsPermissionDenied() {
        if (!PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.SMS_PERMISSION)) {
            PermissionManager.showSettingsDialog(
                context = requireContext(),
                title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.SMS),
                message = PermissionManager.getSettingsMessage(PermissionManager.PermissionType.SMS)
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.sms_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleContactsPermissionDenied() {
        if (!PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.CONTACTS_PERMISSION)) {
            PermissionManager.showSettingsDialog(
                context = requireContext(),
                title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.CONTACTS),
                message = PermissionManager.getSettingsMessage(PermissionManager.PermissionType.CONTACTS)
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.contacts_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchContactPicker() {
        pickContact.launch(null)
    }

    private fun checkContactsPermissionAndPick(phoneEditText: TextInputEditText) {
        currentPhoneEditText = phoneEditText
        when {
            PermissionManager.hasPermissions(requireContext(), PermissionManager.CONTACTS_PERMISSION) -> {
                launchContactPicker()
            }
            PermissionManager.shouldShowRationale(requireActivity(), PermissionManager.CONTACTS_PERMISSION) -> {
                PermissionManager.showRationaleDialog(
                    context = requireContext(),
                    title = PermissionManager.getRationaleTitle(PermissionManager.PermissionType.CONTACTS),
                    message = PermissionManager.getRationaleMessage(PermissionManager.PermissionType.CONTACTS),
                    onPositiveClick = {
                        requestContactsPermission.launch(PermissionManager.CONTACTS_PERMISSION)
                    }
                )
            }
            else -> {
                requestContactsPermission.launch(PermissionManager.CONTACTS_PERMISSION)
            }
        }
    }

    private fun getPhoneNumberFromContact(contactUri: Uri) {
        var phoneNumber: String? = null
        var contactName: String? = null
        
        val cursor: Cursor? = requireContext().contentResolver.query(
            contactUri, null, null, null, null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                if (idIndex >= 0) {
                    val contactId = it.getString(idIndex)
                    if (nameIndex >= 0) {
                        contactName = it.getString(nameIndex)
                    }
                    
                    if (hasPhoneIndex >= 0 && it.getInt(hasPhoneIndex) > 0) {
                        val phoneCursor = requireContext().contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )
                        
                        phoneCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val numberIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex >= 0) {
                                    phoneNumber = pc.getString(numberIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (phoneNumber != null) {
            currentPhoneEditText?.setText(phoneNumber)
            Toast.makeText(
                requireContext(), 
                getString(R.string.contact_selected, contactName ?: "Contact"),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.no_phone_for_contact), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSmsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_send_sms, null)

        val tilPhoneNumber = dialogView.findViewById<TextInputLayout>(R.id.tilPhoneNumber)
        val etPhoneNumber = dialogView.findViewById<TextInputEditText>(R.id.etPhoneNumber)
        val btnPickContact = dialogView.findViewById<MaterialButton>(R.id.btnPickContact)
        val tilMessage = dialogView.findViewById<TextInputLayout>(R.id.tilMessage)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etMessage)

        // Setup contact picker button
        btnPickContact.setOnClickListener {
            checkContactsPermissionAndPick(etPhoneNumber)
        }

        // Pre-fill message with checklist
        val checklistText = generateChecklistText()
        etMessage.setText(checklistText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.send_via_sms)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.send) { _, _ ->
                val phoneNumber = etPhoneNumber.text?.toString()?.trim() ?: ""
                val message = etMessage.text?.toString()?.trim() ?: ""

                if (phoneNumber.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.please_enter_phone), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendSms(phoneNumber, message)
            }
            .show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            // Use SMS intent as a fallback for better compatibility
            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:$phoneNumber")
                putExtra("sms_body", message)
            }
            startActivity(smsIntent)
            Toast.makeText(requireContext(), getString(R.string.opening_sms_app), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Try direct SMS send if intent fails
            try {
                @Suppress("DEPRECATION")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requireContext().getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
                // Split message if too long
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                Toast.makeText(requireContext(), getString(R.string.sms_sent_success), Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                Toast.makeText(requireContext(), getString(R.string.sms_send_failed, ex.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareChecklist() {
        val checklistText = generateChecklistText()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My FitLife Equipment Checklist")
            putExtra(Intent.EXTRA_TEXT, checklistText)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Checklist"))
    }

    private fun generateChecklistText(): String {
        val builder = StringBuilder()
        builder.appendLine("📋 FitLife Equipment Checklist")
        builder.appendLine("=".repeat(30))
        builder.appendLine()

        val groupedEquipment = allEquipment.groupBy { it.category }

        for ((category, items) in groupedEquipment) {
            val categoryName = category.displayName

            builder.appendLine("${getCategoryEmoji(category)} $categoryName")
            for (item in items) {
                val checkbox = if (checkedItems.contains(item.id)) "✅" else "⬜"
                builder.appendLine("  $checkbox ${item.name}")
            }
            builder.appendLine()
        }

        val checkedCount = checkedItems.count { id -> allEquipment.any { it.id == id } }
        builder.appendLine("Progress: $checkedCount/${allEquipment.size} items")
        builder.appendLine()
        builder.appendLine("Sent from FitLife App")

        return builder.toString()
    }

    private fun getCategoryEmoji(category: EquipmentCategory): String {
        return when (category) {
            EquipmentCategory.STRENGTH -> "🏋️"
            EquipmentCategory.CARDIO -> "🏃"
            EquipmentCategory.MATS -> "🧘"
            EquipmentCategory.ACCESSORIES -> "🎒"
            EquipmentCategory.WEIGHTS -> "💪"
            EquipmentCategory.RESISTANCE -> "🔗"
            EquipmentCategory.OTHER -> "📦"
        }
    }
}
