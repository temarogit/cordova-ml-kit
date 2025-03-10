package hr.antoniostipic;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.net.Uri;
import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.graphics.Point;

import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import java.io.IOException;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;

import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.util.List;

public class MlKitPlugin extends CordovaPlugin {
    private static final String TAG = "MlKitPlugin";
    private static Context context;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        context = cordova.getActivity().getApplicationContext();
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("getText")) {
            final String img = args.getString(0);
            cordova.getThreadPool().execute(() -> runTextRecognition(callbackContext, img, "", false));
            return true;
        } else if (action.equals("getTextCloud")) {
            final String img = args.getString(0);
            final String lang = args.getString(1);
            Log.d(TAG, "LANGUAGE=" + lang);
            cordova.getThreadPool().execute(() -> runTextRecognition(callbackContext, img, lang, true));
            return true;
        } else if (action.equals("getLabel")) {
            final String img = args.getString(0);
            cordova.getThreadPool().execute(() -> runLabelRecognition(callbackContext, img, false));
            return true;
        } else if (action.equals("getLabelCloud")) {
            final String img = args.getString(0);
            cordova.getThreadPool().execute(() -> runLabelRecognition(callbackContext, img, true));
            return true;
        }else if (action.equals("getTextLive")) {
            final ByteBuffer img = str_to_bb(args.getString(0));
            final JSONObject frameMetadata = args.getJSONObject(1);
            cordova.getThreadPool().execute(() -> runTextRecognitionLive(callbackContext, img, frameMetadata, "", false));
            return true;
        }
        return false;
    }

    private JSONObject mapBoundingBox(Rect rect) throws JSONException {
      JSONObject oBloundingBox = new JSONObject();
      oBloundingBox.put("left", rect.left);
      oBloundingBox.put("right", rect.right);
      oBloundingBox.put("top", rect.top);
      oBloundingBox.put("bottom", rect.bottom);
      return oBloundingBox;
    }

    private JSONArray mapPoints(Point[] points) throws JSONException {
      JSONArray aPoints = new JSONArray();
      for (Point point: points) {
        JSONObject oPoint =  new JSONObject();
        oPoint.put("x", point.x);
        oPoint.put("y", point.y);
        aPoints.put(oPoint);
      }
      return aPoints;
    }

    private void runTextRecognition(final CallbackContext callbackContext, final String img, final String language, final Boolean onCloud) {
        Uri imgSource = Uri.parse(img);

        try {
            FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(cordova.getContext(), imgSource);
            FirebaseVisionTextRecognizer textRecognizer;

            if(onCloud) {
                textRecognizer = this.getTextRecognitionCloud(language);
            } else {
                textRecognizer = this.getTextRecognitionDevice();
            }

            textRecognizer.processImage(image).addOnSuccessListener(texts -> {
              try {
                JSONObject json = new JSONObject();
                JSONArray blocks = new JSONArray();

                json.put("text", texts.getText());
                json.put("textBlocks", blocks);

                for (FirebaseVisionText.TextBlock block : texts.getTextBlocks()) {
                    Log.d(TAG, block.getText());
                    JSONObject oBlock = new JSONObject();
                    JSONArray lines = new JSONArray();
                    oBlock.put("text", block.getText());
                    oBlock.put("confidence", block.getConfidence());
                    oBlock.put("boundingBox", mapBoundingBox(block.getBoundingBox()));
                    oBlock.put("cornerPoints", mapPoints(block.getCornerPoints()));
                    oBlock.put("lines", lines);
                    blocks.put(oBlock);

                    for (FirebaseVisionText.Line line : block.getLines()) {
                      JSONObject oLine = new JSONObject();
                      oLine.put("text", line.getText());
                      oLine.put("confidence", line.getConfidence());
                      oLine.put("boundingBox", mapBoundingBox(line.getBoundingBox()));
                      oLine.put("cornerPoints", mapPoints(line.getCornerPoints()));
                      lines.put(oLine);
                    }
                }
                callbackContext.success(json);
              } catch(JSONException e) {
                e.printStackTrace();
                callbackContext.error(e.getMessage());
              }
            }).addOnFailureListener(e -> {
                e.printStackTrace();
                callbackContext.error(e.getMessage());
            });
        } catch (IOException e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }

    }

    private void runTextRecognitionLive(final CallbackContext callbackContext, final ByteBuffer data, final JSONObject frameMetadata, final String language, final Boolean onCloud) {
      //try {

          int width = 0;
          int height = 0;
          int rotation = 0;

          try{
            width = frameMetadata.getInt("width");
            height = frameMetadata.getInt("height");
            rotation = frameMetadata.getInt("rotation");
          }catch(JSONException e){
            Log.d(TAG, "framedata extraction failed");
            
            e.printStackTrace();
            callbackContext.error(e.getMessage());
          }  

          Log.d(TAG, "frameData Extracted");
          Log.d(TAG, "width: " + width);
          Log.d(TAG, "height: " + height);
          Log.d(TAG, "rotation: " + rotation);

          FirebaseVisionImageMetadata metadata =
          new FirebaseVisionImageMetadata.Builder()
              .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
              .setWidth(width)
              .setHeight(height)
              .setRotation(rotation)
              .build();

          Log.d(TAG, "metadata initialized");
          

          FirebaseVisionTextRecognizer textRecognizer;

          if(onCloud) {
              textRecognizer = this.getTextRecognitionCloud(language);
          } else {
              textRecognizer = this.getTextRecognitionDevice();
          }

          Log.d(TAG, "textrecognizer initialized");
          

          // get image 
          FirebaseVisionImage image = FirebaseVisionImage.fromByteBuffer(data, metadata);

          Log.d(TAG, "FirebaseVisionImage extracted");

          textRecognizer.processImage(image).addOnSuccessListener(texts -> {
            Log.d(TAG, "process image successful");
          
            try {
              JSONObject json = new JSONObject();
              JSONArray blocks = new JSONArray();

              json.put("text", texts.getText());
              json.put("textBlocks", blocks);

              for (FirebaseVisionText.TextBlock block : texts.getTextBlocks()) {
                  Log.d(TAG, block.getText());
                  JSONObject oBlock = new JSONObject();
                  JSONArray lines = new JSONArray();
                  oBlock.put("text", block.getText());
                  oBlock.put("confidence", block.getConfidence());
                  oBlock.put("boundingBox", mapBoundingBox(block.getBoundingBox()));
                  oBlock.put("cornerPoints", mapPoints(block.getCornerPoints()));
                  oBlock.put("lines", lines);
                  blocks.put(oBlock);

                  for (FirebaseVisionText.Line line : block.getLines()) {
                    JSONObject oLine = new JSONObject();
                    oLine.put("text", line.getText());
                    oLine.put("confidence", line.getConfidence());
                    oLine.put("boundingBox", mapBoundingBox(line.getBoundingBox()));
                    oLine.put("cornerPoints", mapPoints(line.getCornerPoints()));
                    lines.put(oLine);
                  }
              }

              Log.d(TAG, "text extracted");  

              callbackContext.success(json);
            } catch(JSONException e) {
              Log.d(TAG, "text extraction failed");

              e.printStackTrace();
              callbackContext.error(e.getMessage());
            }
          }).addOnFailureListener(e -> {
              Log.d(TAG, "process image failed");

              e.printStackTrace();
              callbackContext.error(e.getMessage());
          });
      //} catch (IOException e) {
      //    e.printStackTrace();
      //    callbackContext.error(e.getMessage());
      //}

  }

    private void runLabelRecognition(final CallbackContext callbackContext, final String img, final Boolean onCloud) {
        Uri imgSource = Uri.parse(img);

        try {
            FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(cordova.getContext(), imgSource);

            FirebaseVisionImageLabeler labeler;

            if (onCloud) {
            	labeler = FirebaseVision.getInstance().getCloudImageLabeler();
            } else {
            	labeler = FirebaseVision.getInstance().getOnDeviceImageLabeler();
            }

            labeler.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                  @Override
                  public void onSuccess(List<FirebaseVisionImageLabel> labels) {

                    JSONObject json = new JSONObject();
                    
                    int i = 0;
                    for (FirebaseVisionImageLabel label: labels) {
                      try {

                          String text = label.getText();
                          String entityId = label.getEntityId();
                          float confidence = label.getConfidence();

                          JSONObject jsonObject = new JSONObject();
                          jsonObject.put("text", text);
                          jsonObject.put("entityId", entityId);
                          jsonObject.put("confidence", confidence);

                          json.put(String.valueOf(i), jsonObject);

                          i = i + 1;
                      } catch (JSONException e) {
                          e.printStackTrace();
                      }
                    }

                    callbackContext.success(json);
                  }
                })
                .addOnFailureListener(new OnFailureListener() {
                  @Override
                  public void onFailure(@NonNull Exception e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                  }
                });

        } catch (IOException e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }

    }

    private FirebaseVisionTextRecognizer getTextRecognitionDevice() {
        return FirebaseVision.getInstance().getOnDeviceTextRecognizer();
    }

    private FirebaseVisionTextRecognizer getTextRecognitionCloud( final String language) {
        if (!language.isEmpty()) {
            FirebaseVisionCloudTextRecognizerOptions options = new FirebaseVisionCloudTextRecognizerOptions.Builder()
                    .setLanguageHints(Arrays.asList(language)).build();

            return FirebaseVision.getInstance()
                    .getCloudTextRecognizer(options);
        } else {
            return FirebaseVision.getInstance().getCloudTextRecognizer();
        }
    }

    private static ByteBuffer str_to_bb(String msg){
        return ByteBuffer.wrap(msg.getBytes());
    }

}
