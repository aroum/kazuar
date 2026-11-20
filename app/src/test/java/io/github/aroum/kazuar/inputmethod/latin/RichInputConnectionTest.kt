package io.github.aroum.kazuar.inputmethod.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class RichInputConnectionTest {
    class TestIme : InputMethodService() {
        lateinit var editorConnection: InputConnection

        override fun getCurrentInputConnection(): InputConnection = editorConnection
    }

    @Test fun reopeningWordForCorrectionKeepsItsBoundaryWhenDeleting() {
        val ime = Robolectric.buildService(TestIme::class.java).get()
        val editor = EditText(ime).apply {
            setText("one two")
            setSelection(length())
        }
        ime.editorConnection = object : BaseInputConnection(editor, true) {
            override fun getEditable() = editor.text
        }
        val connection = RichInputConnection(ime)
        connection.resetCachesUponCursorMoveAndReturnSuccess(7, 7, false)
        connection.beginBatchEdit()
        try {
            connection.setComposingRegion(4, 7)
            connection.setComposingRegion(4, 7)
            connection.setComposingText("tw", 1)

            assertEquals("one tw", editor.text.toString())
            assertEquals(6, connection.expectedSelectionStart)
            assertEquals("one tw", connection.getTextBeforeCursor(6, 0).toString())
        } finally {
            connection.endBatchEdit()
        }
    }
}
