package saioapi.comm.v2;

import android.util.Log;

import java.util.Arrays;

abstract class Connection {
    private final String TAG = "SaioComManager-Connection";

    private final int MAX_PACKET = 1024;
    private final int MAX_READ_PACKET = 1024;
    private final int MAX_RETRIES = 3;
    private int mProtocol;
    byte[] Receiveddata = new byte[MAX_PACKET];
    int Receivedlength = 0;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    abstract int open(int dev);
    abstract int[] getUsbDevId(int vid, int pid);
    abstract int close();
    protected abstract int bulkWrite(byte[] data, int timeout);
    protected abstract int bulkRead(byte[] data, int dataLength, int timeout);
    abstract void listener(int event);
    abstract boolean isOpened();
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private byte[] formatMessage(byte[] request) {
        byte lrc = 0;
        int i;

        byte[] bCmd = new byte[request.length + 3];

        // Add STX
        bCmd[0] = VNG.STX;

        // Add Command
        System.arraycopy(request, 0, bCmd, 1, request.length);

        // Add ETX
        bCmd[request.length + 1] = VNG.ETX;

        // Add LRC
        for (i = 1; i < request.length + 2; i++) {
            lrc = (byte) (lrc ^ bCmd[i]);
        }
        bCmd[i] = lrc;

        return bCmd;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private boolean validateLrc(byte[] response, int length) {
        byte lrc = 0;

        for (int i = 1; i < length - 1; i++) {
            lrc = (byte) (lrc ^ response[i]);
        }

        return (lrc == response[length - 1]);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public byte[] concat(byte[] a, int aLen, byte[] b, int bLen) {
        byte[] c = new byte[aLen + bLen];
        if (a != null) {
            System.arraycopy(a, 0, c, 0, aLen);
        }
        if (b != null) {
            System.arraycopy(b, 0, c, aLen, bLen);
        }
        return c;
    }

    public int connect(int protocol) {
            mProtocol= protocol;
            return 0;
    }
    public int getProtocol() {
        return mProtocol;
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public int VNG_write(byte[] request, int len, final int timeout) {
        int writeLen = ComManager.ERR_OPERATION;
        int retries;
        byte[] bCmd;
        byte[] Cmd;

        if (isOpened() == false)
            return ComManager.ERR_NOT_OPEN;

        try {
            Cmd = Arrays.copyOf(request,len);
            bCmd = formatMessage(Cmd);

            // Attempt to send command to the device
            // Only attempt retries if we are receiving NAKs
            for (retries = 0; retries < MAX_RETRIES; retries++) {
                writeLen = bulkWrite(bCmd, timeout);
                if (writeLen > 0) {
                    //Send command succeeded
                    break;
                }
                else {
                    //Send command failed
                    return ComManager.ERR_NOT_READY;
                }
            }

            // Send EOT if maximum retries was reached
            if (retries == MAX_RETRIES) {
                bulkWrite(new byte[]{VNG.EOT}, timeout);
                return ComManager.ERR_NOT_READY;
            }
            return request.length;
        }
        catch (Exception e) {
            Log.d(TAG, "usbSendAndGetResponse Exception");
            e.printStackTrace();
        }
        return ComManager.ERR_NOT_READY;
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public int RAW_DATA_write(byte[] request, int len, final int timeout) {
        int writeLen=ComManager.ERR_OPERATION;;
        byte[] bCmd;

        if (isOpened() == false)
            return ComManager.ERR_NOT_OPEN;

        try {
            bCmd = Arrays.copyOf(request,len);;
            writeLen = bulkWrite(bCmd, timeout);
            if (writeLen > 0) {
                return writeLen;
            }
            else {
                return ComManager.ERR_NOT_READY;
            }
        }
        catch (Exception e) {
            Log.d(TAG, "usbSendAndGetResponse Exception");
            e.printStackTrace();
        }
        return ComManager.ERR_NOT_READY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public int VNG_read (final int timeout) {
        byte[] response = new byte[MAX_READ_PACKET];
        byte[] fullPacket = null;
        int fullPacketLength = 0;
        int readLen;
        int writeLen;

        readLen = bulkRead(response, MAX_READ_PACKET, timeout);
        if (readLen != ComManager.ERR_NO_CONNECTED) {
            if (readLen > 0) {
                if (fullPacketLength == 0) {
                    if (response[0] == VNG.EOT || response[0] == VNG.ACK || response[0] == VNG.DLE
                            && readLen == 1) {
                        return 0;
                    }
                }
                // Append response to full packet
                fullPacket = concat(fullPacket, fullPacketLength, response, readLen);
                fullPacketLength = fullPacket.length;

                // Determine if full message was processed
                boolean fullPacketReceived = false;
                int i;
                for (i = 0; i < fullPacketLength; i++) {
                    if (fullPacket[i] == VNG.ETX) {
                        // Make sure we have the LRC in the next position
                        if (i == fullPacketLength - 2) {
                            fullPacketReceived = true;
                        }
                    } else if (fullPacket[i] == VNG.RS) {
                        // Check if we have enough data to read the tag payload length
                        if (i < fullPacketLength - 2) {
                            int payloadLength = ((int) fullPacket[i + 1] & 0xFF) << 8;
                            payloadLength += (int) fullPacket[i + 2] & 0xFF;
                            payloadLength += i + 5; // Processed message length + RS, LEN1, LEN2, ETX, LRC

                            if (payloadLength <= fullPacketLength) {
                                fullPacketReceived = true;
                            }
                        }
                    }
                }
                if (fullPacketReceived) {
                    // Process entirety of message now that it is all delivered
                    if (validateLrc(fullPacket, fullPacketLength)) {
                        writeLen = bulkWrite(new byte[]{VNG.ACK}, timeout);
                        if (writeLen > 0) {
                            // Return the actual response, not the entire byte array used to read the response
                            byte[] saturnResp = new byte[fullPacketLength - 3];
                            System.arraycopy(fullPacket, 1, saturnResp, 0, fullPacketLength - 3);
                            System.arraycopy(saturnResp, 0, Receiveddata, 0, saturnResp.length);
                            Receivedlength = saturnResp.length;
                            listener(ComManager.EVENT_DATA_READY);
                        }
                    } else {
                        bulkWrite(new byte[]{VNG.NAK}, timeout);
                    }
                }
            }
            return 0;

        }else{
            listener(ComManager.EVENT_DISCONNECT);
            return ComManager.ERR_NO_CONNECTED;
        }
    }

    public int RAW_DATA_read (final int timeout) {
        byte[] response = new byte[MAX_READ_PACKET];
        int readLen;

        readLen = bulkRead(response, MAX_READ_PACKET, timeout);
        if (readLen != ComManager.ERR_NO_CONNECTED) {
            if (readLen > 0) {
                System.arraycopy(response, 0, Receiveddata, 0, readLen);
                Receivedlength = readLen;
                listener(ComManager.EVENT_DATA_READY);
            }
            return 0;
        }else{
            listener(ComManager.EVENT_DISCONNECT);
            return ComManager.ERR_NO_CONNECTED;
        }
    }

    public int read (byte[] data,int len, int timout){
        if (Receivedlength > data.length || Receivedlength <= 0){
            return ComManager.ERR_OPERATION;
        }else{
            System.arraycopy(Receiveddata, 0, data, 0, Receivedlength);
            Arrays.fill(Receiveddata, (byte) 0);
            return Receivedlength;
        }
    }
}

