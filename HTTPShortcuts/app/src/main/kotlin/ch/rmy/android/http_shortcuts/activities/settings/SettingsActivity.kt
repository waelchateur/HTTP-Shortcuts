package ch.rmy.android.http_shortcuts.activities.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.rmy.android.http_shortcuts.R
import ch.rmy.android.http_shortcuts.activities.BaseActivity
import ch.rmy.android.http_shortcuts.activities.misc.LicensesActivity
import ch.rmy.android.http_shortcuts.data.Controller
import ch.rmy.android.http_shortcuts.dialogs.ChangeLogDialog
import ch.rmy.android.http_shortcuts.dialogs.DialogBuilder
import ch.rmy.android.http_shortcuts.dialogs.HelpDialogBuilder
import ch.rmy.android.http_shortcuts.extensions.applyTheme
import ch.rmy.android.http_shortcuts.extensions.attachTo
import ch.rmy.android.http_shortcuts.extensions.bindViewModel
import ch.rmy.android.http_shortcuts.extensions.logException
import ch.rmy.android.http_shortcuts.extensions.showSnackbar
import ch.rmy.android.http_shortcuts.extensions.showToast
import ch.rmy.android.http_shortcuts.extensions.startActivity
import ch.rmy.android.http_shortcuts.import_export.Exporter
import ch.rmy.android.http_shortcuts.import_export.Importer
import ch.rmy.android.http_shortcuts.utils.BaseIntentBuilder
import ch.rmy.android.http_shortcuts.utils.CrashReporting
import ch.rmy.android.http_shortcuts.utils.Destroyer
import ch.rmy.android.http_shortcuts.utils.GsonUtil
import ch.rmy.android.http_shortcuts.utils.Settings
import ch.rmy.android.http_shortcuts.utils.ThemeHelper
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.nononsenseapps.filepicker.FilePickerActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import java.io.File


class SettingsActivity : BaseActivity() {

    private val viewModel: SettingsViewModel by bindViewModel()

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_view, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val destroyer = Destroyer()

        private val viewModel: SettingsViewModel
            get() = (activity as SettingsActivity).viewModel

        private val themeHelper: ThemeHelper
            get() = (activity as SettingsActivity).themeHelper

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            initListPreference("click_behavior")

            initListPreference("theme") {
                val returnIntent = Intent().apply {
                    putExtra(EXTRA_THEME_CHANGED, true)
                }
                activity!!.setResult(Activity.RESULT_OK, returnIntent)
                activity!!.finish()
                activity!!.overridePendingTransition(0, 0)
            }

            initPreference("lock_settings") {
                showAppLockDialog()
            }

            initPreference("export") {
                showExportOptions()
            }

            initPreference("import") {
                showImportOptions()
            }

            initPreference("privacy_policy") {
                HelpDialogBuilder(context!!)
                    .title(R.string.title_privacy_policy)
                    .message(R.string.privacy_policy)
                    .build()
                    .show()
            }

            initListPreference("crash_reporting") { newValue ->
                CrashReporting.enabled = newValue != "false"
            }

            initPreference("changelog") {
                ChangeLogDialog(context!!, false)
                    .show()
                    .subscribe()
                    .attachTo(destroyer)
            }
                .summary = getString(R.string.settings_changelog_summary, versionName)

            initPreference("mail") {
                contactDeveloper()
            }

            initPreference("faq") {
                openFAQPage()
            }

            initPreference("play_store") {
                openPlayStore()
            }

            initPreference("github") {
                gotoGithub()
            }

            initPreference("translate") {
                helpTranslate()
            }

            initPreference("licenses") {
                showLicenses()
            }
        }

        private val versionName: String
            get() = try {
                context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName
            } catch (e: NameNotFoundException) {
                "???"
            }

        private fun initPreference(key: String, action: () -> Unit = {}): Preference {
            val preference = findPreference<Preference>(key)!!
            preference.applyTheme(themeHelper)
            preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                action()
                true
            }
            return preference
        }

        private fun initListPreference(key: String, action: (newValue: Any) -> Unit = {}): ListPreference {
            val preference = findPreference<ListPreference>(key)!!
            preference.applyTheme(themeHelper)
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                updateSummary(preference, newValue)
                action(newValue)
                true
            }
            updateSummary(preference, null)
            return preference
        }

        private fun updateSummary(preference: ListPreference, value: Any?) {
            var index = preference.findIndexOfValue((value ?: preference.value) as String?)
            if (index == -1) {
                index = 0
            }
            preference.summary = preference.entries[index]
        }

        private fun showAppLockDialog() {
            DialogBuilder(context!!)
                .title(R.string.dialog_title_lock_app)
                .message(R.string.dialog_text_lock_app)
                .positive(R.string.button_lock_app)
                .textInput(allowEmpty = false, maxLength = 50) { input ->
                    lockApp(input)
                }
                .negative(R.string.dialog_cancel)
                .showIfPossible()
        }

        private fun lockApp(password: String) {
            viewModel.setAppLock(password)
                .subscribe({
                    val returnIntent = Intent().apply {
                        putExtra(EXTRA_APP_LOCKED, true)
                    }
                    activity!!.setResult(Activity.RESULT_OK, returnIntent)
                    activity!!.finish()
                },
                    { e ->
                        activity?.showSnackbar(R.string.error_generic, long = true)
                        logException(e)
                    })
                .attachTo(destroyer)
        }

        private fun showExportOptions() {
            DialogBuilder(context!!)
                .title(R.string.title_export)
                .item(R.string.button_export_to_filesystem, ::showExportInstructions)
                .item(R.string.button_export_send_to, ::sendExport)
                .showIfPossible()
        }

        private fun showExportInstructions() {
            DialogBuilder(context!!)
                .positive(R.string.dialog_ok) { openFilePickerForExport() }
                .negative(R.string.dialog_cancel)
                .message(R.string.export_instructions)
                .showIfPossible()
        }

        private fun sendExport() {
            Controller().use { controller ->
                val base = controller.exportBase()
                val data = GsonUtil.exportData(base)
                Intent(Intent.ACTION_SEND)
                    .setType(IMPORT_EXPORT_FILE_TYPE)
                    .putExtra(Intent.EXTRA_TEXT, data)
                    .let {
                        Intent.createChooser(it, getString(R.string.title_export))
                    }
                    .startActivity(activity!!)
            }
        }

        private fun showImportOptions() {
            DialogBuilder(context!!)
                .title(R.string.title_import)
                .item(R.string.button_import_from_filesystem, ::showImportInstructions)
                .item(R.string.button_import_from_general, ::openGeneralPickerForImport)
                .showIfPossible()
        }

        private fun showImportInstructions() {
            DialogBuilder(context!!)
                .positive(R.string.dialog_ok) { openLocalFilePickerForImport() }
                .negative(R.string.dialog_cancel)
                .message(R.string.import_instructions)
                .showIfPossible()
        }

        private fun openFilePickerForExport() {
            Intent(context!!, FilePickerActivity::class.java)
                .putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                .putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                .putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
                .putExtra(FilePickerActivity.EXTRA_START_PATH, Settings(context!!).importExportDirectory)
                .startActivity(this, REQUEST_PICK_DIR_FOR_EXPORT)
        }

        private fun openLocalFilePickerForImport() {
            Intent(context!!, FilePickerActivity::class.java)
                .putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                .putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false)
                .putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE)
                .putExtra(FilePickerActivity.EXTRA_START_PATH, Settings(context!!).importExportDirectory)
                .startActivity(this, REQUEST_PICK_FILE_FOR_IMPORT)
        }

        private fun openGeneralPickerForImport() {
            val pickerIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Intent(Intent.ACTION_OPEN_DOCUMENT)
            } else {
                Intent(Intent.ACTION_GET_CONTENT)
            }
                .apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            try {
                pickerIntent
                    .startActivity(this, REQUEST_IMPORT_FROM_DOCUMENTS)
            } catch (e: ActivityNotFoundException) {
                activity!!.showToast(R.string.error_not_supported)
            }
        }

        private fun contactDeveloper() {
            sendMail(CONTACT_SUBJECT, CONTACT_TEXT, getString(R.string.settings_mail))
        }

        private fun sendMail(subject: String, text: String, title: String) {
            try {
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL"))
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let {
                        Intent.createChooser(it, title)
                    }
                    .startActivity(this)
            } catch (e: ActivityNotFoundException) {
                activity!!.showToast(R.string.error_not_supported)
            }
        }

        private fun openFAQPage() {
            openURL(FAQ_PAGE_URL)
        }

        private fun openPlayStore() {
            openURL(PLAY_STORE_URL)
        }

        private fun gotoGithub() {
            openURL(GITHUB_URL)
        }

        private fun openURL(url: String) {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .startActivity(this)
            } catch (e: ActivityNotFoundException) {
                activity!!.showToast(R.string.error_not_supported)
            }
        }

        private fun helpTranslate() {
            sendMail(TRANSLATE_SUBJECT, TRANSLATE_TEXT, getString(R.string.settings_help_translate))
        }

        private fun showLicenses() {
            LicensesActivity.IntentBuilder(context!!)
                .build()
                .startActivity(this)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
            if (resultCode != RESULT_OK || intent == null) {
                return
            }
            when (requestCode) {
                REQUEST_PICK_DIR_FOR_EXPORT -> {
                    val uri = intent.data ?: return
                    val directoryPath = uri.path ?: return
                    persistPath(directoryPath)
                    startExport(directoryPath)
                }
                REQUEST_PICK_FILE_FOR_IMPORT -> {
                    val uri = intent.data ?: return
                    val filePath = uri.path
                    val directoryPath = File(filePath).parent
                    persistPath(directoryPath)
                    startImport(uri)
                }
                REQUEST_IMPORT_FROM_DOCUMENTS -> {
                    val uri = intent.data ?: return
                    startImport(uri)
                }
            }
        }

        private fun persistPath(path: String) {
            Settings(context!!).importExportDirectory = path
        }

        private fun startExport(directoryPath: String) {
            // TODO: Replace progress dialog with something better
            val progressDialog = ProgressDialog(activity).apply {
                setMessage(getString(R.string.export_in_progress))
            }
            Exporter()
                .export(directoryPath)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    progressDialog.show()
                }
                .doOnEvent { _, _ ->
                    progressDialog.dismiss()
                }
                .subscribe({ status ->
                    showSnackbar(context!!.resources.getQuantityString(
                        R.plurals.shortcut_export_success,
                        status.exportedShortcuts,
                        status.exportedShortcuts
                    ))
                }, {
                    // TODO: Show more meaningful error message
                    showSnackbar(R.string.export_failed)
                })
                .attachTo(destroyer)
        }

        private fun startImport(uri: Uri) {
            // TODO: Replace progress dialog with something better
            val progressDialog = ProgressDialog(activity).apply {
                setMessage(getString(R.string.import_in_progress))
            }
            Importer()
                .import(context!!.applicationContext, uri)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    progressDialog.show()
                }
                .doOnEvent { _, _ ->
                    progressDialog.dismiss()
                }
                .subscribe({ status ->
                    showSnackbar(context!!.resources.getQuantityString(
                        R.plurals.shortcut_import_success,
                        status.importedShortcuts,
                        status.importedShortcuts
                    ))
                }, { e ->
                    if (e is JsonParseException || e is JsonSyntaxException) {
                        showSnackbar(getString(R.string.import_failed_with_reason, getString(R.string.import_failure_reason_invalid_json)), long = true)
                    } else if (e is IllegalArgumentException) {
                        showSnackbar(getString(R.string.import_failed_with_reason, e.message), long = true)
                    } else {
                        showSnackbar(R.string.import_failed)
                    }
                    logException(e)
                })
                .attachTo(destroyer)
        }

        private fun showSnackbar(@StringRes message: Int, long: Boolean = false) {
            activity?.showSnackbar(message, long)
        }

        private fun showSnackbar(message: CharSequence, long: Boolean = false) {
            activity?.showSnackbar(message, long)
        }

        override fun onDestroy() {
            super.onDestroy()
            destroyer.destroy()
        }

    }

    class IntentBuilder(context: Context) : BaseIntentBuilder(context, SettingsActivity::class.java)

    companion object {

        const val EXTRA_THEME_CHANGED = "theme_changed"
        const val EXTRA_APP_LOCKED = "app_locked"

        private const val CONTACT_SUBJECT = "HTTP Shortcuts"
        private const val CONTACT_TEXT = "Hey Roland,\n\n"
        private const val TRANSLATE_SUBJECT = "Translate HTTP Shortcuts"
        private const val TRANSLATE_TEXT = "Hey Roland,\n\nI would like to help translate your app into [INSERT LANGUAGE HERE]. Please give me access to the translation tool.\n\nMy Github username is [INSERT GITHUB USERNAME HERE], and I would like to be credited as a contributor"
        private const val DEVELOPER_EMAIL = "android@rmy.ch"
        private const val FAQ_PAGE_URL = "http://waboodoo.ch/http-shortcuts/#faq"
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=ch.rmy.android.http_shortcuts"
        private const val GITHUB_URL = "https://github.com/Waboodoo/HTTP-Shortcuts"

        private const val REQUEST_PICK_DIR_FOR_EXPORT = 1
        private const val REQUEST_PICK_FILE_FOR_IMPORT = 2
        private const val REQUEST_IMPORT_FROM_DOCUMENTS = 3

        private const val IMPORT_EXPORT_FILE_TYPE = "text/plain"
    }

}
