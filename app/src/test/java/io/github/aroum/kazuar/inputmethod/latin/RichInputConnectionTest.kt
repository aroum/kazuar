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
    @Test fun deletionAfterEditorRetainsCompositionDoesNotRemovePrecedingSpace() {
        val ime = Robolectric.buildService(TestIme::class.java).get()
        val editor = EditText(ime).apply {
            setText("Привела логика")
            setSelection(length())
        }
        ime.editorConnection = object : BaseInputConnection(editor, true) {
            override fun getEditable() = editor.text
        }
        val connection = RichInputConnection(ime)
        connection.resetCachesUponCursorMoveAndReturnSuccess(editor.length(), editor.length(), false)
        // Simulate the editor retaining a composing span after the keyboard has committed
        // its word. deleteSurroundingText skips that span unless it is finished first.
        ime.editorConnection.setComposingRegion("Привела ".length, editor.length())
        connection.beginBatchEdit()
        try {
            connection.deleteTextBeforeCursor(1)
            assertEquals("Привела логик", editor.text.toString())
            assertEquals(editor.length(), connection.expectedSelectionStart)
            assertEquals(editor.text.toString(),
                connection.getTextBeforeCursor(editor.length(), 0).toString())
            connection.setComposingRegion("Привела ".length, editor.length())
            connection.setComposingText("логикой", 1)
            assertEquals("Привела логикой", editor.text.toString())
            assertEquals(editor.length(), connection.expectedSelectionStart)
        } finally {
            connection.endBatchEdit()
        }
    }

    @Test fun movingWithinCorrectedWordPreservesItsPrefixAndPrecedingSpace() {
        for (prefix in listOf("Привела ", "🙂 Привела ")) {
            val ime = Robolectric.buildService(TestIme::class.java).get()
            val editor = EditText(ime).apply {
                setText(prefix + "логика")
                setSelection(length())
            }
            ime.editorConnection = object : BaseInputConnection(editor, true) {
                override fun getEditable() = editor.text
            }
            val connection = RichInputConnection(ime)
            val end = editor.length()
            connection.resetCachesUponCursorMoveAndReturnSuccess(end, end, false)
            connection.beginBatchEdit()
            try {
                connection.setComposingRegion(prefix.length, end)
                // Cursor moves retain the editor's composition. Include its first character,
                // where the cached prefix is empty, then return to the end before typing.
                for (cursor in listOf(end - 2, prefix.length, end)) {
                    editor.setSelection(cursor)
                    connection.resetCachesUponCursorMoveAndReturnSuccess(cursor, cursor, false)
                }
                connection.setComposingText("логи", 1)
                assertEquals(prefix + "логи", editor.text.toString())
                assertEquals(prefix.length + 4, connection.expectedSelectionStart)
                connection.setComposingText("логикой", 1)
                assertEquals(prefix + "логикой", editor.text.toString())
                assertEquals(prefix.length + 7, connection.expectedSelectionStart)
                assertEquals(editor.text.toString(),
                    connection.getTextBeforeCursor(editor.length(), 0).toString())
                connection.commitText("логикой", 1)
                connection.commitText(" ", 1)
                assertEquals(prefix + "логикой ", editor.text.toString())
                assertEquals(editor.length(), connection.expectedSelectionStart)
            } finally {
                connection.endBatchEdit()
            }
        }
    }

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
