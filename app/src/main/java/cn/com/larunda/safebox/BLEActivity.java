package cn.com.larunda.safebox;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.larunda.safebox.R;
import com.larunda.titlebar.TitleBar;
import com.larunda.titlebar.TitleListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cn.com.larunda.safebox.adapter.BLEAdapter;
import cn.com.larunda.safebox.recycler.MyBLE;
import cn.com.larunda.safebox.util.BaseActivity;

import static cn.com.larunda.safebox.util.Util.bluetoothGatt;
import static cn.com.larunda.safebox.util.Util.isLinked;

public class BLEActivity extends BaseActivity implements View.OnClickListener {

    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final int REQUEST_ENABLE_BT = 1;

    private SharedPreferences preferences;
    private String token;
    private TitleBar titleBar;
    private Button searchButton;

    private RecyclerView recyclerView;
    private BLEAdapter adapter;
    private LinearLayoutManager manager;
    private List<MyBLE> bleList = new ArrayList<>();
    private BluetoothAdapter mBluetoothAdapter;

    private boolean isSearch = false;

    private List<BluetoothGattService> serviceList = new ArrayList<>();
    private List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
    private ArrayList<BluetoothGattCharacteristic> characteristicList = new ArrayList<>();

    private HashMap<String, BluetoothDevice> bluetoothDeviceArrayList = new HashMap<>();
    private List<BluetoothDevice> bluetoothDeviceList = new ArrayList<>();
    final BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (!bluetoothDeviceArrayList.containsKey(device.getAddress())) {
                bluetoothDeviceArrayList.put(device.getAddress(), device);
                bluetoothDeviceList.add(device);
                bleList.add(new MyBLE(device.getName(), 0));
                adapter.notifyDataSetChanged();
            }

        }
    };
    private BluetoothDevice bluetoothDevice;
    private int lastPosition;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        initView();
        initEvent();

        //开启位置服务，支持获取ble蓝牙扫描结果
        if (Build.VERSION.SDK_INT >= 23 && !isLocationOpen(getApplicationContext())) {
            Intent enableLocate = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(enableLocate, REQUEST_LOCATION_PERMISSION);
        } else {
            initBLE();
        }
    }

    /**
     * 初始化蓝牙
     */
    private void initBLE() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        //判断是否已经连接蓝牙
        if (bluetoothGatt != null && bluetoothGatt.connect() && isLinked) {
            BluetoothDevice device = bluetoothGatt.getDevice();
            bluetoothDeviceArrayList.put(device.getAddress(), device);
            bluetoothDeviceList.add(device);
            bleList.add(new MyBLE(device.getName(), 2));
            adapter.notifyDataSetChanged();
            lastPosition = 0;
            isLinked = true;
        }
    }


    /**
     * 初始化点击事件
     */
    private void initEvent() {
        titleBar.setOnClickListener(new TitleListener() {
            @Override
            public void onLeftButtonClickListener(View v) {
            }

            @Override
            public void onLeftBackButtonClickListener(View v) {
                finish();
            }

            @Override
            public void onRightButtonClickListener(View v) {

            }
        });

        searchButton.setOnClickListener(this);

        adapter.setBleOnClickListener(new BLEAdapter.BLEOnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClickListener(View v, final int position) {
                if (mBluetoothAdapter != null) {
                    mBluetoothAdapter.stopLeScan(callback);
                    searchButton.setBackground(getDrawable(R.color.menu_sz_color));
                    searchButton.setText("搜索");
                }

                bluetoothDevice = bluetoothDeviceList.get(position);
                /**
                 * 判断是否已经链接蓝牙
                 */
                if (bluetoothGatt != null && bluetoothGatt.connect()) {
                    /**
                     * 如果是链接的当前设备则不做处理
                     */

                    if (TextUtils.equals(bluetoothDevice.getAddress(), bluetoothGatt.getDevice().getAddress())) {

                        return;
                    } else {
                        bluetoothGatt.disconnect();
                        bluetoothGatt = null;
                        bleList.get(lastPosition).setStatus(0);
                    }
                }
                isLinked = false;
                lastPosition = position;
                bleList.get(position).setStatus(1);
                adapter.notifyDataSetChanged();
                bluetoothGatt = bluetoothDevice.connectGatt(getApplicationContext(), true, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, final int status, int newState) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    bleList.get(lastPosition).setStatus(1);
                                    adapter.notifyDataSetChanged();
                                }
                            });
                            gatt.discoverServices();
                        } else if (newState == BluetoothGatt.STATE_CONNECTING) {
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            Log.d("main","断开");
                            isLinked = false;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    bleList.get(lastPosition).setStatus(0);
                                    adapter.notifyDataSetChanged();
                                }
                            });

                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);
                        serviceList = gatt.getServices();
                        for (BluetoothGattService service : serviceList) {
                            characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                characteristicList.add(characteristic);
                                gatt.setCharacteristicNotification(characteristic, true);
                                //解决收不到数据
                                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                }

                            }

                        }

                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        super.onCharacteristicRead(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        super.onCharacteristicChanged(gatt, characteristic);
                        String content = new String(characteristic.getValue()).trim();
                        writeCharacteristic(String.valueOf(characteristic.getUuid()), content.replace("?", "").replace(":", "").getBytes());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isLinked = true;
                                bleList.get(lastPosition).setStatus(2);
                                adapter.notifyDataSetChanged();
                            }
                        });


                    }
                });


            }
        });
    }

    /**
     * 初始化view
     */
    private void initView() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        token = preferences.getString("token", null);

        searchButton = findViewById(R.id.ble_search);

        recyclerView = findViewById(R.id.ble_recycler);
        adapter = new BLEAdapter(this, bleList);
        manager = new LinearLayoutManager(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(manager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        titleBar = findViewById(R.id.ble_title_bar);
        titleBar.setTextViewText("蓝牙列表");
        titleBar.setLeftButtonVisible(View.GONE);
        titleBar.setRightButtonSrc(0);
        titleBar.setLeftBackButtonVisible(View.VISIBLE);
    }

    /**
     * 判断位置信息是否开启
     *
     * @param context
     * @return
     */
    public static boolean isLocationOpen(final Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        //gps定位
        boolean isGpsProvider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        //网络定位
        boolean isNetWorkProvider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return isGpsProvider || isNetWorkProvider;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (isLocationOpen(getApplicationContext())) {
                    initBLE();
                } else {
                    Toast.makeText(this, "需要打开位置服务,才能使用蓝牙功能", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this, "需要打开蓝牙,才能使用此功能", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    /**
     * 点击事件监听
     *
     * @param v
     */
    @SuppressLint("NewApi")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ble_search:
                if (isSearch) {

                } else {
                    mBluetoothAdapter.startLeScan(callback);
                    isSearch = true;
                    searchButton.setBackground(getDrawable(R.color.ziblack2));
                    searchButton.setText("正在搜索");
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mBluetoothAdapter.stopLeScan(callback);
                                    isSearch = false;
                                    searchButton.setBackground(getDrawable(R.color.menu_sz_color));
                                    searchButton.setText("搜索");
                                }
                            });

                        }
                    }, 4000);
                }
                break;
        }
    }

    /**
     * 写入数据的方法
     *
     * @param uuid
     * @param bytes
     * @return
     */
    public boolean writeCharacteristic(String uuid, byte[] bytes) {
        if (null == characteristics) {
            return false;
        }
        if (bluetoothGatt != null) {
            for (BluetoothGattCharacteristic characteristic : characteristicList) {
                /*Log.d("main",characteristic.getUuid().toString());*/
                //判断是否为协议约定的特征Characteristic
                if (TextUtils.equals(uuid, characteristic.getUuid().toString())) {
                    characteristic.setValue(bytes);
                    return bluetoothGatt.writeCharacteristic(characteristic);
                }
            }

        }
        return false;
    }

    @Override
    protected void onDestroy() {
        //判断蓝牙是否已经链接成功
        if (!isLinked && bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt = null;
        }
        super.onDestroy();
    }
}
