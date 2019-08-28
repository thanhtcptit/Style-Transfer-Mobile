package com.mobile.adain;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Matrix;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = "AdaIN";
    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_GALLERY = 2;

    private TensorFlowInferenceInterface inferenceInterface;
    private static final String MODEL_FILE = "file:///android_asset/stylize_quantized.pb";
    private static final String INPUT_NODE = "input";
    private static final String STYLE_NODE = "style_num";
    private static final String OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid";
    private static final int NUM_STYLES = 26;

    private RecyclerView styleListRC;
    private RecyclerView.Adapter styleAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private ImageView previewIV;

    private int currentStyle = -1;
    private String currentImagePath;
    private Bitmap currentBitmap;
    private Bitmap currentResizedBitmap;
    private Bitmap currentResizedBitmapCopy;
    private int[] currentResizedResolution;
    private int[] imageData;
    private float[] imageDataFloat;

    private boolean debug = false;
    public boolean computing = false;

    public static final int IMAGE_LOW_BOUND_VALUE = 1280;

    private Handler handler;
    private HandlerThread handlerThread;

    private Button leftStyleBtn;
    private Button rightStyleBtn;
    private Button saveBtn;

    private SeekBar seekBar;
    private TextView weightTV;
    public boolean mixMode = false;
    private int[] styleAlpha;

    private CheckBox mixModeCB;

    // Resources
    String styles[] = {"Air, Iron, and Water" , "Bicentennial Print", "Black Spot",
                       "Brush Stroke", "Candy Tree", "Composition VII", "Dawn", "Evening Tones", "Feathers Leaves & Petals",
                       "Wiener Werkstätte", "Forest Witches", "Jealousy", "The City Of Paris", "A Muse",
                       "Landscape of Daydream", "Prisma Mosaic", "Perfume", "Abstract Geometric",
                       "Sigalion Wallpaper", "The Starry Night", "The Idea-Motion-Fight", "The Mellow Pad",
                       "The Staircase", "Udnie", "Japanese Calligraphy", "Wild Garden"};
    int stylesImageId[] = {R.drawable.style0, R.drawable.style1, R.drawable.style2,
                           R.drawable.style3, R.drawable.style4, R.drawable.style5, R.drawable.style6,
                           R.drawable.style7, R.drawable.style8, R.drawable.style9, R.drawable.style10,
                           R.drawable.style11, R.drawable.style12, R.drawable.style13, R.drawable.style14,
                           R.drawable.style15, R.drawable.style16, R.drawable.style17, R.drawable.style18,
                           R.drawable.style19, R.drawable.style20, R.drawable.style21, R.drawable.style22,
                           R.drawable.style23, R.drawable.style24, R.drawable.style25};
    private ArrayList<Style> styleArrays = new ArrayList<>();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermission();

        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);

        previewIV = findViewById(R.id.previewIV);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        styleArrays.clear();
        for(int i = 0; i < styles.length; i++) {
            styleArrays.add(new Style(styles[i], stylesImageId[i], 0));
        }

        styleListRC = findViewById(R.id.styleListRC);
        styleListRC.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        styleListRC.setLayoutManager(layoutManager);

        styleAdapter = new StyleAdapter(styleArrays, styleListRC, this);
        styleListRC.setAdapter(styleAdapter);

        currentBitmap = ImageUtils.drawableToBitmap(
                ContextCompat.getDrawable(this, R.drawable.default_preview));
        setUpPreviewImageData();
        previewIV.setImageBitmap(currentResizedBitmap);

        saveBtn = findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (computing) return;
                String path = ImageUtils.saveBitmap(currentResizedBitmapCopy);
                Toast.makeText(getApplicationContext(), "Saved to: " + path, Toast.LENGTH_SHORT).show();
            }
        });

        leftStyleBtn = findViewById(R.id.leftStyleBtn);
        rightStyleBtn = findViewById(R.id.rightStyleBtn);
        leftStyleBtn.setEnabled(false);
        leftStyleBtn.setAlpha(0.3f);
        rightStyleBtn.setEnabled(false);
        rightStyleBtn.setAlpha(0.3f);
        leftStyleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchStyleWithArrow(true);
            }
        });
        rightStyleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { switchStyleWithArrow(false);
            }
        });

        styleAlpha = new int[NUM_STYLES];
        for (int i = 0; i < NUM_STYLES; i++) styleAlpha[i] = 0;
        seekBar = findViewById(R.id.seekBar);
        seekBar.setMin(10);
        weightTV = findViewById(R.id.weightTV);

        seekBar.setEnabled(false);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                styleAlpha[currentStyle] = i;
                weightTV.setText("Alpha: " + i + " %");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (computing) return;
                runStyleTransfer(true);
            }
        });

        mixModeCB = findViewById(R.id.mixModeCB);
        mixModeCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mixMode = b;
                if (mixMode) {
                    toggleButton(leftStyleBtn, 0);
                    toggleButton(rightStyleBtn, 0);
                } else {
                    resetAppStatus();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public synchronized void onStart() {
        Log.i(TAG, "onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        Log.i(TAG, "onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                return true;
            }
        });
    }

    @Override
    public synchronized void onPause() {
        Log.i(TAG, "onPause " + this);
        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        Log.i(TAG, "onStop " + this);
//        if (!isFinishing()) {
//            Log.i(TAG, "Requesting finish");
//            finish();
//        }
//
//        handlerThread.quitSafely();
//        try {
//            handlerThread.join();
//            handlerThread = null;
//            handler = null;
//        } catch (final InterruptedException e) {
//            Log.e(TAG, "Exception: " + e);
//        }
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        Log.i(TAG, "onDestroy " + this);
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
            Toast.makeText(getApplicationContext(), "Processing...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    requestPermission();
                }
            }
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(MainActivity.this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    public void onTakePhotoAction(MenuItem mi) {
        if (computing) return;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photo = null;
            try {
                photo = ImageUtils.createImageFile();
                currentImagePath = photo.getAbsolutePath();
            } catch (IOException e)
            {
                Toast.makeText(MainActivity.this, "Error creating photo", Toast.LENGTH_SHORT).show();
            }
            if (photo != null) {
                Uri photoUri = FileProvider.getUriForFile(this, "com.mobile.adain.fileprovider", photo);
                Log.i(TAG, photoUri.toString());
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            }
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public void onOpenGalleryAction(MenuItem mi) {
        if (computing) return;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_GALLERY);
    }

    public void onResetStyleAction(MenuItem mi) {
        if (computing) return;
        resetAppStatus();
        layoutManager.scrollToPosition(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap bitmap = BitmapFactory.decodeFile(currentImagePath);
            Matrix rotate = ImageUtils.getRotateMatrix(currentImagePath);
            currentBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), rotate, true);
            setUpPreviewImageData();
            resetAppStatus();
        }
        else if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK) {
            try {
                currentBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
            } catch (Exception e) {
                e.printStackTrace();
            }
            setUpPreviewImageData();
            resetAppStatus();
        }
    }

    public void setUpPreviewImageData() {
        Log.i(MainActivity.TAG, "Content original: " + currentBitmap.getHeight() + "x" + currentBitmap.getWidth());
//        currentCroppedBitmap = ImageUtils.cropBitmap(currentBitmap);
        currentResizedResolution = ImageUtils.getResizedResolution(currentBitmap.getHeight(), currentBitmap.getWidth(),
                IMAGE_LOW_BOUND_VALUE);
        currentResizedBitmap = ImageUtils.getResizedBitmap(
                currentBitmap, currentResizedResolution[0], currentResizedResolution[1]);
        Log.i(MainActivity.TAG, "Content resized: " + currentResizedBitmap.getHeight() + "x" + currentResizedBitmap.getWidth());
        currentResizedResolution[0] = currentResizedResolution[1] = IMAGE_LOW_BOUND_VALUE;
        currentResizedBitmap = ImageUtils.cropBitmap(currentResizedBitmap);
        currentResizedBitmapCopy = Bitmap.createBitmap(currentResizedBitmap);
        Log.i(MainActivity.TAG, "Content cropped: " + currentResizedBitmap.getHeight() + "x" + currentResizedBitmap.getWidth());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            debug = !debug;
            if (currentStyle != -1) layoutManager.scrollToPosition(currentStyle);
        }
        return true;
    }

    public void switchStyleWithArrow(boolean left) {
        if (computing) return;
        int nextStyle;
        styleArrays.get(currentStyle).setIsApply(0);
        if (left) nextStyle = currentStyle - 1;
        else nextStyle = currentStyle + 1;

        toggleStyle(nextStyle);
        layoutManager.scrollToPosition(currentStyle);
    }

    public boolean isSelected() {
        int style = -1;
        for(int i = 0; i < styleArrays.size(); i++) {
            if (styleArrays.get(i).getIsApply() == 1) {
                style = i;
                break;
            }
        }
        return style != -1;
    }

    public void toggleButton(Button btn, int status) {
        if (btn.isEnabled() && status == 0) {
            btn.setEnabled(false);
            btn.setAlpha(0.3f);
        }
        else if (!btn.isEnabled() && status == 1) {
            btn.setEnabled(true);
            btn.setAlpha(1f);
        }
    }

    public void toggleSeekBar(int status) {
        if (seekBar.isEnabled() && status == 0)
            seekBar.setEnabled(false);
        else if (!seekBar.isEnabled() && status == 1)
            seekBar.setEnabled(true);
    }

    public void toggleUI(int status) {
        if (status == 0) {
            toggleButton(leftStyleBtn, status);
            toggleButton(saveBtn, status);
            toggleButton(rightStyleBtn, status);
            toggleSeekBar(status);
        } else {
            if (!mixMode) {
                if (currentStyle == 0) {
                    toggleButton(leftStyleBtn, 0);
                    toggleButton(rightStyleBtn, 1);
                } else {
                    toggleButton(leftStyleBtn, 1);
                    if (currentStyle == styleArrays.size() - 1)
                        toggleButton(rightStyleBtn, 0);
                    else toggleButton(rightStyleBtn, 1);
                }
            }
            else {
                toggleButton(leftStyleBtn, 0);
                toggleButton(rightStyleBtn, 0);
            }
            toggleButton(saveBtn, 1);
            toggleSeekBar(1);
        }
    }

    public void resetAppStatus() {
        for (int i = 0; i < styleArrays.size(); i++) {
            styleArrays.get(i).setIsApply(0);
            styleAlpha[i] = 0;
        }

        seekBar.setProgress(100);
        currentStyle = -1;
        toggleSeekBar(0);
        toggleButton(leftStyleBtn, 0);
        toggleButton(rightStyleBtn, 0);
        styleListRC.getAdapter().notifyDataSetChanged();
        previewIV.setImageBitmap(currentResizedBitmap);
    }

    public void toggleStyle(int style) {
        int state = styleArrays.get(style).getIsApply();
        styleArrays.get(style).setIsApply(1 - state);
        if (!mixMode) {
            for(int i = 0; i < styleArrays.size(); i++) {
                if (i == style) continue;
                styleArrays.get(i).setIsApply(0);
            }
        }
        styleAdapter.notifyDataSetChanged();

        if (state == 0) {
            if (currentStyle == -1 || styleAlpha[currentStyle] == 0) styleAlpha[style] = 100;
            else styleAlpha[style] = styleAlpha[currentStyle];
            currentStyle = style;
            seekBar.setProgress(styleAlpha[currentStyle]);
            runStyleTransfer(true);
        }
        else {
            if (!mixMode || !isSelected()) {
                currentStyle = -1;
                toggleSeekBar(0);
                toggleButton(leftStyleBtn, 0);
                toggleButton(rightStyleBtn, 0);
                styleListRC.getAdapter().notifyDataSetChanged();
                previewIV.setImageBitmap(currentResizedBitmap);
            }
            else if (mixMode) {
                styleAlpha[style] = 0;
                runStyleTransfer(false);
            }
        }
    }

    public void changeStyleCursor(int style) {
        currentStyle = style;
        toggleSeekBar(1);
        seekBar.setProgress(styleAlpha[style]);
    }

    public void runStyleTransfer(final boolean toggle) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                stylishImage(toggle);
            }
        });
    }

    public void stylishImage(final boolean toggle) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleUI(0);
            }
        });
        computing = true;
        float[] styleVals = new float[NUM_STYLES];
        if (mixMode) {
            for (int i = 0; i < NUM_STYLES; i++)
                styleVals[i] = (float) styleAlpha[i] / 100;
        } else {
            styleVals[currentStyle] = (float) styleAlpha[currentStyle] / 100;
        }
        currentResizedBitmapCopy = Bitmap.createBitmap(currentResizedBitmap);
        imageData = new int[currentResizedResolution[0] * currentResizedResolution[1]];
        imageDataFloat = new float[currentResizedResolution[0] * currentResizedResolution[1] * 3];

        currentResizedBitmapCopy.getPixels(imageData, 0, currentResizedBitmapCopy.getWidth(),
                0, 0, currentResizedBitmapCopy.getWidth(), currentResizedBitmapCopy.getHeight());
        for (int i = 0; i < imageData.length; i++) {
            int val = imageData[i];
            imageDataFloat[i * 3] = ((val >> 16) & 0xff) / 255.0f;
            imageDataFloat[i * 3 + 1] = ((val >> 8) & 0xff) / 255.0f;
            imageDataFloat[i * 3 + 2] = (val & 0xff) / 255.0f;
        }

        inferenceInterface.feed(INPUT_NODE, imageDataFloat, 1,
                currentResizedBitmapCopy.getWidth(), currentResizedBitmapCopy.getHeight(), 3);
        inferenceInterface.feed(STYLE_NODE, styleVals, NUM_STYLES);
        inferenceInterface.run(new String[] {OUTPUT_NODE}, false);
        inferenceInterface.fetch(OUTPUT_NODE, imageDataFloat);

        for (int i = 0; i < imageData.length; i++) {
            imageData[i] = 0xff000000
                    | (((int) (imageDataFloat[i * 3] * 255)) << 16)
                    | (((int) (imageDataFloat[i * 3 + 1] * 255)) << 8)
                    | (((int) (imageDataFloat[i * 3 + 2] * 255)));
        }
        currentResizedBitmapCopy.setPixels(imageData, 0, currentResizedBitmapCopy.getWidth(), 0, 0,
                currentResizedBitmapCopy.getWidth(), currentResizedBitmapCopy.getHeight());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (toggle)
                    toggleUI(1);
                previewIV.setImageBitmap(currentResizedBitmapCopy);
            }
        });
        computing = false;
    }
}
