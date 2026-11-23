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
    @Test fun revertingExpansionAndEditingPreservesPrecedingSpace() {
        val ime = Robolectric.buildService(TestIme::class.java).get()
        val editor = EditText(ime).apply {
            setText("на вторник ")
            setSelection(length())
        }
        ime.editorConnection = object : BaseInputConnection(editor, true) {
            override fun getEditable() = editor.text
        }
        val connection = RichInputConnection(ime)
        connection.resetCachesUponCursorMoveAndReturnSuccess(11, 11, false)
        connection.beginBatchEdit()
        try {
            connection.deleteTextBeforeCursor(8)
            connection.setComposingText("вт", 1)
            assertEquals("на вт", editor.text.toString())
            connection.setComposingText("в", 1)
            assertEquals("на в", editor.text.toString())
            assertEquals(4, connection.expectedSelectionStart)
            connection.setComposingText("вт", 1)
            connection.commitText("вт", 1)
            connection.commitText(" ", 1)
            assertEquals("на вт ", editor.text.toString())
            assertEquals(6, connection.expectedSelectionStart)
            assertEquals("на вт ", connection.getTextBeforeCursor(6, 0).toString())
        } finally {
            connection.endBatchEdit()
        }
    }

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
