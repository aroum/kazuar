package io.github.aroum.kazuar.inputmethod.latin.performance

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText

/** Disposable editor shipped only in the isolated profiling variant. */
class BenchmarkEditorActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(EditText(this).apply {
            contentDescription = "Benchmark editor"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(intent.getStringExtra("text").orEmpty())
            setSelection(length())
        })
    }
}
