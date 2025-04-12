import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

import com.example.purrytify.network.NetworkStateManager

class NetworkStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            val networkUtil = NetworkUtil(context)
            val isConnected = networkUtil.isNetworkAvailable()

            NetworkStateManager.getInstance().setConnected(isConnected)
        }
    }
}