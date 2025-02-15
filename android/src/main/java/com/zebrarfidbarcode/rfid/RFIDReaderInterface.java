package com.zebrarfidbarcode.rfid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;

import java.util.ArrayList;

public class RFIDReaderInterface implements RfidEventsListener {
  private final IRFIDReaderListener listener;
  private final String TAG = "RFIDReaderInterface";
  private Readers readers;
  private ArrayList<ReaderDevice> availableRFIDReaderList;
  public static ReaderDevice readerDevice;
  private RFIDReader reader;

  public RFIDReaderInterface(IRFIDReaderListener listener) {
    this.listener = listener;
    Log.d(TAG, "RFIDReaderInterface initialized.");
  }

  public ArrayList<ReaderDevice> getAvailableReaders() {
    try {
      Log.d(TAG, "Fetching available RFID readers.");
      return readers.GetAvailableRFIDReaderList();
    } catch (Exception e) {
      Log.e(TAG, "Error fetching RFID readers: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  public void connect(Context context, String scannerName) {
    Log.d(TAG, "Initializing readers and attempting connection to: " + scannerName);
    readers = new Readers(context, ENUM_TRANSPORT.ALL);
    try {
      availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
      if (availableRFIDReaderList != null && !availableRFIDReaderList.isEmpty()) {
        for (ReaderDevice rfid : availableRFIDReaderList) {
          Log.d(TAG, "Available reader found: " + rfid.getName());
          if (rfid.getName().equals(scannerName)) {
            readerDevice = rfid;
            reader = readerDevice.getRFIDReader();
            if (!reader.isConnected()) {
              reader.connect();
              Log.d(TAG, "Successfully connected to: " + scannerName);
              configureReader();
            } else {
              Log.d(TAG, "Reader already connected: " + scannerName);
            }
          }
        }
      } else {
        Log.w(TAG, "No RFID readers found.");
      }
    } catch (InvalidUsageException | OperationFailureException e) {
      Log.e(TAG, "Error connecting to reader: " + e.getMessage());
    }
  }

  private void configureReader() {
    Log.d(TAG, "Configuring reader...");
    if (reader.isConnected()) {
      TriggerInfo triggerInfo = new TriggerInfo();
      triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
      triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
      try {
        reader.Events.addEventsListener(this);
        reader.Events.setHandheldEvent(true);
        reader.Events.setTagReadEvent(true);
        reader.Events.setAttachTagDataWithReadEvent(false);
        reader.Config.setStartTrigger(triggerInfo.StartTrigger);
        reader.Config.setStopTrigger(triggerInfo.StopTrigger);
        Log.d(TAG, "Reader configured successfully.");
      } catch (InvalidUsageException | OperationFailureException e) {
        Log.e(TAG, "Error configuring reader: " + e.getMessage());
      }
    } else {
      Log.w(TAG, "Reader is not connected. Configuration skipped.");
    }
  }

  @Override
  public void eventReadNotify(RfidReadEvents rfidReadEvents) {
    Log.d(TAG, "Received read event notification.");
    TagData[] readTags = reader.Actions.getReadTags(100);
    if (readTags != null) {
      ArrayList<String> listTags = new ArrayList<>();
      for (TagData myTag : readTags) {
        String tagID = myTag.getTagID();
        if (tagID != null) {
          listTags.add(tagID);
          Log.d(TAG, "Tag read: " + tagID);
        }
      }
      listener.onRFIDRead(listTags);
    } else {
      Log.w(TAG, "No tags read in the event notification.");
    }
  }

  @SuppressLint("StaticFieldLeak")
  public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
    Log.d(TAG, "Status notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
    if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
      if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData
          .getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
        Log.d(TAG, "Handheld trigger pressed.");
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            try {
              reader.Actions.Inventory.perform();
              Log.d(TAG, "Inventory started successfully.");
            } catch (InvalidUsageException | OperationFailureException e) {
              Log.e(TAG, "Error starting inventory: " + e.getMessage());
            }
            return null;
          }
        }.execute();
      } else if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData
          .getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
        Log.d(TAG, "Handheld trigger released.");
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            try {
              reader.Actions.Inventory.stop();
              Log.d(TAG, "Inventory stopped successfully.");
            } catch (InvalidUsageException | OperationFailureException e) {
              Log.e(TAG, "Error stopping inventory: " + e.getMessage());
            }
            return null;
          }
        }.execute();
      }
    }
  }

  private String getMemBankData(String memoryBankData, ACCESS_OPERATION_STATUS opStatus) {
    Log.d(TAG, "Access operation status: " + opStatus);
    return (opStatus != ACCESS_OPERATION_STATUS.ACCESS_SUCCESS) ? opStatus.toString() : memoryBankData;
  }

  public void onDestroy() {
    Log.d(TAG, "Destroying RFIDReaderInterface.");
    try {
      reader.Events.removeEventsListener(this);
      reader.disconnect();
      reader.Dispose();
      readers.Dispose();
      Log.d(TAG, "Reader and resources disposed successfully.");
    } catch (Exception e) {
      Log.e(TAG, "Error during cleanup: " + e.getMessage());
    }
  }
}
