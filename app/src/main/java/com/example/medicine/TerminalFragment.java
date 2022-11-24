package com.example.medicine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainer;

import android.widget.TimePicker;

import com.google.android.material.internal.FlowLayout;


public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener{

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextView sendText2;
    private String medicineNum;



    //private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private String BTresponse;
    private String bufBTresponse;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_setup, container, false);

        View clear_Box = view.findViewById(R.id.clear_box);
        clear_Box.setOnClickListener(v -> send("111111"));

        View run_Motor_Once_btn = view.findViewById(R.id.run_motor_once);
        run_Motor_Once_btn.setOnClickListener(v -> send("222222"));

        View alarm_Once = view.findViewById(R.id.alarm_once);
        alarm_Once.setOnClickListener(v -> send("333333"));

        View alarm_in_eng = view.findViewById(R.id.alarm_in_Eng);
        alarm_in_eng.setOnClickListener(v -> send("444444"));

        View alarm_in_chinese = view.findViewById(R.id.alarm_in_Chinese);
        alarm_in_chinese.setOnClickListener(v -> send("555555"));


//           sendText2 = view.findViewById(R.id.send_text2); // this part is to confirm pw

//           view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE); // i comment it out because we are not using it.


        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        //sendText = view.findViewById(R.id.send_text); I comment it out because we created buttons to send the string command to the hardware instead of typing it into the EditText.

        //hexWatcher = new TextUtil.HexWatcher(sendText);
        //hexWatcher.enable(hexEnabled);
        //sendText.addTextChangedListener(hexWatcher);
        //sendText.setHint(hexEnabled ? "HEX mode" : "");
        //getActivity().onBackPressed(); //This line and the 4 lines of code above i have no idea what is it for, and i do not use it so i commented it out.

        //View sendBtn = view.findViewById(R.id.send_btn);
        //sendBtn.setOnClickListener(v -> send(sendText.getText().toString())); // After keying 6 digit numbers into the EditText and click on the senBtn, it will call the send function, and parse the numbers into string according to the send function. Pls refer the public void send(string str) below.


/*      view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);
        view.findViewById(R.id.unlockPWrow).setVisibility(View.GONE);
        view.findViewById(R.id.buttonrow).setVisibility(View.GONE);
        view.findViewById(R.id.textView2).setVisibility(View.GONE);*/ // All these codes i do not need it as well.

        receiveText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {

            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }


            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                    if ((receiveText.getText().toString().length() == 4) && (s.length() == 4)) { // im sorry have no idea what this block of codes are for
                        receiveText.removeTextChangedListener(this);
                        String fxBTresponse = receiveText.getText().toString();
                        Toast.makeText(getActivity(), fxBTresponse, Toast.LENGTH_SHORT).show();


                        // you can call or do what you want with your EditText here
                        Toast.makeText(getActivity(), "BTresponse...", Toast.LENGTH_SHORT).show();
                        view.findViewById(R.id.textView2).setVisibility(View.VISIBLE);
                        ((TextView) view.findViewById(R.id.textView2)).setText(medicineNum);

                        /*if (receiveText.getText().toString().charAt(1) == 'A') {
                            view.findViewById(R.id.confirmPWrow).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.unlockPWrow).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.buttonrow).setVisibility(View.VISIBLE);

                            ((TextView) view.findViewById(R.id.textView)).setText(R.string.TitleTextSet);
                            ((Button) sendBtn).setText(R.string.buttonSet);
                            Toast.makeText(getActivity(), "AVAILABLE!", Toast.LENGTH_SHORT).show();

                        } else if (receiveText.getText().toString().charAt(1) == 'B') {
                            view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);
                            view.findViewById(R.id.unlockPWrow).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.buttonrow).setVisibility(View.VISIBLE);
                            ((TextView) view.findViewById(R.id.textView)).setText(R.string.TitleText);
                            ((Button) sendBtn).setText(R.string.button);
                            Toast.makeText(getActivity(), "BOOKED!", Toast.LENGTH_SHORT).show();*/

                          if (receiveText.getText().toString().charAt(1) == 'O') {
                            Toast.makeText(getActivity(), "UNLOCKED!", Toast.LENGTH_SHORT).show();

                        } else if (receiveText.getText().toString().charAt(1) == 'E') {
                            Toast.makeText(getActivity(), "WRONG PASSWORD!", Toast.LENGTH_SHORT).show();

                        }
                        receiveText.removeTextChangedListener(this);
                        receiveText.setText("");
                        receiveText.addTextChangedListener(this);
                    }
                } catch (NumberFormatException e) {
                    //do whatever you like when value is incorrect
                }
            }
        });
        return view;
    }



    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            //hexWatcher.enable(hexEnabled);
            //sendText.setHint(hexEnabled ? "HEX mode" : "");
            //item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            Toast.makeText(getActivity(), "Connecting...", Toast.LENGTH_SHORT).show();
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
            medicineNum = device.getName().toUpperCase();


        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }



    private void send(String str) {

        //if the str is master key, reset code $MM#
        //the strings below are nothing specific, it's just a reference to the functions hard programmed into the hardware
        if (str.equals("111111")) {
            str = "$CL#";   //CLEAR BOX,NEED 10S
        } else if (str.equals("222222")) {
            str = "$AA#";   //motor run once
        } else if (str.equals("333333")) {
            str = "$AG#";   //ALARM  once
        } else if (str.equals("444444")) {
            str = "$EN#" + "$AA#";  //alarm in ENGLISH
            Toast.makeText(getActivity(), "In English", Toast.LENGTH_SHORT).show();  // To show a pop out that it is in English when this is pressed
        } else if (str.equals("555555")) {
            str = "$CH#" + "$AA#";  //alarm in CHINESE
            Toast.makeText(getActivity(), "In Chinese", Toast.LENGTH_SHORT).show();
        } else {
            str = '$' + str + '#';
        }
        /*"$BO#" ,BUZZER OPEN;
        "$BC#" ,BUZZER CLOSE;*/

        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if (hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);//This is sending text...
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if (hexEnabled) {
            //receiveText.append(TextUtil.toHexString(data) + '\n'); //hex text?
        } else {
            String msg = new String(data);


            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }

            //Will open when connect "$AA#" "$AE#", pop up to set password
            //If someone booked, will wait for password "$BA#" "$BI#"
            //if password correct, will unlock and reset
            //Master reset need to send $MM#
            //Password set of unlock is $PPPPPP#
            //limit password to 6 digits.

            receiveText.append(msg);
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn); //Status display
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        Toast.makeText(getActivity(), "connected...", Toast.LENGTH_SHORT).show();
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
        Toast.makeText(getActivity(), "Connection failed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
        Toast.makeText(getActivity(), "Connection lost!", Toast.LENGTH_SHORT).show();
    }

}

