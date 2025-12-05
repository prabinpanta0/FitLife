package com.example.fitlife.ui.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fitlife.R
import com.example.fitlife.data.model.Equipment
import com.example.fitlife.data.model.EquipmentCategory

data class ChecklistCategory(
    val category: EquipmentCategory,
    val items: List<Equipment>,
    val checkedItems: Set<Long> = emptySet()
)

class ChecklistCategoryAdapter(
    private val onItemChecked: (Equipment, Boolean) -> Unit,
    private val onItemDeleted: (Equipment) -> Unit
) : ListAdapter<ChecklistCategory, ChecklistCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewCategoryIndicator: View = itemView.findViewById(R.id.viewCategoryIndicator)
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvCategoryCount: TextView = itemView.findViewById(R.id.tvCategoryCount)
        private val rvCategoryItems: RecyclerView = itemView.findViewById(R.id.rvCategoryItems)

        fun bind(checklistCategory: ChecklistCategory) {
            tvCategoryName.text = checklistCategory.category.displayName

            val checkedCount = checklistCategory.items.count {
                checklistCategory.checkedItems.contains(it.id)
            }
            tvCategoryCount.text = itemView.context.getString(
                R.string.checked_count_format,
                checkedCount,
                checklistCategory.items.size
            )

            // Set category color indicator
            val color = getCategoryColor(checklistCategory.category)
            viewCategoryIndicator.setBackgroundColor(
                itemView.context.getColor(color)
            )

            // Setup inner RecyclerView
            val equipmentAdapter = EquipmentAdapter(
                checkedItems = checklistCategory.checkedItems,
                onItemChecked = onItemChecked,
                onItemDeleted = onItemDeleted
            )
            rvCategoryItems.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = equipmentAdapter
                isNestedScrollingEnabled = false
            }
            equipmentAdapter.submitList(checklistCategory.items)
        }

        private fun getCategoryColor(category: EquipmentCategory): Int {
            return when (category) {
                EquipmentCategory.STRENGTH -> R.color.primary
                EquipmentCategory.CARDIO -> R.color.secondary
                EquipmentCategory.MATS -> R.color.category_clothing
                EquipmentCategory.ACCESSORIES -> R.color.category_accessories
                EquipmentCategory.WEIGHTS -> R.color.category_nutrition
                EquipmentCategory.RESISTANCE -> R.color.category_electronics
                EquipmentCategory.OTHER -> R.color.text_secondary
            }
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<ChecklistCategory>() {
        override fun areItemsTheSame(oldItem: ChecklistCategory, newItem: ChecklistCategory): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: ChecklistCategory, newItem: ChecklistCategory): Boolean {
            return oldItem == newItem
        }
    }
}

class EquipmentAdapter(
    private val checkedItems: Set<Long>,
    private val onItemChecked: (Equipment, Boolean) -> Unit,
    private val onItemDeleted: (Equipment) -> Unit
) : ListAdapter<Equipment, EquipmentAdapter.EquipmentViewHolder>(EquipmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EquipmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_equipment, parent, false)
        return EquipmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: EquipmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EquipmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbEquipment: com.google.android.material.checkbox.MaterialCheckBox =
            itemView.findViewById(R.id.cbEquipment)
        private val tvEquipmentName: TextView = itemView.findViewById(R.id.tvEquipmentName)
        private val tvEquipmentQuantity: TextView = itemView.findViewById(R.id.tvEquipmentQuantity)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(equipment: Equipment) {
            tvEquipmentName.text = equipment.name

            // Hide quantity field since Equipment doesn't have quantity
            tvEquipmentQuantity.visibility = View.GONE

            cbEquipment.isChecked = checkedItems.contains(equipment.id)

            // Apply strikethrough effect if checked
            if (cbEquipment.isChecked) {
                tvEquipmentName.paintFlags = tvEquipmentName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                tvEquipmentName.alpha = 0.6f
            } else {
                tvEquipmentName.paintFlags = tvEquipmentName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvEquipmentName.alpha = 1.0f
            }

            cbEquipment.setOnCheckedChangeListener { _, isChecked ->
                onItemChecked(equipment, isChecked)
            }

            btnDelete.setOnClickListener {
                onItemDeleted(equipment)
            }
        }
    }

    class EquipmentDiffCallback : DiffUtil.ItemCallback<Equipment>() {
        override fun areItemsTheSame(oldItem: Equipment, newItem: Equipment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Equipment, newItem: Equipment): Boolean {
            return oldItem == newItem
        }
    }
}
