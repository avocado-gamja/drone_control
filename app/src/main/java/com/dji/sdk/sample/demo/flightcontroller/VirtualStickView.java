package com.dji.sdk.sample.demo.flightcontroller;

import android.app.Service;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.OnScreenJoystickListener;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.OnScreenJoystick;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.view.PresentableView;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.KeyManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.Simulator;

import com.dji.sdk.sample.internal.utils.TcpServerUtil;

//Ahn part for controlling drone

/**
 * Class for virtual stick.
 */
public class VirtualStickView extends RelativeLayout implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, PresentableView, TcpServerUtil.TcpCommandListener {
    private Button btnEnableVirtualStick;
    private Button btnDisableVirtualStick;
    private Button btnEnableServer;
    private Button btnDisableServer;
    private Button btnHorizontalCoordinate;
    private Button btnSetYawControlMode;
    private Button btnSetVerticalControlMode;
    private Button btnSetRollPitchControlMode;
    private ToggleButton btnSimulator;
    private Button btnTakeOff;
    private Button btnLand;
    private Button btnMoveForward;
    private Button btnMoveBackward;
    private Button btnMoveLeft;
    private Button btnMoveRight;
    private Button btnMoveUp;
    private Button btnMoveDown;
    private Button btnRotateLeft;
    private Button btnRotateRight;
    private Button btnRotateStop;


    private TextView textView;

    private OnScreenJoystick screenJoystickRight;
    private OnScreenJoystick screenJoystickLeft;

    private Timer sendVirtualStickDataTimer;
    private SendVirtualStickDataTask sendVirtualStickDataTask;

    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;
    private boolean isSimulatorActived = false;
    private FlightController flightController = null;


    private boolean isMoving = false;
    //회전
    private boolean isRotatingLeft = false;
    private boolean isRotatingRight = false;
    // 전/후진 제어용 변수
    private boolean isMovingForward = false;
    private boolean isMovingBackward = false;

    // 좌/우 이동 제어용 변수
    private boolean isMovingLeft = false;
    private boolean isMovingRight = false;

    // 상승/하강 제어용 변수
    private boolean isMovingUp = false;
    private boolean isMovingDown = false;


    private static final int TCP_PORT = 6689; // TCP 서버 포트 번호
    private TcpServerUtil tcpServer;


    private boolean isServerRunning = false;

    private Simulator simulator = null;

    @Override
    public void onCommandReceived(String command) {
        DialogUtils.showDialog(getContext(), "Command: " + command);
        // 명령 처리
        processCommand(command);
    }
    // DroneMovementState enum 추가
    public enum DroneMovementState {
        STOP,
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        ROTATE_LEFT,
        ROTATE_RIGHT,
        UP,
        DOWN
    }


    private DroneMovementState currentMovementState = DroneMovementState.STOP;

    private void setDroneMovementState(DroneMovementState state) {
        currentMovementState = state;
        isMoving = (state != DroneMovementState.STOP);
        resetAllMovementFlags();

        switch(state) {
            case FORWARD:
                isMovingForward = true;
                break;
            case BACKWARD:
                isMovingBackward = true;
                break;
            case LEFT:
                isMovingLeft = true;
                break;
            case RIGHT:
                isMovingRight = true;
                break;
            case ROTATE_LEFT:
                isRotatingLeft = true;
                break;
            case ROTATE_RIGHT:
                isRotatingRight = true;
                break;
            case UP:
                isMovingUp = true;
                break;
            case DOWN:
                isMovingDown = true;
                break;
        }

        sendControlData();
    }

    private void resetAllMovementFlags() {
        isMovingForward = false;
        isMovingBackward = false;
        isRotatingLeft = false;
        isRotatingRight = false;
        isMovingLeft = false;
        isMovingRight = false;
        isMovingUp = false;
        isMovingDown = false;
    }


    public VirtualStickView(Context context) {
        super(context);
        init(context);
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setUpListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (null != sendVirtualStickDataTimer) {
            if (sendVirtualStickDataTask != null) {
                sendVirtualStickDataTask.cancel();

            }
            sendVirtualStickDataTimer.cancel();
            sendVirtualStickDataTimer.purge();
            sendVirtualStickDataTimer = null;
            sendVirtualStickDataTask = null;
        }
        tearDownListeners();
        if (tcpServer != null) {
            tcpServer.stopServer(); // 서버 종료
        }
        super.onDetachedFromWindow();

    }

    private void init(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_virtual_stick, this, true);
        initParams();
        initUI();
        // TCP 서버 초기화 및 시작
        tcpServer = new TcpServerUtil(TCP_PORT, this);

    }

    private void initParams() {
        // We recommand you use the below settings, a standard american hand style.
        if (flightController == null) {
            if (ModuleVerificationUtil.isFlightControllerAvailable()) {
                flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            }
        }
        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        // Check if the simulator is activated.
        if (simulator == null) {
            simulator = ModuleVerificationUtil.getSimulator();
        }
        isSimulatorActived = simulator.isSimulatorActive();

    }

    private void initUI() {
        btnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        btnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        btnEnableServer = (Button) findViewById(R.id.btn_enable_server_command);
        btnDisableServer = (Button) findViewById(R.id.btn_disable_server_command);
        btnHorizontalCoordinate = (Button) findViewById(R.id.btn_horizontal_coordinate);
        btnSetYawControlMode = (Button) findViewById(R.id.btn_yaw_control_mode);
        btnSetVerticalControlMode = (Button) findViewById(R.id.btn_vertical_control_mode);
        btnSetRollPitchControlMode = (Button) findViewById(R.id.btn_roll_pitch_control_mode);
        btnTakeOff = (Button) findViewById(R.id.btn_take_off);
        btnLand = (Button) findViewById(R.id.btn_Land);
        btnMoveForward = (Button) findViewById(R.id.btn_move_forward);
        btnMoveBackward = (Button) findViewById(R.id.btn_move_backward);
        btnMoveLeft = (Button) findViewById(R.id.btn_move_left);
        btnMoveRight = (Button) findViewById(R.id.btn_move_right);
        btnMoveUp = (Button) findViewById(R.id.btn_move_up);
        btnMoveDown = (Button) findViewById(R.id.btn_move_down);
        btnRotateLeft = (Button) findViewById(R.id.btn_rotate_left);
        btnRotateRight = (Button) findViewById(R.id.btn_rotate_right);
        btnRotateStop = (Button) findViewById(R.id.btn_rotate_stop);

        btnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);

        textView = (TextView) findViewById(R.id.textview_simulator);

        screenJoystickRight = (OnScreenJoystick) findViewById(R.id.directionJoystickRight);
        screenJoystickLeft = (OnScreenJoystick) findViewById(R.id.directionJoystickLeft);

        btnEnableVirtualStick.setOnClickListener(this);
        btnDisableVirtualStick.setOnClickListener(this);
        btnEnableServer.setOnClickListener(this);
        btnDisableServer.setOnClickListener(this);
        btnHorizontalCoordinate.setOnClickListener(this);
        btnSetYawControlMode.setOnClickListener(this);
        btnSetVerticalControlMode.setOnClickListener(this);
        btnSetRollPitchControlMode.setOnClickListener(this);
        btnTakeOff.setOnClickListener(this);
        btnSimulator.setOnCheckedChangeListener(VirtualStickView.this);
        btnTakeOff.setOnClickListener(this);
        btnLand.setOnClickListener(this);
        btnMoveForward.setOnClickListener(this);
        btnMoveBackward.setOnClickListener(this);
        btnMoveLeft.setOnClickListener(this);
        btnMoveRight.setOnClickListener(this);
        btnMoveUp.setOnClickListener(this);
        btnMoveDown.setOnClickListener(this);
        btnRotateLeft.setOnClickListener(this);
        btnRotateRight.setOnClickListener(this);
        btnRotateStop.setOnClickListener(this);

        if (isSimulatorActived) {
            btnSimulator.setChecked(true);
            textView.setText("Simulator is On.");
        }
    }

    private void setUpListeners() {
        if (simulator != null) {
            simulator.setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(@NonNull final SimulatorState simulatorState) {
                    ToastUtils.setResultToText(textView,
                            "Yaw : "
                                    + simulatorState.getYaw()
                                    + ","
                                    + "X : "
                                    + simulatorState.getPositionX()
                                    + "\n"
                                    + "Y : "
                                    + simulatorState.getPositionY()
                                    + ","
                                    + "Z : "
                                    + simulatorState.getPositionZ());
                }
            });
        } else {
            ToastUtils.setResultToToast("Simulator disconnected!");
        }

        screenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                float pitchJoyControlMaxSpeed = 5;
                float rollJoyControlMaxSpeed = 5;

                pitch = pitchJoyControlMaxSpeed * pY;
                roll = rollJoyControlMaxSpeed * pX;

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 50, 100);
                }
            }
        });

        screenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if (Math.abs(pX) < 0.02) {
                    pX = 0;
                }

                if (Math.abs(pY) < 0.02) {
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 4;
                float yawJoyControlMaxSpeed = 10;

                if (isRotatingLeft) {
                    yaw = -yawJoyControlMaxSpeed; // 왼쪽으로 회전하는 값
                } else if (isRotatingRight) {
                    yaw = yawJoyControlMaxSpeed; // 오른쪽으로 회전하는 값
                } else {
                    yaw = yawJoyControlMaxSpeed * pX;
                }

                yaw = yawJoyControlMaxSpeed * pX;
                throttle = verticalJoyControlMaxSpeed * pY;

                if (null == sendVirtualStickDataTimer) {
                    sendVirtualStickDataTask = new SendVirtualStickDataTask();
                    sendVirtualStickDataTimer = new Timer();
                    sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, 100);
                }
            }
        });
    }

    private void processCommand(String command) {
        switch (command.toUpperCase()) {
//            case "TAKE_OFF":
//                btnTakeOff.performClick();
//                break;
//            case "LAND":
//                btnLand.performClick();
//                break;
//            case "MOVE_FORWARD":
//                setDroneMovementState(DroneMovementState.FORWARD);
//                break;
//            case "MOVE_BACKWARD":
//                setDroneMovementState(DroneMovementState.BACKWARD);
//                break;
//            case "MOVE_LEFT":
//                setDroneMovementState(DroneMovementState.LEFT);
//                break;
//            case "MOVE_RIGHT":
//                setDroneMovementState(DroneMovementState.RIGHT);
//                break;
//            case "ROTATE_LEFT":
//                setDroneMovementState(DroneMovementState.ROTATE_LEFT);
//                break;
//            case "ROTATE_RIGHT":
//                setDroneMovementState(DroneMovementState.ROTATE_RIGHT);
//                break;
//            case "STOP":
//                setDroneMovementState(DroneMovementState.STOP);
//                break;
            default:
                ToastUtils.setResultToToast("Unknown command: " + command);
        }
    }

    private void sendControlData() {
        if (null == sendVirtualStickDataTimer) {
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, 100);
        }
    }
    private void tearDownListeners() {
        Simulator simulator = ModuleVerificationUtil.getSimulator();
        if (simulator != null) {
            simulator.setStateCallback(null);
        }
        screenJoystickLeft.setJoystickListener(null);
        screenJoystickRight.setJoystickListener(null);
    }

    @Override

    public void onClick(View v) {
        FlightController flightController = ModuleVerificationUtil.getFlightController();
        if (flightController == null) {
            return;
        }

        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        flightController.setVirtualStickAdvancedModeEnabled(true);
                        DialogUtils.showDialogBasedOnError(getContext(), djiError);
                    }
                });
                break;

            case R.id.btn_disable_virtual_stick:
                flightController.setVirtualStickModeEnabled(false, djiError ->
                        DialogUtils.showDialogBasedOnError(getContext(), djiError));
                break;

            case R.id.btn_enable_server_command:
                startServer();
                break;
            case R.id.btn_disable_server_command:
                stopServer();
                break;

            case R.id.btn_take_off:
                flightController.startTakeoff(djiError ->
                        DialogUtils.showDialogBasedOnError(getContext(), djiError));
                break;

            case R.id.btn_Land:
                DialogUtils.showDialog(getContext(), "landingStart");
                flightController.startLanding(djiError ->
                        DialogUtils.showDialogBasedOnError(getContext(), djiError));
                break;

            // 이동 관련 명령들
            case R.id.btn_move_forward:
                DialogUtils.showDialog(getContext(), "going forward");
                setDroneMovementState(DroneMovementState.FORWARD);
                break;

            case R.id.btn_move_backward:
                DialogUtils.showDialog(getContext(), "going backwards");
                setDroneMovementState(DroneMovementState.BACKWARD);
                break;

            case R.id.btn_move_left:
                DialogUtils.showDialog(getContext(), "going left");
                setDroneMovementState(DroneMovementState.LEFT);
                break;

            case R.id.btn_move_right:
                DialogUtils.showDialog(getContext(), "going right");
                setDroneMovementState(DroneMovementState.RIGHT);
                break;

            case R.id.btn_move_up:
                DialogUtils.showDialog(getContext(), "moving up");
                setDroneMovementState(DroneMovementState.UP);
                break;

            case R.id.btn_move_down:
                DialogUtils.showDialog(getContext(), "moving down");
                setDroneMovementState(DroneMovementState.DOWN);
                break;

            // 회전 관련 명령들
            case R.id.btn_rotate_left:
                DialogUtils.showDialog(getContext(), "rotating left");
                setDroneMovementState(DroneMovementState.ROTATE_LEFT);
                break;

            case R.id.btn_rotate_right:
                DialogUtils.showDialog(getContext(), "rotating right");
                setDroneMovementState(DroneMovementState.ROTATE_RIGHT);
                break;

            case R.id.btn_rotate_stop:
                DialogUtils.showDialog(getContext(), "Stop");
                setDroneMovementState(DroneMovementState.STOP);
                break;

            // 제어 모드 설정
            case R.id.btn_roll_pitch_control_mode:
                flightController.setRollPitchControlMode(
                        flightController.getRollPitchControlMode() == RollPitchControlMode.VELOCITY
                                ? RollPitchControlMode.ANGLE
                                : RollPitchControlMode.VELOCITY
                );
                ToastUtils.setResultToToast(flightController.getRollPitchControlMode().name());
                break;

            case R.id.btn_yaw_control_mode:
                flightController.setYawControlMode(
                        flightController.getYawControlMode() == YawControlMode.ANGULAR_VELOCITY
                                ? YawControlMode.ANGLE
                                : YawControlMode.ANGULAR_VELOCITY
                );
                ToastUtils.setResultToToast(flightController.getYawControlMode().name());
                break;

            case R.id.btn_vertical_control_mode:
                flightController.setVerticalControlMode(
                        flightController.getVerticalControlMode() == VerticalControlMode.VELOCITY
                                ? VerticalControlMode.POSITION
                                : VerticalControlMode.VELOCITY
                );
                ToastUtils.setResultToToast(flightController.getVerticalControlMode().name());
                break;

            case R.id.btn_horizontal_coordinate:
                flightController.setRollPitchCoordinateSystem(
                        flightController.getRollPitchCoordinateSystem() == FlightCoordinateSystem.BODY
                                ? FlightCoordinateSystem.GROUND
                                : FlightCoordinateSystem.BODY
                );
                ToastUtils.setResultToToast(flightController.getRollPitchCoordinateSystem().name());
                break;
        }
    }
    private void startServer() {
        if (!isServerRunning) {
            tcpServer.startServer();
            isServerRunning = true;

            ToastUtils.setResultToToast("Server started");
            DialogUtils.showDialog(getContext(), "Server started");
            btnEnableServer.setEnabled(false);
            btnDisableServer.setEnabled(true);
        }
    }

    private void stopServer() {
        if (isServerRunning) {
            tcpServer.stopServer();
            isServerRunning = false;

            ToastUtils.setResultToToast("Server stopped");
            DialogUtils.showDialog(getContext(), "Server stopped");
            btnEnableServer.setEnabled(true);
            btnDisableServer.setEnabled(false);
        }
    }
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == btnSimulator) {
            onClickSimulator(b);
        }
    }

    private void onClickSimulator(boolean isChecked) {
        if (simulator == null) {
            return;
        }
        if (isChecked) {
            textView.setVisibility(VISIBLE);
            simulator.start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        ToastUtils.setResultToToast(djiError.getDescription());
                    }
                }
            });
        } else {
            textView.setVisibility(INVISIBLE);
            simulator.stop(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        ToastUtils.setResultToToast(djiError.getDescription());
                    }
                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.flight_controller_listview_virtual_stick;
    }

    private class SendVirtualStickDataTask extends TimerTask {
        @Override
        public void run() {
            final float PITCH_SPEED = 0.15f;
            final float ROLL_SPEED = 0.15f;
            final float YAW_SPEED = 10f;

            if (flightController != null) {
                if (!isMoving) {
                    pitch = roll = yaw = 0;
                    resetAllMovementFlags();
                } else {
                    switch (currentMovementState) {
                        case FORWARD:
                            pitch = PITCH_SPEED;
                            break;
                        case BACKWARD:
                            pitch = -PITCH_SPEED;
                            break;
                        case LEFT:
                            roll = -ROLL_SPEED;
                            break;
                        case RIGHT:
                            roll = ROLL_SPEED;
                            break;
                        case ROTATE_LEFT:
                            yaw = -YAW_SPEED;
                            break;
                        case ROTATE_RIGHT:
                            yaw = YAW_SPEED;
                            break;
                    }
                }

                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(roll, pitch, yaw, throttle),
                        error -> {
                            if (error != null) {
                                ToastUtils.setResultToToast(error.getDescription());
                            }
                        }
                );
            }
        }
    }


}
