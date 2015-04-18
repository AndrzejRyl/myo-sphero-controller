package com.fleenmobile.androidapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import java.util.List;

import orbotix.robot.base.CollisionDetectedAsyncData;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.sensor.DeviceSensorsData;
import orbotix.sphero.CollisionListener;
import orbotix.sphero.ConnectionListener;
import orbotix.sphero.DiscoveryListener;
import orbotix.sphero.PersistentOptionFlags;
import orbotix.sphero.SensorControl;
import orbotix.sphero.SensorFlag;
import orbotix.sphero.SensorListener;
import orbotix.sphero.Sphero;

public class MainActivity extends Activity {

    private TextView mLockStateView;
    private TextView mTextView;
    private ImageView mImageView;
    private Sphero mRobot;
    private static final String TAG = "main";

    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            mTextView.setTextColor(Color.CYAN);
            Toast.makeText(MainActivity.this, "connect", Toast.LENGTH_LONG).show();
            Log.e("A", "Paired with armband");
            pairSphero();
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            mTextView.setTextColor(Color.RED);
            Toast.makeText(MainActivity.this, "disconnec", Toast.LENGTH_LONG).show();
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mTextView.setText(myo.getArm() == Arm.LEFT ? R.string.arm_left : R.string.arm_right);
            Toast.makeText(MainActivity.this, "armsync", Toast.LENGTH_LONG).show();
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
            mTextView.setText(R.string.hello_world);
            Toast.makeText(MainActivity.this, "unsync", Toast.LENGTH_LONG).show();
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.unlocked);
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.locked);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }

            // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
            mImageView.setRotation(roll / 4);
            mImageView.setRotationX(pitch / 4);
            mImageView.setRotationY(yaw / 4);
        }

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            switch (pose) {
                case UNKNOWN:
                    mTextView.setText(getString(R.string.hello_world));
                    break;
                case REST:
                case DOUBLE_TAP:
                    int restTextId = R.string.hello_world;
                    switch (myo.getArm()) {
                        case LEFT:
                            restTextId = R.string.arm_left;
                            break;
                        case RIGHT:
                            restTextId = R.string.arm_right;
                            break;
                    }
                    mTextView.setText(getString(restTextId));
                    break;
                case FIST:
                    mTextView.setText(getString(R.string.pose_fist));
                    drive(0.0f, 0, 0 ,255);
                    break;
                case WAVE_IN:
                    mTextView.setText(getString(R.string.pose_wavein));
                    drive(270.0f, 255, 0 ,255);
                    break;
                case WAVE_OUT:
                    mTextView.setText(getString(R.string.pose_waveout));
                    drive(90.0f, 0, 255 ,0);
                    break;
                case FINGERS_SPREAD:
                    mTextView.setText(getString(R.string.pose_fingersspread));
                    drive(180.0f, 255, 0 ,0);
                    break;
            }

            if (pose != Pose.UNKNOWN && pose != Pose.REST) {
                // Tell the Myo to stay unlocked until told otherwise. We do that here so you can
                // hold the poses without the Myo becoming locked.
                myo.unlock(Myo.UnlockType.HOLD);

                // Notify the Myo that the pose has resulted in an action, in this case changing
                // the text on the screen. The Myo will vibrate.
                myo.notifyUserAction();
            } else {
                // Tell the Myo to stay unlocked only for a short period. This allows the Myo to
                // stay unlocked while poses are being performed, but lock after inactivity.
                myo.unlock(Myo.UnlockType.TIMED);
            }
        }

        private void drive(float angle, int r, int g, int b) {
            if (mRobot != null) {
                mRobot.setColor(r,g,b);
                mRobot.drive(angle, 0.5f);
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        mRobot.stop();
                    }
                }, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLockStateView = (TextView) findViewById(R.id.lock_state);
        mTextView = (TextView) findViewById(R.id.text);
        mImageView = (ImageView)findViewById(R.id.logoView);

//        Intent i  = new Intent(this, RobotHelloWorld.class);
//        startActivity(i);

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();

            //  TODO: TEEEESSSTTTTTT REMOVE IT!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

            //  TODO: TEEEESSSTTTTTT REMOVE IT!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            return;
        }

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);

        Intent intent = new Intent(this, ScanActivity.class);
        this.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    public void pairSphero() {
        RobotProvider.getDefaultProvider().addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnected(Robot robot) {
                Log.e("A", "Paired with sphero");
                mRobot = (Sphero) robot;
                MainActivity.this.connected();
            }

            @Override
            public void onConnectionFailed(Robot sphero) {
                Log.d(TAG, "Connection Failed: " + sphero);
                Toast.makeText(MainActivity.this, "Sphero Connection Failed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected(Robot robot) {
                Log.d(TAG, "Disconnected: " + robot);
                Toast.makeText(MainActivity.this, "Sphero Disconnected", Toast.LENGTH_SHORT).show();
                MainActivity.this.stopBlink();
                mRobot = null;
            }
        });

        RobotProvider.getDefaultProvider().addDiscoveryListener(new DiscoveryListener() {
            @Override
            public void onBluetoothDisabled() {
                Log.d(TAG, "Bluetooth Disabled");
                Toast.makeText(MainActivity.this, "Bluetooth Disabled", Toast.LENGTH_LONG).show();
            }

            @Override
            public void discoveryComplete(List<Sphero> spheros) {
                Log.d(TAG, "Found " + spheros.size() + " robots");
            }

            @Override
            public void onFound(List<Sphero> sphero) {
                Log.d(TAG, "Found: " + sphero);
                RobotProvider.getDefaultProvider().connect(sphero.iterator().next());
            }
        });

        boolean success = RobotProvider.getDefaultProvider().startDiscovery(MainActivity.this);
        if(!success){
            Toast.makeText(this, "Unable To start Discovery!", Toast.LENGTH_LONG).show();
        }
    }

    private void connected() {
        // Send a roll command to Sphero so it goes forward at full speed.

        // Send a delayed message on a handler
        final Handler handler = new Handler();                               // 2
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                // Send a stop to Sphero
                mRobot.stop();                                               // 3
            }
        }, 2000);

        Log.d(TAG, "Connected On Thread: " + Thread.currentThread().getName());
        Log.d(TAG, "Connected: " + mRobot);
        Toast.makeText(this, mRobot.getName() + " Connected", Toast.LENGTH_LONG).show();

        final SensorControl control = mRobot.getSensorControl();
        control.addSensorListener(new SensorListener() {
            @Override
            public void sensorUpdated(DeviceSensorsData sensorDataArray) {
                Log.d(TAG, sensorDataArray.toString());
            }
        }, SensorFlag.ACCELEROMETER_NORMALIZED, SensorFlag.GYRO_NORMALIZED);

//        control.setRate(1);
//        mRobot.enableStabilization(false);
//        mRobot.drive(90, 1);
//        mRobot.setBackLEDBrightness(.5f);
//
//        mRobot.getCollisionControl().startDetection(255, 255, 255, 255, 255);
//        mRobot.getCollisionControl().addCollisionListener(new CollisionListener() {
//            public void collisionDetected(CollisionDetectedAsyncData collisionData) {
//                Log.d(TAG, collisionData.toString());
//            }
//        });
//

        boolean preventSleepInCharger = mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.PreventSleepInCharger);
        Log.d(TAG, "Prevent Sleep in charger = " + preventSleepInCharger);
        Log.d(TAG, "VectorDrive = " + mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.EnableVectorDrive));

        mRobot.getConfiguration().setPersistentFlag(PersistentOptionFlags.PreventSleepInCharger, false);
        mRobot.getConfiguration().setPersistentFlag(PersistentOptionFlags.EnableVectorDrive, true);

        Log.d(TAG, "VectorDrive = " + mRobot.getConfiguration().isPersistentFlagEnabled(PersistentOptionFlags.EnableVectorDrive));
        Log.v(TAG, mRobot.getConfiguration().toString());
    }
    boolean blinking = true;

    private void stopBlink() {
        blinking = false;
    }

    /**
     * Causes the robot to blink once every second.
     *
     * @param lit
     */
    private void blink(final boolean lit) {
        if (mRobot == null) {
            blinking = false;
            return;
        }

        //If not lit, send command to show blue light, or else, send command to show no light
        if (lit) {
            mRobot.setColor(0, 0, 0);

        } else {
            mRobot.setColor(0, 255, 0);
        }

        if (blinking) {
            //Send delayed message on a handler to run blink again
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    blink(!lit);
                }
            }, 2000);
        }
    }

    /**
     * Starts 1st training
     * @param v
     */
    public void startTraining(View v) {

        if (mRobot == null) {
            Toast.makeText(this, "F U. Robot is not here", Toast.LENGTH_LONG).show();
            return;
        }

        Intent i  = new Intent(this, FirstTraining.class);
        i.putExtra("SPHERO", mRobot);
        startActivity(i);
    }
}
