package com.stockflip.ui.dialogs

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

fun focusInput(editText: EditText, selectAll: Boolean = true) {
    fun requestFocusAndKeyboard() {
        editText.isFocusableInTouchMode = true
        editText.requestFocusFromTouch()
        editText.requestFocus()
        if (selectAll) {
            editText.selectAll()
        } else {
            editText.text?.length?.let(editText::setSelection)
        }
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    editText.post {
        requestFocusAndKeyboard()
        // Retry once after the dialog window is fully attached; this fixes cases
        // where the value is selected but the IME does not open on the first pass.
        editText.postDelayed({ requestFocusAndKeyboard() }, 100)
    }
}
