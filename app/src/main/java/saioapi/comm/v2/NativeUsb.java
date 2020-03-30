
package saioapi.comm.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

class NativeUsb extends Connection{
    private final String TAG = "SaioComManager-NativeUsb";
    private UsbDeviceConnection mUsbConnection;
    private UsbEndpoint mUsbOutEndpoint;
    private UsbEndpoint mUsbInEndpoint;
    private UsbEndpoint mUsbEndpoint;
    private UsbInterface mUsbInterface;
    private boolean mOpened;
    private boolean mEndpointIn = false;
    private boolean mEndpointOut = false;
    private UsbManager mUsbManager;
    private UsbDevice usbDevice;
    private int device_id[]= new int[10];
    private OnComEventListener mOnComEventListener = null;
    private Context mContext;
    boolean mReconnectEvent;
    private int LOG_MAX_LENGTH = 256;


    NativeUsb(Context context, OnComEventListener listener, boolean reconnect)
    {
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mOnComEventListener = listener;
        mContext = context;
        mReconnectEvent = reconnect;
    }

    void listener(int event)
    {
        //
        //  Call your real function to handle event here
        //
        if (null != mOnComEventListener)
        {
            mOnComEventListener.onEvent(event);
        }
    }

    private boolean openEndpoint() {
        //Log.i(TAG,"XAC ID: " + usbDevice.getDeviceId());
        // Attempt to connect to each USB device and send a API_VERSION message to the device until pinpad is found
        // Obtain the interface for the USB device

        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            mUsbInterface = usbDevice.getInterface(i);
            // Endpoint Configuration
            for (int j = 0; j < mUsbInterface.getEndpointCount(); j++) {
                mUsbEndpoint = mUsbInterface.getEndpoint(j);
                switch(mUsbEndpoint.getType()) {
                    case UsbConstants.USB_ENDPOINT_XFER_BULK:
                        if (mUsbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                            mUsbInEndpoint = mUsbEndpoint;
                            mEndpointIn = true;
                        } else {
                            mUsbOutEndpoint = mUsbEndpoint;
                            mEndpointOut = true;
                        }
                        break;
                }
            }
            if (mEndpointIn && mEndpointIn){
                break;
            }else{
                mEndpointIn=false;
                mEndpointOut=false;
            }
        }

        // Return an error if the endpoints are not properly found
        if (mEndpointIn && mEndpointOut) {
            // Setup the USB connection
            mUsbConnection = mUsbManager.openDevice(usbDevice);

            if (mUsbConnection != null) {
                // Force claim the interface
                if (mUsbConnection.claimInterface(mUsbInterface, true)) {
                    mOpened = true;
                    return true;//Open success!!
                } else {
                    Log.d(TAG, usbDevice.getDeviceName() + " - Claim Interface Failed");
                }
            } else {
                Log.d(TAG, usbDevice.getDeviceName() + " - Open Device Failed");
            }
        }
        close();
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    int open(int dev) {
        if (mUsbManager == null) {
            return ComManager.ERR_NOT_OPEN;
        }

        int id = dev;

        HashMap<String, UsbDevice> deviceHashMap = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceHashMap.values().iterator();

        while (deviceIterator.hasNext()) {
            usbDevice = deviceIterator.next();
            if (usbDevice.getDeviceId() == id) {
                if (openEndpoint()) {
                    if(mReconnectEvent) {
                        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
                        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
                        mContext.registerReceiver(mUsbReceiver, filter);
                    }
                    Log.d(TAG, "open() dev is : " + dev);
                    return 0;
                }
            }
        }

        return ComManager.ERR_NOT_OPEN;
    }


    int[] getUsbDevId(int vid, int pid){
        HashMap<String, UsbDevice> deviceHashMap = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator_for_num = deviceHashMap.values().iterator();
        int device_num=0;
        Arrays.fill(device_id, -1);
        while (deviceIterator_for_num.hasNext()) {
            usbDevice = deviceIterator_for_num.next();
            if (usbDevice.getVendorId() == vid && usbDevice.getProductId() == pid) {
                device_id[device_num]= usbDevice.getDeviceId();
                Log.i(TAG, "device_id["+device_num+"]:" + device_id[device_num]);
                device_num++;
            }
        }

        Log.i(TAG, "device_num:" + device_num);

        int[] sorted_dev = Arrays.copyOf(device_id, device_num);

        Arrays.sort(sorted_dev);
        /*for(int i=0; i < sorted_dev.length; i++) {
            Log.i(TAG, "sorted_dev["+i+"]:" + sorted_dev[i]);
        }*/

        return sorted_dev;
    }

    int close() {

        if (mUsbConnection != null) {
            mUsbConnection.releaseInterface(mUsbInterface);
            mUsbConnection.close();
            mUsbConnection = null;
        }

        if (mUsbOutEndpoint != null) {
            mUsbOutEndpoint = null;
        }

        if (mUsbInEndpoint != null) {
            mUsbInEndpoint = null;
        }

        if (mUsbInterface != null) {
            mUsbInterface = null;
        }

        mOpened = false;
        if(mReconnectEvent) {
            mContext.unregisterReceiver(mUsbReceiver);
        }
        Log.d(TAG, "close()");
        return 0;
    }

    boolean isOpened() {
        return mOpened;
    }

    String printLog(byte[] data, int dataLen)
    {
        StringBuilder log = new StringBuilder();
        if (dataLen > LOG_MAX_LENGTH) {
            for (int h = 0; h < LOG_MAX_LENGTH/2; h++) {
                log.append(String.format("%02X ", data[h] & 0xFF));
            }
            log.append("... ");
            for (int t = dataLen-(LOG_MAX_LENGTH/2); t < dataLen; t++) {
                log.append(String.format("%02X ", data[t] & 0xFF));
            }
        }else{
            for (int a = 0; a < dataLen; a++) {
                log.append(String.format("%02X ", data[a] & 0xFF));
            }
        }

        return log.toString();

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    protected int bulkWrite(byte[] data, int timeout) {
        try {
            if (mUsbConnection.claimInterface(mUsbInterface, true)) {
                Log.d(TAG, "TX["+data.length+"] = "+ printLog(data,data.length));
                int writelen = mUsbConnection.bulkTransfer(mUsbOutEndpoint, data, data.length, timeout);
                if (writelen > 0)
                    Log.d(TAG, "Write: done!!, written data length:"+writelen);
                return writelen;
            } else {
                mOpened = false;
                return -1;
            }
        }catch(Exception e) {
            mOpened = false;
            return -1;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    protected int bulkRead(byte[] data, int dataLength, int timeout) {
        try{
            if (mUsbConnection.claimInterface(mUsbInterface, true)) {
                int readlen = mUsbConnection.bulkTransfer(mUsbInEndpoint, data, dataLength, timeout);
                if (readlen > 0) {
                    Log.d(TAG, "RX[" + readlen + "] = "+printLog(data,readlen));
                    Log.d(TAG, "Read: done!!, read data length:"+readlen);
                }
                return readlen;
            }
            else {
                mOpened = false;
                return ComManager.ERR_NO_CONNECTED;
            }
        }
        catch(Exception e) {
            mOpened = false;
            return ComManager.ERR_NO_CONNECTED;
        }
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (device != null) {
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    if (device.getVendorId() == usbDevice.getVendorId() && device.getProductId() == usbDevice.getProductId()) {
                        usbDevice = device;
                        if (openEndpoint()) {
                            mOpened = true;
                            mOnComEventListener.onEvent(ComManager.EVENT_CONNECT);
                        }
                    }
                }
                else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    // call your method that cleans up and closes communication with the device
                    if (device == usbDevice) {
                        mOpened = false;
                        mOnComEventListener.onEvent(ComManager.EVENT_DISCONNECT);
                    }
                }
            }
        }
    };
}
