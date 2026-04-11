package com.stockflip.ui.dialogs

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

fun focusInput(editText: EditText, selectAll: Boolean = true) {
    editText.post {
        editText.requestFocus()
        if (selectAll) {
            editText.selectAll()
        }
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
}
