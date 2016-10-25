package com.lizai.passion.myzipperanimation.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.widget.FrameLayout;

import com.lizai.passion.myzipperanimation.R;
import com.lizai.passion.myzipperanimation.anim.FloatValueEvaluator;

public class ZipperView extends FrameLayout implements FloatValueEvaluator.OnAnimationValueChangedListener {

    private final int MAX_CORNER_RADIUS = dp2px(30);

    private int mWidth, mHeight;
    private int mCenterX;
    private float mCurrentY = 0;
    private float mCornerRadius = 0;

    private boolean mIsTouchEffective = false;
    private VelocityTracker mTracker = null;
    private OnZipperOpenedListener mOnZipperOpenedListener;

    private Path mBgPath = new Path(); // 背景图片路径（不含折角）
    private Path mLeftCornerPath = new Path();
    private Path mRightCornerPath = new Path();
    private PointF mStartPoint = new PointF();

    private BitmapDrawable wallpaper = (BitmapDrawable) getResources().getDrawable(R.mipmap.wallpaper);
    private Drawable mShadowInner = getResources().getDrawable(R.mipmap.lc_zipper_corner_shadow);
    private Drawable mShadowBack = getResources().getDrawable(R.mipmap.lc_zipper_shadow_bg);
    private Drawable mShadowChain = getResources().getDrawable(R.mipmap.lc_zipper_shadow_chain);
    private BitmapDrawable zipperChain = (BitmapDrawable) getResources().getDrawable(R.mipmap.lc_zipper_chain);
    private BitmapDrawable leftChain = (BitmapDrawable) getResources().getDrawable(R.mipmap.lc_zipper_chain_left);
    private BitmapDrawable rightChain = (BitmapDrawable) getResources().getDrawable(R.mipmap.lc_zipper_chain_right);
    private Drawable zipperHandlerBottom = getResources().getDrawable(R.mipmap.rounded_rectangle3);
    private Drawable zipperHandlerMid = getResources().getDrawable(R.mipmap.rounded_rectangle2);
    private Drawable zipperHandlerTop = getResources().getDrawable(R.mipmap.rounded_rectangle);

    // 拐角曲线变量
    private int mChainCenterX = leftChain.getIntrinsicWidth() / 2;
    private int mChainStepLength; // 拉链拐角步长，平衡性能和精度
    private int mChainStepCount;
    private float[] mChainStepVerts;

    public ZipperView(Context context) {
        this(context, null);
    }

    public ZipperView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ZipperView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
    }

    private void drawTopCorner(Canvas canvas) {
        canvas.save();
        Rect rect = new Rect();
        int cornerWidth = dp2px(20);
        float startX = mCenterX - mCurrentY + mCornerRadius;
        float endX = startX + dp2px(20); // TODO 计算距离
        if (mCornerPoints[2].x > mCenterX) {
            startX = mWidth - startX;
            endX = mWidth - endX;
            cornerWidth = -cornerWidth;
        }
        rect.set((int) startX, 0, (int) endX, Math.abs(cornerWidth));
        float[] srcBase = new float[]{rect.left, rect.top, rect.right, rect.top, rect.right, rect.bottom, rect.left, rect.bottom};
        float[] destBase = new float[]{
                mCornerPoints[3].x, mCornerPoints[3].y, // 顶角
                mCornerPoints[2].x, mCornerPoints[2].y, // 底部
                mCornerPoints[2].x + cornerWidth, mCornerPoints[2].y, // 补一个底部
                mCornerPoints[3].x + cornerWidth, mCornerPoints[3].y // 补一个顶角
        };
        if (mCornerPoints[2].x > mCenterX) {
            rect.set((int) endX, 0, (int) startX, Math.abs(cornerWidth));
        }
        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(srcBase, 0, destBase, 0, 4);
        canvas.concat(matrix);
        wallpaper.setBounds(rect);
        canvas.drawBitmap(wallpaper.getBitmap(), rect, rect, null);
        canvas.restore();
    }

    private void drawZipChain(Canvas canvas) {
        long time = System.currentTimeMillis();
        // 初始化
        float cornerX = mCurrentY < mCenterX ? mCenterX - mCurrentY : 0;
        float cornerY = mCurrentY;
        float endY = cornerY + mCornerRadius;

        int offset = mCurrentY > mCenterX ? (int) ((mCurrentY - mCenterX) / mChainStepLength) : 0;
        int count = (int) (mCurrentY / mChainStepLength - offset);

        for (int i = 0; i <= offset && i < mChainStepCount; i++) {
            int startIndex = i * 4;
            mChainStepVerts[startIndex] = cornerX;
            mChainStepVerts[startIndex + 1] = cornerY;
            mChainStepVerts[startIndex + 2] = cornerX;
            mChainStepVerts[startIndex + 3] = cornerY;
        }
        for (int i = offset; i <= mChainStepCount; i++) {
            int startIndex = i * 4;
            mChainStepVerts[startIndex] = mCenterX;
            mChainStepVerts[startIndex + 1] = endY;
            mChainStepVerts[startIndex + 2] = mCenterX;
            mChainStepVerts[startIndex + 3] = endY;
        }
        // 折一下角
        float controlX = cornerX + mCornerRadius * 2.5f;
        float controlY = cornerY - mCornerRadius * 0.4f * (mCenterX - cornerX) / mCenterX;
        Path bottomPath = new Path();
        bottomPath.moveTo(cornerX, cornerY);
        bottomPath.cubicTo(controlX, controlY, mCenterX, controlY, mCenterX, endY);
        PathMeasure pm = new PathMeasure(bottomPath, false);
        float[] point = new float[2];
        float[] tan = new float[2];
        float step = pm.getLength() / count;
        count = offset + count <= mChainStepCount ? count : mChainStepCount - offset; // 解锁时防止数组越界
        for (int i = 0; i < count; i++) {
            int startIndex = (offset + i) * 4;
            pm.getPosTan(i * step, point, tan);
            mChainStepVerts[startIndex] = point[0];
            mChainStepVerts[startIndex + 1] = point[1] - mChainCenterX;
            mChainStepVerts[startIndex + 2] = point[0];
            mChainStepVerts[startIndex + 3] = point[1] + mChainCenterX;
        }
        canvas.drawBitmapMesh(leftChain.getBitmap(), 1, mChainStepCount, mChainStepVerts, 0, null, 0, null);

        for (int i = 0; i <= mChainStepCount; i++) {
            int startIndex = i * 4;
            float temp = mWidth - mChainStepVerts[startIndex];
            mChainStepVerts[startIndex] = mWidth - mChainStepVerts[startIndex + 2];
            mChainStepVerts[startIndex + 2] = temp;
            temp = mChainStepVerts[startIndex + 1];
            mChainStepVerts[startIndex + 1] = mChainStepVerts[startIndex + 3];
            mChainStepVerts[startIndex + 3] = temp;
        }
        canvas.drawBitmapMesh(rightChain.getBitmap(), 1, mChainStepCount, mChainStepVerts, 0, null, 0, null);
    }

    private void drawShadow(Canvas canvas, Drawable shadow, float degrees, float x, float y) {
        canvas.save();
        canvas.rotate(degrees, x, y);
        shadow.draw(canvas);
        canvas.restore();
    }

    private void init() {
        if (mHeight != getHeight()) {
            mWidth = getWidth();
            mHeight = getHeight();
            mCenterX = mWidth / 2;
            mShadowInner.setBounds(dp2px(-100), 0, dp2px(500), mShadowInner.getIntrinsicHeight());
            mShadowBack.setBounds(dp2px(-400), dp2px(5) - mShadowBack.getIntrinsicHeight(), dp2px(800), dp2px(5));
            int zipChainWidth = zipperChain.getIntrinsicWidth() / 2;
            zipperChain.setBounds(mCenterX - zipChainWidth, 0, mCenterX + zipChainWidth, mHeight);
            mShadowChain.setBounds(mCenterX - zipChainWidth, 0, mCenterX + zipChainWidth, mHeight);

            mChainStepLength = 1;
            mChainStepCount = leftChain.getIntrinsicHeight() / mChainStepLength;
            mChainStepVerts = new float[(mChainStepCount + 1) * 2 * 2];
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long time = System.currentTimeMillis();
        init();

        Rect rect = new Rect(0, 0, getWidth(), getHeight());
        initPath(mCurrentY, mCornerRadius);
        Log.d(getClass().getSimpleName(), "time_init=" + (System.currentTimeMillis() - time));

        // 折角外阴影
        drawShadow(canvas, mShadowBack, 45, mStartPoint.x - mStartPoint.y, 0);
        drawShadow(canvas, mShadowBack, -45, mWidth - mStartPoint.x + mStartPoint.y, 0);

        // 壁纸----------------------------------
        canvas.save();
        canvas.clipPath(mBgPath);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // 6.0系统文字可能画过被切割的canvas
            canvas.clipPath(mLeftCornerPath, Region.Op.UNION);
            canvas.clipPath(mRightCornerPath, Region.Op.UNION);
        }
        wallpaper.setBounds(rect);
        wallpaper.draw(canvas);

        // 拉链
        zipperChain.draw(canvas);
        mShadowChain.draw(canvas);

        super.dispatchDraw(canvas);

        // 折角直角边阴影
        canvas.clipRect(0, 0, mWidth, mCurrentY + dp2px(10));
        drawShadow(canvas, mShadowBack, -90, mCenterX - mCurrentY, 0);
        drawShadow(canvas, mShadowBack, 90, mCenterX + mCurrentY, 0);
        canvas.restore();
        // 壁纸----------------------------------

        // 翻折部分------------------------------
        canvas.save();
        canvas.clipPath(mLeftCornerPath);
        canvas.clipPath(mRightCornerPath, Region.Op.UNION);
        canvas.clipPath(mBgPath, Region.Op.DIFFERENCE);
        // 画折角
        drawCorner(canvas, true);
        drawCorner(canvas, false);

        // 补齐折角上拐角
        if (mCurrentY <= mCenterX) {
            drawTopCorner(canvas);
            imageMirrorTransformation(mCornerPoints); // 取镜像点
            drawTopCorner(canvas);
        }

        // 折角内阴影
        drawShadow(canvas, mShadowInner, 45, mStartPoint.x - mStartPoint.y, 0);
        drawShadow(canvas, mShadowInner, -45, mWidth - mStartPoint.x + mStartPoint.y, 0);
        canvas.restore();
        // 翻折部分------------------------------


        // 链带----------------------------------
        canvas.save();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            canvas.clipPath(mBgPath);
            canvas.clipPath(mRightCornerPath, Region.Op.UNION);
            canvas.clipPath(mLeftCornerPath, Region.Op.UNION);
        }
        drawZipChain(canvas);
        canvas.restore();

        // 拉链拉头--------------------------------
        drawZipperHandler(canvas);

    }

    private void drawDrawableCenter(Canvas canvas, Drawable drawable, int x, int y) {
        int centerX = drawable.getIntrinsicWidth() / 2;
        int centerY = drawable.getIntrinsicHeight() / 2;
        drawable.setBounds(x - centerX, y - centerY, x + centerX, y + centerY);
        drawable.draw(canvas);
    }

    private void drawDrawableTop(Canvas canvas, Drawable drawable, int x, int y) {
        int centerX = drawable.getIntrinsicWidth() / 2;
        int height = drawable.getIntrinsicHeight();
        drawable.setBounds(x - centerX, y, x + centerX, y + height);
        drawable.draw(canvas);
    }

    private void drawZipperHandler(Canvas canvas) {
        int x = mCenterX;
        int y = (int) (mCurrentY + mCornerRadius / 2 + zipperHandlerBottom.getIntrinsicHeight() / 2);
        drawDrawableCenter(canvas, zipperHandlerBottom, x, y);
        drawDrawableTop(canvas, zipperHandlerMid, x, y - dp2px(5));
        drawDrawableCenter(canvas, zipperHandlerTop, x, y);
    }

    private PointF[] mCornerPoints;
    private PointF[] mControlPoints;
    private PointF[] getCornerPoints(float radius) {
        if (mCornerPoints == null) {
            mCornerPoints = new PointF[4];
            mControlPoints = new PointF[2];
            for (int i = 0; i < mCornerPoints.length; i++) {
                mCornerPoints[i] = new PointF();
            }
            for (int i = 0; i < mControlPoints.length; i++) {
                mControlPoints[i] = new PointF();
            }
        }
        mCornerPoints[0].set(-radius, 0);
        mCornerPoints[1].set(0, 0);
        mCornerPoints[2].set(0, radius);
        mCornerPoints[3].set(-radius / 4, radius / 4);
        mControlPoints[0].set(-radius / 2, 0);
        mControlPoints[1].set(0, radius / 2);
        return mCornerPoints;
    }

    private void offsetPoints(PointF[] points, float offsetX, float offsetY) {
        if (offsetX != 0 || offsetY != 0) {
            for (int i = 0; i < points.length; i++) {
                points[i].offset(offsetX, offsetY);
            }
        }
    }

    private void initLeftCorner(Path path, PointF[] points, float offsetX, float offsetY) {
        offsetPoints(points, offsetX, offsetY);
        path.lineTo(points[0].x, points[0].y); // 拐角起点
        path.quadTo(points[1].x, points[1].y,
                points[2].x, points[2].y); // 拐角
    }

    private void initRightCorner(Path path, PointF[] points, float offsetX, float offsetY) {
        offsetPoints(points, offsetX, offsetY);
        path.lineTo(points[2].x, points[2].y); // 拐角起点
        path.quadTo(points[1].x, points[1].y,
                points[0].x, points[0].y); // 拐角
    }

    private void addPointToPath(Path path, PointF[] points, int... ids) {
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (path.isEmpty()) {
                path.moveTo(points[id].x, points[id].y);
            } else {
                path.lineTo(points[id].x, points[id].y);
            }
        }
    }

    private void imageMirrorTransformation(PointF[] points) {
        for (int i = 0; i < points.length; i++) {
            points[i].x = mWidth - points[i].x;
        }
    }

    private void initPath(float mCurrentY, float mCornerRadius) {

        int height = getHeight();

        PointF[] cornerPoints = getCornerPoints(mCornerRadius);
        mBgPath.reset();
        mLeftCornerPath.reset();
        mRightCornerPath.reset();

        // 背景壁纸左侧区域
        mStartPoint.set(mCenterX - mCurrentY + cornerPoints[3].x, cornerPoints[3].y);
        if (mCurrentY <= mCenterX) {
            mBgPath.moveTo(0, 0); // 左上角
            initLeftCorner(mBgPath, cornerPoints, mCenterX - mCurrentY, 0); // 第一个拐角
            mBgPath.lineTo(mCenterX - mCurrentY, mCurrentY); // 折角
            addPointToPath(mLeftCornerPath, cornerPoints, 3, 2); // 拐角中点、内侧
            mLeftCornerPath.lineTo(mCenterX - mCurrentY, mCurrentY); // 折角
            initLeftCorner(mBgPath, cornerPoints, mCurrentY, mCurrentY); // 第二个拐角
        } else {
            mBgPath.moveTo(0, mCurrentY); // 左上角
            mLeftCornerPath.moveTo(mStartPoint.x, mStartPoint.y);
            mLeftCornerPath.lineTo(0, mCurrentY);
            initLeftCorner(mBgPath, cornerPoints, mCenterX, mCurrentY); // 第二个拐角
        }
        addPointToPath(mLeftCornerPath, cornerPoints, 0, 3); // 拐角内侧、中点

        // 背景壁纸右侧区域
        imageMirrorTransformation(cornerPoints); // 取镜像点
        initRightCorner(mBgPath, cornerPoints, 0, 0); // 第三个拐角
        addPointToPath(mRightCornerPath, cornerPoints, 3, 0); // 拐角中点、内侧
        mBgPath.lineTo(mCenterX + mCornerRadius, mCurrentY); // 右侧第一个拐角起点
        if(mCurrentY <= mCenterX) {
            mBgPath.lineTo(mCenterX + mCurrentY, mCurrentY); // 右侧折角
            initRightCorner(mBgPath, cornerPoints, mCurrentY, -mCurrentY); // 第四个拐角
            mBgPath.lineTo(mWidth, 0); // 右上角
            mRightCornerPath.lineTo(mCenterX + mCurrentY, mCurrentY); // 右侧折角
            addPointToPath(mRightCornerPath, cornerPoints, 2, 3); // 拐角内侧、中点
        } else {
            mBgPath.lineTo(mWidth, mCurrentY); // 右上角
            mRightCornerPath.lineTo(mWidth, mCurrentY);
            mRightCornerPath.lineTo(mWidth - mStartPoint.x, mStartPoint.y);
        }

        mBgPath.lineTo(mWidth, height);
        mBgPath.lineTo(0, height);
        mBgPath.close();
        mLeftCornerPath.close();
        mRightCornerPath.close();
    }

    private void drawCorner(Canvas canvas, boolean isLeft) {
        Matrix matrix = new Matrix();
        RectF rect = new RectF();
        float width = mCurrentY < mCenterX ? mCurrentY : mCenterX;
        width = isLeft ? width : -width;
        rect.set(mCenterX - width, mCurrentY - Math.abs(width), mCenterX, mCurrentY);
        // 向下翻折
        canvas.save();
        float[] src = new float[]{rect.left, rect.top, rect.right, rect.top, rect.right, rect.bottom, rect.left, rect.bottom};
        float[] dest = new float[]{rect.left, rect.top, rect.left, rect.bottom, rect.right, rect.bottom, rect.right, rect.top};
        if (rect.right < rect.left) {
            rect.set(rect.right, rect.top, rect.left, rect.bottom);
        }
        matrix.setPolyToPoly(src, 0, dest, 0, 3);
        canvas.concat(matrix);
        Rect rect1 = new Rect();
        rect1.set((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
        canvas.drawBitmap(wallpaper.getBitmap(), rect1, rect1, null);
        canvas.restore();
        Log.d(getClass().getSimpleName(), "rect" + rect);

        //canvas.drawRect(rect, testPaint);
    }

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setCurrentPoint(MotionEvent event) {
        setCurrentPoint(event.getY() - dp2px(80));
    }

    private void setCurrentPoint(float x) {
        mCurrentY = x;
        if (mCurrentY < 0) {
            mCurrentY = 0;
        }
        mCornerRadius = mCurrentY * 0.25f;
        if (mCornerRadius > MAX_CORNER_RADIUS) {
            mCornerRadius = MAX_CORNER_RADIUS;
        }
        postInvalidate();
    }
    private void moveCurrentPoint(MotionEvent event) {
        if (mTracker == null) {
            getTracker();
        }
        mTracker.addMovement(event);
        mTracker.computeCurrentVelocity(1000);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsTouchEffective = calculateTouchArea(event.getX(), event.getY());
                if (mIsTouchEffective) {
                    setCurrentPoint(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsTouchEffective) {
                    setCurrentPoint(event);
                    moveCurrentPoint(event);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTouchEffective) {
                    if (event.getY() > mHeight - dp2px(80) || (event.getY() > mHeight * 0.3) && getYVelocity() > 1000) {
                        continueToEndPoint();
                    } else {
                        backToStartPoint();
                    }
                }
                break;
        }
        return true;
    }

    public boolean calculateTouchArea(float x, float y) {
        float startX = mWidth / 2 - dp2px(30);
        float endX = mWidth / 2 + dp2px(30);
        float startY = mCurrentY;
        float endY = startY + dp2px(80);

        if (x >= startX && x <= endX && y >= startY && y <= endY) {
            return true;
        }
        return false;
    }

    private VelocityTracker getTracker() {
        if (mTracker == null) {
            mTracker = VelocityTracker.obtain();
        } else {
            mTracker.clear();
        }
        return mTracker;
    }

    private float getYVelocity() {
        float yVelocity = 0f;
        if (mTracker != null) {
            yVelocity = mTracker.getYVelocity();
        }
        return yVelocity;
    }

    public interface OnZipperOpenedListener {
        void onZipperOpened();
    }

    public void setOnZipperOpenedListener(OnZipperOpenedListener listener) {
        this.mOnZipperOpenedListener = listener;
    }

    private void backToStartPoint() {
        mFloatValueEvaluator.start(mCurrentY, 0, 800);
    }

    private void continueToEndPoint() {
        float endX = mHeight + mCenterX + MAX_CORNER_RADIUS;
        mFloatValueEvaluator.start(mCurrentY, endX, 800);
    }

    private FloatValueEvaluator mFloatValueEvaluator = new FloatValueEvaluator(this);

    @Override
    public void onAnimationValueChanged(float value) {
        setCurrentPoint(value);
        if (value >= mHeight + mCenterX && mOnZipperOpenedListener != null) {
            mOnZipperOpenedListener.onZipperOpened();
        }
    }

    public void resetParams() {
        setCurrentPoint(0);
    }
}