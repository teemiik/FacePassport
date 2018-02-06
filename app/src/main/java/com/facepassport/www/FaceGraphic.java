package com.facepassport.www;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.facepassport.www.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;

import static com.facepassport.www.FacePassportActivity.isTakeImage;

class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 15.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float BOX_STROKE_WIDTH = 15.0f;

    private static final int COLOR_CHOICES[] = {
        Color.GREEN,
        Color.RED,
    };

    private Paint mFacePositionPaint;
    private Paint mIdPaint;
    private Paint mBoxPaint;

    private volatile Face mFace;
    private int mFaceId;

    private int top;
    private int weight;

    private int windowTop;
    private int windowWeight;

    private int selectedColor;

    FaceGraphic(GraphicOverlay overlay, int top, int weight, int windowTop, int windowWeght) {
        super(overlay);

        selectedColor = COLOR_CHOICES[1];

        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

        this.top = top;
        this.weight = weight;
        this.windowTop = windowTop;
        this.windowWeight = windowWeght;
    }

    void setId(int id) {
        mFaceId = id;
    }


     /*Updates the face instance from the detection of the most recent frame.  Invalidates the
     relevant portions of the overlay to trigger a redraw.*/
    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    //Draws the face annotations for position on the supplied canvas.
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset + yOffset / 3.5f;

        double lenthOfALine = Math.sqrt(Math.pow(right - left, 2) + Math.pow(bottom - top, 2));
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        int leftR = 3;
        int topR = 0;
        int rightR = this.windowTop;
        int bottomR = this.windowWeight - this.weight;

        double lenthOfALineR = Math.sqrt(Math.pow(rightR - leftR, 2) + Math.pow(bottomR - topR, 2));
        //canvas.drawRect(leftR, topR, rightR, bottomR, mBoxPaint);

        int middleX = bottomR / 4 + this.weight / 2;
        int middleY = rightR / 2;

        //find the area for a photo and change a color shapes
        if (lenthOfALine / lenthOfALineR >= 0.94 && lenthOfALine / lenthOfALineR <= 0.97
                && x >= middleX - middleX * 0.22 && x <= middleX
                && y >= middleY - middleY * 0.24 && y <= middleY + middleY * 0.24) {

            mBoxPaint.setColor(COLOR_CHOICES[0]);

            mFacePositionPaint.setColor(COLOR_CHOICES[0]);

            isTakeImage = true;
           // flip.setEnabled(true);
        } else {
            mBoxPaint.setColor(COLOR_CHOICES[1]);

            mFacePositionPaint.setColor(COLOR_CHOICES[1]);

            isTakeImage = false;
           // flip.setEnabled(false);
        }
    }
}
