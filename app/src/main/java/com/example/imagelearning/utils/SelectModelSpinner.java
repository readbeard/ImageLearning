package com.example.imagelearning.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.example.imagelearning.R;

public class SelectModelSpinner extends androidx.appcompat.widget.AppCompatSpinner implements View.OnClickListener{
    private ImageView icon;
    private final Animation rotateToZero = AnimationUtils.loadAnimation(getContext(), R.anim.rotation_to_0);
    private final Animation rotateToPi = AnimationUtils.loadAnimation(getContext(), R.anim.rotation_to_180);

    public SelectModelSpinner(Context context) {
        super(context);
    }

    public SelectModelSpinner(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        rotateToPi.setFillAfter(true);
        rotateToPi.setDuration(300);
        rotateToZero.setFillAfter(true);
        rotateToZero.setDuration(300);
    }

    @Override
    public void onClick(View view) {
        if (hasBeenOpened()) {
            performClosedEvent();
        } else {
            performClick();
        }
    }

    private boolean mOpenInitiated = false;

    @Override
    public boolean performClick() {
        // register that the Spinner was opened so we have a status
        // indicator for when the container holding this Spinner may lose focus
        mOpenInitiated = true;
        icon.startAnimation(rotateToPi);
        return super.performClick();
    }

    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
        if (hasBeenOpened() && hasFocus) {
            performClosedEvent();
        }
    }

    public void performClosedEvent() {
        mOpenInitiated = false;
        icon.startAnimation(rotateToZero);
    }

    public boolean hasBeenOpened() {
        return mOpenInitiated;
    }

    public void setIcon(ImageView icon) {
        this.icon = icon;
        this.icon.setOnClickListener(this);
    }

    public ImageView getIcon() {
        return icon;
    }

}
