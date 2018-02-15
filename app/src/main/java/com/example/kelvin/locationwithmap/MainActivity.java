package com.example.kelvin.locationwithmap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

//Implement Google Maps interface
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {
    /*
    References
    http://www.codingforandroid.com/2011/01/using-orientation-sensors-simple.html

    */

    //Map API
    private FusedLocationProviderClient fusedLocationProviderClient;//Gets starting location
    private GoogleMap googleMap;//Reference to map

    //Reference to context
    Context thisContext = this;

    //Sensors
    private SensorManager sensorManager;
    private Sensor stepSensor;

    //Gravity and rotation info; Used for calculating orientation
    private Sensor accelSensor, magnetSensor;
    private float [] lastAccel = new float[3];
    private float [] lastMagnet = new float[3];
    private boolean accelSet = false, magnetSet = false;
    private float [] rotation = new float[9];
    private float [] orientation = new float[3];
    float currentAngle = 0f;

    private GeomagneticField geomagneticField;

    private ArrayList<LatLng> userPath;

    private double stepLength = 0.762;//Default step length

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Set up location client
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        init();//Initialize objects

    }

    //Initialize and display map
    @Override
    public void onMapReady(final GoogleMap googleMap) {

        this.googleMap = googleMap;

        //Make a marker in start location and display
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,new String[]{ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);

        }
        //Plot starting position
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    LatLng start = new LatLng(location.getLatitude(),location.getLongitude());
                    //Place marker at start position
                    googleMap.addMarker(new MarkerOptions().position(start).title("Start location"));
                    //Center map on marker and zoom in
                    //Zoom is a float in range [2.0f,21.0f]
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start,20.0f));

                    //Add start location to buffer
                    userPath.add(start);

                    //Get declination for finding true north
                    geomagneticField = new GeomagneticField((float)location.getLatitude(),
                            (float)location.getLatitude(),(float)location.getAltitude(),System.currentTimeMillis());

                }
                else {
                    //Show AUS if location not found
                    LatLng sydney = new LatLng(-33.852,151.211);
                    googleMap.addMarker(new MarkerOptions().position(sydney).title("Location not found so here's Australia"));
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
                }
            }
        });

    }

    //Initialize objects
    private void init(){

        //Initialize buffer
        userPath = new ArrayList<>();

        //Initialize sensors
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        assert sensorManager != null;//Assume phone has step counter
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    }

    protected void onResume(){
        super.onResume();

        //Register sensor listeners
        sensorManager.registerListener(this,stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,magnetSensor,SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,accelSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause(){
        super.onPause();
        sensorManager.unregisterListener(this,stepSensor);
        sensorManager.unregisterListener(this,accelSensor);
        sensorManager.unregisterListener(this,magnetSensor);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        //Accel sensor
        if(event.sensor == accelSensor){
            lastAccel = event.values;
            accelSet = true;
        }

        //Magnet sensor
        else if(event.sensor == magnetSensor){
            lastMagnet = event.values;
            magnetSet = true;
        }

        //Get orientation of phone
        if(accelSet && magnetSet && geomagneticField != null){

            SensorManager.getRotationMatrix(rotation,null,lastAccel,lastMagnet);
            SensorManager.getOrientation(rotation,orientation);

            float azimuthRadians = orientation[0];
            currentAngle = ((float)(Math.toDegrees(azimuthRadians) + 360) % 360) - geomagneticField.getDeclination();

        }

        //If event is a step
        if(event.sensor == stepSensor || userPath.size() >= 1) {

            LatLng lastLocation = userPath.get(userPath.size() - 1);

            //Get direction of movement, in degrees East of North
            double direction = currentAngle;

            //Adjust angle based on phone's rotation
            double x = lastAccel[0];
            double y = lastAccel[1];
            double z = lastAccel[2];

            double xFactor = 0;
            double yFactor = 0;
            double zFactor = 0;

            //X is rotated
            if(x > 0.5 || x < -0.5){
                xFactor = (x / 9.8) * 1;
            }

            //Y is rotated
            if(y > 0.5 || y < -0.5){
                yFactor = (y / 9.8) * 30;
            }

            //Z is rotated
            if(z > 0.5 || z < -0.5){
                zFactor = (z / 9.8) * 1;
            }

            direction = direction + xFactor + yFactor + zFactor;

            //Calculate new LatLng
            LatLng currentPos = SphericalUtil.computeOffset(lastLocation, stepLength, direction);

            //Draw a line between last and current positions
            Polyline line = googleMap.addPolyline(new PolylineOptions().add(lastLocation, currentPos).width(25).color(Color.RED));

            userPath.add(currentPos);

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //Do nothing
    }

}
