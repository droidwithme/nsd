package com.cloudwalker.tv.nsd_client

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cloudwalker.tv.nsd.NsdHelper
import com.cloudwalker.tv.nsd.NsdListener
import com.cloudwalker.tv.nsd.NsdService
import com.cloudwalker.tv.nsd.NsdType

class MainActivity : AppCompatActivity(), NsdListener {

    val TAG = "MainActivity"

    var nsdHelper: NsdHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nsdHelper = NsdHelper(this, this)
        nsdHelper?.isAutoResolveEnabled = true
        nsdHelper?.isLogEnabled = true
        nsdHelper?.ser
        nsdHelper?.startDiscovery(NsdType.HTTP)

    }


    override fun onMessageReceived(message: String?) {
        logMsg("onMessageReceived$message")
        logMsg(message)
    }

    override fun onNsdServiceResolved(resolvedService: NsdService?) {
        logMsg("onNsdServiceResolved")
        nsdHelper?.connectToService(resolvedService!!.host.hostAddress)
    }

    override fun onNsdServiceLost(lostService: NsdService?) {
        logMsg("onNsdServiceLost $lostService")
    }

    override fun onNsdError(errorMessage: String?, errorCode: Int, errorSource: String?) {
        logMsg(errorMessage)
    }

    override fun onNsdDiscoveryFinished() {
        logMsg("onNsdDiscoveryFinished()")
    }

    override fun onNsdServiceFound(foundService: NsdService?) {
        nsdHelper?.resolveService(foundService)
        logMsg("onNsdServiceFound$foundService")
    }

    override fun onServiceConnected() {
        logMsg("onServiceConnected")
        nsdHelper?.sendMessage("getInfo")
    }

    fun logMsg(msg: String?) {
        Log.e(TAG, msg)
    }
}
