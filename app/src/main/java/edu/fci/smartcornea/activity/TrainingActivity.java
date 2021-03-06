package edu.fci.smartcornea.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import edu.fci.smartcornea.R;
import edu.fci.smartcornea.core.Communicator;
import edu.fci.smartcornea.core.DataManager;
import edu.fci.smartcornea.core.OpenCVEngine;
import edu.fci.smartcornea.util.Constant;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrainingActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private final int NEW_PERSON_NAME_REQUEST_CODE = 0;
    private static final String TAG = "TrainingActivity";

    private Mat mRgba;
    private Mat mGray;
    private OpenCVEngine mOpenCVEngine;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase mOpenCvCameraView;
    private LinearLayout mTrainingImagesLayout;
    private Button mSaveButton;
    private Button mCaptureButton;

    private ArrayList<ImageView> mTrainingImages;
    private ArrayList<Mat> mGrayTrainingImages;
    private boolean captureNow = false;

    private Communicator communicator;

    public TrainingActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_training);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.training_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mTrainingImagesLayout = (LinearLayout) findViewById(R.id.training_images_layout);
        mSaveButton = (Button) findViewById(R.id.save_button);
        mCaptureButton = (Button) findViewById(R.id.capture_button);

        mOpenCVEngine = OpenCVEngine.getInstance();
        communicator = Communicator.getInstance();
        mTrainingImages = new ArrayList<>();
        mGrayTrainingImages = new ArrayList<>();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mOpenCvCameraView.enableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mOpenCVEngine.setDetectorMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();

        if (mOpenCVEngine != null)
            mOpenCVEngine.detect(mGray, faces);

        Rect[] facesArray = faces.toArray();
        if (captureNow) {
            if (facesArray.length == 1) {
                try {
                    addNewImage(mRgba.submat(facesArray[0]), mGray.submat(facesArray[0]));
                }catch (Exception e) {}
            }
            captureNow = false;
        }
        for (int i = 0; i < facesArray.length; i++) {
            try {
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), Constant.FACE_RECT_COLOR, 2);
            } catch(Exception e) {}
        }
        return mRgba;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_PERSON_NAME_REQUEST_CODE && resultCode == RESULT_OK) {
            String personName = data.getStringExtra("newPersonName");
            ArrayList<Integer> labels = new ArrayList<>();
            int label = mOpenCVEngine.getRandomLabel();
            for(int i = 0; i < mGrayTrainingImages.size(); ++i) {
                labels.add(label);
            }
            mOpenCVEngine.updateRecognizer(mGrayTrainingImages, labels);
            mOpenCVEngine.setLabelInfo(label, personName);
            saveRecognizerState();
            Toast.makeText(this.getApplicationContext(), "Person Added Successfully, id = " + label, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void saveClicked(View view) {
        Intent intent = new Intent(this, SavePersonActivity.class);
        startActivityForResult(intent, NEW_PERSON_NAME_REQUEST_CODE);
    }

    public void captureClicked(View view) {
        captureNow = true;
    }

    public void cancelClicked(View view) {
        finish();
    }

    private void addNewImage(Mat rgbaImage, Mat grayImage) {
        Bitmap bmp = Bitmap.createBitmap(rgbaImage.cols(), rgbaImage.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbaImage, bmp);
        ImageView img = new ImageView(this);
        img.setImageBitmap(Bitmap.createScaledBitmap(bmp, 100, 100, false));
        mTrainingImages.add(img);
        mGrayTrainingImages.add(grayImage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTrainingImagesLayout.addView(mTrainingImages.get(mTrainingImages.size() - 1));
                if (mTrainingImages.size() >= 10) {
                    mSaveButton.setEnabled(true);
                    mSaveButton.postInvalidate();
                    mCaptureButton.setEnabled(false);
                    mCaptureButton.postInvalidate();
                }
            }
        });
    }

    private void saveRecognizerState() {
        try {
            File tempDir = getDir("scTempDir", Context.MODE_PRIVATE);
            File mTemplateFile = new File(tempDir, "template.xml");
            mOpenCVEngine.saveRecognizer(mTemplateFile.getAbsolutePath());

            FileInputStream is = new FileInputStream(mTemplateFile);
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                for(int i = 0; i < bytesRead; ++i) {
                    sb.append((char)buffer[i]);
                }
            }
            String state = new String(Base64.encode(sb.toString().getBytes(), Base64.DEFAULT));
            is.close();
            DataManager dm = DataManager.getInstance();
            communicator.storeStateFile((String)dm.getObject(Constant.DOMAIN_ID), state).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TrainingActivity.this, "Recognizer state saved on the server.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TrainingActivity.this, "Couldn't save recognizer state on the server.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            tempDir.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
