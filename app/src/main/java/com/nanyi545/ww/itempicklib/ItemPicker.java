package com.nanyi545.ww.itempicklib;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewConfigurationCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created by nanyi545 on 16-9-30.
 */
public class ItemPicker extends View {



    private float itemTextSize; // in sp
    private static final float DEFAULT_ITEM_TEXT_SIZE=16; // sp

    private float heightAdjustmentFactor;
    private static final float DEFAULT_HEIGHT_ADJUSTMENT_FACTOR=1.0f;

    private float focalPoint=0f;
    private static final float DEFAULT_FOCAL_POINT=0.5f;


    private int selectedItemIndex,startIndex;
    private static final int DEFAULT_SELECTED_ITEM_INDEX=0,DEFAULT_START_INDEX=0;

    private int itemCountsHalf;  // total display item count = itemCountsHalf*2+1;
    private static final int DEFAULT_ITEMCOUNT_HALF=3;


    private int selectionIndicator;
    private static final int INDICATOR_NONE=0,INDICATOR_RECT=1,INDICATOR_SINGLE_LINE=2,INDICATOR_DOUBLE_LINE=3,INDICATOR_ROUNDED_RECT=4;

    private int indicatorColor;
    private static final int DEFAULT_INDICATOR_COLOR=Color.argb(180,102,255,255);

    private int scrollMode;
    private static final int MODE_CYCLIC=1,MODE_ONCE=2;



    public static void syncFocalPoint(ItemPicker... pickers){
        int totalNumber=pickers.length;
        FocalHelper[] widths=new FocalHelper[totalNumber];
        int totalWidth=0;
        for (int ii=0;ii<totalNumber;ii++){
            widths[ii]=new FocalHelper(pickers[ii].getWidth(),totalWidth);
            totalWidth+=pickers[ii].getWidth();
        }
        int halfWidth=totalWidth/2;
//        Log.i("BBB","halfWidth:"+halfWidth+"   totalWidth:"+totalWidth);

        for (int ii=0;ii<totalNumber;ii++){
            float newFocal=(float)(halfWidth - widths[ii].start) / widths[ii].length;
//            Log.i("BBB","ii:"+ii+"   newFocal:"+newFocal+"  start:"+widths[ii].start+"    length:"+widths[ii].length);
            pickers[ii].resetFocalPoint( newFocal );
        }

    }

    public void resetFocalPoint(float focalPoint){
        this.focalPoint=focalPoint;
        invalidate();
    }

    /**
     * helper class to calculate focal point for a group of ItemPickers laid-out horizontally
     */
    private static class FocalHelper{
        private int start;
        private int length;
        public FocalHelper(int length, int start) {
            this.length = length;
            this.start = start;
        }
    }

    public ItemPicker(Context context) {
        super(context);
        initViewConfiguration();
        readAttrs(context, null, 0);
        init();
    }

    public ItemPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViewConfiguration();
        readAttrs(context, attrs, 0);
        init();
    }

    public ItemPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initViewConfiguration();
        readAttrs(context, attrs, defStyleAttr);
        init();
    }



    private Formatter formatter=new DateFormatter();  //  DefaultFormatter

    public void resetFormatter(Formatter formatter){
        this.formatter=formatter;
        measureText();
        topShaderPaint=null;    //  change formatter might change   itemTextRect.height   (  possible when change between   pure number <--> english/chinese text  )
        bottomShaderPaint=null; //  set shader to null will reset shader (in onMeasure) according to the new itemTextRect.height  ...
        requestLayout();  // to remeasure the view size
        invalidate();
    }

    public void resetFormatter(Formatter formatter,int[] newItemList) {
        itemList=newItemList;
        init();
        initDisplay();
        resetFormatter(formatter);
    }

    public void resetFormatter(Formatter formatter,int[] newItemList,int startIndex) {
        this.startIndex=startIndex;  // for both MODE_CYCLIC / MODE_ONCE

        itemList=newItemList;
        init();

        initDisplay();
        resetFormatter(formatter);
        lateInit.sendEmptyMessageDelayed(1,500);

    }




    public String getFormattedItem(int item){
        if (scrollMode==MODE_CYCLIC)
        return formatter.format(itemList[item]);
        else if (scrollMode==MODE_ONCE)
            return formatter.format(itemListForOnce[item]);
        return "wrong scroll mode!";
    }


    public int getSelectedItem(){
        if (scrollMode==MODE_CYCLIC)
            return itemList[selectedItemIndex];
        else return itemListForOnce[selectedItemIndex];   // MODE_ONCE
    }


    private TextPaint itemPaint;
    private Paint mLinePaint, topShaderPaint, bottomShaderPaint,selectionPaint;
    private Paint testPaint,testPaint1;

    float mDensity;
    float scaledDensity; //   sp/px --> sp * scaledDensity   = px
    int viewWidth,viewHeight,verticalSpacing, viewCenterY,itemAngle,offSetAngleMax;


    int[] itemList={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17};
    int[] itemListForOnce={};


    ItemHolder[] displayList;

    Rect itemTextRect;



//    getDimension() returns a floating point number which is the dimen value adjusted with current display metrics:
//    getDimensionPixelSize() returns an integer. It is the same as getDimension() rounded to an int with any non-zero dimension ensured to be at least one pixel in size.
    private void readAttrs(Context context, AttributeSet attrs, int defStyleAttr) {
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ItemPicker, defStyleAttr, 0);
        itemTextSize=a.getDimension(R.styleable.ItemPicker_textSize, DEFAULT_ITEM_TEXT_SIZE *scaledDensity );
        heightAdjustmentFactor=a.getFloat(R.styleable.ItemPicker_heightAdjustment, DEFAULT_HEIGHT_ADJUSTMENT_FACTOR );
        focalPoint=a.getFloat(R.styleable.ItemPicker_focalPoint, DEFAULT_FOCAL_POINT );
        startIndex= a.getInteger(R.styleable.ItemPicker_startIndex,DEFAULT_START_INDEX);
        itemCountsHalf= a.getInteger(R.styleable.ItemPicker_itemCountHalf,DEFAULT_ITEMCOUNT_HALF);
        selectionIndicator= a.getInteger(R.styleable.ItemPicker_highLightIndicator,INDICATOR_NONE);
        indicatorColor= a.getInteger(R.styleable.ItemPicker_highLightColor,DEFAULT_INDICATOR_COLOR);
        scrollMode= a.getInteger(R.styleable.ItemPicker_scrollMode,MODE_CYCLIC);
        a.recycle();
    }



    private void initViewConfiguration(){
        matrix = new Matrix();
        camera = new Camera();

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        touchSlope = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity() / VELOCITY_REDUCED_FACTOR;
        scaledDensity = getContext().getResources().getDisplayMetrics().scaledDensity;
        mDensity = getResources().getDisplayMetrics().density;

    }

    private void measureText(){
        int maxLengthIndex=0;
        for (int ii=0;ii<itemList.length;ii++){
            if ( formatter.format(itemList[ii]).length()  >  formatter.format(itemList[maxLengthIndex]).length()  )  maxLengthIndex=ii;
        }
        itemPaint.getTextBounds( formatter.format(itemList[maxLengthIndex]), 0,  formatter.format(itemList[maxLengthIndex]).length(), itemTextRect);
    }



    int paddingLeft,paddingRight,paddingTop,paddingBottom;

    private void init(){
        currentOffsetAngle=0;
        currentReducedOffsetAngle=0;
        paddingLeft=getPaddingLeft();
        paddingRight=getPaddingRight();
        paddingTop=getPaddingTop();
        paddingBottom=getPaddingBottom();

        displayList=new ItemHolder[itemCountsHalf*2+1]; //

        switch(scrollMode){
            case MODE_CYCLIC:
                selectedItemIndex=DEFAULT_SELECTED_ITEM_INDEX;
                oldValue=selectedItemIndex;
                break;

            case MODE_ONCE:
                int arrayLength=itemList.length+2*itemCountsHalf;

                itemListForOnce=new int[arrayLength];

                for (int ii=0;ii<itemCountsHalf;ii++){
                    itemListForOnce[ii]=-1;
                    itemListForOnce[arrayLength-1-ii]=-1;
                }
                for (int ii=0;ii<itemList.length;ii++){
                    itemListForOnce[ii+itemCountsHalf]=itemList[ii];
                }
                selectedItemIndex=DEFAULT_SELECTED_ITEM_INDEX;
                selectedItemIndex+=itemCountsHalf;
                oldValue=selectedItemIndex;

                StringBuilder sb=new StringBuilder();
                for (int ii=0;ii<itemListForOnce.length;ii++){
                    sb.append(""+itemListForOnce[ii]);
                }
                break;
        }

        itemPaint = new TextPaint();
        itemPaint.setTextSize(itemTextSize);
        itemPaint.setColor(Color.rgb(0,0,0));
        itemPaint.setFlags(TextPaint.ANTI_ALIAS_FLAG);
        itemPaint.setTextAlign(Paint.Align.CENTER);


        mLinePaint = new Paint();
        mLinePaint.setColor(Color.rgb(20,20,240));
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(1 * mDensity);

        testPaint= new Paint();
        testPaint.setColor(Color.rgb(240,90,20));
        testPaint1= new Paint();
        testPaint1.setColor(Color.rgb(20,90,240));

        selectionPaint=new Paint();
        selectionPaint.setColor(indicatorColor);
        selectionPaint.setStrokeWidth(3*mDensity);

        itemTextRect=new Rect();
        measureText();

        itemAngle=(60/itemCountsHalf)%2==1?60/itemCountsHalf+1:60/itemCountsHalf;  // to make sure itemAngle is an even integer
        offSetAngleMax=itemAngle*itemCountsHalf+itemAngle/2;

        flingScroller = new Scroller(getContext(), null);
        adjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));


        lateInit.sendEmptyMessageDelayed(1,500);
    }



    Handler lateInit=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (startIndex!=DEFAULT_START_INDEX) {
                adjustAngleStart = 0;
                adjustScroller.startScroll(0, 0, 0, -startIndex * itemAngle, 300);
                invalidate();
            }
        }
    };



    private void initDisplay(){

        switch (scrollMode){
            case MODE_CYCLIC:
                for (int ii=0;ii<displayList.length;ii++){
                    ItemHolder temp = new ItemHolder(selectedItemIndex - itemCountsHalf + ii, viewCenterY  +  (int)(Math.sin((ii-itemCountsHalf)*itemAngle*Math.PI/180)*viewHeight/2),(ii-itemCountsHalf)*itemAngle);
                    if (temp.index > itemList.length - 1) {
                        temp.index -= itemList.length;
                    } else if (temp.index < 0) {
                        temp.index += itemList.length;
                    }
                    displayList[ii] = temp;
                }

                break;

            case MODE_ONCE:
                for (int ii=0;ii<displayList.length;ii++){
                    ItemHolder temp = new ItemHolder(selectedItemIndex - itemCountsHalf + ii, viewCenterY  +  (int)(Math.sin((ii-itemCountsHalf)*itemAngle*Math.PI/180)*viewHeight/2),(ii-itemCountsHalf)*itemAngle);
                    if (temp.index > itemListForOnce.length - 1) {
                        temp.index -= itemListForOnce.length;
                    } else if (temp.index < 0) {
                        temp.index += itemListForOnce.length;
                    }
                    displayList[ii] = temp;
                }

                break;


        }
    }



    private int convert2OffsetAngle(int offsetY){
        int signNum=offsetY>0?1:-1;
        int ret=Math.abs(offsetY)/2>itemAngle/2?itemAngle/2*signNum:offsetY/2;
        return ret;
    }

    private int currentOffsetAngle,currentReducedOffsetAngle;


    private int indexRelative2Selection(int offset){
        switch (scrollMode){
            case  MODE_CYCLIC:
                if (offset>=0){
                    return (selectedItemIndex+offset)%itemList.length;
                } else  {
                    if ((selectedItemIndex+offset)==-1){
                        return itemList.length-1;
                    } else if((selectedItemIndex+offset)>-1){
                        return selectedItemIndex+offset;
                    } else {   //  -->  ((selectedItemIndex+offset)<-1)
                        return itemList.length+selectedItemIndex+offset;
                    }
                }


            case MODE_ONCE:

                int cycleLength=itemListForOnce.length-itemCountsHalf;
                if (offset>=0){
                    if (selectedItemIndex+offset>=cycleLength) return selectedItemIndex+offset-cycleLength+itemCountsHalf;
                    else return (selectedItemIndex+offset);
                } else  {
                    if ((selectedItemIndex+offset)==itemCountsHalf-1){
                        return cycleLength-1;
                    } else if((selectedItemIndex+offset)>itemCountsHalf-1){
                        return selectedItemIndex+offset;
                    } else {   //  -->  ((selectedItemIndex+offset)<-1)
                        return cycleLength+selectedItemIndex+offset;
                    }
                }


            default:
                return 0;


        }

    }

    private int indexRelative(int targetIndex,int offset){

        switch(scrollMode){
            case MODE_CYCLIC:
                if (offset>=0){
                    return (targetIndex+offset)%itemList.length;
                } else  {
                    if ((targetIndex+offset)==-1){
                        return itemList.length-1;
                    } else if((targetIndex+offset)>-1){
                        return targetIndex+offset;
                    } else {   //  -->  ((targetIndex+offset)<-1)
                        return itemList.length+targetIndex+offset;
                    }
                }

            case MODE_ONCE:
                if (offset>=0){
                    return (targetIndex+offset)%itemListForOnce.length;
                } else  {
                    if ((targetIndex+offset)==-1){
                        return itemListForOnce.length-1;
                    } else if((targetIndex+offset)>-1){
                        return targetIndex+offset;
                    } else {   //  -->  ((targetIndex+offset)<-1)
                        return itemListForOnce.length+targetIndex+offset;
                    }
                }



            default:
                return 0;


        }




    }


    private void checkSelection(int offsetAngle){
        currentReducedOffsetAngle+=offsetAngle;
        if (currentReducedOffsetAngle<-itemAngle/2){
            selectedItemIndex= indexRelative2Selection(1);
            currentReducedOffsetAngle+=itemAngle;
        }
        if (currentReducedOffsetAngle>itemAngle/2){
            selectedItemIndex= indexRelative2Selection(-1);
            currentReducedOffsetAngle-=itemAngle;
        }
    }




    private void updateDisplay(int offset,boolean angle) {


        int offsetAngle=0;
        if (!angle) {
            offsetAngle=convert2OffsetAngle(offset);
            currentOffsetAngle +=offsetAngle ;
            checkSelection(offsetAngle);
        } else {
            offsetAngle=offset;
            currentOffsetAngle += offsetAngle;
            checkSelection(offsetAngle);
        }

        StringBuilder sb=new StringBuilder();
        for (int i = 0; i < displayList.length; i++) {
            sb.append("i:"+i+"---");

            displayList[i].offsetAngle+=offsetAngle;

            if (displayList[i].offsetAngle>offSetAngleMax){  // scroll up
                displayList[i].offsetAngle-=offSetAngleMax*2;
                displayList[i].index=indexRelative(displayList[i].index,-2*itemCountsHalf-1);
            }
            if (displayList[i].offsetAngle<-offSetAngleMax){  // scroll down
                displayList[i].offsetAngle+=offSetAngleMax*2;
                displayList[i].index=indexRelative(displayList[i].index,2*itemCountsHalf+1);
            }

            sb.append("index:"+displayList[i].index+"    ");

            displayList[i].yPosition = viewCenterY+(int)(Math.sin((displayList[i].offsetAngle)*Math.PI/180)*viewHeight/2);

        }
//        Log.i("BBB",""+sb.toString());
        invalidate();
    }


    Camera camera;
    Matrix matrix;

    private boolean drawIndicatorLines=false;


    private void drawSelectionIndicator(Canvas canvas){

        int ovalWidth=itemTextRect.height()/2;
        int selectorHeightHalf= (int)(itemTextRect.height()/2*1.6f);

        switch(selectionIndicator) {
            case INDICATOR_RECT:
                Rect rectT=new Rect(paddingLeft-ovalWidth/2,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+viewWidth+ovalWidth/2,viewHeight/2+selectorHeightHalf+paddingTop);
                canvas.drawRect(rectT,selectionPaint);
                break;

            case INDICATOR_ROUNDED_RECT:
                RectF oval1=new RectF(paddingLeft/2,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+ovalWidth+paddingLeft/2,viewHeight/2+selectorHeightHalf+paddingTop);
                Rect rect1=new Rect(paddingLeft/2,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+ovalWidth/2+paddingLeft/2,viewHeight/2+selectorHeightHalf+paddingTop);
                RectF oval2=new RectF(paddingLeft/2+viewWidth-ovalWidth,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+viewWidth+paddingLeft/2,viewHeight/2+selectorHeightHalf+paddingTop);
                Rect rect2=new Rect(paddingLeft/2+viewWidth-ovalWidth/2,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+viewWidth+paddingLeft/2,viewHeight/2+selectorHeightHalf+paddingTop);

                Rect rectCenter=new Rect(paddingLeft+ovalWidth/2,viewHeight/2-selectorHeightHalf+paddingTop,paddingLeft+viewWidth-ovalWidth/2,viewHeight/2+selectorHeightHalf+paddingTop);
                canvas.save();
                canvas.clipRect(rect1);
                canvas.drawOval(oval1,selectionPaint);
                canvas.restore();

                canvas.save();
                canvas.clipRect(rect2);
                canvas.drawOval(oval2,selectionPaint);
                canvas.restore();

                canvas.drawRect(rectCenter,selectionPaint);
                break;
            case INDICATOR_SINGLE_LINE:
                canvas.drawLine(0,viewHeight/2+selectorHeightHalf+paddingTop,viewWidth+paddingLeft+paddingRight,viewHeight/2+selectorHeightHalf+paddingTop,selectionPaint);
                break;
            case INDICATOR_DOUBLE_LINE:
                canvas.drawLine(0,viewHeight/2-selectorHeightHalf+paddingTop,viewWidth+paddingLeft+paddingRight,viewHeight/2-selectorHeightHalf+paddingTop,selectionPaint);
                canvas.drawLine(0,viewHeight/2+selectorHeightHalf+paddingTop,viewWidth+paddingLeft+paddingRight,viewHeight/2+selectorHeightHalf+paddingTop,selectionPaint);
                break;
            case INDICATOR_NONE:break;
            default:break;
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(drawIndicatorLines) canvas.drawLine(paddingLeft,viewHeight/2+paddingTop,viewWidth+ paddingLeft, viewHeight/2+paddingTop,mLinePaint);  // center indicator line

        drawSelectionIndicator(canvas);

        for (int ii=0;ii<displayList.length;ii++){

            int indexUpperLimit=0;
            switch (scrollMode){
                case MODE_CYCLIC:
                    indexUpperLimit=itemList.length;
                    break;
                case MODE_ONCE:
                    indexUpperLimit=itemListForOnce.length;
                    break;
            }

            if (displayList[ii].index >= 0 && displayList[ii].index <= indexUpperLimit ) {

                if(drawIndicatorLines) canvas.drawLine(paddingLeft,displayList[ii].yPosition+paddingTop,viewWidth+ paddingLeft,displayList[ii].yPosition+paddingTop,mLinePaint);  // position inidicting line

                canvas.save();
                camera.save();
                camera.translate(0, 0, Math.abs(viewHeight/2-displayList[ii].yPosition));
                camera.rotate(-(displayList[ii].offsetAngle), 0, 0);  // minimum sdk 12...
                camera.getMatrix(matrix);
                matrix.preTranslate(- (viewWidth *focalPoint+paddingLeft), -(displayList[ii].yPosition +paddingTop));  // so that the rotation happens arround the point x,y   ,  + itemTextRect.height()/2
                matrix.postTranslate( viewWidth *focalPoint+ paddingLeft,displayList[ii].yPosition+paddingTop  );
                camera.restore();
                canvas.concat(matrix);

                switch (scrollMode){
                    case MODE_CYCLIC:
                        canvas.drawText(formatter.format(itemList[displayList[ii].index]), viewWidth / 2+paddingLeft, (float)(displayList[ii].yPosition + itemTextRect.height()/2 )+paddingTop , itemPaint);  //
                        break;
                    case MODE_ONCE:
//                        Log.i("BBB","ii:"+ii+"    displayList[ii].index:"+displayList[ii].index+"     displayList[ii].yPosition:"+displayList[ii].yPosition);
                        canvas.drawText(formatter.format(itemListForOnce[displayList[ii].index]), viewWidth / 2+paddingLeft, (float)(displayList[ii].yPosition + itemTextRect.height()/2 )+paddingTop , itemPaint);  //
                        break;

                }

                canvas.restore();
            }
        }


        initShaders();
        canvas.drawRect(0, paddingTop, viewWidth+paddingLeft+paddingRight, viewHeight / 2-itemTextRect.height()/2+paddingTop, topShaderPaint);  // testPaint   topShaderPaint
        canvas.drawRect(0, viewHeight / 2+itemTextRect.height()/2 +paddingTop, viewWidth+paddingLeft+paddingRight, viewHeight+paddingTop, bottomShaderPaint);   //  bottomShaderPaint

    }



    int oldValue;




    private void adjustYPosition() {

        switch (scrollMode){
            case MODE_CYCLIC:
                if (adjustScroller.isFinished()  && !scrolling ) {

                    int selectedIndexInDisplayList=0;
                    for (int ii=0;ii<displayList.length;ii++) {
                        if (displayList[ii].index==selectedItemIndex){
                            selectedIndexInDisplayList=ii;
                            break;
                        }
                    }

                    int dyAngle=displayList[selectedIndexInDisplayList].offsetAngle ;

                    if (dyAngle != 0) {
                        adjustAngleStart=0;
                        adjustScroller.startScroll(0, 0, 0, -dyAngle, 300);
                        invalidate();
                    } else {

                        if (oldValue!=selectedItemIndex) {
                            if (onSelectionChangedListener != null)
                                onSelectionChangedListener.onSelectionChanged(this, oldValue, selectedItemIndex);
                            oldValue = selectedItemIndex;
                        }

                    }
                }

                break;
            case MODE_ONCE:


                if (adjustScroller.isFinished()  && !scrolling ) {

                    int selectedIndexInDisplayList=0;
                    for (int ii=0;ii<displayList.length;ii++) {
                        if (displayList[ii].index==selectedItemIndex){
                            selectedIndexInDisplayList=ii;
                            break;
                        }
                    }


                    if (currentOffsetAngle>= 0){
                        if (currentOffsetAngle==0){

                            if (oldValue!=selectedItemIndex) {
                                if (onSelectionChangedListener != null)
                                    onSelectionChangedListener.onSelectionChanged(this, oldValue, selectedItemIndex);
                                oldValue = selectedItemIndex;
                            }

                            return;
                        }
                        adjustAngleStart=0;
                        adjustScroller.startScroll(0, 0, 0, -currentOffsetAngle, 300);
                        invalidate();
                        return;
                    }

                    if (currentOffsetAngle<= (-(itemList.length-1) * itemAngle)){
                        if (currentOffsetAngle==(-(itemList.length-1) * itemAngle)) {

                            if (oldValue!=selectedItemIndex) {
                                if (onSelectionChangedListener != null)
                                    onSelectionChangedListener.onSelectionChanged(this, oldValue, selectedItemIndex);
                                oldValue = selectedItemIndex;
                            }

                            return;
                        }

                        adjustAngleStart=0;
                        adjustScroller.startScroll(0, 0, 0,  (-(itemList.length-1) * itemAngle)-currentOffsetAngle, 300);
                        invalidate();
                        return;
                    }



                    int dyAngle=displayList[selectedIndexInDisplayList].offsetAngle ;
                    Log.i("BBB","adjustYPosition...  currentOffsetAngle:"+currentOffsetAngle+"  dyAngle:"+dyAngle+"-----------");
                    if (dyAngle != 0) {
//                        Log.i("BBB","currentOffsetAngle:"+currentOffsetAngle+"  dyAngle:"+dyAngle);
                        adjustAngleStart=0;
                        adjustScroller.startScroll(0, 0, 0, -dyAngle, 300);
                        invalidate();
                    } else {
//                        Log.i("BBB","currentOffsetAngle:"+currentOffsetAngle+"  dyAngle:"+dyAngle);
                        if (oldValue!=selectedItemIndex) {
                            if (onSelectionChangedListener != null)
                                onSelectionChangedListener.onSelectionChanged(this, oldValue, selectedItemIndex);
                            oldValue = selectedItemIndex;
                        }

                    }
                }



                break;


        }
    }


    int adjustAngleStart,currentAdjustAngle,adjustAngleOffset;

    @Override
    public void computeScroll() {

//        Log.i("BBB","computeScroll...");
        Scroller scroller = flingScroller;
        if (scroller.isFinished()) {
            adjustYPosition();
            scroller=adjustScroller;
            if (scroller.isFinished()) {
                return;
            }
        }
        if (scrollMode==MODE_ONCE){
            if ((currentOffsetAngle< (-(itemList.length-1) * itemAngle))|| (currentOffsetAngle>0) ){
//                Log.i("BBB","currentOffsetAngle:"+currentOffsetAngle);
                flingScroller.forceFinished(true);
            }
        }

        scroller.computeScrollOffset();

        currentAdjustAngle=scroller.getCurrY();
        adjustAngleOffset=currentAdjustAngle-adjustAngleStart;

        updateDisplay(adjustAngleOffset,true);

        adjustAngleStart=currentAdjustAngle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        //Get the width measurement
        int widthSize = View.resolveSize(getDesiredWidth(), widthMeasureSpec);

        //Get the height measurement
        int heightSize = View.resolveSize(getDesiredHeight(), heightMeasureSpec);

        //MUST call this to store the measurements
        setMeasuredDimension(widthSize, heightSize);

        viewWidth=widthSize-paddingRight-paddingLeft;
        viewHeight=heightSize-paddingTop-paddingBottom;
        verticalSpacing=55;
        viewCenterY= viewHeight / 2;
        initDisplay();


        initShaders();

    }

    private void initShaders() {
        if (topShaderPaint ==null && bottomShaderPaint ==null ){
            topShaderPaint = new Paint();
            bottomShaderPaint = new Paint();

            int bgColor= Color.rgb(255,255,255);

            try {
                bgColor=((ColorDrawable)getBackground()).getColor();
            } catch (Exception e) {
                e.printStackTrace();
            }


            Shader topShader = new LinearGradient(paddingLeft, paddingTop, paddingLeft, viewHeight/2-itemTextRect.height()/2+paddingTop, new int[] {            //bgColor & 0xdfFFFFFF
                    bgColor & 0xefFFFFFF,
                    bgColor & 0xcfFFFFFF,
                    bgColor & 0xafFFFFFF,
                    bgColor & 0x00FFFFFF },
                    null, Shader.TileMode.CLAMP);
            //下遮罩
            Shader bottomShader = new LinearGradient(paddingLeft, viewHeight/2+itemTextRect.height()/2+paddingTop, paddingLeft, viewHeight+paddingTop, new int[] {
                    bgColor & 0x00FFFFFF,
                    bgColor & 0xafFFFFFF,
                    bgColor & 0xcfFFFFFF,
                    bgColor & 0xefFFFFFF},          // 00 CF DF
                    null, Shader.TileMode.CLAMP);
            topShaderPaint.setShader(topShader);
            bottomShaderPaint.setShader(bottomShader);

        }
    }

    private int getDesiredHeight() {
        return (int) (itemTextRect.height()*(2*itemCountsHalf+1)*heightAdjustmentFactor) +paddingTop+paddingBottom;
    }

    private int getDesiredWidth() {
        return itemTextRect.width()+paddingLeft+paddingRight;
    }


    private int maxFlingVelocity,minFlingVelocity;
    private static final int VELOCITY_REDUCED_FACTOR=8;
    private VelocityTracker mVelocityTracker;
    private Scroller adjustScroller,flingScroller;

    private int currentTouchAction = MotionEvent.ACTION_CANCEL;
    private int touchStartY,touchCurrentY,touchOffsetY,touchSlope;
    private boolean scrolling =false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }

        mVelocityTracker.addMovement(event);

        int action = event.getActionMasked();
        currentTouchAction = action;

        switch (currentTouchAction){
            case MotionEvent.ACTION_DOWN:
//                Log.i("BBB","ACTION_DOWN...");
                scrolling =true;
                touchStartY= (int) event.getY();
                if (!adjustScroller.isFinished()||!flingScroller.isFinished()){
                    adjustScroller.forceFinished(true);
                    flingScroller.forceFinished(true);
//                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);// add listener ...
                }
                break;
            case MotionEvent.ACTION_MOVE:
//                Log.i("BBB","ACTION_MOVE...");
                touchCurrentY= (int) event.getY();
                touchOffsetY=touchCurrentY-touchStartY;

//                Log.i("CCC","touchOffsetY:"+Math.abs(touchOffsetY)+"    touchSlope:"+touchSlope);
                if (!scrolling && Math.abs(touchOffsetY) < touchSlope && Math.abs(touchOffsetY) > 3 * touchSlope) {
                    return false;
                } else {
                    scrolling =true;
                    if (touchOffsetY > touchSlope) {
                        touchOffsetY -= touchSlope;
                    } else if (touchOffsetY < -touchSlope) {
                        touchOffsetY += touchSlope;
                    }
                }

                if (scrollMode==MODE_ONCE){  //  touchOffsetY +  --> finger scroll down       touchOffsetY -  --> finger scroll up
                    if ( ((selectedItemIndex+itemCountsHalf)>=itemListForOnce.length-1) &&  touchOffsetY<0 ){
                        touchOffsetY=0;
                        Log.i("BBB","UP--------REACHED");
                    }
                    if ( ((selectedItemIndex-itemCountsHalf)<=0) &&  touchOffsetY>0 ){
                        touchOffsetY=0;
                        Log.i("BBB","DOWN--------REACHED");
                    }
                }
                touchStartY=touchCurrentY;

                updateDisplay(touchOffsetY,false);

                break;

            case MotionEvent.ACTION_UP:
//                Log.i("BBB","ACTION_UP...");
                scrolling = false;


                mVelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                int initialVelocity = (int) mVelocityTracker.getYVelocity();

//                Log.i("CCC","FLING???  ....     initialVelocity:"+initialVelocity+"    minFlingVelocity:"+minFlingVelocity);
                if (Math.abs(initialVelocity) > minFlingVelocity  ) {//如果快速滑动

                    switch(scrollMode){
                        case MODE_CYCLIC:
                            fling(initialVelocity);
                            break;
                        case MODE_ONCE:
                            fling(initialVelocity);
                            break;

                    }

//                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                } else {
                    adjustYPosition();
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;

                break;

        }


        return true;

    }




    private void fling(int startYVelocity) {
//        Log.i("BBB","fling...");
        int maxY = 0;
        switch(scrollMode){
            case MODE_CYCLIC:
                maxY=120;
                break;
            case MODE_ONCE:
                maxY=120;
                break;
        }
        if (startYVelocity > 0) {//向下滑动
            adjustAngleStart = 0;
            flingScroller.fling(0, 0, 0, startYVelocity, 0, 0, 0, maxY);
        } else if (startYVelocity < 0) {//向上滑动
            adjustAngleStart = maxY;
            flingScroller.fling(0, maxY, 0, startYVelocity, 0, 0, 0, maxY);
        }
        invalidate();
    }






    class ItemHolder{
        private int index;
        private int yPosition;
        private int offsetAngle;
        private ItemHolder(int index, int yPosition,int angle) {
            this.index = index;
            this.yPosition = yPosition;
            this.offsetAngle = angle;
        }
    }




    public static int[] generateArray(int size){
        int[] ret=new int[size];
        for (int ii=0;ii<size;ii++)ret[ii]=ii;
        return ret;
    }

    /**
     * monitor selection changes
     */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(ItemPicker picker, int oldValue, int newValue);
    }
    private OnSelectionChangedListener onSelectionChangedListener;

    public void setOnSelectionChangedListener(OnSelectionChangedListener onSelectionChangedListener){
        this.onSelectionChangedListener=onSelectionChangedListener;
    }



    /**
     * Formatters are responsible for converting integers to display
     */

    public interface Formatter {
        String format(int item);
    }


    private static class DefaultFormatter implements Formatter{
        @Override
        public String format(int item) {
            if (item<0) return "";
            return ""+item;
        }
    }

    public static class TestFormatter implements Formatter{
        @Override
        public String format(int item) {
            if (item<0) return "---";
            return ""+item;
        }
    }


    public static class DateFormatter implements Formatter{
        @Override
        public String format(int item) {
            if (item<0) return "";
            GregorianCalendar c=new GregorianCalendar();
            c.add(Calendar.DAY_OF_YEAR,item);
            SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
            String formatted = format1.format(c.getTime());
            String suffix=(item==0)?"":"";
            return formatted+suffix;
        }
    }

    /**
     *   0--> 0:00      1--> 0:30     2--> 1:00  ...  47--> 23:30
     */
    public static class HourFormatterBy30 implements Formatter{
        @Override
        public String format(int item) {
            if (item<0) return "";
            int hour=item/2;
            int minute=item%2;
            String hourStr=(hour>9?""+hour:" "+hour);
            String minuteStr=(minute==1?"30":"00");
            return ""+hourStr+":"+minuteStr;
        }
    }



}
