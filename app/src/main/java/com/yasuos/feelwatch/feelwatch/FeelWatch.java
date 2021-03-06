/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yasuos.feelwatch.feelwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class FeelWatch extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<FeelWatch.Engine> mWeakReference;

        public EngineHandler(FeelWatch.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            FeelWatch.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        Paint mTextPaint;

        //int mNumRes[10];


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(FeelWatch.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = FeelWatch.this.getResources();

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
/*
            mNumRes[0] = R.drawable.white_c0;
            mNumRes[1] = R.drawable.white_c1;
            mNumRes[2] = R.drawable.white_c2;
            mNumRes[3] = R.drawable.white_c3;
            mNumRes[4] = R.drawable.white_c4;
            mNumRes[5] = R.drawable.white_c5;
            mNumRes[6] = R.drawable.white_c6;
            mNumRes[7] = R.drawable.white_c7;
            mNumRes[8] = R.drawable.white_c8;
            mNumRes[9] = R.drawable.white_c9;
*/
            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = FeelWatch.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = FeelWatch.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            /*
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
           */
            int msRes[] = {
                    R.drawable.white_c0, R.drawable.white_c1, R.drawable.white_c2,
                    R.drawable.white_c3, R.drawable.white_c4, R.drawable.white_c5,
                    R.drawable.white_c6, R.drawable.white_c7, R.drawable.white_c8,
                    R.drawable.white_c9
                };
            int c = ( bounds.width() / 2 ) - 40;

            int m1, m2;

            m1 = c + 10;
            m2 = c + 53;

            // 時
            Bitmap hour1 = BitmapFactory.decodeResource(getResources(), msRes[mTime.hour / 10] );
            Bitmap hour2 = BitmapFactory.decodeResource(getResources(), msRes[mTime.hour % 10] );

            canvas.drawBitmap( hour1, m1, mYOffset - ( hour1.getHeight() + 10), (Paint)null);
            canvas.drawBitmap( hour2, m2, mYOffset - ( hour2.getHeight() + 10), (Paint)null);

            // 分
            Bitmap minu1 = BitmapFactory.decodeResource(getResources(), msRes[mTime.minute / 10] );
            Bitmap minu2 = BitmapFactory.decodeResource(getResources(), msRes[mTime.minute % 10] );

            canvas.drawBitmap( minu1, m1, mYOffset, (Paint)null);
            canvas.drawBitmap( minu2, m2, mYOffset, (Paint)null);

            // 曜日
            int wDayRes[] = {
                    R.drawable.white_w_sun, R.drawable.white_w_mon, R.drawable.white_w_thu,
                    R.drawable.white_w_wed, R.drawable.white_w_thu, R.drawable.white_w_fri,
                    R.drawable.white_w_sat
                };
            Bitmap wday = BitmapFactory.decodeResource( getResources(), wDayRes[mTime.weekDay] );

            canvas.drawBitmap( wday, c - (wday.getWidth()+10), mYOffset - ( wday.getHeight() + 10 ), (Paint)null);

            // 午前・午後
            Bitmap ampm = BitmapFactory.decodeResource( getResources(), R.drawable.white_f_am );
            canvas.drawBitmap( ampm,
                    ( m2 + minu2.getWidth() + 15 ),
                    ( mYOffset - ( ampm.getHeight() + 10 )),
                    (Paint)null);

            // 秒針
            int ssRes[] = {
                    R.drawable.white_s0, R.drawable.white_s1, R.drawable.white_s2,
                    R.drawable.white_s3, R.drawable.white_s4, R.drawable.white_s5,
                    R.drawable.white_s6, R.drawable.white_s7, R.drawable.white_s8,
                    R.drawable.white_s9
                };
            int ss[] = {mTime.second / 10, mTime.second % 10};

            Bitmap ss1 = BitmapFactory.decodeResource(getResources(), ssRes[ss[0]] );
            Bitmap ss2 = BitmapFactory.decodeResource(getResources(), ssRes[ss[1]] );


            canvas.drawBitmap( ss1,
                    ( m2 + minu2.getWidth() + 15 ),
                    ( mYOffset + ( minu2.getHeight() - ss1.getHeight()) ),
                    (Paint)null);
            canvas.drawBitmap( ss2,
                    ( m2 + minu2.getWidth() + ss1.getWidth() + 20 ),
                    ( mYOffset + ( minu2.getHeight() - ss1.getHeight()) ),
                    (Paint)null);


            // 日付
            Bitmap mm1, mm2, dd1, dd2;
            int smallNumRes[] = {
                    R.drawable.white_s0, R.drawable.white_s1, R.drawable.white_s2,
                    R.drawable.white_s3, R.drawable.white_s4, R.drawable.white_s5,
                    R.drawable.white_s6, R.drawable.white_s7, R.drawable.white_s8,
                    R.drawable.white_s9
                };
            int mm = mTime.month + 1;

            if(( mm / 10 ) >= 1 ) {
                mm1 = BitmapFactory.decodeResource(getResources(), smallNumRes[1] );
            } else {
                mm1 = null;
            }
            mm2 = BitmapFactory.decodeResource(getResources(), smallNumRes[mm % 10] );

            if((mTime.monthDay / 10 ) >= 1 ) {
                dd1 = BitmapFactory.decodeResource(getResources(), smallNumRes[mTime.monthDay/10] );
            } else {
                dd1 = null;
            }
            dd2 = BitmapFactory.decodeResource(getResources(), smallNumRes[mTime.monthDay % 10] );

            int xx, yy;

            yy = ((int)mYOffset + minu1.getHeight()) - dd2.getHeight();
            xx = c - ( dd2.getWidth() + 15 );
            if( dd2 != null ) {
                canvas.drawBitmap(dd2, xx, yy, (Paint) null);
            }

            xx = xx - ( dd2.getWidth() + 5 );
            if( dd1 != null ) {
                canvas.drawBitmap(dd1, xx, yy, (Paint) null);
            }

            xx = xx - ( mm2.getWidth() + 15 );
            if( mm2 != null ) {
                canvas.drawBitmap(mm2, xx, yy, (Paint) null);
            }

            xx = xx - ( mm2.getWidth() + 5 );
            if( mm1 != null ) {
                canvas.drawBitmap(mm1, xx, yy, (Paint) null);
            }
/*
            String text = String.format( "%d", 1 );
            canvas.drawText(text, h1, mYOffset, mTextPaint);
            text = String.format( "%d", 2 );
            canvas.drawText(text, h2, mYOffset, mTextPaint);

            text = String.format( "%d", 3 );
            canvas.drawText(text, m1, mYOffset, mTextPaint);
            text = String.format( "%d", 4 );
            canvas.drawText(text, m2, mYOffset, mTextPaint);
*/

            return;

        /*
            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);
        */
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            FeelWatch.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            FeelWatch.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
