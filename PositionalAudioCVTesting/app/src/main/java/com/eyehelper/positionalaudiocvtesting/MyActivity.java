package com.eyehelper.positionalaudiocvtesting;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;


public class MyActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, SensorEventListener {
    private static final String TAG = "OCVSample::Activity";

    //The matrix of the image in rgba
    private Mat rgba;

    //Camera Parameters
    private double focal = 2.8;
    private int objWidth = 127;
    private int imgHeight = 512;
    private int sensorHeight = 4;

    //Position Variables
    private double distance = 100;
    private double angle = 0;
    private double height = 0;

    //Sound Variables
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private int currentFile = R.raw.height0angle_85;

    //Camera View
    private CameraBridgeViewBase openCvCameraView;

    //Text Views
    private TextView angleText;
    private TextView heightText;
    private TextView distanceText;
    private TextView azimuthText;
    private TextView pitchText;
    private TextView rollText;

    //Sensor Data
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float azimuth = 0.0f;
    private float pitch = 0.0f;
    private float roll = 0.0f;
    private float[] gravity;
    private float[] geomagnetic;

    private volatile boolean soundRunning;

    private ObjectTracker objectTracker;

    //This is what we use to determine whether or not the app loaded successfully
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                //The app loaded successfully
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCvCameraView.enableView();
                    //openCvCameraView.setOnTouchListener(MyActivity.this);
                }
                break;
                //Otherwise it didn't
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    //When the activity is created
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        //Set the camera to appear on the whole screen
        openCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        //Make this class, which extends CameraVeiwListener the listener
        openCvCameraView.setCvCameraViewListener(this);
        final GestureDetector gestureDetector = new GestureDetector(this, new TapDetector());
        openCvCameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

        //Display the angle, height, and distance on the screen on the glass
        angleText = (TextView) findViewById(R.id.textViewA);
        heightText = (TextView) findViewById(R.id.textViewH);
        distanceText = (TextView) findViewById(R.id.textViewD);
        azimuthText = (TextView) findViewById(R.id.textViewAzimuth);
        pitchText = (TextView) findViewById(R.id.textViewPitch);
        rollText = (TextView) findViewById(R.id.textViewRoll);

        //Sensors! (To get the head tilt information)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    }

    @Override
    public void onPause() {
        //When the app is paused, stop the camera and pause the music
        sensorManager.unregisterListener(this);
        if (openCvCameraView != null) {
            openCvCameraView.disableView();
        }
        soundRunning = false;
        super.onPause();
    }

    @Override
    public void onResume() {
        //When the app is resumed, restart the camera asynchronously
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, loaderCallback);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        startSoundThread();
        super.onResume();
    }

    private void startSoundThread() {
        soundRunning = true;
        Thread soundThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (soundRunning) {
                    try {
                        Thread.sleep(500);
                        playSound();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
        soundThread.start();
    }

    //When a user swipes down to quit, finish the app
    @Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return false;
    }

    //Right after the camera starts
    @Override
    public void onCameraViewStarted(int width, int height) {
        //Declare the image matrix to be a matrix of the height and width of the image
        rgba = new Mat(height, width, CvType.CV_8UC4);
    }

    //when the camera view stops
    @Override
    public void onCameraViewStopped() {
        //When the camera view stops, release the camera
        rgba.release();
    }
    
    //Every time we get a new camera frame
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        if (objectTracker != null) {
            objectTracker.matchObject(inputFrame.gray());
        }
        return inputFrame.rgba();
    }

    //Update text on the glass's display
    public void updateText() {
        //In order to update UI elements, we have to run on the UI thread
        MyActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Update the text views
                distanceText.setText("Distance: " + String.format("%.1f", distance));
                angleText.setText("Angle: " + String.format("%.1f", angle));
                heightText.setText("Height: " + String.format("%.1f", height));

                azimuthText.setText("Azimuth: " + String.format("%.2f", Math.toDegrees(azimuth)));
                pitchText.setText("Pitch: " + String.format("%.2f", pitch));
                rollText.setText("Roll: " + String.format("%.2f", Math.toDegrees(roll)));
            }
        });

    }

    //Decide which sound file to play
    public int getSoundFile() {
        int angleFile;

        //If the height is less than one, chose a sound file based on angle. Angles are in the middle of angle ranges of 10 degrees
        //For Example angles 1-10 -> angle5
        if (height < 1) {
            if (angle <= -80) {
                angleFile = R.raw.height0angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height0angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height0angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height0angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height0angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height0angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height0angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height0angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height0angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height0angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height0angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height0angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height0angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height0angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height0angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height0angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height0angle75;
            } else {
                angleFile = R.raw.height0angle85;
            }
            //If the height is less than 2, choose a sound file corresponding to height 1 and some angle
        } else if (height < 2) {
            if (angle <= -80) {
                angleFile = R.raw.height1angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height1angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height1angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height1angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height1angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height1angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height1angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height1angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height1angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height1angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height1angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height1angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height1angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height1angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height1angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height1angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height1angle75;
            } else {
                angleFile = R.raw.height1angle85;
            }
            //If the height is less than 3, choose a sound file corresponding to height 2 and the angle
        } else if (height < 3) {
            if (angle <= -80) {
                angleFile = R.raw.height2angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height2angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height2angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height2angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height2angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height2angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height2angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height2angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height2angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height2angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height2angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height2angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height2angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height2angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height2angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height2angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height2angle75;
            } else {
                angleFile = R.raw.height2angle85;
            }
            //If the height is less than 4, choose a sound file corresponding to height 3 and the angle
        } else if (height < 4) {
            if (angle <= -80) {
                angleFile = R.raw.height3angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height3angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height3angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height3angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height3angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height3angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height3angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height3angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height3angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height3angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height3angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height3angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height3angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height3angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height3angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height3angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height3angle75;
            } else {
                angleFile = R.raw.height3angle85;
            }
            //If the height is less than 5, choose a sound file corresponding to height 4 and the angle
        } else if (height < 5) {
            if (angle <= -80) {
                angleFile = R.raw.height4angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height4angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height4angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height4angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height4angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height4angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height4angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height4angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height4angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height4angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height4angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height4angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height4angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height4angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height4angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height4angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height4angle75;
            } else {
                angleFile = R.raw.height4angle85;
            }

            //If the height is less than 6, choose a sound file corresponding to height 5 and the angle
        } else if (height < 6) {
            if (angle <= -80) {
                angleFile = R.raw.height5angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height5angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height5angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height5angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height5angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height5angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height5angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height5angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height5angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height5angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height5angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height5angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height5angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height5angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height5angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height5angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height5angle75;
            } else {
                angleFile = R.raw.height5angle85;
            }
            //If the height is less than 7, choose a sound file corresponding to height 6 and the angle
        } else if (height < 7) {
            if (angle <= -80) {
                angleFile = R.raw.height6angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height6angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height6angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height6angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height6angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height6angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height6angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height6angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height6angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height6angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height6angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height6angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height6angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height6angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height6angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height6angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height6angle75;
            } else {
                angleFile = R.raw.height6angle85;
            }
            //Otherwise, choose a sound file corresponding to height 7 and the angle.
        } else {
            if (angle <= -80) {
                angleFile = R.raw.height7angle_85;
            } else if (angle <= -70) {
                angleFile = R.raw.height7angle_75;
            } else if (angle <= -60) {
                angleFile = R.raw.height7angle_65;
            } else if (angle <= -50) {
                angleFile = R.raw.height7angle_55;
            } else if (angle <= -40) {
                angleFile = R.raw.height7angle_45;
            } else if (angle <= -30) {
                angleFile = R.raw.height7angle_35;
            } else if (angle <= -20) {
                angleFile = R.raw.height7angle_25;
            } else if (angle <= -10) {
                angleFile = R.raw.height7angle_15;
            } else if (angle <= 0) {
                angleFile = R.raw.height7angle_5;
            } else if (angle <= 10) {
                angleFile = R.raw.height7angle5;
            } else if (angle <= 20) {
                angleFile = R.raw.height7angle15;
            } else if (angle <= 30) {
                angleFile = R.raw.height7angle25;
            } else if (angle <= 40) {
                angleFile = R.raw.height7angle35;
            } else if (angle <= 50) {
                angleFile = R.raw.height7angle45;
            } else if (angle <= 60) {
                angleFile = R.raw.height7angle55;
            } else if (angle <= 70) {
                angleFile = R.raw.height7angle65;
            } else if (angle <= 80) {
                angleFile = R.raw.height7angle75;
            } else {
                angleFile = R.raw.height7angle85;
            }
        }

        //Return which file to play
        return angleFile;
    }

    //Play a sound given the resource
    public void playSound() {
        //If a sound is currently playing, stop it.
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        //Set up the media player
        mediaPlayer = MediaPlayer.create(this.getApplicationContext(), currentFile);
        mediaPlayer.start();

        //Listen for completion
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                //Release the media player on completion
                mp.release();
            }
        });
    }

    //When the accuracy of a sensor changes
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //When the reading of a sensor changes
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            gravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            geomagnetic = event.values;
        if (gravity != null && geomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimuth = orientation[0]; // orientation contains: azimuth, pitch and roll
                pitch = orientation[1];
                roll = orientation[2];
            }
        }
    }

    class TapDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.v(TAG, String.format("x: %f, y: %f", e.getX(), e.getY()));
            return super.onSingleTapUp(e);
        }
    }
}
