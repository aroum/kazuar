package io.github.aroum.kazuar.inputmethod.latin.performance

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.view.inputmethod.InputMethodManager

/** Disposable editor shipped only in the isolated profiling variant. */
class BenchmarkEditorActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(EditText(this).apply {
            contentDescription = "Benchmark editor"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(intent.getStringExtra("text").orEmpty())
            setSelection(length())
            setOnClickListener {
                // A preceding macrobenchmark may have force-stopped and rebound the IME.
                // Refresh the editor connection before requesting the next keyboard window.
                requestFocus()
                val manager = getSystemService(InputMethodManager::class.java)
                manager.restartInput(this)
                manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        })
    }
}
