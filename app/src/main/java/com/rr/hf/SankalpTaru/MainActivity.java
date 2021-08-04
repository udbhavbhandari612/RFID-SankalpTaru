package com.rr.hf.SankalpTaru;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.rr.hf.SankalpTaru.listeners.MifareData;
import com.rr.hf.SankalpTaru.listeners.OnMifareDataListener;
import com.rr.hf.SankalpTaru.utils.Fx;
import com.rr.hf.SankalpTaru.utils.OEMHelper;
import com.rr.hf.SankalpTaru.utils.StringHelper;
import com.rr.hf.oem09operations.BleDeviceService;
import com.rr.hf.oem09operations.BleManager.BleManager;
import com.rr.hf.oem09operations.BleManager.Scanner;
import com.rr.hf.oem09operations.BleManager.ScannerCallback;
import com.rr.hf.oem09operations.DeviceManager.BleDevice;
import com.rr.hf.oem09operations.DeviceManager.ComByteManager;
import com.rr.hf.oem09operations.DeviceManager.DeviceManager;
import com.rr.hf.oem09operations.DeviceManager.DeviceManagerCallback;
import com.rr.hf.oem09operations.Exception.CardNoResponseException;
import com.rr.hf.oem09operations.Exception.DeviceNoResponseException;
import com.rr.hf.oem09operations.Tool.StringTool;
import com.rr.hf.oem09operations.card.Iso15693Card;
import com.rr.hf.oem09operations.card.Mifare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public static BleDevice sBleDevice;
    BleDeviceService mBleDeviceService;
    private Scanner mScanner;
    private TextView msgText;
    private Button beepBtn;
    private Button connectBtn;
    private Button inventory15693Btn;
    private Button inventory14443Btn;
    private Button read15693Btn;
    private Button write15693Btn;
    private Button read14443Btn;
    private Button write14443Btn;
    private Button renameDeviceBtn;

    //new fields specific read/write variables
    private Button btnCoreOps;
    private Button btnDataSpecificOps;
    private LinearLayout layoutCoreOps;
    private LinearLayout layoutDataSpecificOps;
    private Button readDataBtn;
    private Button writeDataBtn;
    private Button btnLocationFetch;
    private EditText editTextLocation;
    private EditText editTextTreeId;
    private EditText editTextTreeName;
    private EditText editTextSpecies;
    private EditText editTextTreeUrl;
    private EditText editTextBeneficiaryName;
    private TextView textViewDateOfPlantation;

    private MifareData mfData;
    private Location currentLocation;
    private final List<String> checkBoxesActive = new ArrayList<>();
    //new fields specific read/write variables

    private StringBuffer msgBuffer;
    private BluetoothDevice mNearestBle = null;
    private final Lock mNearestBleLock = new ReentrantLock();
    private int lastRssi = -100;

    private boolean readSuc = false;
    private ArrayAdapter<String> deviceListAdapter = null;
    private AlertDialog deviceRelatedDialog;
    private SharedPreferences sharedPreferences;


    private final ScannerCallback scannerCallback = new ScannerCallback() {
        @Override
        public void onReceiveScanDevice(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            super.onReceiveScanDevice(device, rssi, scanRecord);
            //search ble device and select the nearest device by rssi
            if ((scanRecord != null) && (StringTool.byteHexToSting(scanRecord).contains("017f5450"))) {  //filter ble device
                if (rssi < -70) {
                    return;
                }
                //added to avoid duplicate bluetooth devices in list
                //if(deviceAddress.size() > 0) {
                if (deviceListAdapter.getCount() > 0) {
                    boolean isIncluded = false;
                    for (int i = 0; i < deviceListAdapter.getCount(); i++) {
                        String cmpAddress = Objects.requireNonNull(deviceListAdapter.getItem(i)).substring(Objects.requireNonNull(deviceListAdapter.getItem(i)).indexOf("--") + 3, Objects.requireNonNull(deviceListAdapter.getItem(i)).lastIndexOf("--")).trim();
                        if (device.getAddress().trim().equals(cmpAddress)) {
                            isIncluded = true;
                            break;
                        }
                    }
                    /*for(int i = 0; i < deviceAddress.size(); i++) {
                        if(device.getAddress().trim().equals(deviceAddress.get(i).toString().trim())) {
                           isIncluded = true;
                           break;
                        }
                    }*/
                    if (!isIncluded) {
                        //deviceAddress.add(device.getAddress());
                        deviceListAdapter.add(device.getName() + " -- " + device.getAddress() + " -- " + rssi);
                    }
                } else {
                    //deviceAddress.add(device.getAddress());
                    deviceListAdapter.add(device.getName() + " -- " + device.getAddress() + " -- " + rssi);
                }
                //end added

                runOnUiThread(() -> deviceListAdapter.notifyDataSetChanged());
            }
        }

        @Override
        public void onScanDeviceStopped() {
            super.onScanDeviceStopped();
        }
    };

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            msgText.setText(msgBuffer);
            if ((sBleDevice.isConnection() == BleManager.STATE_CONNECTED) || ((sBleDevice.isConnection() == BleManager.STATE_CONNECTING))) {
                connectBtn.setText(getResources().getString(R.string.btn_disconnect));
                beepBtn.setEnabled(true);
                renameDeviceBtn.setEnabled(true);
                inventory15693Btn.setEnabled(true);
                inventory14443Btn.setEnabled(true);
                read15693Btn.setEnabled(true);
                write15693Btn.setEnabled(true);
                read14443Btn.setEnabled(true);
                write14443Btn.setEnabled(true);
                readDataBtn.setEnabled(true);
                writeDataBtn.setEnabled(true);
            } else {
                connectBtn.setText(getResources().getString(R.string.btn_search));
                beepBtn.setEnabled(false);
                renameDeviceBtn.setEnabled(false);
                inventory15693Btn.setEnabled(false);
                inventory14443Btn.setEnabled(false);
                read15693Btn.setEnabled(false);
                write15693Btn.setEnabled(false);
                read14443Btn.setEnabled(false);
                write14443Btn.setEnabled(false);
                readDataBtn.setEnabled(false);
                writeDataBtn.setEnabled(false);
            }

            switch (msg.what) {
                case 1:
                case 2:
                    break;
                case 3:
                    new Thread(() -> {
                        try {
                            byte versions = sBleDevice.getDeviceVersions();
                            msgBuffer.append(getResources().getString(R.string.text_version)).append(String.format("%02x", versions)).append("\r\n");
                            handler.sendEmptyMessage(0);
                            double voltage = sBleDevice.getDeviceBatteryVoltage();
                            msgBuffer.append(getResources().getString(R.string.text_power)).append(String.format(Locale.ENGLISH, "%.2f", voltage)).append("\r\n");
                            if (voltage < 3.61) {
                                msgBuffer.append(getResources().getString(R.string.text_power_low));
                            } else {
                                msgBuffer.append(getResources().getString(R.string.text_power_enough));
                            }
                            handler.sendEmptyMessage(0);
                            boolean isSuc = sBleDevice.androidFastParams(true);
                            if (isSuc) {
                                msgBuffer.append("\r\n").append(getResources().getString(R.string.text_fast_succeed));
                            } else {
                                msgBuffer.append("\n").append(getResources().getString(R.string.text_fast_failed));
                            }
                            handler.sendEmptyMessage(0);

                            /*msgBuffer.append("\n").append(getResources().getString(R.string.text_start_auto_search)).append("\r\n");
                            handler.sendEmptyMessage(0);*/
                            //start auto search
                            //startAutoSearchCard();
                        } catch (DeviceNoResponseException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
            }
        }
    };

    //device manager callback
    private final DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        @Override
        public void onReceiveConnectBtDevice(boolean blnIsConnectSuc) {
            super.onReceiveConnectBtDevice(blnIsConnectSuc);
            if (blnIsConnectSuc) {
                msgBuffer.delete(0, msgBuffer.length());
                msgBuffer.append(getResources().getString(R.string.text_connect_success)).append("\r\n");
                if (mNearestBle != null) {
                    msgBuffer.append(getResources().getString(R.string.text_device_name)).append(sBleDevice.getDeviceName()).append("\r\n");
                }
                msgBuffer.append(getResources().getString(R.string.text_rssi_value)).append(lastRssi).append("dB\r\n");
                msgBuffer.append(getResources().getString(R.string.text_sdk_version)).append(BleDevice.SDK_VERSIONS).append("\r\n");

                //sleep 500ms before send command
                try {
                    Thread.sleep(500L);
                    handler.sendEmptyMessage(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onReceiveDisConnectDevice(boolean blnIsDisConnectDevice) {
            ActivityCollector.finishAll();
            super.onReceiveDisConnectDevice(blnIsDisConnectDevice);
            msgBuffer.delete(0, msgBuffer.length());
            msgBuffer.append(getResources().getString(R.string.text_disconnect));
            handler.sendEmptyMessage(0);
        }

        @Override
        public void onReceiveConnectionStatus(boolean blnIsConnection) {
            super.onReceiveConnectionStatus(blnIsConnection);
        }

        @Override
        public void onReceiveInitCiphy(boolean blnIsInitSuc) {
            super.onReceiveInitCiphy(blnIsInitSuc);
        }

        @Override
        public void onReceiveDeviceAuth(byte[] authData) {
            super.onReceiveDeviceAuth(authData);
        }

        @Override
        //card is detected here
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus || cardType == BleDevice.CARD_TYPE_NO_DEFINE) {
                return;
            }

            System.out.println(getResources().getString(R.string.log_find_card) + "UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));
            /*if(isShow){

            }*/
        }

        @Override
        public void onReceiveRfmSentApduCmd(byte[] bytApduRtnData) {
            super.onReceiveRfmSentApduCmd(bytApduRtnData);
        }

        @Override
        public void onReceiveRfmClose(boolean blnIsCloseSuc) {
            super.onReceiveRfmClose(blnIsCloseSuc);
        }

        @Override
        //button callback
        public void onReceiveButtonEnter(byte keyValue) {
            if (keyValue == DeviceManager.BUTTON_VALUE_SHORT_ENTER) {
                msgBuffer.append(getResources().getString(R.string.text_button_short_press)).append("\r\n");
                handler.sendEmptyMessage(0);
            } else if (keyValue == DeviceManager.BUTTON_VALUE_LONG_ENTER) {
                msgBuffer.append(getResources().getString(R.string.text_button_long_press)).append("\r\n");
                handler.sendEmptyMessage(0);
            }
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleDeviceService = ((BleDeviceService.LocalBinder) service).getService();
            sBleDevice = mBleDeviceService.getBleDevice();
            mScanner = mBleDeviceService.getScanner();
            mBleDeviceService.setDeviceManagerCallback(deviceManagerCallback);
            mBleDeviceService.setScannerCallback(scannerCallback);

            //search ble device
            //searchNearestBleDevice(); - commented to avoid auto search, connect
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleDeviceService = null;
        }
    };


    //---------------------------------------------------------------------------------//
    //                              Lifecycle method overloads                         //
    //---------------------------------------------------------------------------------//

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.drawable.sankalptaru_logo_green);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        setContentView(R.layout.activity_main);

        msgBuffer = new StringBuffer();

        msgText = findViewById(R.id.logTxt);
        msgText.setTextIsSelectable(true);
        connectBtn = findViewById(R.id.btnConnect);
        beepBtn = findViewById(R.id.btnBeep);
        renameDeviceBtn = findViewById(R.id.btnRename);
        inventory15693Btn = findViewById(R.id.btnInventory15693);
        inventory14443Btn = findViewById(R.id.btnInventory14443);
        read15693Btn = findViewById(R.id.btnRead15693);
        write15693Btn = findViewById(R.id.btnWrite15693);
        read14443Btn = findViewById(R.id.btnRead14443);
        write14443Btn = findViewById(R.id.btnWrite14443);
        deviceListAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_ble_layout);

        //new fields specific read/write variables
        layoutCoreOps = findViewById(R.id.layoutCoreOps);
        layoutDataSpecificOps = findViewById(R.id.layoutDataSpecificOps);
        btnCoreOps = findViewById(R.id.btnCoreOperations);
        btnDataSpecificOps = findViewById(R.id.btnDataSpecificOps);
        readDataBtn = findViewById(R.id.read_data);
        writeDataBtn = findViewById(R.id.write_data);
        //new fields specific read/write variables

        connectBtn.setOnClickListener(new ConnectListener());
        beepBtn.setOnClickListener(new BeepListener());
        renameDeviceBtn.setOnClickListener(new RenameListener());
        inventory15693Btn.setOnClickListener(new Inventory15693Listener());
        inventory14443Btn.setOnClickListener(new Inventory14443Listener());
        read15693Btn.setOnClickListener(new Read15693Listener());
        write15693Btn.setOnClickListener(new Write15693Listener());
        read14443Btn.setOnClickListener(new Read14443Listener());
        write14443Btn.setOnClickListener(new Write14443Listener());

        sharedPreferences = getSharedPreferences("checks", 0);

        //init read/write buttons and data
        opsBtnInit();
        writeDataInit();
        readDataInit();
        setLocationListener();
        checkBleSupport();
        mfData = new MifareData();
        setRan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBleDeviceService != null) {
            mBleDeviceService.setScannerCallback(scannerCallback);
            mBleDeviceService.setDeviceManagerCallback(deviceManagerCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
    }

    /**
     * check first run
     */
    public boolean getFirstRun() {
        return sharedPreferences.getBoolean("checks", true);
    }

    /**
     * set first run
     */
    public void setRan() {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean("firstRun", false);
        edit.apply();
    }

    //---------------------------------------------------------------------------------//
    //                      New/Modified Data Specific Operations                      //
    //---------------------------------------------------------------------------------//

    /**
     * check gps is open
     *
     * @param context -current context
     * @return true gps is open
     */
    public static boolean gpsIsOPen(final Context context) {
        try {
            LocationManager locationManager
                    = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean gps = Objects.requireNonNull(locationManager).isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (gps || network) {
                return true;
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        return false;
    }

    private void setLocationListener() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!getFirstRun()) {
                Toast.makeText(MainActivity.this, "Location permissions missing", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            return;
        }
        List<String> providers = Objects.requireNonNull(lm).getAllProviders();
        for (String provider : providers) {
            lm.requestLocationUpdates(provider, 0, 0, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (currentLocation != null) {
                        if (currentLocation.getAccuracy() > location.getAccuracy())
                            currentLocation = location;
                    } else
                        currentLocation = location;
                }

                @Override
                public void onProviderEnabled(String s) {

                }

                @Override
                public void onProviderDisabled(String s) {

                }
            });
        }
    }

    //initialize operations button listeners
    private void opsBtnInit() {
        btnCoreOps.setOnClickListener(view -> {
            Drawable drawable;
            if (layoutCoreOps.isShown()) {
                Animation a = Fx.slide_up(MainActivity.this, layoutCoreOps);
                drawable = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.arrow_down_float);
                btnCoreOps.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        layoutCoreOps.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
            } else {
                drawable = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.arrow_up_float);
                btnCoreOps.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                Fx.slide_down(MainActivity.this, layoutCoreOps);
                layoutCoreOps.setVisibility(View.VISIBLE);
            }
        });
        btnDataSpecificOps.setOnClickListener(view -> {
            Drawable drawable;
            if (layoutDataSpecificOps.isShown()) {
                Animation a = Fx.slide_up(MainActivity.this, layoutDataSpecificOps);
                drawable = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.arrow_down_float);
                btnDataSpecificOps.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        layoutDataSpecificOps.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
            } else {
                drawable = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.arrow_up_float);
                btnDataSpecificOps.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                Fx.slide_down(MainActivity.this, layoutDataSpecificOps);
                layoutDataSpecificOps.setVisibility(View.VISIBLE);
            }
        });
    }

    //write data event
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void writeDataInit() {

        writeDataBtn.setOnClickListener(parentView -> {

            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialog = Objects.requireNonNull(inflater).inflate(R.layout.write_data, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("Write data to SankalpTaru RFID tag");
            myBuilder.setView(dialog);
            myBuilder.setCancelable(false);

            btnLocationFetch = dialog.findViewById(R.id.btnLocationFetch);
            Button btnLocationClear = dialog.findViewById(R.id.btnLocationClear);
            Button btnDateOfPlantation = dialog.findViewById(R.id.btnDateofPlantation);
            editTextLocation = dialog.findViewById(R.id.editTextLocation);
            editTextTreeId = dialog.findViewById(R.id.editTextTreeId);
            editTextTreeName = dialog.findViewById(R.id.editTextTreeName);
            editTextSpecies = dialog.findViewById(R.id.editTextSpecies);
            editTextTreeUrl = dialog.findViewById(R.id.editTextTreeUrl);
            editTextBeneficiaryName = dialog.findViewById(R.id.editTextBeneficiariesName);
            textViewDateOfPlantation = dialog.findViewById(R.id.textViewDateofPlantation);

            //fetch location btn listener
            btnLocationFetch.setOnClickListener(view -> {
                setLocationListener();
                btnLocationFetch.setClickable(false);
                if (!gpsIsOPen(MainActivity.this))
                    Toast.makeText(MainActivity.this, "Please turn on location", Toast.LENGTH_LONG).show();
                Location location;
                if (currentLocation != null)
                    location = currentLocation;
                else
                    location = getLocation(dialog.getContext());
                if (location != null) {
                    String loc = "latitude\t\t(" + location.getLatitude() + ")\nlongitude\t(" + location.getLongitude() + ")";
                    editTextLocation.setText(loc);
                }
                btnLocationFetch.setClickable(true);
            });

            //clear location btn listener
            btnLocationClear.setOnClickListener(view -> editTextLocation.setText(""));

            //pick plantation date btn listener
            btnDateOfPlantation.setOnClickListener(view -> {
                DatePickerDialog picker = new DatePickerDialog(MainActivity.this);
                picker.setOnDateSetListener((datePicker, year, month, date) -> {
                    month += 1;
                    String m = String.format("%s", month).length() > 1 ? String.format("%s", month) : 0 + String.format("%s", month);
                    String d = String.format("%s", date).length() > 1 ? String.format("%s", date) : 0 + String.format("%s", date);

                    String dt = d + "/" + m + "/" + year + " (dd/mm/yyyy)";
                    textViewDateOfPlantation.setText(dt);
                });
                picker.show();
            });

            myBuilder.setPositiveButton("Save", (writeDialog, which) -> writeDataToMifare());

            myBuilder.setNegativeButton("Close", (writeDialog, which) -> writeDialog.cancel());
            myBuilder.show();

        });

    }

    private void writeDataToMifare() {
        Map<String, byte[]> maps = new HashMap<>();

        if (editTextLocation.getText() != null && editTextLocation.getText().toString().trim().length() != 0) {
            String lat = editTextLocation.getText().toString().split("\n")[0];
            lat = lat.substring(lat.indexOf("(") + 1, lat.lastIndexOf(")"));

            String lon = editTextLocation.getText().toString().split("\n")[1];
            lon = lon.substring(lon.indexOf("(") + 1, lon.lastIndexOf(")"));

            maps.put("1", StringHelper.stringToBytes(lat, 16));
            maps.put("2", StringHelper.stringToBytes(lon, 16));
        }

        if (editTextTreeId.getText() != null && editTextTreeId.getText().toString().trim().length() != 0) {
            String treeId = editTextTreeId.getText().toString();
            maps.put("4", StringHelper.stringToBytes(treeId, 16));
        }

        if (editTextTreeName.getText() != null && editTextTreeName.getText().toString().trim().length() != 0) {
            String treeName = editTextTreeName.getText().toString();
            maps.put("5", StringHelper.stringToBytes(treeName, 16));
        }

        if (editTextSpecies.getText() != null && editTextSpecies.getText().toString().trim().length() != 0) {
            String species = editTextSpecies.getText().toString();
            maps.put("6", StringHelper.stringToBytes(species, 16));
        }

        if (textViewDateOfPlantation.getText().toString().contains("/")) {
            String date = textViewDateOfPlantation.getText().toString().split(" ")[0];
            maps.put("8", StringHelper.stringToBytes(date, 16));
        }

        if (editTextTreeUrl.getText() != null && editTextTreeUrl.getText().toString().trim().length() != 0) {
            String treeUrl = editTextTreeUrl.getText().toString();
            byte[] dataToWrite = StringHelper.stringToBytes(treeUrl, 128);
            int start = 0, end = 16, i = 9;
            while (end <= 128) {
                if ((i + 1) % 4 != 0) {
                    maps.put(String.format("%s", i), Arrays.copyOfRange(dataToWrite, start, end));
                    start += 16;
                    end += 16;
                }
                i++;
            }
        }

        if (editTextBeneficiaryName.getText() != null && editTextBeneficiaryName.getText().toString().trim().length() != 0) {
            String beneficiaryName = editTextBeneficiaryName.getText().toString();
            byte[] dataToWrite = StringHelper.stringToBytes(beneficiaryName, 32);
            maps.put("20", Arrays.copyOfRange(dataToWrite, 0, 16));
            maps.put("21", Arrays.copyOfRange(dataToWrite, 16, 32));
        }
        try {
            if (maps.size() != 0) {
                msgBuffer.delete(0, msgBuffer.length());
                msgBuffer.append("Please wait! writing data...\r\n");
                handler.sendEmptyMessage(0);

                mfData.setOnMifareDataListener(new OnMifareDataListener() {
                    @Override
                    public void onDataRead(Map<String, String> data) {

                    }

                    @Override
                    public void onDataWrite(boolean isSuccess) {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Data written successfully!\r\n");
                        handler.sendEmptyMessage(0);
                        readDataBtn.setEnabled(true);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data written successfully!", Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onDataDelete(boolean isSuccess) {

                    }

                    @Override
                    public void onError(String error) {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Error occurred!\r\n").append(error);
                        handler.sendEmptyMessage(0);
                        readDataBtn.setEnabled(true);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, new StringBuilder().append("Error occurred!\r\n").append("cause: ").append(error), Toast.LENGTH_LONG).show());
                    }
                });

                readDataBtn.setEnabled(false);
                OEMHelper.writeMifareCard(maps, sBleDevice, mfData);
            }

        } catch (Exception ex) {
            Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }


    }

    //read data event
    private void readDataInit() {
        readDataBtn.setOnClickListener(view -> {
            checkBoxesActive.clear();
            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.read_data, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("Data on SankalpTaru RFID tag");
            myBuilder.setView(dialogView);
            myBuilder.setCancelable(false);

            mfData.setOnMifareDataListener(new OnMifareDataListener() {
                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void onDataRead(final Map<String, String> data) {

                    runOnUiThread(() -> {
                        boolean isEmpty = true;

                        LinearLayout progress = dialogView.findViewById(R.id.linearLayoutProgress);
                        TableLayout tl = dialogView.findViewById(R.id.tableLayoutReadData);
                        TextView noData = new TextView(dialogView.getContext());
                        noData.setText(new StringBuilder().append("No data found"));
                        Button deleteSelected = new Button(dialogView.getContext());
                        deleteSelected.setTextColor(Color.rgb(211, 47, 47));
                        deleteSelected.setBackgroundColor(Color.rgb(250, 250, 250));
                        deleteSelected.setVisibility(View.GONE);

                        noData.setPadding(20, 20, 20, 20);

                        progress.setVisibility(View.GONE);

                        if (data.size() == 0) {
                            Toast.makeText(MainActivity.this, "Empty dataset", Toast.LENGTH_LONG).show();
                            tl.addView(noData);
                            return;
                        }
                        for (Map.Entry<String, String> entry : data.entrySet()) {
                            if (entry.getValue().trim().length() != 0) {
                                isEmpty = false;
                                break;
                            }
                        }
                        if (isEmpty) {
                            Toast.makeText(MainActivity.this, "Empty dataset", Toast.LENGTH_LONG).show();
                            tl.addView(noData);
                            return;
                        }

                        deleteSelected.setOnClickListener(v -> {
                            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
                            myBuilder.setTitle("Are you sure you want to delete following fields?");
                            myBuilder.setCancelable(false);

                            StringBuilder message = new StringBuilder();
                            for (String id : checkBoxesActive) {
                                message.append("◉ ").append(OEMHelper.getNameFromAccessor(id)).append("\n");
                            }
                            myBuilder.setMessage(message);

                            myBuilder.setPositiveButton("Delete", (dialog, which) -> {
                            });

                            myBuilder.setNegativeButton("Cancel", (dialog, which) -> {
                                dialog.cancel();
                            });
                            final AlertDialog dialog = myBuilder.create();
                            dialog.show();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v1 -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                                dialog.setTitle("Please wait");
                                dialog.setMessage("Deleting...");
                                List<Integer> blocks = new ArrayList<>();

                                for (String accessor : checkBoxesActive) {
                                    blocks.addAll(OEMHelper.getBlocksFromAccessor(accessor));
                                }

                                MifareData mfData = new MifareData();
                                mfData.setOnMifareDataListener(new OnMifareDataListener() {
                                    @Override
                                    public void onDataRead(Map<String, String> data) {

                                    }

                                    @Override
                                    public void onDataWrite(boolean isSuccess) {

                                    }

                                    @Override
                                    public void onDataDelete(boolean isSuccess) {
                                        runOnUiThread(() -> {
                                            dialog.setTitle(isSuccess ? "Success" : "Error");
                                            dialog.setMessage(isSuccess ? "All Data deleted successfully! To see the changes read the data again." : "There was some error deleting the data");
                                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                        });
                                    }

                                    @Override
                                    public void onError(String error) {
                                        runOnUiThread(() -> {
                                            dialog.setTitle("Error");
                                            dialog.setMessage(error);
                                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                        });
                                    }
                                });
                                OEMHelper.deleteDataMifareCard(sBleDevice, mfData, blocks);
                            });
                        });

                        Toast.makeText(MainActivity.this, "Data found!", Toast.LENGTH_LONG).show();
                        int width = tl.getWidth();

                        StringBuilder mapLink = new StringBuilder();
                        tl.addView(deleteSelected);
                        for (Map.Entry<String, String> entry : data.entrySet()) {
                            TableRow tr = new TableRow(dialogView.getContext());
                            CheckBox deleteBox = new CheckBox(dialogView.getContext());
                            deleteBox.setId(View.generateViewId());

                            deleteBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                if (isChecked) {
                                    checkBoxesActive.add(entry.getKey());
                                    if (checkBoxesActive.size() > 0 && deleteSelected.getVisibility() == View.GONE)
                                        deleteSelected.setVisibility(View.VISIBLE);
                                } else {
                                    checkBoxesActive.remove(entry.getKey());
                                    if (checkBoxesActive.size() == 0 && deleteSelected.getVisibility() == View.VISIBLE)
                                        deleteSelected.setVisibility(View.GONE);
                                }
                                deleteSelected.setText(new StringBuilder().append("Delete Selected (").append(checkBoxesActive.size()).append(")"));
                            });

                            View divider = new View(dialogView.getContext());
                            divider.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1));
                            divider.setPadding(10, 10, 10, 0);
                            divider.setBackgroundColor(Color.rgb(51, 51, 51));

                            tr.setPadding(10, 10, 10, 10);
                            TextView key = new TextView(dialogView.getContext());
                            TextView value = new TextView(MainActivity.this);

                            value.setPadding(10, 10, 0, 5);
                            key.setPadding(10, 10, 10, 5);

                            //key.setWidth((int) (width / 2.7));
                            deleteBox.setScaleX(0.80F);
                            deleteBox.setScaleY(0.80F);
                            value.setWidth((int) (width / 2));

                            key.setTextIsSelectable(true);
                            value.setTextIsSelectable(true);
                            value.setTextColor(Color.rgb(0, 0, 0));

                            if (entry.getKey().equals("latitude") || entry.getKey().equals("longitude")) {
                                if (entry.getValue().trim().length() == 0) {
                                    key.setText(new StringBuilder().append("Location"));
                                    value.setText("N/A");
                                    deleteBox.setEnabled(false);
                                    continue;
                                }
                                if (mapLink.length() == 0) {
                                    mapLink.append("<a href=\"https://www.google.com/maps/search/?api=1&query=")
                                            .append(data.get("latitude"))
                                            .append(Uri.encode(","))
                                            .append(data.get("longitude"))
                                            .append("\">")
                                            .append("latitude(")
                                            .append(Objects.requireNonNull(data.get("latitude")).trim().length() != 0 ? data.get("latitude") : "0.000000000")
                                            .append(")")
                                            .append(", ")
                                            .append("longitude(")
                                            .append(Objects.requireNonNull(data.get("longitude")).trim().length() != 0 ? data.get("longitude") : "0.000000000")
                                            .append(")")
                                            .append("</a>");
                                    key.setText(new StringBuilder().append("Location"));
                                    value.setClickable(true);
                                    value.setMovementMethod(LinkMovementMethod.getInstance());
                                    value.setText(Html.fromHtml(mapLink.toString(), Html.FROM_HTML_MODE_LEGACY));
                                } else {
                                    continue;
                                }
                            } else {
                                key.setText(OEMHelper.getNameFromAccessor(entry.getKey()));
                                if (entry.getValue().trim().length() != 0)
                                    value.setText(entry.getValue());
                                else {
                                    value.setText(new StringBuilder().append("N/A"));
                                    deleteBox.setEnabled(false);
                                }
                            }
                            if (entry.getKey().equals("tree_url")) {
                                Linkify.addLinks(value, Linkify.ALL);
                            }
                            tr.addView(deleteBox);
                            tr.addView(key);
                            tr.addView(value);
                            tl.addView(tr);
                            tl.addView(divider);
                        }
                    });

                }

                @Override
                public void onDataWrite(boolean isSuccess) {

                }

                @Override
                public void onDataDelete(boolean isSuccess) {

                }

                @Override
                public void onError(final String error) {
                    runOnUiThread(() -> {
                        LinearLayout progress = dialogView.findViewById(R.id.linearLayoutProgress);
                        TableLayout tl = dialogView.findViewById(R.id.tableLayoutReadData);
                        TextView noData = new TextView(dialogView.getContext());
                        String err = noData.getText().toString();
                        noData.setText(new StringBuilder().append(error).append(err));
                        noData.setPadding(20, 20, 20, 20);
                        tl.setPadding(20, 0, 20, 0);

                        progress.setVisibility(View.GONE);
                        tl.addView(noData);
                    });
                }
            });
            myBuilder.setNegativeButton("Close", (dialog, which) -> dialog.cancel());
            myBuilder.show();
            OEMHelper.readMifareCard(sBleDevice, mfData);
        });
    }

    private Location getLocation(final Context context) {
        Location lastLocation = null;
        try {
            LocationManager lm = (LocationManager) context.getSystemService(LOCATION_SERVICE);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                if (lm != null) {
                    lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }

            } else {
                Toast.makeText(MainActivity.this, "Location permissions missing", Toast.LENGTH_LONG).show();
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            }
            return lastLocation;

        } catch (Exception ex) {

            msgBuffer.append(ex.getMessage());
            handler.sendEmptyMessage(1);
            return null;
        }

    }

    //---------------------------------------------------------------------------------//
    //              below are all the core and pre implementations                     //
    //---------------------------------------------------------------------------------//

    //our listeners end here,
    //functions for read and write to card and tag
    //function to read from i-code tag
    private boolean read15693Card(final int blockToRead) {
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard();
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_ISO15693) {
                        final Iso15693Card iso15693Card = (Iso15693Card) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (iso15693Card != null) {
                            try {
                                byte[] bytes = iso15693Card.read((byte) blockToRead);
                                msgBuffer.append("Reading Data in I-CODE Tag From Block No. ").append(blockToRead).append(" : ").append(StringTool.getStringByBytes(bytes)).append("\r\n");
                                readSuc = true;
                                sBleDevice.stoptAutoSearchCard();
                                handler.sendEmptyMessage(0);
                            } catch (CardNoResponseException ex) {
                                readSuc = false;
                                msgBuffer.append("Error : Card is not responding or another problem..").append("\r\n").append("Reason : ").append(ex.getMessage()).append("\r\n");
                                sBleDevice.stoptAutoSearchCard();
                                handler.sendEmptyMessage(0);
                            }
                        }
                    } else {
                        readSuc = false;
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Some Problem Occurred in ISO 15693 Read Block, Perhaps, Card is not available !").append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
                sBleDevice.closeRf();
            } catch (DeviceNoResponseException de) {
                readSuc = false;
                handler.sendEmptyMessage(0);
                msgBuffer.append("Device not responding..").append("\r\n");
            }
        }).start());
        return readSuc;
    }

    //function to read data from mifare card
    private boolean readMifareCard(final int blockToRead) {
        readSuc = false;
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard();
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                        final Mifare mifare = (Mifare) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (mifare != null) {
                            try {
                                msgBuffer.append(getResources().getString(R.string.text_mifare_verify)).append("\r\n");
                                handler.sendEmptyMessage(0);
                                byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                                boolean auth = mifare.authenticate((byte) blockToRead, Mifare.MIFARE_KEY_TYPE_B, key);
                                if (auth) {
                                    byte[] bytes = mifare.read((byte) blockToRead);
                                    msgBuffer.append("Reading Data in Mifare Card From Block No. ").append(blockToRead).append(" : ").append(StringTool.getStringByBytes(bytes)).append("\r\n");
                                    StringBuilder data = new StringBuilder();
                                    for (byte aByte : bytes) {
                                        data.append((char) aByte);
                                    }
                                    msgBuffer.append("Data read is : ").append(data).append("\r\n");
                                    readSuc = true;
                                } else {
                                    readSuc = false;
                                    msgBuffer.append("Error : Authentication failed..").append("\r\n");
                                }
                                handler.sendEmptyMessage(0);
                            } catch (CardNoResponseException ex) {
                                readSuc = false;
                                msgBuffer.append("Error : Card is not responding or another problem..").append("\r\n").append("Reason : ").append(ex.getMessage()).append("\r\n");
                                handler.sendEmptyMessage(0);
                            }
                        }
                    } else {
                        readSuc = false;
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Some Problem Occurred in ISO 14443 Mifare Read Block, Perhaps, Card is not available !").append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
                sBleDevice.closeRf();
            } catch (DeviceNoResponseException de) {
                readSuc = false;
                msgBuffer.append("Device not responding..").append("\r\n");
                handler.sendEmptyMessage(0);
            }
        }).start());

        return readSuc;
    }

    //function to write data to i-code tag
    private boolean write15693Card(final int blockToWrite, final byte[] data) {
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard();
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_ISO15693) {
                        final Iso15693Card iso15693Card = (Iso15693Card) sBleDevice.getCard();
                        //card is matched, so, go with write operation.
                        if (iso15693Card != null) {
                            try {
                                readSuc = iso15693Card.write((byte) blockToWrite, data);
                                msgBuffer.append("Written Data in I-CODE TAG To Block No. ").append(blockToWrite).append(" : ").append(StringTool.byteHexToSting(data)).append("\r\n");
                                readSuc = true;
                                handler.sendEmptyMessage(0);
                            } catch (CardNoResponseException ex) {
                                readSuc = false;
                                msgBuffer.append("Error : Card is not responding or another problem..").append("\r\n").append("Reason : ").append(ex.getMessage()).append("\r\n");
                                handler.sendEmptyMessage(0);
                            }
                        }
                    } else {
                        readSuc = false;
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Some Problem Occurred in ISO 15693 Write Block, Perhaps, Card is not available !").append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
                sBleDevice.closeRf();
            } catch (DeviceNoResponseException de) {
                readSuc = false;
                msgBuffer.append("Device not responding..").append("\r\n");
                handler.sendEmptyMessage(0);
            }
        }).start());
        return readSuc;
    }

    //function to write data to mifare card
    private boolean write14443Card(final int blockToWrite, final byte[] data) {
        readSuc = false;
        sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> new Thread(() -> {
            try {
                boolean isFound = startAutoSearchCard();
                if (isFound) {
                    sBleDevice.stoptAutoSearchCard();
                    if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                        final Mifare mifare = (Mifare) sBleDevice.getCard();
                        //card is matched, so, go with inventory operation.
                        if (mifare != null) {
                            try {
                                msgBuffer.append(getResources().getString(R.string.text_mifare_verify)).append("\r\n");
                                //handler.sendEmptyMessage(0);
                                byte[] key = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
                                boolean auth = mifare.authenticate((byte) blockToWrite, Mifare.MIFARE_KEY_TYPE_A, key);
                                if (auth) {
                                    readSuc = mifare.write((byte) blockToWrite, data);
                                    msgBuffer.append("Written Data in Mifare Card To Block No. ").append(blockToWrite).append(" : ").append(StringTool.byteHexToSting(data)).append("\r\n");
                                    readSuc = true;
                                } else {
                                    readSuc = false;
                                    msgBuffer.append("Error : Authentication failed..").append("\r\n");
                                }
                                handler.sendEmptyMessage(0);
                            } catch (CardNoResponseException ex) {
                                readSuc = false;
                                msgBuffer.append("Error : Card is not responding or another problem..").append("\r\n").append("Reason : ").append(ex.getMessage()).append("\r\n");
                                handler.sendEmptyMessage(0);
                            }
                        }
                        sBleDevice.closeRf();
                    } else {
                        readSuc = false;
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append("Some Problem Occurred in Mifare Write Block, Perhaps, Card is not available !").append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }
            } catch (DeviceNoResponseException de) {
                readSuc = false;
                handler.sendEmptyMessage(0);
                msgBuffer.append("Device not responding..").append("\r\n");
            }
        }).start());

        return readSuc;
    }

    private void checkBleSupport() {
        //checking whether ble supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            String msg = msgText.getText() + "Bluetooth Low Energy Not Supported, So App can't work." + "\r\n";
            msgText.setText(msg);
            connectBtn.setEnabled(false);
        } else {
            //ble supported, so, initialize app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android M Permission check
                if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                } else {
                    //ble_nfc service initialization
                    Intent gattServiceIntent = new Intent(this, BleDeviceService.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                }
            } else {
                //ble_nfc service initialization
                Intent gattServiceIntent = new Intent(this, BleDeviceService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            }

            //ble_nfc service initialization
            Intent gattServiceIntent = new Intent(this, BleDeviceService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

            msgText.setText(R.string.OEM_version);
        }
    }

    private boolean startAutoSearchCard() throws DeviceNoResponseException {
        //open auto search, interval 20ms
        boolean isSuc;
        int falseCnt = 0;
        do {
            isSuc = sBleDevice.startAutoSearchCard((byte) 20, ComByteManager.ISO14443_P4);
        } while (!isSuc && (falseCnt++ < 1000));
        if (!isSuc) {
            //msgBuffer.delete(0, msgBuffer.length());
            msgBuffer.append(getResources().getString(R.string.text_open_auto_search_failed)).append("\r\n");
            handler.sendEmptyMessage(0);
        }
        return isSuc;
    }

    private void searchNearestBleDevice() {
        if (!mScanner.isScanning() && (sBleDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        mScanner.startScan(0);
                        mNearestBleLock.lock();
                        try {
                            mNearestBle = null;
                        } finally {
                            mNearestBleLock.unlock();
                        }
                        lastRssi = -100;

                        runOnUiThread(() -> {
                            deviceRelatedDialog = generateDeviceListDialog(false, false);
                            deviceRelatedDialog.setCanceledOnTouchOutside(false);
                            deviceRelatedDialog.show();
                        });
                        int searchCnt = 0;
                        while ((mNearestBle == null)
                                && (searchCnt < 10000)
                                && (mScanner.isScanning())
                                && (sBleDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
                            searchCnt++;
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (mScanner.isScanning() && (sBleDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
                            int myCnt = 0;
                            while ((myCnt < 60)
                                    && (mScanner.isScanning())
                                    && (sBleDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
                                myCnt++;
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                mScanner.stopScan();
                                mNearestBleLock.lock();
                            }
                            try {
                                mScanner.stopScan();
                                //if(deviceAddress.isEmpty() != true) {
                                if (!deviceListAdapter.isEmpty()) {
                                    runOnUiThread(() -> {
                                        deviceRelatedDialog.dismiss();
                                        deviceRelatedDialog = generateDeviceListDialog(true, true);
                                        deviceRelatedDialog.setCanceledOnTouchOutside(false);
                                        deviceRelatedDialog.show();
                                    });
                                } else {
                                    runOnUiThread(() -> {
                                        deviceRelatedDialog.dismiss();
                                        deviceRelatedDialog = generateDeviceListDialog(true, false);
                                        deviceRelatedDialog.setCanceledOnTouchOutside(false);
                                        deviceRelatedDialog.show();
                                    });
                                }
                            } finally {
                                mNearestBleLock.unlock();
                            }
                        } else {
                            mScanner.stopScan();
                        }
                    }
                }
            }).start();
        }
    }

    //function that process and returns dialogs as per the requirement for device search
    private AlertDialog generateDeviceListDialog(boolean isComplete, boolean isDevices) {
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog_scan_device, null);
        final AlertDialog.Builder deviceListDialog = new AlertDialog.Builder(MainActivity.this);
        deviceListDialog.setView(dialogView);
        TextView statusDevice = dialogView.findViewById(R.id.message);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress);
        LinearLayout listLayout = dialogView.findViewById(R.id.listPart);
        LinearLayout progressLayout = dialogView.findViewById(R.id.progressPart);
        statusDevice.setText(new StringBuilder("Searching Devices..."));

        if (!isComplete) {
            listLayout.setVisibility(View.GONE);
            progressLayout.setVisibility(View.VISIBLE);
        } else {
            if (!isDevices) {
                listLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                statusDevice.setText(new StringBuilder("Device Not Found, or Central mode is not supported in your device."));
            }
            if (isDevices) {
                //listScanDevices.setAdapter(deviceListAdapter);
                deviceListDialog.setAdapter(deviceListAdapter, (dialogInterface, i) -> {
                    //code or function to detect clicked device name and connect device.
                    //String myDeviceAddress = deviceAddress.get(i).toString();
                    String adapter = Objects.requireNonNull(deviceListAdapter.getItem(i));
                    String myDeviceAddress = adapter.substring(adapter.indexOf("--") + 3, adapter.lastIndexOf("--")).trim();
                    try {
                        //mScanner.stopScan();
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append(getResources().getString(R.string.text_connecting));
                        handler.sendEmptyMessage(0);
                        sBleDevice.requestConnectBleDevice(myDeviceAddress);
                        deviceListAdapter.clear();
                        //deviceAddress.clear();
                    } catch (Exception cex) {
                        msgBuffer.append("\r\n").append("Error in connecting Device !").append("\r\n").append(cex.getMessage());
                    }
                });
                progressLayout.setVisibility(View.GONE);
                listLayout.setVisibility(View.VISIBLE);
            }
        }

        deviceListDialog.setTitle("Select Device");

        deviceListDialog.setNegativeButton("Cancel", (dialogInterface, i) -> {
            if (mScanner.isScanning()) {
                mScanner.stopScan();
            }
            deviceListAdapter.clear();
            //deviceAddress.clear();
            msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
            handler.sendEmptyMessage(0);
            dialogInterface.dismiss();
        });

        deviceListDialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mScanner.isScanning()) {
                    mScanner.stopScan();
                }
                deviceListAdapter.clear();
                //deviceAddress.clear();
                msgBuffer.append("\r\n").append("You have not selected any device, select device to operate");
                handler.sendEmptyMessage(0);
                dialogInterface.dismiss();
                return true;
            }
            return false;
        });

        return deviceListDialog.create();
    }

    //function to change name of connected bluetooth device
    private String renameDevice(String newName) {
        if (newName.equals("")) {
            msgBuffer.append("\r\n").append("Blank Name Not Allowed.");
            handler.sendEmptyMessage(0);
            return newName;
        }
        try {
            if (sBleDevice.changeBleName(newName)) {
                msgBuffer.delete(0, msgBuffer.length());
                msgBuffer.append(getResources().getString(R.string.text_set_name_succeed)).append("\r\n");
                handler.sendEmptyMessage(0);
                return newName;
            } else {
                msgBuffer.delete(0, msgBuffer.length());
                msgBuffer.append(getResources().getString(R.string.text_set_name_failed)).append("\r\n");
                handler.sendEmptyMessage(0);
                return "";
            }
        } catch (Exception dex) {
            dex.printStackTrace();
            msgBuffer.delete(0, msgBuffer.length());
            msgBuffer.append("Error in Changing Device Name : ").append(dex.getMessage()).append("\r\n");
            handler.sendEmptyMessage(0);
            return "";
        }
    }

    //our button listeners
    //connect button event
    private class ConnectListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Button b = (Button) v;
            if (b.getText().toString().equals("disconnect")) {
                connectBtn.setText(getResources().getString(R.string.btn_search));
                beepBtn.setEnabled(false);
                inventory15693Btn.setEnabled(false);
                inventory14443Btn.setEnabled(false);
                read15693Btn.setEnabled(false);
                write15693Btn.setEnabled(false);
                read14443Btn.setEnabled(false);
                write14443Btn.setEnabled(false);
                readDataBtn.setEnabled(false);
                writeDataBtn.setEnabled(false);
                sBleDevice.requestDisConnectDevice();
            } else {
                searchNearestBleDevice();
                //generateDeviceListDialog();
            }
        }
    }

    //beep button event
    private class BeepListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            sBleDevice.requestOpenBeep(50, 50, 1, isSuc -> {
                if (isSuc) {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append(getResources().getString(R.string.text_open_beep_succeed));
                    handler.sendEmptyMessage(0);
                }
            });
        }
    }

    //rename ble device event
    private class RenameListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //taking prompt using dialogue
            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog3, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("New Device Name");
            myBuilder.setView(dialogView);
            myBuilder.setPositiveButton("OK", (dialog, which) -> {
                final EditText deviceName = dialogView.findViewById(R.id.device_name);
                new Thread(() -> {
                    String newName;
                    newName = renameDevice(deviceName.getText().toString());
                    if (!newName.equals("")) {
                        msgBuffer.append("New Device Name - ").append(newName).append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                }).start();
                dialog.cancel();
            });
            myBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            myBuilder.show();
        }
    }

    //click event for button inventory iso 15693.
    private class Inventory15693Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //search card
            sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> {
                if (cardType == DeviceManager.CARD_TYPE_ISO15693) {
                    //card is matched, so, go with inventory operation.
                    final Iso15693Card iso15693Card = (Iso15693Card) sBleDevice.getCard();
                    if (iso15693Card != null) {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append(getResources().getString(R.string.text_15693_find)).append(iso15693Card.uidToString()).append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                } else {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("Some Problem Occurred in ISO 15693 Inventory, Perhaps, Card is not available !").append("\r\n");
                    handler.sendEmptyMessage(0);
                }
            });
        }
    }

    //click event for button inventory iso 14443.
    private class Inventory14443Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //search card
            sBleDevice.requestRfmSearchCard(ComByteManager.ISO14443_P4, (blnIsSus, cardType, bytCardSn, bytCarATS) -> {
                if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                    //card is matched, so, go with inventory operation.
                    final Mifare mifare = (Mifare) sBleDevice.getCard();
                    if (mifare != null) {
                        msgBuffer.delete(0, msgBuffer.length());
                        msgBuffer.append(getResources().getString(R.string.text_mifare_find)).append(mifare.uidToString()).append("\r\n");
                        handler.sendEmptyMessage(0);
                    }
                } else {
                    msgBuffer.delete(0, msgBuffer.length());
                    msgBuffer.append("Some Problem Occurred in ISO 14443 Mifare Inventory, Perhaps, Mifare Card is not available !").append("\r\n");
                    handler.sendEmptyMessage(0);
                }
            });
        }
    }

    private class Read15693Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            try {
                //taking prompt using dialogue
                LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog1, null);
                AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
                myBuilder.setTitle("Block No.");
                myBuilder.setView(dialogView);
                myBuilder.setPositiveButton("OK", (dialog, which) -> {
                    EditText blkNum = dialogView.findViewById(R.id.block_no);
                    if (blkNum.getText().toString().matches("")) {
                        msgBuffer.append("\r\n").append("Block Number is missing...");
                        handler.sendEmptyMessage(0);
                        dialog.cancel();
                        return;
                    }
                    final int blockToRead = Integer.parseInt(blkNum.getText().toString());
                    boolean isMyRead = read15693Card(blockToRead);
                    if (isMyRead) {
                        msgBuffer.append("Data Read From I-CODE Tag Successful.").append("\r\n");
                    } else {
                        msgBuffer.append("Some problem Occurred..").append("\r\n");
                    }
                    handler.sendEmptyMessage(0);
                });
                myBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                myBuilder.show();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class Write15693Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //taking prompt using dialogue
            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog2, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("Block No. and Data(8 Byte Hex)");
            myBuilder.setView(dialogView);
            myBuilder.setPositiveButton("OK", (dialog, which) -> {
                EditText blkNum = dialogView.findViewById(R.id.block_no);
                EditText blkData = dialogView.findViewById(R.id.data_write);
                if (blkNum.getText().toString().matches("") || blkData.getText().toString().matches("")) {
                    msgBuffer.append("\r\n").append("Block Number or Data is missing...");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                final int blockToWrite = Integer.parseInt(blkNum.getText().toString());
                String blkDataString = blkData.getText().toString();
                byte[] TmpArray = new byte[blkDataString.length() / 2];
                try {
                    int i = 0;
                    int j = 0;
                    while (i < blkDataString.length()) {
                        int t = Integer.parseInt(blkDataString.substring(i, i + 2), 16);
                        TmpArray[j] = (byte) t;
                        i = i + 2;
                        j = j + 1;
                    }
                } catch (Exception ex) {
                    msgBuffer.append("\r\n").append("Invalid Data !");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                if (!blkNum.getText().toString().equals("")) {
                    boolean isMyWrite = write15693Card(blockToWrite, TmpArray);
                    if (isMyWrite) {
                        msgBuffer.append("Data Write From I-CODE Tag Successful.").append("\r\n");
                    } else {
                        msgBuffer.append("Some problem Occurred..").append("\r\n");
                    }
                    handler.sendEmptyMessage(0);
                }
            });
            myBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            myBuilder.show();
        }
    }

    private class Read14443Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //taking prompt using dialogue
            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog1, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("Block No.");
            myBuilder.setView(dialogView);
            myBuilder.setPositiveButton("OK", (dialog, which) -> {
                EditText blkNum = dialogView.findViewById(R.id.block_no);
                if (blkNum.getText().toString().matches("")) {
                    msgBuffer.append("\r\n").append("Block Number is missing...");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                final int blockToRead = Integer.parseInt(blkNum.getText().toString());
                if (!blkNum.getText().toString().equals("")) {
                    boolean isMyRead = readMifareCard(blockToRead);
                    if (isMyRead) {
                        msgBuffer.append("Data Read From Mifare Card Successful.").append("\r\n");
                    } else {
                        msgBuffer.append("Some problem occurred..").append("\r\n");
                    }
                    handler.sendEmptyMessage(0);
                }
            });
            myBuilder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            myBuilder.show();
        }
    }

    private class Write14443Listener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if ((sBleDevice.isConnection() != BleManager.STATE_CONNECTED)) {
                msgText.setText(getResources().getString(R.string.text_non_connect));
                return;
            }
            //taking prompt using dialogue
            LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View dialogView = Objects.requireNonNull(inflater).inflate(R.layout.layout_dialog2, null);
            AlertDialog.Builder myBuilder = new AlertDialog.Builder(MainActivity.this);
            myBuilder.setTitle("Block No. and Data(32 Byte Hex)");
            myBuilder.setView(dialogView);
            myBuilder.setPositiveButton("OK", (dialog, which) -> {
                EditText blkNum = dialogView.findViewById(R.id.block_no);
                EditText blkData = dialogView.findViewById(R.id.data_write);
                if (blkNum.getText().toString().matches("") || blkData.getText().toString().matches("")) {
                    msgBuffer.append("\r\n").append("Block Number or Data is missing...");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                if (blkNum.getText().toString().trim().equals("0") || blkNum.getText().toString().trim().equals("00")) {
                    msgBuffer.append("\r\n").append("This Block is Read Only !");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                if ((Integer.parseInt(blkNum.getText().toString().trim()) + 1) % 4 == 0) {
                    msgBuffer.append("\r\n").append("This is a Trailer(read-only) Block !");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                final int blockToWrite = Integer.parseInt(blkNum.getText().toString());
                String blkDataString = blkData.getText().toString();
                byte[] TmpArray = new byte[blkDataString.length() / 2];
                try {
                    int i = 0;
                    int j = 0;
                    while (i < blkDataString.length()) {
                        int t = Integer.parseInt(blkDataString.substring(i, i + 2), 16);
                        TmpArray[j] = (byte) t;
                        i = i + 2;
                        j = j + 1;
                    }
                } catch (Exception ex) {
                    msgBuffer.append("\r\n").append("Invalid Data : ").append(ex.getMessage()).append("\r\n");
                    handler.sendEmptyMessage(0);
                    dialog.cancel();
                    return;
                }
                if (!blkNum.getText().toString().equals("")) {
                    boolean isMyWrite = write14443Card(blockToWrite, TmpArray);
                    if (isMyWrite) {
                        msgBuffer.append("Data Write From Mifare Card Successful.").append("\r\n");
                    } else {
                        msgBuffer.append("Some problem occurred..").append("\r\n");
                    }
                    handler.sendEmptyMessage(0);
                }
            }).setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            myBuilder.show();
        }
    }
}