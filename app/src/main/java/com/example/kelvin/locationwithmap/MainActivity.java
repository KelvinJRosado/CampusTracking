package com.example.kelvin.locationwithmap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
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

    //Sensors
    private SensorManager sensorManager;
    private Sensor stepSensor, magnetSensor, accelSensor;

    //Gravity and rotation info
    private float[] gravity, magnet;
    GeomagneticField geomagneticField;

    private ArrayList<LatLng> userPath;
    private float azimuth = 0;

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
            }
        });

        //Show AUS if location not found
        LatLng sydney = new LatLng(-33.852,151.211);
        googleMap.addMarker(new MarkerOptions().position(sydney).title("Location not found so here's Australia"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

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

        //Register sensor listeners
        sensorManager.registerListener(this,stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,magnetSensor,SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,accelSensor,SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        //Accel sensor
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            gravity = sensorEvent.values;
        }

        //Magnet sensor
        if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            magnet = sensorEvent.values;
        }

        //Get orientation of phone
        if(gravity != null && magnet != null && geomagneticField != null){
            float [] R = new float[9];
            float [] I = new float[9];

            boolean worked = SensorManager.getRotationMatrix(R,I,gravity,magnet);

            //Adjust x component of R
            R[0] = -R[0];

            if(worked){
                float [] orientation = new float[3];

                SensorManager.getOrientation(R,orientation);

                azimuth = orientation[0] - geomagneticField.getDeclination();//Get direction phone is facing, adjusted
                azimuth = (float)((azimuth * 180 / Math.PI) - 90.0);

            }

        }

        //If event is a step
        if(sensorEvent.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {

            //TODO: Get more accurate step length. 0.71628m is not enough
            final double stepLength = 0.762;//Average step length of an adult, in meters
            LatLng lastLocation = userPath.get(userPath.size() - 1);

            //Get direction of movement
            double direction = azimuth;

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
