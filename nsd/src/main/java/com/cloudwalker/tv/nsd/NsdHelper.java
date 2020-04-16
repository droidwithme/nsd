package com.cloudwalker.tv.nsd;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * NsdHelper is a helper class for {@link NsdManager}.
 */
public class NsdHelper implements DiscoveryTimer.OnTimeoutListener {
    private static final String TAG = "NsdHelper";
    private final NsdManager mNsdManager;
    private NsdListener mNsdListener;

    private NsdServiceInfo mRegisteredServiceInfo = new NsdServiceInfo();


    // Discovery
    private boolean mDiscoveryStarted = false;
    private long mDiscoveryTimeout = 100;
    private String mDiscoveryServiceType;
    private String mDiscoveryServiceName;
    private NsdListenerDiscovery mDiscoveryListener;
    private DiscoveryTimer mDiscoveryTimer;

    // Resolve
    private boolean mAutoResolveEnabled = true;
    private ResolveQueue mResolveQueue;

    // Common
    private boolean mLogEnabled = false;


    //MESSAGE

    private ObjectOutputStream output;
    private ObjectInputStream input;
    private Socket connection;
    private String message = "";
    private int port = 9000;


    /**
     * @param context     Context is only needed to create {@link NsdManager} instance.
     * @param nsdListener Service discovery listener.
     */
    public NsdHelper(Context context, NsdListener nsdListener) {
        this.mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.mNsdListener = nsdListener;
        this.mDiscoveryTimer = new DiscoveryTimer(this, mDiscoveryTimeout);
        this.mResolveQueue = new ResolveQueue(this);
    }

    /**
     * @return Is logcat enabled.
     */
    public boolean isLogEnabled() {
        return mLogEnabled;
    }

    /**
     * Enable logcat messages.
     * By default their disabled.
     *
     * @param isLogEnabled If true logcat is enabled.
     */
    public void setLogEnabled(boolean isLogEnabled) {
        this.mLogEnabled = isLogEnabled;
    }

    /**
     * @return Is auto resolving discovered services enabled.
     */
    public boolean isAutoResolveEnabled() {
        return mAutoResolveEnabled;
    }

    /**
     * Enable auto resolving discovered services.
     * By default it's enabled.
     *
     * @param isAutoResolveEnabled If true discovered service will be automatically resolved.
     */
    public void setAutoResolveEnabled(boolean isAutoResolveEnabled) {
        this.mAutoResolveEnabled = isAutoResolveEnabled;
    }

    /**
     * @return onNsdDiscoveryTimeout timeout in seconds.
     */
    public long getDiscoveryTimeout() {
        return mDiscoveryTimeout;
    }


    public void setDiscoveryTimeout(int seconds) {
        if (seconds < 0)
            throw new IllegalArgumentException("Timeout has to be greater or equal 0!");

        if (seconds == 0)
            mDiscoveryTimeout = Integer.MAX_VALUE;
        else
            mDiscoveryTimeout = seconds;

        mDiscoveryTimer.timeout(mDiscoveryTimeout);
    }

    /**
     * @return True if discovery is running.
     */
    public boolean isDiscoveryRunning() {
        return mDiscoveryStarted;
    }

    /**
     * @return Discovery service type to discover.
     */
    public String getDiscoveryServiceType() {
        return mDiscoveryServiceType;
    }

    /**
     * @return Discovery service name to discover.
     */
    public String getDiscoveryServiceName() {
        return mDiscoveryServiceName;
    }

    public void setDiscoveryServiceName(String serviceName) {
        mDiscoveryServiceName = serviceName;
    }


    public void startDiscovery(String serviceType) {
        startDiscovery(serviceType, null);
    }


    public void startDiscovery(String serviceType, String serviceName) {
        if (!mDiscoveryStarted) {
            mDiscoveryStarted = true;
            mDiscoveryTimer.start();
            mDiscoveryServiceType = serviceType;
            mDiscoveryServiceName = serviceName;
            mDiscoveryListener = new NsdListenerDiscovery(this);
            mNsdManager.discoverServices(mDiscoveryServiceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }
    }

    /**
     * Stop discovering services.
     */
    public void stopDiscovery() {
        if (mDiscoveryStarted) {
            mDiscoveryStarted = false;
            mDiscoveryTimer.cancel();
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            if (mNsdListener != null)
                mNsdListener.onNsdDiscoveryFinished();
        }
    }

    /**
     * Resolve service host and port.
     *
     * @param nsdService Service to be resolved.
     */
    public void resolveService(NsdService nsdService) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(nsdService.getName());
        serviceInfo.setServiceType(nsdService.getType());
        mResolveQueue.enqueue(serviceInfo);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internals
    ///////////////////////////////////////////////////////////////////////////

    void resolveService(NsdServiceInfo serviceInfo) {
        mNsdManager.resolveService(serviceInfo, new NsdListenerResolve(this));
    }

    public void connectToService(String ipAddress) {
        startRunning(ipAddress);
    }


    public void startRunning(String serverIP) {
        try {
            try {
                connection = new Socket(InetAddress.getByName(serverIP), port);
            } catch (IOException ioEception) {
                logMsg("Server Might Be Down!");
            }
            logMsg("Connected to: " + connection.getInetAddress().getHostName());
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush();
            input = new ObjectInputStream(connection.getInputStream());
            mNsdListener.onServiceConnected();
            whileChatting();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void whileChatting() throws IOException {
        do {
            try {
                message = (String) input.readObject();
                mNsdListener.onMessageReceived(message);
            } catch (ClassNotFoundException classNotFoundException) {
            }
        } while (!message.equals("END"));
    }

    public void sendMessage(String message) {
        try {
            output.writeObject(message);
            output.flush();
        } catch (IOException ioException) {
            logMsg("\n Unable to Send Message");
        }
    }


    void onNsdServiceFound(NsdServiceInfo foundService) {
        mDiscoveryTimer.reset();
        if (mNsdListener != null)
            mNsdListener.onNsdServiceFound(new NsdService(foundService));
        if (mAutoResolveEnabled)
            mResolveQueue.enqueue(foundService);
    }

    void onNsdServiceResolved(NsdServiceInfo resolvedService) {
        mResolveQueue.next();
        mDiscoveryTimer.reset();
        if (mNsdListener != null)
            mNsdListener.onNsdServiceResolved(new NsdService(resolvedService));
    }

    void onNsdServiceLost(NsdServiceInfo lostService) {
        if (mNsdListener != null)
            mNsdListener.onNsdServiceLost(new NsdService(lostService));
    }

    void logMsg(String msg) {
        if (mLogEnabled)
            Log.e(TAG, msg);
    }

    void logError(String errorMessage, int errorCode, String errorSource) {
        Log.e(TAG, errorMessage);
        if (mNsdListener != null)
            mNsdListener.onNsdError(errorMessage, errorCode, errorSource);
    }

    NsdServiceInfo getRegisteredServiceInfo() {
        return mRegisteredServiceInfo;
    }

    @Override
    public void onNsdDiscoveryTimeout() {
        stopDiscovery();
    }


}
