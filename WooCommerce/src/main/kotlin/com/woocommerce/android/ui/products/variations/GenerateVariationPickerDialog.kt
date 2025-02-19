package com.woocommerce.android.ui.products.variations

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.woocommerce.android.databinding.DialogGenerateVariationsBinding

class GenerateVariationPickerDialog(context: Context) : BottomSheetDialog(context) {
    private var binding: DialogGenerateVariationsBinding =
        DialogGenerateVariationsBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)
        binding.allVariation.setOnClickListener {
            listener?.onGenerateAllVariations()
            dismiss()
        }
        binding.newVariation.setOnClickListener {
            listener?.onGenerateNewVariation()
            dismiss()
        }
    }

    var listener: GenerateVariationPickerDialogListener? = null

    interface GenerateVariationPickerDialogListener {
        fun onGenerateAllVariations()
        fun onGenerateNewVariation()
    }
}
