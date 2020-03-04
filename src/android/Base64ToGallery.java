package it.nexxa.base64ToGallery;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * Base64ToGallery.java
 *
 * Android implementation of the Base64ToGallery for iOS.
 * Inspirated by Joseph's "Save HTML5 Canvas Image to Gallery" plugin
 * http://jbkflex.wordpress.com/2013/06/19/save-html5-canvas-image-to-gallery-phonegap-android-plugin/
 *
 * @author Vegard Løkken <vegard@headspin.no>
 */
public class Base64ToGallery extends CordovaPlugin {

  // Consts
  public static final String EMPTY_STR = "";
  private CallbackContext _callback;
  private Bitmap _bmp;
  private String _filePrefix;
  private boolean _mediaScannerEnabled;
  @Override
  public boolean execute(String action, JSONArray args,
      CallbackContext callbackContext) throws JSONException {

    String base64               = args.optString(0);
    String filePrefix           = args.optString(1);
    boolean mediaScannerEnabled = args.optBoolean(2);
    this._callback = callbackContext;
    this._mediaScannerEnabled = mediaScannerEnabled;
    this._filePrefix = filePrefix;
    // isEmpty() requires API level 9
    if (base64.equals(EMPTY_STR)) {
      callbackContext.error("Missing base64 string");
    }

    // Create the bitmap from the base64 string
    byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
    Bitmap bmp           = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

    if (bmp == null) {
      callbackContext.error("The image could not be decoded");

    } else {
      this._bmp = bmp;

      if (PermissionHelper.hasPermission(this, WRITE_EXTERNAL_STORAGE)) {
        Log.d("SaveImageGallery", "Permissions already granted, or Android version is lower than 6");

        savePhoto(bmp, filePrefix, this._callback);
      } else {
        Log.d("SaveImageGallery", "Requesting permissions for WRITE_EXTERNAL_STORAGE");
        PermissionHelper.requestPermission(this, 1000, WRITE_EXTERNAL_STORAGE);
      }
    }

    return true;
  }

  private void savePhoto(Bitmap bmp, String prefix ,CallbackContext callbackContext) {

    try {
      String deviceVersion = Build.VERSION.RELEASE;
      Calendar c           = Calendar.getInstance();
      String date          = EMPTY_STR
                              + c.get(Calendar.YEAR)
                              + c.get(Calendar.MONTH)
                              + c.get(Calendar.DAY_OF_MONTH)
                              + c.get(Calendar.HOUR_OF_DAY)
                              + c.get(Calendar.MINUTE)
                              + c.get(Calendar.SECOND);

      int check = deviceVersion.compareTo("2.3.3");

      File folder;

      /*
       * File path = Environment.getExternalStoragePublicDirectory(
       * Environment.DIRECTORY_PICTURES ); //this throws error in Android
       * 2.2
       */
      if (check >= 1) {
        folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        if (!folder.exists()) {
          folder.mkdirs();
        }

      } else {
        folder = Environment.getExternalStorageDirectory();
      }

      File imageFile = new File(folder, prefix + date + ".png");

      FileOutputStream out = new FileOutputStream(imageFile);
      bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      out.close();

      if (imageFile == null) {
        callbackContext.error("Error while saving image");
      }

      // Update image gallery
      if (this._mediaScannerEnabled) {
        scanPhoto(imageFile);
      }

      callbackContext.success(imageFile.toString());


    } catch (Exception e) {
      Log.e("Base64ToGallery", "An exception occured while saving image: " + e.toString());
      callbackContext.error("An exception occured while saving image: " + e.toString());
    }

  }

  /**
   * Invoke the system's media scanner to add your photo to the Media Provider's database,
   * making it available in the Android Gallery application and to other apps.
   */
  private void scanPhoto(File imageFile) {
    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    Uri contentUri         = Uri.fromFile(imageFile);

    mediaScanIntent.setData(contentUri);

    cordova.getActivity().sendBroadcast(mediaScanIntent);
  }

  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
    for (int r : grantResults) {
      if (r == PackageManager.PERMISSION_DENIED) {
        Log.d("SaveImageGallery", "Permission not granted by the user");
        _callback.error("Permissions denied");
        return;
      }
    }

    switch (requestCode) {
      case 1000:
        Log.d("SaveImageGallery", "User granted the permission for WRITE_EXTERNAL_STORAGE");
        savePhoto(this._bmp, this._filePrefix, this._callback);
        break;
    }
  }
}
