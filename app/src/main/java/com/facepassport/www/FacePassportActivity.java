package com.facepassport.www;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.facepassport.www.ui.camera.CameraSourcePreview;
import com.facepassport.www.ui.camera.GraphicOverlay;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public final class FacePassportActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_PERMISSIONA_ALL= 2;

    private int windowTop;
    private int windowHeight;
    private int mRotation;

    private Timer waitTakeImage;

    @SuppressLint("StaticFieldLeak")
    public static Button flip;
    public static boolean isTakeImage = false;

    // Activity Methods

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);


        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        //If add Button (setVisibility = Visible)
        flip = (Button) findViewById(R.id.flipButton);
        flip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeImage();
            }
        });
        flip.setVisibility(View.INVISIBLE);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        windowTop = size.x;
        windowHeight = size.y;

        // Check for the camera permissions before accessing the camera.  If the
        // permissions is not granted yet, request permissions.

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraAndExternalPermission();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void requestCameraAndExternalPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String [] permission = new String[]{Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};

        if(!hasPermissions(this, permission)){
            ActivityCompat.requestPermissions(this, permission, RC_HANDLE_PERMISSIONA_ALL);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!hasPermissions(thisActivity, permission)){
                    ActivityCompat.requestPermissions(thisActivity, permission, RC_HANDLE_PERMISSIONA_ALL);
                }
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }


    //Creates and starts the camera.
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(getResources().getInteger(R.integer.top), getResources().getInteger(R.integer.weight))
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    //Restarts the camera.
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
        waitTakeImage();
    }

    //Stops the camera.
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        waitTakeImage.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release(); //Releases the resources associated with the camera source
        }
        waitTakeImage.cancel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_PERMISSIONA_ALL) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    // Camera Source Preview
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    /*Factory for creating a face tracker to be associated with a new face.  The multiprocessor
      uses this factory to create face trackers as needed -- one for each individual.*/
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /*Face tracker for each detected individual. This maintains a face graphic within the app's
     associated face overlay.*/
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, getResources().getInteger(R.integer.top), getResources().getInteger(R.integer.weight),
                    windowTop, windowHeight);
        }

        //Start tracking the detected face instance within the face overlay.
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }


        //Update the position/characteristics of the face within the overlay.
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /*Hide the graphic when the corresponding face was not detected.  This can happen for
         intermediate frames temporarily (e.g., if the face was momentarily blocked from
         view).*/
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /*Called when the face is assumed to be gone for good. Remove the graphic annotation from
            the overlay.*/
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    //Make and save image
    public void takeImage() {
        try{
            mCameraSource.takePicture(null, new CameraSource.PictureCallback() {

                private File imageFile;
                @Override
                public void onPictureTaken(byte[] bytes) {
                    try {
                        // convert byte array into bitmap
                        Bitmap mLoadedImage;
                        Bitmap mRotatedBitmap;
                        mLoadedImage = BitmapFactory.decodeByteArray(bytes, 0,
                                bytes.length);

                        // rotate Image
                        mRotation = getWindowManager().getDefaultDisplay().getRotation();
                        Matrix mRotateMatrix = new Matrix();
                        mRotateMatrix.postRotate(mRotation);
                        mRotatedBitmap = Bitmap.createBitmap(mLoadedImage, 0, 0,
                                mLoadedImage.getWidth(), mLoadedImage.getHeight(),
                                mRotateMatrix, false);
                        String state = Environment.getExternalStorageState();
                        File mFolder;
                        if (state.contains(Environment.MEDIA_MOUNTED)) {
                            mFolder = new File(Environment
                                    .getExternalStorageDirectory() + "/PassportPhoto");
                        } else {
                            mFolder = new File(Environment
                                    .getExternalStorageDirectory() + "/PassportPhoto");
                        }

                        boolean mSuccess = true;
                        if (!mFolder.exists()) {
                            mSuccess = mFolder.mkdirs();
                        }
                        if (mSuccess) {
                            java.util.Date date = new java.util.Date();
                            imageFile = new File(mFolder.getAbsolutePath()
                                    + File.separator
                                    + "passport_image" + date.getTime() + ".jpg");

                            imageFile.createNewFile();
                        } else {
                            Toast.makeText(getBaseContext(), "Image Not saved",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        ByteArrayOutputStream ostream = new ByteArrayOutputStream();

                        // save image into gallery
                        mRotatedBitmap.setDensity(300);
                        mRotatedBitmap = resize(mRotatedBitmap, 413, 531);
                        mRotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, ostream);

                        FileOutputStream fileOut = new FileOutputStream(imageFile);
                        fileOut.write(ostream.toByteArray());
                        fileOut.close();
                        ContentValues contentValues = new ContentValues();

                        contentValues.put(MediaStore.Images.Media.DATE_TAKEN,
                                System.currentTimeMillis());
                        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        contentValues.put(MediaStore.MediaColumns.DATA,
                                imageFile.getAbsolutePath());

                        FacePassportActivity.this.getContentResolver().insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

                        Toast.makeText(getBaseContext(), "Make photo!", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        }catch (Exception ex){
             ex.printStackTrace();
        }
    }

    //Set image size
    private Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            //keeping the aspect ratio

            /*int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > 1) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }*/

            image = Bitmap.createScaledBitmap(image, maxWidth, maxHeight, true);
            return image;
        } else {
            return image;
        }
    }

    //A photo to make at the right time
    private void waitTakeImage() {

        waitTakeImage = new Timer();

        waitTakeImage.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isTakeImage) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            takeImage();
                        }
                    });
                }
            }
        }, 0, 1000);
    }
}
