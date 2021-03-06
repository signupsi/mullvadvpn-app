package net.mullvad.mullvadvpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.delay
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.ui.widget.Button
import net.mullvad.mullvadvpn.ui.widget.UrlButton
import org.joda.time.DateTime

val POLL_INTERVAL: Long = 15 /* s */ * 1000 /* ms */

class WelcomeFragment : ServiceDependentFragment(OnNoService.GoToLaunchScreen) {
    private lateinit var accountLabel: TextView

    override fun onSafelyCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.welcome, container, false)

        view.findViewById<View>(R.id.settings).setOnClickListener {
            parentActivity.openSettings()
        }

        accountLabel = view.findViewById<TextView>(R.id.account_number).apply {
            setOnClickListener { copyAccountTokenToClipboard() }
        }

        view.findViewById<UrlButton>(R.id.buy_credit).apply {
            prepare(daemon, jobTracker)
        }

        view.findViewById<Button>(R.id.redeem_voucher).apply {
            setOnClickAction("openRedeemVoucherDialog", jobTracker) {
                showRedeemVoucherDialog()
            }
        }

        return view
    }

    override fun onSafelyResume() {
        accountCache.onAccountNumberChange.subscribe(this) { account ->
            updateAccountNumber(account)
        }

        accountCache.onAccountExpiryChange.subscribe(this) { expiry ->
            checkExpiry(expiry)
        }

        jobTracker.newBackgroundJob("pollAccountData") {
            while (true) {
                accountCache.fetchAccountExpiry()
                delay(POLL_INTERVAL)
            }
        }
    }

    override fun onSafelyPause() {
        accountCache.onAccountNumberChange.unsubscribe(this)
        accountCache.onAccountExpiryChange.unsubscribe(this)
        jobTracker.cancelJob("pollAccountData")
    }

    private fun updateAccountNumber(rawAccountNumber: String?) {
        val accountText = rawAccountNumber?.let { account ->
            addSpacesToAccountText(account)
        }

        jobTracker.newUiJob("updateAccountNumber") {
            accountLabel.text = accountText ?: ""
            accountLabel.setEnabled(accountText != null && accountText.length > 0)
        }
    }

    private fun addSpacesToAccountText(account: String): String {
        val length = account.length

        if (length == 0) {
            return ""
        } else {
            val numParts = (length - 1) / 4 + 1

            val parts = Array(numParts) { index ->
                val startIndex = index * 4
                val endIndex = minOf(startIndex + 4, length)

                account.substring(startIndex, endIndex)
            }

            return parts.joinToString(" ")
        }
    }

    private fun checkExpiry(maybeExpiry: DateTime?) {
        maybeExpiry?.let { expiry ->
            val tomorrow = DateTime.now().plusDays(1)

            if (expiry.isAfter(tomorrow)) {
                jobTracker.newUiJob("advanceToConnectScreen") {
                    advanceToConnectScreen()
                }
            }
        }
    }

    private fun advanceToConnectScreen() {
        fragmentManager?.beginTransaction()?.apply {
            replace(R.id.main_fragment, ConnectFragment())
            commit()
        }
    }

    private fun copyAccountTokenToClipboard() {
        val accountToken = accountLabel.text
        val clipboardLabel = resources.getString(R.string.mullvad_account_number)
        val toastMessage = resources.getString(R.string.copied_mullvad_account_number)

        val context = parentActivity
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(clipboardLabel, accountToken)

        clipboard.primaryClip = clipData

        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun showRedeemVoucherDialog() {
        val transaction = fragmentManager?.beginTransaction()

        transaction?.addToBackStack(null)

        RedeemVoucherDialogFragment().show(transaction, null)
    }
}
