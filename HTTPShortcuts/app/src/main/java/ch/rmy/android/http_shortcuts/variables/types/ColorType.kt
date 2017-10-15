package ch.rmy.android.http_shortcuts.variables.types

import android.content.Context
import android.graphics.Color
import android.os.Handler
import ch.rmy.android.http_shortcuts.activities.BaseActivity
import ch.rmy.android.http_shortcuts.realm.Controller
import ch.rmy.android.http_shortcuts.realm.models.Variable
import ch.rmy.android.http_shortcuts.variables.Showable
import com.pavelsikun.vintagechroma.ChromaDialog
import com.pavelsikun.vintagechroma.IndicatorMode
import com.pavelsikun.vintagechroma.colormode.ColorMode
import org.jdeferred.Deferred

internal class ColorType : BaseVariableType(), AsyncVariableType {

    override fun hasTitle() = false

    override fun createDialog(context: Context, controller: Controller, variable: Variable, deferredValue: Deferred<String, Void, Void>): Showable {
        val dialog = ChromaDialog.Builder()
                .initialColor(getInitialColor(variable))
                .colorMode(ColorMode.RGB)
                .indicatorMode(IndicatorMode.HEX)
                .onColorSelected { color ->
                    val colorFormatted = String.format("%06x", color and 0xffffff)
                    deferredValue.resolve(colorFormatted)
                    controller.setVariableValue(variable, colorFormatted)
                }
                .create()

        return {
            dialog.show((context as BaseActivity).supportFragmentManager, "ColorDialog")

            // The following hack is needed because the ChromaDialog library does not have a method to register a dismiss listener
            Handler().post {
                dialog.dialog.setOnDismissListener {
                    if (deferredValue.isPending) {
                        deferredValue.reject(null)
                    }
                }
            }
        }
    }

    private fun getInitialColor(variable: Variable): Int {
        if (variable.rememberValue && variable.value!!.length == 6) {
            try {
                val color = Integer.parseInt(variable.value, 16)
                return color + 0xff000000.toInt()
            } catch (e: NumberFormatException) {

            }
        }
        return Color.BLACK
    }

    override fun createEditorFragment() = TextEditorFragment()

}
