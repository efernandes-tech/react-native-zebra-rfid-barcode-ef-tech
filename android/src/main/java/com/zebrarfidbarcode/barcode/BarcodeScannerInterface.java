package com.zebrarfidbarcode.barcode;

import android.content.Context;
import android.util.Log;

import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;

public class BarcodeScannerInterface implements IDcsSdkApiDelegate {
  private static final String TAG = "BarcodeScannerInterface";
  private IBarcodeScannedListener listener;
  private SDKHandler sdkHandler;
  private ArrayList<DCSScannerInfo> scannerInfoList = new ArrayList<>();

  public BarcodeScannerInterface(IBarcodeScannedListener listener) {
    Log.d(TAG, "Initializing BarcodeScannerInterface");
    this.listener = listener;
  }

  public ArrayList<DCSScannerInfo> getAvailableScanners(Context context) {
    Log.d(TAG, "Getting available scanners");
    if (sdkHandler == null) {
      Log.d(TAG, "SDKHandler is null, initializing");
      sdkHandler = new SDKHandler(context);
    }

    DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC);
    Log.d(TAG, "DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC: " + result);

    sdkHandler.dcssdkSetDelegate(this);
    int notificationsMask = 0;
    notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value
        | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;

    notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value
        | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value
        | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value
        | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;

    Log.d(TAG, "Subscribing to events");
    sdkHandler.dcssdkSubsribeForEvents(notificationsMask);
    sdkHandler.dcssdkEnableAvailableScannersDetection(true);

    scannerInfoList.clear();
    sdkHandler.dcssdkGetAvailableScannersList(scannerInfoList);
    Log.d(TAG, "Available scanners: " + scannerInfoList.size());
    return scannerInfoList;
  }

  public boolean connectToScanner(int scannerID) {
    Log.d(TAG, "Connecting to scanner with ID: " + scannerID);
    try {
      DCSScannerInfo scanner = null;
      for (DCSScannerInfo info : scannerInfoList) {
        if (info.getScannerID() == scannerID) {
          scanner = info;
          break;
        }
      }
      if (scanner != null && scanner.isActive()) {
        Log.d(TAG, "Scanner is already active");
        return true;
      }

      DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkEstablishCommunicationSession(scannerID);
      boolean success = result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS;
      Log.d(TAG, "Connection result: " + success);
      return success;
    } catch (Exception e) {
      Log.e(TAG, "Error connecting to scanner: " + e.getMessage(), e);
      return false;
    }
  }

  public void onDestroy() {
    Log.d(TAG, "Destroying BarcodeScannerInterface");
    try {
      if (sdkHandler != null) {
        Log.d(TAG, "SDKHandler set to null");
        sdkHandler = null;
      }
    } catch (Exception e) {
      Log.e(TAG, "Error during onDestroy: " + e.getMessage(), e);
    }
  }

  @Override
  public void dcssdkEventScannerAppeared(DCSScannerInfo p0) {
    Log.d(TAG, "Scanner appeared: " + p0.getScannerName());
  }

  @Override
  public void dcssdkEventScannerDisappeared(int p0) {
    Log.d(TAG, "Scanner disappeared with ID: " + p0);
  }

  @Override
  public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo p0) {
    Log.d(TAG, "Communication session established with scanner: " + p0.getScannerName());
  }

  @Override
  public void dcssdkEventCommunicationSessionTerminated(int p0) {
    Log.d(TAG, "Communication session terminated with ID: " + p0);
  }

  @Override
  public void dcssdkEventBarcode(byte[] p0, int p1, int p2) {
    String barcode = new String(p0);
    Log.d(TAG, "Barcode scanned: " + barcode);
    listener.onBarcodeScanned(barcode);
  }

  @Override
  public void dcssdkEventImage(byte[] p0, int p1) {
    Log.d(TAG, "Image event received");
  }

  @Override
  public void dcssdkEventVideo(byte[] p0, int p1) {
    Log.d(TAG, "Video event received");
  }

  @Override
  public void dcssdkEventBinaryData(byte[] p0, int p1) {
    Log.d(TAG, "Binary data event received");
  }

  @Override
  public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent p0) {
    Log.d(TAG, "Firmware update event received");
  }

  @Override
  public void dcssdkEventAuxScannerAppeared(DCSScannerInfo p0, DCSScannerInfo p1) {
    Log.d(TAG, "Auxiliary scanner appeared: " + p0.getScannerName() + " Parent scanner: " + p1.getScannerName());
  }
}
