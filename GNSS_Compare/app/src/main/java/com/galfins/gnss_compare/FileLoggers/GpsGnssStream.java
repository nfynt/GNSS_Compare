
package com.galfins.gnss_compare.FileLoggers;

import android.content.Context;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import java.io.IOException;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;

import com.galfins.gnss_compare.Constellations.Constellation;
import com.galfins.gogpsextracts.Coordinates;

/**
 * Created by NFYNT on 23/07/2020.
 */

public class GpsGnssStream{

    protected String TAG = "GpsGnssStream";

    private boolean isStarted = false;
    private static boolean initialized = false;

    Thread Thread1 = null;

    ServerSocket serverSocket;
    public static String SERVER_IP = "";
    public static final int SERVER_PORT = 50000;

    //message to be sent over stream
    String message;
    //data to be sent over stream
    long timestamp; //timestamp in milliseconds
    double latitude; // latitude
    double longitude; // longitude
    double altitude;    //altitude
    float accuracy;     //GPS accuracy from google service
    double speed;   //GPS speed from google service
    int satelliteNum;   //number of satellites
    int multipathIndicator; //0,1 indicator for multipath
    int constellationType;  //{CONSTELLATION_UNKNOWN, CONSTELLATION_GPS, CONSTELLATION_SBAS, CONSTELLATION_GLONASS,CONSTELLATION_QZSS, CONSTELLATION_BEIDOU, CONSTELLATION_GALILEO}
    double accumulatedDeltaRangeUncertaintyMeters; //Gets the accumulated delta range uncertainty since the last channel reset, in meters.

    private final Context mContext;

    public GpsGnssStream(Context context) {
        this.mContext = context;
        try {
            SERVER_IP = getLocalIpAddress();
        }catch(UnknownHostException exp) {
            Log.e(TAG,exp.getMessage());
        }
    }

    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void ConnectAndStartStreaming() {
        isStarted=true;
        Thread1 = new Thread(new Thread1());
        Thread1.start();
    }

    // Disconnect connection and close stream
    public void Disconnect() {
        isStarted=false;
    }
    /**
     * Add new pose to the file dummy function
     */
    public void addNewPose(Coordinates pose, Constellation constellation) { }

    /**
     * update fine location
     */
    public void updateFineLocation(Location fineLocation) {

//        String locationStream =
//                String.format(Locale.ENGLISH,
//                        "Fix,%s,%f,%f,%f,%f,%f,%d",
//                        fineLocation.getProvider(),
//                        fineLocation.getLatitude(),
//                        fineLocation.getLongitude(),
//                        fineLocation.getAltitude(),
//                        fineLocation.getSpeed(),
//                        fineLocation.getAccuracy(),
//                        fineLocation.getTime());

        latitude = fineLocation.getLatitude();
        longitude = fineLocation.getLongitude();
        altitude = fineLocation.getAltitude();
        speed = fineLocation.getSpeed();
        accuracy = fineLocation.getAccuracy();
    }

    /**
     * update gnss measurement
     * @param event
     */
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {

        GnssClock gnssClock = event.getClock();
        timestamp = System.currentTimeMillis();
        GnssMeasurement[] measurement = (GnssMeasurement[]) event.getMeasurements().toArray();
        int ind = measurement.length;
        multipathIndicator = measurement[ind - 1].getMultipathIndicator();
        constellationType = measurement[ind - 1].getConstellationType();
        accumulatedDeltaRangeUncertaintyMeters = measurement[ind - 1].getAccumulatedDeltaRangeUncertaintyMeters();

    }

    public void OnGnssStatusUpdate(GnssStatus status)
    {
        satelliteNum = status.getSatelliteCount();
        //status.getSvid(0);    satellite type
    }

    private PrintWriter output;
    private BufferedReader input;

    class Thread1 implements Runnable {
        @Override
        public void run() {
            Socket socket;
            try {
                //return number of milliseconds since January 1, 1970, 00:00:00 GMT
                long time;
                serverSocket = new ServerSocket(SERVER_PORT);
                socket = serverSocket.accept();
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream());
                //start message
                message = "#<time lat lon alt speed accuracy satellite_cnt multipath_ind acc_delta_range_uncertainty_m constellation_type>";
                while (true) {
                    if(!isStarted) break;

                    time = System.currentTimeMillis();
                    output.write(message);
                    output.flush();
                    //message = "$<time,lat,lon,alt>: ("+t_stamp+"," + String.valueOf(latitude) + ","+String.valueOf(longitude)+","+String.valueOf(altitude)+")";
                    //$<time,lat,lon,alt,speed,accuracy,satellite_cnt,multipath_ind,acc_delta_range_uncertainty_m,constellation_type>
                    message = String.format(Locale.ENGLISH,"%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            time,latitude,longitude,altitude,speed,accuracy,satelliteNum,multipathIndicator,
                            accumulatedDeltaRangeUncertaintyMeters,constellationType);
                    Thread.sleep(1000);
                }
                output.flush();
                serverSocket.close();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


