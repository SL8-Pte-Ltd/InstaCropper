package com.yashoid.instacropper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Yashar on 3/11/2017.
 */

public class InstaCropperActivity extends Activity {

    public static final int DEFAULT_OUTPUT_QUALITY = 80;

    public static final String EXTRA_OUTPUT = MediaStore.EXTRA_OUTPUT;

    public static final String EXTRA_PREFERRED_RATIO = "preferred_ratio";
    public static final String EXTRA_MINIMUM_RATIO = "minimum_ratio";
    public static final String EXTRA_MAXIMUM_RATIO = "maximum_ratio";

    public static final String EXTRA_WIDTH_SPEC = "width_spec";
    public static final String EXTRA_HEIGHT_SPEC = "height_spec";

    public static final String EXTRA_OUTPUT_QUALITY = "output_quality";

    public static Intent getIntent(Context context, Uri src, Uri dst, int maxWidth, int outputQuality) {
        return getIntent(
                context,
                src,
                dst,
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                outputQuality
                );
    }

    public static Intent getIntent(Context context, Uri src, Uri dst, int widthSpec, int heightSpec, int outputQuality) {
        return getIntent(
                context,
                src,
                dst,
                InstaCropperView.DEFAULT_RATIO,
                InstaCropperView.DEFAULT_MINIMUM_RATIO,
                InstaCropperView.DEFAULT_MAXIMUM_RATIO,
                widthSpec,
                heightSpec,
                outputQuality
                );
    }

    public static Intent getIntent(Context context, Uri src, Uri dst,
            float preferredRatio, float minimumRatio, float maximumRatio,
            int widthSpec, int heightSpec, int outputQuality) {
        Intent intent = new Intent(context, InstaCropperActivity.class);

        intent.setData(src);

        intent.putExtra(EXTRA_OUTPUT, dst);

        intent.putExtra(EXTRA_PREFERRED_RATIO, preferredRatio);
        intent.putExtra(EXTRA_MINIMUM_RATIO, minimumRatio);
        intent.putExtra(EXTRA_MAXIMUM_RATIO, maximumRatio);

        intent.putExtra(EXTRA_WIDTH_SPEC, widthSpec);
        intent.putExtra(EXTRA_HEIGHT_SPEC, heightSpec);
        intent.putExtra(EXTRA_OUTPUT_QUALITY, outputQuality);

        return intent;
    }

    private InstaCropperView mInstaCropper;

    private int mWidthSpec;
    private int mHeightSpec;
    private int mOutputQuality;
    private boolean scaleType = true;

    private Uri mOutputUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instacropper);

        mInstaCropper = (InstaCropperView) findViewById(R.id.instacropper);

        Intent intent = getIntent();

        Uri uri = intent.getData();

        float defaultRatio = intent.getFloatExtra(EXTRA_PREFERRED_RATIO, InstaCropperView.DEFAULT_RATIO);
        float minimumRatio = intent.getFloatExtra(EXTRA_MINIMUM_RATIO, InstaCropperView.DEFAULT_MINIMUM_RATIO);
        float maximumRatio = intent.getFloatExtra(EXTRA_MAXIMUM_RATIO, InstaCropperView.DEFAULT_MAXIMUM_RATIO);

        mInstaCropper.setRatios(defaultRatio, minimumRatio, maximumRatio);
        mInstaCropper.setImageUri(uri);

        mWidthSpec = intent.getIntExtra(EXTRA_WIDTH_SPEC, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mHeightSpec = intent.getIntExtra(EXTRA_HEIGHT_SPEC, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mOutputQuality = intent.getIntExtra(EXTRA_OUTPUT_QUALITY, DEFAULT_OUTPUT_QUALITY);

        mOutputUri = intent.getParcelableExtra(EXTRA_OUTPUT);

        try {
            /*** Button actions ***/

            // crop action
            TextView cropButton = (TextView) findViewById(R.id.crop_button);
            cropButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onCropClick();
                }
            });


            // cancel action
            TextView cancelButton = (TextView) findViewById(R.id.cancel);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onCancel();
                }
            });

            // toggle scale action
            ImageButton btnSum = (ImageButton) findViewById(R.id.scale);
            btnSum.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onScaleClick();
                }
            });

        } catch (Exception e) {
        }
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_instacropper, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_crop);

        Drawable d = menuItem.getIcon().mutate();

        int color;

        if (Build.VERSION.SDK_INT < 23) {
            color = getResources().getColor(R.color.instacropper_crop_color);
        }
        else {
            color = getResources().getColor(R.color.instacropper_crop_color, getTheme());
        }

        d.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        menuItem.setIcon(d);

        return true;
    }
    */

    private Toast toast;

    private void showScaleWarning() {
        if (toast == null || toast.getView().getWindowVisibility() != View.VISIBLE) {
            toast = Toast.makeText(getApplicationContext(), "Can't zoom low resolution image", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private boolean onScaleClick() {
        if(!scaleType) {
            // fit center
            scaleType = true;
            mInstaCropper.scaleDrawableToFitWithinViewWithValidRatio();
            return true;
        }

        // Set default position & scale
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        final float screenHeight = displayMetrics.heightPixels;
        final float screenWidth = displayMetrics.widthPixels;
        final float imageHeight = mInstaCropper.getImageRawHeight();
        final float imageWidth = mInstaCropper.getImageRawWidth();
        final float mWidth = mInstaCropper.getMinWidth();
        final float mHeight = mInstaCropper.getMinHeight();

        float scale = 0;

        if (mInstaCropper.isPortrait()) {
            // new width = screenWidth
            float newHeight = screenWidth / imageWidth * imageHeight;
            scale = newHeight / imageHeight;

        } else {
            // new height = screenWidth
            float newWidth = screenWidth / imageHeight * imageWidth;
            scale = newWidth / imageWidth;
        }

        scale = Math.min(scale, mInstaCropper.getMaximumAllowedScale());
        if(scale <= mInstaCropper.getMinimumAllowedScale()) {
            scale = 0;
            showScaleWarning();
        }

        if(scaleType && scale > 0) {
            // crop center
            scaleType = false;
            mInstaCropper.setDrawableScale(scale);
        } else {
            // fit center
            scaleType = true;
            mInstaCropper.scaleDrawableToFitWithinViewWithValidRatio();
        }
        return true;
    }

    private void onCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private boolean onCropClick() {
        try {
            if (toast != null && toast.getView().getWindowVisibility() == View.VISIBLE) {
                toast.cancel();
            }
        } catch (Exception e) {

        }
        mInstaCropper.crop(mWidthSpec, mHeightSpec, mBitmapCallback);
        return true;
    }

    /*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        mInstaCropper.crop(mWidthSpec, mHeightSpec, mBitmapCallback);

        return true;
    }
    */

    private InstaCropperView.BitmapCallback mBitmapCallback = new InstaCropperView.BitmapCallback() {

        @Override
        public void onBitmapReady(final Bitmap bitmap) {
            if (bitmap == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            new AsyncTask<Void, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        OutputStream os = getContentResolver().openOutputStream(mOutputUri);

                        bitmap.compress(Bitmap.CompressFormat.JPEG, mOutputQuality, os);

                        os.flush();
                        os.close();

                        return true;
                    } catch (IOException e) { }

                    return false;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if (success) {
                        Intent data = new Intent();
                        data.setData(mOutputUri);
                        setResult(RESULT_OK, data);
                    }
                    else {
                        setResult(RESULT_CANCELED);
                    }

                    finish();
                }

            }.execute();
        }

    };

}
