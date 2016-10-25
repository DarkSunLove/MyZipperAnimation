package com.lizai.passion.myzipperanimation.anim;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

public class FloatValueEvaluator implements TypeEvaluator {
    private OnAnimationValueChangedListener mOnAnimationValueChangedListener;
    private ValueAnimator mValueAnimator;
    private float mCounter;

    public FloatValueEvaluator(OnAnimationValueChangedListener listener) {
        mOnAnimationValueChangedListener = listener;
    }

    @Override
    public Object evaluate(float fraction, Object from, Object to) {
        float value = (float) from + mCounter * fraction;
        mOnAnimationValueChangedListener.onAnimationValueChanged(value);
        return value;
    }

    public void start(float from, float to, long duration) {
        mCounter = to - from;
        mValueAnimator = ValueAnimator.ofObject(this, from, to);
        mValueAnimator.setDuration(duration);
        mValueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mValueAnimator.start();
    }

    public void cancel() {
        mValueAnimator.cancel();
        mValueAnimator = null;
    }

    public interface OnAnimationValueChangedListener {
        void onAnimationValueChanged(float value);
        //void onAnimationStopped(float value);
    }
}