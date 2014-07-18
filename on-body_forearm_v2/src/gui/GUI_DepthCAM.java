package gui;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.opencv.core.Mat;

import gab.opencv.Contour;
import gab.opencv.OpenCV;
import SimpleOpenNI.SimpleOpenNI;
import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.core.PVector;
import processing.opengl.PGraphicsOpenGL;

@SuppressWarnings("serial")
public class GUI_DepthCAM extends PApplet{
	
	private Main parent;
	private int width,height;
	
	private SimpleOpenNI context;
	private int[] depth;
	
	// DEBUGGING VARIABLE
	private boolean canvasCalibration = true;
	
	// TRACKING / SMOOTHING VARIABLES
	private boolean nearTouch = false;

	private Hand canvasHand   = new Hand();
	private Hand modelingHand   = new Hand();
	private Forearm canvasArm = new Forearm();

	
	// OPENCV
	private OpenCV opencv;
	private PImage middle;
	private PVector[] depthMap_middle; 			// saves the realworld depth map of the middle segment
	private PImage top;
	private PImage leftHand;
	private PImage original;
	private boolean mode2D = false;
	
	// GUI variables
	private PeasyCam cam;
	private PFont font;
	private int bounds = 1000;
	private boolean freeze = false;
	
	// MESH variables
	private float u = 4;
//	private float v = 8;
	
	// GESTURE VARIABLES
	private boolean scaling = false;
	private PVector[] scalingRef = new PVector[4]; // persistent reference to the true corners of the forearm
	
	
	public GUI_DepthCAM(Main parent){
		this.parent = parent;
		this.width  = 640;
		this.height = 480;
	}
	
	public void setup(){
		context = new SimpleOpenNI(this,SimpleOpenNI.RUN_MODE_MULTI_THREADED);
		
		size(this.width, this.height, PGraphicsOpenGL.OPENGL);
		font = loadFont("CourierNewPSMT-18.vlw");
		
		background(200,0,0);
		
		context.setMirror(false);
		
		// enable depthMap generation 
		context.enableDepth();
		
		opencv = new OpenCV(this, width, height);
//		opencv.useColor();
		
		top = createImage(context.depthWidth(),context.depthHeight(),ARGB);
		middle = createImage(width,height,ARGB);
		
		
		// Initialize camera
        cam = new PeasyCam(this, bounds);
        cam.setCenterDragHandler(cam.getPanDragHandler());
        cam.setWheelHandler(cam.getZoomWheelHandler());
        cam.setWheelScale(.5f);
        if (mode2D)
        	cam.setRightDragHandler(null); // just allow 2D zooming
        else
        	 cam.setRightDragHandler(cam.getRotateDragHandler());
        cam.setLeftDragHandler(null);
        cam.setMinimumDistance(0);
        cam.setMaximumDistance(bounds*100);
      
        
        modelingHand.isFlipped = true;
	}
	

	public void draw(){
		if (modelingHand.isTouching)
			background(255,0,0);
		else
			background(0);
		
		// update the depth cam if not frozen
		if (!freeze){
			context.update();
			original  = context.depthImage();
			depth = context.depthMap();		
		}		
		
		// should be adapted based on detected wrist
		int near = 0;
		int mid = 720;
		int far = 770;
		
		// break depth image into different stratas
		PImage combined = segment(near,mid,far);
		
//		if (!canvasCalibration)
			smartSegment();
		
//		if (!nearTouch){
//			image(original,0,0);	// showing live depth map
			image(combined,0,0);	// showing middle and near segments over live depth map
			image(middle,0,0);
			image(top,0,0);
//		}
		
		
	
		// FIND CANVAS ARM
		opencv.loadImage(middle);
		opencv.blur(10);
		opencv.threshold(128);	
		opencv.erode();
//		opencv.erode();
		opencv.dilate();
		opencv.dilate();
//		image(opencv.getSnapshot(),0,0);
		
		ArrayList<Contour> canvas = opencv.findContours();
		Rectangle bb = new Rectangle();
		pushStyle();
		strokeWeight(1);
		stroke(255,0,0);
		noFill();
		Contour arm = createCanvasArm(canvas);		
		
		if (arm != null){
			bb = arm.getConvexHull().getBoundingBox();
			stroke(61,153,113);
			rect(bb.x,bb.y, bb.width, bb.height);			
		}
		


		popStyle();
		
		
		
			
			
		
//		if (!canvasCalibration){
	
			// FIND MODELING HAND
			opencv.loadImage(top);
			opencv.threshold(0);
			opencv.blur(15);
			opencv.threshold(128);		
			opencv.erode();
			opencv.erode();
			opencv.dilate();
			opencv.dilate();
			opencv.dilate();
			leftHand = opencv.getSnapshot();
//			image(leftHand,0,0);
								
			ArrayList<Contour> contours = opencv.findContours();
			
			Contour hand = createModelingHand(contours);
			
			if (canvasCalibration){
			
			// if we are not pinching, and our elbow points are in original position
			if (!scaling && canvasArm.getCorners()[3].y < 5){
				for (int i=0; i<4; i++)
					scalingRef[i] = canvasArm.getCorners()[i].get();
			}
			
			// check if the canvas hand is open or closed to freeze the 3D world
			if ((modelingHand.isClosed() || canvasHand.isClosed()) && 
				!modelingHand.isPinching && !parent.get3D().getFreeze())
				parent.get3D().setFreeze(true);
			
			else if (!modelingHand.isClosed() 
					 && (canvasHand.isDetected && !canvasHand.isClosed()) 
					 && parent.get3D().getFreeze())
				parent.get3D().setFreeze(false);
					
	
			// Detect Touch (NOT WORKING)
			if (hand != null && !bb.isEmpty()){
				
				nearTouch = hand.getBoundingBox().intersects(bb);
				canvasCalibration = !nearTouch;
	
				if (nearTouch && !modelingHand.isPinching && !scaling && modelingHand.getFingers()[0] != null){
					image(middle,0,0);
	
					int x = (int) modelingHand.getFingers()[0].x;	// just pick any point for now
					int y = (int) modelingHand.getFingers()[0].y;
					PVector rwp = context.depthMapRealWorld()[x+y*context.depthWidth()];
					parent.get3D().setPointer(rwp);
				}
				else
					parent.get3D().setPointer(new PVector());
				
			}
			
		}

		
		drawAxes();

		cam.beginHUD();
		pushStyle();
		fill(200,0,200);
		textFont(font, 12);
		text(frameRate, 10, 22);
		popStyle();
		cam.endHUD();
	}
	
	
	private void smartSegment(){
		
//		PImage segmented = createImage(context.depthWidth(),context.depthHeight(),ARGB);
//		segmented.loadPixels();
		
		// reset PImages
		top = createImage(context.depthWidth(),context.depthHeight(),ARGB);
		top.loadPixels();
		
		// if we're close, don't update the middle layer
//		if (!nearTouch){
//			middle = createImage(context.depthWidth(),context.depthHeight(),ARGB);
//			middle.loadPixels();
//			
//			depthMap_middle = context.depthMapRealWorld();
//		}
			
		
		for (int i=0; i<depth.length; i++){
					
			int d = depth[i];
			
			// change 1D array into 2D array
			int y =  i / context.depthWidth();
			int x =  i % context.depthWidth();
			
			// if it's significantly above our highest point ... put to top
			if (d < canvasArm.axisB.z - 10 && d > 400 && y < 5*height/8 && x < 5*width/8){
//				segmented.set(x, y, color(255,0,0));
//				segmented.updatePixels();
				top.set(x, y, color(255,0,0));
				top.updatePixels();
			}
			// if it's in between the desk and the top of forearm
			else if (d >= canvasArm.axisB.z - 10 && d < 770){ // 770 is our hard coded table top depth
				// check if x,y coordinates are near the arm
//				if (canvasArm.bb.contains(x,y)){
					
					// find out where the z-value should be based on our YZ slope
					float run = canvasArm.axisB.y - y;
					float m = canvasArm.getYZSlope();
					
					float d2 = canvasArm.axisB.z - 10 - (m*run);
					
					
					// if d is smaller, we are on top
					if (d < d2){
//						println("axisA.z :"+canvasArm.axisA.z+ ", axisB.z: "+canvasArm.axisB.z+", slopeYZ: "+m);
//						println("estimated depth of arm at x,y: "+d2);
//						println("depth of modeling hand at x,y: "+d);
//						segmented.set(x, y, color(255,0,0));
//						segmented.updatePixels();
						top.set(x, y, color(255,0,0));
						top.updatePixels();
					}
					// otherwise, it's the forearm
//					else{
//						segmented.set(x, y, color(0,255,0));
//						segmented.updatePixels();
////						middle.set(x, y, color(0,255,0));
////						middle.updatePixels();
//					}
					
//				}
			}
			
			
		}
//		return segmented;	
	}
	
	
	/**
	 * Separate the depth image into 3 different layers.  <br/><br/>
	 * The foreground will have the modeling hand (save in the <i>top</i> PImage); <br/>
	 * The middle ground will have the canvas arm (save in the <i>middle</i> PImage);<br/>
	 * The background will be the desktop (leave transparent). 
	 *
	 * @param near		- threshold for the modeling hand
	 * @param middle	- threshold for the canvas arm
	 * @param far		- threshold for the background
	 * @return PImage with the three layers combined
	 */
	private PImage segment(int near, int mid, int far){
		PImage segmented = createImage(context.depthWidth(),context.depthHeight(),ARGB);
		segmented.loadPixels();
		
		// reset PImages
//		top = createImage(context.depthWidth(),context.depthHeight(),ARGB);
//		top.loadPixels();
				
		
		// if we're close, don't update the middle layer
		if (!nearTouch){
			middle = createImage(context.depthWidth(),context.depthHeight(),ARGB);
			middle.loadPixels();
			
			depthMap_middle = context.depthMapRealWorld();
		}
		
		for (int i=0; i<depth.length; i++){
			
			int d = depth[i];
			
			// change 1D array into 2D array
			int y =  i / context.depthWidth();
			int x =  i % context.depthWidth();
			
			if (d > near && d < mid){								
//				segmented.set(x, y, color(255));
//				segmented.updatePixels();
//				top.set(x, y, color(255));
//				top.updatePixels();
			}
			else if (d > mid && d < far && !nearTouch){				
				segmented.set(x, y, color(0,255,0));
				segmented.updatePixels();
				middle.set(x, y, color(0,255,0));
				middle.updatePixels();
			}
		}
		
		return segmented;
	}

	private Contour createModelingHand(ArrayList<Contour> contours) {
		Contour hand = null;
		
		pushStyle();
		
		for (Contour c : contours){
			if (c.area() > 10000){
	
				hand = c.getPolygonApproximation();
				
				noFill();
				
				// draw original contour
//				stroke (100,100,100);
//				strokeWeight(1);
//				c.draw();
	
				// draw the approx polygon
				stroke(210,209,74);
				strokeWeight(2);
				hand.draw();
	
				// draw the convex hull
				stroke(102,51,76);
				hand.getConvexHull().draw();
	
			}
		}
		
		
		if (hand != null){
			
			modelingHand.isDetected = true;
			
			
			// define a smaller bb to avoid processing the forearm
			Rectangle handRegion = hand.getConvexHull().getBoundingBox();			
			
			handRegion.width =  150;
			handRegion.x += hand.getConvexHull().getBoundingBox().width - handRegion.width + 15;
			handRegion.height = 180;
			handRegion.y += hand.getConvexHull().getBoundingBox().height-handRegion.height + 15;
			
			fill(154,20,204,50);
			rect(handRegion.x,handRegion.y,handRegion.width,handRegion.height);
			
				
			/*
			 * Check if the hand is pinching
			 */
			opencv.loadImage(leftHand);
			ArrayList<Contour> zones = opencv.findContours();
			pushStyle();
			strokeWeight(6);
			for (int i=0; i<zones.size(); i++){
				Contour c = zones.get(i);
				PVector p = c.getPoints().get(c.getPoints().size()/2);
				if (!handRegion.contains(p.x,p.y)){
					zones.remove(i);
					i--;
				}
			}
			popStyle();
			
			if (zones.size() == 2)	
				modelingHand.isPinching = true;
			else
				modelingHand.isPinching = false;
			
			modelingHand.getPinchPos3D(zones);
			
			
			
			
			/*
			 * Detect / ID the finger tips (only index & thumb is necessary for now) 
			 */
			ArrayList<PVector> tips = modelingHand.detectTips(hand, handRegion);			
		
			// NOT PROPERLY CLASSIFYING ALL FINGERS ... JUST MOST IMPORTANT FOR GESTURES 
			ArrayList<PVector> handPts = new ArrayList<PVector>();
			for (int i=0; i<5; i++)
				handPts.add(new PVector());		
			
			fill(255);
			if (tips.size() == 1){
//				println("tips.get(0).x: "+tips.get(0).x+", handRegion.x + (handRegion.width*.9f): "+(handRegion.x + (handRegion.width*.85f)));
				if (tips.get(0).x > handRegion.x + (handRegion.width*.85f)){
					handPts.set(3, tips.get(0));
				}
				else{
					handPts.set(4, tips.get(0));
				}
			}
			
			else if (tips.size() == 2){			 
				if (tips.get(1).x > handRegion.x + (handRegion.width*.5f) && tips.get(0).x > handRegion.x + (handRegion.width*.5f)){
					handPts.set(3, tips.get(0));
					handPts.set(4, tips.get(1));
				}									
			}
			
			else if (tips.size() == 4){
				handPts.set(0, tips.get(0));
				handPts.set(1, tips.get(1));
				handPts.set(2, tips.get(2));
				handPts.set(3, tips.get(3));
			}
			
			else if (tips.size() == 5){		
				handPts.set(0, tips.get(0));
				handPts.set(1, tips.get(1));
				handPts.set(2, tips.get(2));
				handPts.set(3, tips.get(3));
				handPts.set(4, tips.get(4));
			}
			
	
			modelingHand.addFingers(handPts);
			
			PVector[] pts = modelingHand.getFingers();
			
			pushStyle();
			rectMode(CENTER);
			noFill();
			strokeWeight(2);
			stroke(255,0,127,150);
			
			for (int i=0; i<pts.length; i++){
				
				if (pts[i] != null){				
					rect(pts[i].x, pts[i].y,15,15);
					
					String finger;
					if (i==0)
						finger = "PINKY";
					else if (i==1)
						finger = "RING";
					else if (i==2)
						finger = "MIDDLE";
					else if (i==3)
						finger = "INDEX";
					else
						finger = "THUMB";
					
					fill(255);
					int xOffest = finger.length()*2;
					text(finger, pts[i].x + xOffest,pts[i].y+5);
					noFill();
				}
				
			}
			
			
			// PINCHING GESTURE
			if (scaling && nearTouch){
				
				int minThickness = 15;
				
				PVector a,b,c,d;

				if (canvasArm.getXYSlope() < 0){						
					a = scalingRef[0].get();
					b = scalingRef[2].get();
					c = scalingRef[1].get();
					d = scalingRef[3].get();
				}
				else{
					a = scalingRef[0].get();
					b = scalingRef[3].get();
					c = scalingRef[1].get();
					d = scalingRef[2].get();
				}

				
				// LEFT SIDE
				float m = (b.y - a.y) / (b.x - a.x);
				
				line(a.x,a.y,b.x,b.y); // to check we're at the proper points in the forearm
				
				// (y-y1) = m(x-x1)	
				float x0 = (pts[3].y - b.y) / m + b.x;			
				float y0 = min(pts[3].y,a.y);
				ellipse(x0,y0,15,15);
				float x1 = (pts[4].y - b.y) / m + b.x;
				float y1 =  max(pts[4].y,b.y);
				y1 = min(y1, y0-minThickness);
				ellipse(x1,y1,15,15);
				
				float scalar = map(modelingHand.getScalingLength(),0,a.dist(b),0,100);

				// RIGHT SIDE
				m = (d.y - c.y) / (d.x - c.x);
				float x2 = (pts[3].y - c.y) / m + c.x;			
				float y2 = min(pts[3].y,c.y);
				ellipse(x2,y2,15,15);
				float x3 = (pts[4].y - d.y) / m + d.x;
				float y3 =  max(pts[4].y,d.y);
				y3 = min(y3, y2-minThickness);
				ellipse(x3,y3,15,15);
				
				line(c.x,c.y,d.x,d.y);
				
				
				ArrayList<PVector> corners = new ArrayList<PVector>();
				if (canvasArm.getXYSlope() < 0){
					corners.add(new PVector(x0,y0));
					corners.add(new PVector(x2,y2));
					corners.add(new PVector(x1,y1));
					corners.add(new PVector(x3,y3));
				}
				else{
					corners.add(new PVector(x0,y0));
					corners.add(new PVector(x2,y2));
					corners.add(new PVector(x3,y3));
					corners.add(new PVector(x1,y1));
				}

				canvasArm.addCorners(corners);
				
				PVector[] corn	= canvasArm.getCorners();
				
				
				for (int i=0; i<corn.length; i++){
					int radius = ((i+1)*i)+15;
					if(corn[i] != null)
						ellipse(corn[i].x,corn[i].y,radius,radius);
				}
	
			}
			
			

		}
		
		else
			modelingHand.isDetected = false;
		
		popStyle();
		
		return hand;
	}
	
	


	/**
	 * Looks the arm canvas by finding large contours from opencv, 
	 * then stores part of the the contour's <i>polygon approximation</i> 
	 * for averaging. 
	 *
	 * @param canvas	- raw contours from opencv
	 * @param smoothing	- number of contours to store/average
	 * @return
	 */
	private Contour createCanvasArm(ArrayList<Contour> canvas){
		Contour arm = null;

		for (Contour c : canvas){					
			if (c.area() > 10000){

				// we should only have one of this area; if not, tidy up your work station! 
//				c.setPolygonApproximationFactor(3);
				arm = c.getPolygonApproximation();
				
				pushStyle();
				noFill();
				strokeWeight(2);
				stroke(61,153,113);
				arm.draw();
				popStyle();
			}
		}
		
		if (arm != null){
			
			canvasHand.isDetected = true;
			
			detectCanvasArm(arm);	
			canvasArm.generate3D(false);
			parent.get3D().setArmPts(canvasArm.generate3D(true));
		}
		else
			canvasHand.isDetected = false;


		return arm;
	}
	
	
	
	
	
	/**
	 * Use convex hull + convexity defects to detect and id forearm and fingers. <br/><br/>
	 * 
	 * Algorithm based off of  
	 * <a href=http://simena86.github.io/blog/2013/08/12/hand-tracking-and-recognition-with-opencv/>Hand Tracking and Recognition with OpenCV</a>
	 * 
	 * @param c		- averaged polygon approximation of arm contour
	 * @return list of finger points; if list is empty, no fingers found
	 */
	private void detectCanvasArm(Contour c) {

		PVector w0 = new PVector();
		PVector w1 = new PVector();
		PVector e0 = new PVector();
		PVector e1 = new PVector();
		
		pushStyle();
		stroke(61,153,113);
		c.getConvexHull().draw();
		
		/*
		 * Hand size is somewhat constant (especially if open). 
		 * Base our wrist / forearm regions on an offest of this constant hand size.
		 */
		Rectangle bb = c.getConvexHull().getBoundingBox();
		stroke(61,153,113);
		// doesn't *really* work if hand is tightly closed; maybe make a special case for handRegion.height later			
		Rectangle handRegion = new Rectangle();
		handRegion.x = bb.x - 5;		
		handRegion.width = bb.width + 10;
		handRegion.height = 90;
		handRegion.y = bb.height - handRegion.height + 5;
		fill(20,204,124,50);
		rect(handRegion.x,handRegion.y,handRegion.width,handRegion.height);
		
		Rectangle armRegion = new Rectangle();
		armRegion.x = bb.x;
		armRegion.y = bb.y;
		armRegion.width = bb.width;
		armRegion.height = bb.height - handRegion.height;
		fill(20,204,124,100);
		rect(armRegion.x,armRegion.y,armRegion.width,armRegion.height);
		
		
		/*
		 * Find the 3D depth values of the forearm
		 */
		if (canvasCalibration){
			
			// down sample the depth map
			int step = 5;
			ArrayList<PVector> depth = new ArrayList<PVector>();
			
			// create a new point with the depthMap as the Z-value
			for (int x=armRegion.x; x<armRegion.x+armRegion.width; x+=step){
				for (int y=armRegion.y; y<armRegion.y+armRegion.height; y+=step){					
					int index = x + y * context.depthWidth();
					int z = context.depthMap()[index];
					
					// if we're above the table
					if (z < 760)
						depth.add(new PVector(x,y,z));						
				}
			}
			
			canvasArm.lowResArmPts = depth;

		}
		
	
		Rectangle wristRegion = new Rectangle();
		wristRegion.x = armRegion.x;		
		wristRegion.width = armRegion.width;
		wristRegion.height = 40;
		wristRegion.y = (int) (.9f*armRegion.height - wristRegion.height);
		fill(20,204,124,150);
		rect(wristRegion.x,wristRegion.y,wristRegion.width,wristRegion.height);
		
		
		// find and set wrist and elbow points
		ArrayList<PVector> wPts = new ArrayList<PVector>();
		ArrayList<PVector> ePts = new ArrayList<PVector>();
		for (int i=0; i<c.numPoints(); i++){
			PVector pt = c.getPoints().get(i);
			
			if (wristRegion.contains(pt.x,pt.y))
				wPts.add(pt);
			
			if (armRegion.contains(pt.x,pt.y) && !wristRegion.contains(pt.x,pt.y) && pt.y < 10)
				ePts.add(pt);

		}
		
		// remove extra detected points		
		if (wPts.size() > 1){
			w0 = wPts.get(0);
			w1 = wPts.get(wPts.size()-1);		
		}
		if (ePts.size() > 1){
			e0 = ePts.get(0);
			e1 = ePts.get(1);		
		}

		if (!scaling){
			ArrayList<PVector> forearmPts = new ArrayList<PVector>();
			forearmPts.add(w0);
			forearmPts.add(w1);
			forearmPts.add(e0);
			forearmPts.add(e1);

			canvasArm.addCorners(forearmPts);
			
			PVector[] corners	= canvasArm.getCorners();
			
			
			for (int i=0; i<corners.length; i++){
				int radius = (i*i+1)+15;
				if(corners[i] != null)
					ellipse(corners[i].x,corners[i].y,radius,radius);
			}

		}
		
		
		/*
		 * SHOULD BREAK THIS OUT INTO SEVERAL METHODS ...
		 */
//		if (!canvasCalibration){
		
	
			// detect number and type of fingers
			PVector p,s,e;
			ArrayList<PVector> tips = new ArrayList<PVector>();
			for (int i=1; i<c.numPoints()-1; i++){
				
				p = new PVector(c.getPoints().get(i).x, c.getPoints().get(i).y, c.getPoints().get(i).z);
				
				// ignore if the point is outside the hand region
				if (handRegion.contains(p.x,p.y)){
				
					s = new PVector(c.getPoints().get(i-1).x,c.getPoints().get(i-1).y, c.getPoints().get(i-1).z);
					e = new PVector(c.getPoints().get(i+1).x,c.getPoints().get(i+1).y, c.getPoints().get(i+1).z);
		
					PVector start = PVector.sub(s,p);
					PVector end = PVector.sub(e,p);
					float threshold = .2f * c.getBoundingBox().width;
					float theta = PVector.angleBetween(start, end);
					
					// acute angles are tips and valleys
					if (theta < radians(90)) 
						tips.add(p);
					
					// merge multiple points that are close together
					else if (p.dist(s) < threshold || p.dist(e) < threshold){
						float x,y;
						// merge p with s
						if (p.dist(s) < p.dist(e)){				
							if (tips.size() > 0)
								tips.remove(tips.size()-1);
							x = (p.x - s.x) * .5f + s.x;
							y = (p.y - s.y) * .5f + s.y;
						}
						// merge p with e
						else{					
							i++;
							x = (p.x - e.x) * .5f + e.x;
							y = (p.y - e.y) * .5f + e.y;
						}
	
						tips.add(new PVector(x,y,0));
					}			
				}	
			}
			
			
			
			/*
			 * Finger tips WILL be convex hull points.
			 * Convex hull points MAY NOT be finger tips.
			 * 
			 * Compare found finger tips against convex hull points to verify.
			 */
			ArrayList<PVector> valleys = new ArrayList<PVector>();
			for (int i=0; i<tips.size(); i++){
				PVector v = tips.get(i);
				
				boolean contains = false;
				for (int j=0; j<c.getConvexHull().numPoints(); j++){
					PVector v0 = c.getConvexHull().getPoints().get(j);
					if (v.x == v0.x && v.y == v0.y)
						contains = true;
				}
				
				if (!contains){
					valleys.add(v);
					tips.remove(i);
					i--;
				}
				
			}
			
			if (valleys.size() > 0){
				for (PVector pv : valleys)
					ellipse(pv.x,pv.y, 5,5);
			}
				
			
			
			// CHECK IF HAND IS FLIPPED
			// ... not the best method, but working with hand relaxed
			if (tips.size() == 5 ){
				float dist0 = tips.get(0).dist(valleys.get(0)) + tips.get(1).dist(valleys.get(0));
				float dist1 = tips.get(3).dist(valleys.get(valleys.size()-1)) + tips.get(4).dist(valleys.get(valleys.size()-1));
	
				if (dist1 > dist0)
					canvasHand.isFlipped = true;
				else
					canvasHand.isFlipped = false;			
			}
			
			
			
			/*
			 * ID each of the tips.
			 * 
			 * IF 1, chances are it will be the thumb or index
			 * IF 2, chances are it will be the thumb and index, 
			 * 			 or						index and middle,
			 * 			 but could be			thumb and pinky
			 * IF 3, could be thumb, index, middle,
			 * 			 or   index, middle, ring ,	
			 * 		     or   middle, ring, pinky ,
			 * 			 or   thumb, index, pinky
			 * IF 4, could be index - pinky
			 * 			 or   thumb - ring				
			 */
			
			// create an empty arrayList of points
			ArrayList<PVector> handPts = new ArrayList<PVector>();
			for (int i=0; i<5; i++)
				handPts.add(new PVector());		
			
	
			if (tips.size() == 1){
				
				if (tips.get(0).y > bb.height-5){
					handPts.set(1, tips.get(0));
				}
				else{
	
					handPts.set(0, tips.get(0));
				}
			}
			
			else if (tips.size() == 2){
				
				float dist = w0.dist(tips.get(1)) - w0.dist(tips.get(0));
				
				if (dist < 20){
					handPts.set(1, tips.get(0));
					handPts.set(2, tips.get(1));
				}
				// false positive for thumb and index
				else if (tips.get(0).dist(tips.get(1)) > handRegion.width*.7f){
					handPts.set(0, tips.get(0));
					handPts.set(4, tips.get(1));
				}
				else {
					handPts.set(0, tips.get(0));
					handPts.set(1, tips.get(1));
				}
							
			}
			
			else if (tips.size() == 3){
				
				float dist0 = w0.dist(tips.get(0));
				float dist1 = w0.dist(tips.get(1));
	
				if (dist1 - dist0 > 20){
					handPts.set(0, tips.get(0));
					handPts.set(1, tips.get(1));
					handPts.set(2, tips.get(2));
				}	
				else{
					handPts.set(1, tips.get(0));
					handPts.set(2, tips.get(1));
					handPts.set(3, tips.get(2));
				}		
			}
			
			else if (tips.size() == 4){
							
				float dist = w0.dist(tips.get(1)) - w0.dist(tips.get(0));
	
				if (dist > 20){
					handPts.set(0, tips.get(0));
					handPts.set(1, tips.get(1));
					handPts.set(2, tips.get(2));
					handPts.set(3, tips.get(3));
				}
				else{
					handPts.set(1, tips.get(0));
					handPts.set(2, tips.get(1));
					handPts.set(3, tips.get(2));
					handPts.set(4, tips.get(3));
				}
			}
		
			
			else if (tips.size() == 5){		
				handPts.set(0, tips.get(0));
				handPts.set(1, tips.get(1));
				handPts.set(2, tips.get(2));
				handPts.set(3, tips.get(3));
				handPts.set(4, tips.get(4));
			}
	
			popStyle();
	
			canvasHand.addFingers(handPts);
			
			PVector[] pts = canvasHand.getFingers();
			
			pushStyle();
			rectMode(CENTER);
			noFill();
			strokeWeight(2);
			stroke(0,176,204,200);
			for (int i=0; i<pts.length; i++){
				
				if (pts[i] != null){				
					rect(pts[i].x, pts[i].y,15,15);
					
					String finger;
					
					if (canvasHand.isFlipped){
						if (i==0)
							finger = "PINKY";
						else if (i==1)
							finger = "RING";
						else if (i==2)
							finger = "MIDDLE";
						else if (i==3)
							finger = "INDEX";
						else
							finger = "THUMB";
					}
					else{
					
						if (i==0)
							finger = "THUMB";
						else if (i==1)
							finger = "INDEX";
						else if (i==2)
							finger = "MIDDLE";
						else if (i==3)
							finger = "RING";
						else
							finger = "PINKY";
						
					}
					
					fill(255);
					int xOffest = finger.length()/2*7;
					text(finger, pts[i].x - xOffest,pts[i].y+20);
					noFill();
				}
				
			}
			
		
//		}
		popStyle();
	}
	


	

	
	/**
	 * Draw the bounds and axes of the physics world.
	 */
	private void drawAxes(){
		
		pushStyle();
		strokeWeight(5f);
		
		stroke(152, 255, 0,150);	 // y = green
		line(0, -bounds, 0, 0, bounds, 0);	  
		stroke(255, 196, 0,150);	 // x = orange
		line(-bounds, 0, 0, bounds, 0, 0);

		if (!mode2D){
			stroke(0, 189, 255, 150);	 // z = blue
			line(0, 0, -bounds, 0, 0, bounds);	
		
			// draw boundary of physics world
			stroke(255,255,255,50);
			noFill();
			box(2*bounds, 2*bounds, 2*bounds);
		}
		popStyle();

	}
	

	public void keyPressed(){
		
		if (key == 'f')
			freeze = !freeze;
		
		if (key == 'c')
			canvasCalibration = !canvasCalibration;
		
	}
	

	/**
	 * Returns the distance squared between two vectors.
	 * 
	 * @param a
	 * @param b
	 * @return distance squared
	 */
	private float distance(PVector a, PVector b){				
		return (b.x - a.x)*(b.x - a.x) + (b.y - a.y)*(b.y - a.y) + (b.z - a.z)*(b.z - a.z);
	}

	
	
	///////////////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE HAND / FOREARM CLASSES
	///////////////////////////////////////////////////////////////////////////////////////////////

	private class Forearm{
		
		private ArrayList<ArrayList<PVector>> armPts = new ArrayList<ArrayList<PVector>>();
		private ArrayList<ArrayList<PVector>> rwpArmPts = new ArrayList<ArrayList<PVector>>();
		private ArrayList<ArrayList<PVector>> prevScan = new ArrayList<ArrayList<PVector>>();
		
		private PVector[] corners = new PVector [4];
		private PVector[] rwps	  = new PVector [4];
		
		
		private int smoothing = 15;
		private int counter = 0;
		
		private ArrayList<PVector> lowResArmPts = new ArrayList<PVector>();
		
		private PVector axisA = new PVector();
		private PVector axisB = new PVector();
		
		private float width  = 0;
		private float length = 0;
		private float height = 0; // starting height from elbow
		private Rectangle bb = new Rectangle();

		
		public Forearm(){
			for (int i=0; i<4; i++){
				armPts.add(new ArrayList<PVector>());
				rwpArmPts.add(new ArrayList<PVector>());
				corners[i] = new PVector();
				rwps[i] = new PVector();
			}
			
			prevScan.add(new ArrayList<PVector>());
			prevScan.add(new ArrayList<PVector>());
	
			for (int i=0; i<5; i++){
				prevScan.get(0).add(new PVector());
				prevScan.get(1).add(new PVector());
			}
			
		}
		
		public void addCorners(ArrayList<PVector> pts){
			
			for (int i=0; i<4; i++){
				PVector v = pts.get(i);
				int x = (int) v.x; 
				int y = (int) v.y;
				
				
				if (counter < smoothing)
					armPts.get(i).add(v);
				if 	(counter < smoothing+10)
					rwpArmPts.get(i).add(context.depthMapRealWorld()[x+y*context.depthWidth()]);
				
				else{
					armPts.get(i).set(counter%smoothing, v);
					rwpArmPts.get(i).set(counter%(smoothing+10), context.depthMapRealWorld()[x + y * context.depthWidth()]);
				}
			}

			avgCorners();

			counter++;		
		}

		/**
		 * Find average 2D and 3D points for the forearm.
		 * 
		 */
		private void avgCorners() {		
			
			for (int i=0; i<4; i++){
				PVector p = new PVector();
				ArrayList<PVector> list = new ArrayList<PVector>();
				PVector rwp = new PVector();
				ArrayList<PVector> rwpList = new ArrayList<PVector>();
				
				int offset = 0;
				// there should never be a null case with the forearm, but just in case
				for (int j=0; j<armPts.get(i).size(); j++){
					PVector v = (armPts.get(i).get(j));
				
					if (armPts.get(i).get(j).x != 0 && armPts.get(i).get(j).y != 0){
						list.add(v);
						int x = (int) v.x; 
						int y = (int) v.y;
						PVector v1 = context.depthMapRealWorld()[x + y * context.depthWidth()];
						rwpList.add(v1);
						if (v1.z < 1)
							offset++;
					}
				}
				
				
							
				if (list.size() != 0){			// don't update point if we've lost our wrist/elbow points
					for (int j=0; j<list.size(); j++){
						p.add(list.get(j));
						rwp.add(rwpList.get(j));
					}
	
					p.div(list.size());
					corners[i] = p;
					
					rwp.div(list.size() - offset); // ignore 0 readings
					rwp.z = rwp.z;
					rwps[i] = rwp; 

				}
				
			}

			
			
		}
		
		/**
		 * Interpolates two sets of 2D points between wrist points and elbow points. <br/> 
		 * Converts generated 2D points into 3D points.<br/> 
		 * 
		 * Sets <i>width</i>, <i>length</i>, and <i>height</i> of the forearm.
		 * @param threeD - whether or not to generate 2D or 3D points
		 * 
		 * @return points along the wrist <i>(0 index)</i> and elbow <i>(1 index)</i>
		 */
		public ArrayList<ArrayList<PVector>> generate3D(boolean threeD){
			
			findTouch();
			
			
			ArrayList<ArrayList<PVector>> temp = new ArrayList<ArrayList<PVector>>();
			temp.add(new ArrayList<PVector>()); // wrist points
			temp.add(new ArrayList<PVector>()); // elbow points
			
			int res = (int) (u+1);			

			PVector[] array = new PVector[4];
			if (threeD)
				array = rwps;
			else
				array = corners;
			
			PVector p0 = array[0];
			PVector p1 = array[1];
			PVector p2 = array[2];
			PVector p3 = array[3];
			
			// if we have valid corners of the forearm
			if (p0 != null && p1 != null && p2 != null && p3 != null){	
				
				for (int i=0; i<res; i++){

					float t = i*(1.0f/u);
					float x0 = (p1.x - p0.x) * t + p0.x;
					float y0 = (p1.y - p0.y) * t + p0.y;
					float z0 = (p1.z - p0.z) * t + p0.z;
					
					float x1 = (p3.x - p2.x) * t + p2.x;
					float y1 = (p3.y - p2.y) * t + p2.y;					
					float z1 = (p3.z - p2.z) * t + p2.z;
					
					if (!threeD){
						pushStyle();
						fill(154,20,204);
						noStroke();
						ellipse(x0,y0,3,3);
						ellipse(x1,y1,3,3);
						popStyle();
					}
					temp.get(0).add(new PVector(x0,y0,z0));
					temp.get(1).add(new PVector(x1,y1,z1));
	
				}
						
				if (threeD){
					// compare new to old values, and if there's not much change, don't update
					for (int i=0; i<temp.get(0).size(); i++){
						float w0z = prevScan.get(0).get(i).z;
						float w0y = prevScan.get(0).get(i).y;
						float w0x = prevScan.get(0).get(i).x;
						
						float e0z = prevScan.get(1).get(i).z;
						float e0y = prevScan.get(1).get(i).y;
						float e0x = prevScan.get(1).get(i).x;
						
						float w1z = temp.get(0).get(i).z;
						float w1y = temp.get(0).get(i).y;
						float w1x = temp.get(0).get(i).x;
						
						float e1z = temp.get(1).get(i).z;
						float e1y = temp.get(1).get(i).y;
						float e1x = temp.get(1).get(i).x;
						
						float threshold = 1.0f;
						
						// if little change, replace new coordinate with old one
						float delta0 = (w1z - w0z)/w0z*100.0f;
						if (Math.abs(delta0) < threshold)
							temp.get(0).get(i).z = w0z;
						delta0 = (w1y - w0y)/w0y*100.0f;
						if (Math.abs(delta0) < threshold/3)
							temp.get(0).get(i).y = w0y;
						delta0 = (w1x - w0x)/w0x*100.0f;
						if (Math.abs(delta0) < threshold/4)
							temp.get(0).get(i).x = w0x;
						
						float delta1 = (e1z - e0z)/e0z*100.0f;
						if (Math.abs(delta1) < threshold)
							temp.get(1).get(i).z = e0z;
						delta1 = (e1y - e0y)/e0y*100.0f;
						if (Math.abs(delta1) < threshold/3)
							temp.get(1).get(i).y = e0y;
						delta1 = (e1x - e0x)/e0x*100.0f;
						if (Math.abs(delta1) < threshold/4)
							temp.get(1).get(i).x = e0x;
						
						
					}

					// reset the prevScan
					for (int i=0; i<temp.get(0).size(); i++){
						prevScan.get(0).set(i, temp.get(0).get(i).get());
						prevScan.get(1).set(i, temp.get(1).get(i).get());
					}
				}
				
			}
			
			if (!threeD){
				
				// Set axes
				axisA = temp.get(0).get(res/2);
				int x = (int) axisA.x;
				int y = (int) axisA.y;
				axisA.z = context.depthMapRealWorld()[x + y * context.depthWidth()].z;
				axisB = temp.get(1).get(res/2);
				x = (int) axisB.x;
				y = (int) axisB.y;
				axisB.z = context.depthMapRealWorld()[x + y * context.depthWidth()].z;
				
				// set length
				length = axisA.dist(axisB);
				// set width
				width = temp.get(1).get(0).dist(temp.get(1).get(temp.get(0).size()-1));
				// set starting height (from known height of the table)
				height = 770 - axisB.z;
				
				// set bounding box
				bb.x = (int) min(corners[0].x, corners[2].x);
				bb.x = (int) min(bb.x, corners[3].x);
				bb.y = (int) min(corners[0].y, corners[2].y);
				bb.y = (int) min(bb.y, corners[3].y);
				bb.width  = (int) max(corners[1].x - bb.x, corners[2].x - bb.x);
				bb.width  = (int) max(bb.width,corners[3].x - bb.x);
				bb.height = (int) max(corners[0].y - bb.y, corners[1].y - bb.y);
				// add a little offset
				bb.x -= 10;
				bb.width += 20;
				bb.y -= 10;
				bb.height += 20;
				
				pushStyle();
				strokeWeight(3);
				stroke(0,176,204,200);
				line(axisA.x,axisA.y,axisB.x,axisB.y);
				rect(bb.x,bb.y,bb.width,bb.height);
				popStyle();
			}

			return temp;
		}
		
		
		/**
		 * Should return list of points.  
		 * Just visualizing for now.
		 */
		public void findTouch(){
			pushStyle();
		
			stroke(61,153,113);
			strokeWeight(2);
			beginShape();
			for (PVector p : lowResArmPts)
				point(p.x,p.y);
			endShape(POINT);
			
			// check with we have an index finger and check how close it is
			if (modelingHand.isDetected && nearTouch && modelingHand.fingers[3] != null){
				PVector p0 = modelingHand.fingers[3];
				p0 = modelingHand.selPos;
				
				int touchCount = 0;
				float dist = 20; 
				for (int i=0; i<lowResArmPts.size(); i++){
					PVector p1 = lowResArmPts.get(i);					
					if (distance(p0,p1) < dist*dist){
//						println("forearm pt: "+p1);
//						println("hand pt   : "+p0);
//						println("distance  : "+distance(p0,p1));
//						println();
						pushStyle();
						stroke(255,0,0);
						line(p1.x,p1.y,p0.x,p0.y);
						popStyle();
						touchCount++;
					}
				}

				if (touchCount > 0)
					modelingHand.isTouching = true;
				else
					modelingHand.isTouching = false;
			}
			popStyle();
		}

		/**
		 * Averaged 2D points of the forearm corners. <br/><br/>
		 * 0 = wristLeft,	<br/>
		 * 1 = wirstRight,	<br/>
		 * 2 = elbowLeft,	<br/>
		 * 3 = elbowRight	
		 * @return array of the averaged 4 corners of the forearm
		 */
		public PVector[] getCorners(){
			return corners;
		}
		
		public float getXYSlope(){
			return (axisB.y - axisA.y) /  (axisB.x - axisA.x);
		}
		
		public float getYZSlope(){
			return (axisB.z - axisA.z) /  (axisB.y - axisA.y);
		}

		
	}
	
	private class Hand {

		private boolean isDetected = false;
		private boolean isTouching = false;
		
		// for averaging points
		private ArrayList<ArrayList<PVector>> handPts = new ArrayList<ArrayList<PVector>>();	
		private ArrayList<ArrayList<PVector>> rwHandPts = new ArrayList<ArrayList<PVector>>();
		
		// averaged points
		private PVector[] fingers = new PVector [5];
		private PVector[] rwps	  = new PVector [5];
		
		private int smoothing = 10;
		private int counter = 0;

		// do running average for rotation
		// use percent change for isFlipped
		private float[] thetas = new float[3];
		private float rotTotal = 0;
		private float rotAvg = 0;
		private int rotCounter = 0;
		private float currRotYZ = 0;
		private boolean isFlipped = false;
		
		private boolean isPinching = false;
		private PVector pinchPos = new PVector();

		// selection point offset to the left of the right-most point;
		// more stable Z-coordinate
		private PVector selPos = new PVector(); 


		public Hand(){
			for (int i=0; i<5; i++){
				handPts.add(new ArrayList<PVector>());
				rwHandPts.add(new ArrayList<PVector>());
				fingers[i] = new PVector();
				rwps[i] = new PVector();
			}
			for (int i=0; i<thetas.length; i++)
				thetas[i] = 0;
		}
		
		
		/**
		 * Detect and ID fingers of the modeling hand.
		 * 
		 * Should be more modular with <i>detectCanvasArm</i> but we'll see ... just make it work!
		 * 
		 * Set the pinch PVector if <i>pinching gesture</i> is detected.<br/>
		 * The <i>pinchPos</i> is the furthest right convex hull points.
		 * 
		 * @param hand
		 * @param handRegion
		 */
		public ArrayList<PVector> detectTips(Contour c, Rectangle handRegion){
			
			
			
			// detect number and type of fingers
			PVector p,s,e;
			ArrayList<PVector> tips = new ArrayList<PVector>();
			
			// for finding the right-most point
			int xMax = -1;
			int xMaxIndex = -1;

			for (int i=2; i<c.numPoints()-2; i++){

				p = new PVector(c.getPoints().get(i).x, c.getPoints().get(i).y, c.getPoints().get(i).z);

				// ignore if the point is outside the hand region
				if (handRegion.contains(p.x,p.y)){					
									
					// if it's close to a convex hull, but is not a convex hull point, delete
					boolean contains = false;
					float minDist = Float.MAX_VALUE;
					int distThresh = 50;
					for (int j=0; j<c.getConvexHull().numPoints(); j++){
						PVector v0 = c.getConvexHull().getPoints().get(j);
						
						float dist = p.dist(v0);
						
						if (dist < 2){
							contains = true;

						}
						else if (dist < minDist)
							minDist = dist;
						
					}
					float theta = 0;
					if (contains || minDist > distThresh){
						
						s = new PVector(c.getPoints().get(i-1).x,c.getPoints().get(i-1).y, c.getPoints().get(i-1).z);
						e = new PVector(c.getPoints().get(i+1).x,c.getPoints().get(i+1).y, c.getPoints().get(i+1).z);
	
						PVector start = PVector.sub(s,p);
						PVector end = PVector.sub(e,p);
						theta = PVector.angleBetween(start, end);
						
//						println("i: "+i+", p.dist(s): "+p.dist(s)+", p.dist(e): "+p.dist(e)+", threshold:"+threshold);
	
						// acute angles are tips and valleys
						if (theta < radians(100)) 
							tips.add(p);
				
						// find the right-most point
						if (p.x > xMax){
							start.normalize();
							
							start.mult(10);
							end.normalize();
							end.mult(10);
							
							start.add(p);
							end.add(p);
							
							selPos = PVector.lerp(start, end, .5f);

							xMax = (int) p.x;
							xMaxIndex = i;
						}
					
					}
					
					

				}	
			}
			
			
			// find the bisector of the right most point
			if (xMaxIndex > 0 && xMaxIndex < c.numPoints()-1){
				pushStyle();
				stroke(255,0,0);
				strokeWeight(3);
				
				int x = (int) selPos.x; 
				int y = (int) selPos.y;
				selPos.z = context.depthMapRealWorld()[x+y*context.depthWidth()].z;
				
				ellipse(selPos.x,selPos.y,3,3);
				line(c.getPoints().get(xMaxIndex).x,c.getPoints().get(xMaxIndex).y,
						selPos.x,selPos.y);
				popStyle();
			}
			
			
			/*
			 * Finger tips WILL be convex hull points.
			 * Convex hull points MAY NOT be finger tips.
			 * 
			 * Compare found finger tips against convex hull points to verify.
			 */
			ArrayList<PVector> valleys = new ArrayList<PVector>();
			for (int i=0; i<tips.size(); i++){
				PVector v = tips.get(i);
				
				boolean contains = false;
				for (int j=0; j<c.getConvexHull().numPoints(); j++){
					PVector v0 = c.getConvexHull().getPoints().get(j);
					if (v.dist(v0) < 5)
						contains = true;
				}
				
				if (!contains){
					valleys.add(v);
					tips.remove(i);
					i--;
				}
				
			}
			
			// for debugging
			if (valleys.size() > 0){
				fill(154,20,204);
				noStroke();
				for (PVector pv : valleys)
					ellipse(pv.x,pv.y, 5,5);
			}
			
			if (tips.size() > 0){
				fill(255,0,127,150);
				for (PVector pv : tips)
					ellipse(pv.x,pv.y, 15,15);
			}
			
			
			
//			println("tips.size(): "+tips.size());
			
			return tips;			
		}
		

		public void addFingers(ArrayList<PVector> pts){
			
			for (int i=0; i<5; i++){
				PVector v = pts.get(i);
				int x = (int) v.x; 
				int y = (int) v.y;
				if (counter < smoothing){
					handPts.get(i).add(v);
					rwHandPts.get(i).add(context.depthMapRealWorld()[x+y*context.depthWidth()]);
				}
				else{
					handPts.get(i).set(counter%smoothing, pts.get(i));
					rwHandPts.get(i).set(counter%smoothing, context.depthMapRealWorld()[x+y*context.depthWidth()]);
				}
			}

			avgFingers();

			counter++;
		}

		/**
		 * Remove ZERO PVectors from list and average
		 * Average centroid too!
		 */
		private void avgFingers() {

			for (int i=0; i<5; i++){
				PVector p = new PVector();
				ArrayList<PVector> list = new ArrayList<PVector>();
				PVector rwp = new PVector();
				ArrayList<PVector> rwpList = new ArrayList<PVector>();
				
				int offset = 0;
				for (int j=0; j<handPts.get(i).size(); j++){
					
					PVector v = handPts.get(i).get(j);
					if (handPts.get(i).get(j).x != 0 && handPts.get(i).get(j).y != 0){
						list.add(v);
						
						int x = (int) v.x; 
						int y = (int) v.y;
						PVector v1 = context.depthMapRealWorld()[x + y * context.depthWidth()];
						rwpList.add(v1);
						if (v1.z < 1)
							offset++;
					}
				}
				
				
				if (list.size() == 0)
					fingers[i] = null;
				else{
					for (int j=0; j<list.size(); j++){
						p.add(list.get(j));
						rwp.add(rwpList.get(j));
					}
	
					p.div(list.size());
					fingers[i] = p;
					
					rwp.div(list.size() - offset); // ignore 0 readings
					rwp.z = rwp.z;
					rwps[i] = rwp;
				}
			}
			

			
			
			getRotation();
			getScalingLength();
			
		}
		
		/**
		 * Finds the best x,y position for the pinch position.
		 * 
		 * Passes the 3D PVector of the pinchPos to the GUI_3D. <br/>
		 * Vector will be a ZERO vector if not pinching.
		 */
		private void getPinchPos3D(ArrayList<Contour> contours) {
			
			if (isPinching){	
				
				// variables for finding right-most convex hull points of the two contours
				ArrayList<PVector> pinchPts = new ArrayList<PVector>();
				
				for (Contour c : contours){			
					int xMax = -1;
					int xMaxIndex = -1;
					
					ArrayList<PVector> pts = c.getConvexHull().getPoints();
					
					for (int i=0; i<pts.size(); i++){
						if (pts.get(i).x > xMax){
							xMax = (int) pts.get(i).x;
							xMaxIndex = i;
						}
					}
					
					pinchPts.add(pts.get(xMaxIndex));
				}
				
				// interpolate between the two points to get a solid Z-value for the 3D scan point
				float _x = (pinchPts.get(1).x - pinchPts.get(0).x) * .65f + pinchPts.get(0).x;
				float _y = (pinchPts.get(1).y - pinchPts.get(0).y) * .65f + pinchPts.get(0).y;
				int x = (int) _x;
				int y = (int) _y;
				int index = x + y * context.depthWidth();
				
				pinchPos.x = _x;
				pinchPos.y = _y;
				
				fill(31,204,182,150);
				ellipse(pinchPos.x,pinchPos.y,20,20);
				fill(0);
				ellipse(pinchPos.x,pinchPos.y,3,3);				

				parent.get3D().setPinchPosition(context.depthMapRealWorld()[index]);	
			}
			else{
				parent.get3D().setPinchPosition(new PVector());
			}
		}


		/**
		 * Get XY and YZ angle of rotation between the thumb and pinky 
		 */
		public void getRotation(){
						
			if (!freeze && fingers[0] != null && fingers[1] != null && fingers[4] != null){
				
				pushStyle();
				strokeWeight(2);
				stroke(154,20,204);
				line(fingers[0].x,fingers[0].y,fingers[4].x,fingers[4].y);
				popStyle();
				
				PVector handAxisYZ = PVector.sub(rwps[0], rwps[4]);			
				handAxisYZ.normalize();
				handAxisYZ.setMag(40);				

				PVector localAxisYZ = new PVector(handAxisYZ.x,handAxisYZ.y,0);			
				localAxisYZ.normalize();
				localAxisYZ.setMag(40);
				
				float thetaYZ = Math.round(degrees(PVector.angleBetween(localAxisYZ, handAxisYZ)));
				
				if (thetaYZ != 0 && rwps[0].z > rwps[4].z)
					thetaYZ *= -1;
				
				
				if (rotCounter < thetas.length)
					thetas[rotCounter] = thetaYZ;
				else{
					rotTotal -= thetas[rotCounter%thetas.length];
					thetas[rotCounter%thetas.length] = thetaYZ;					
				}
				rotTotal += thetaYZ;
				rotAvg = rotTotal/thetas.length;
				currRotYZ = radians(rotAvg);
				
				parent.get3D().setWristRotation(currRotYZ);
				rotCounter++;
				
			}
			
			
		}

		/**
		 * Location of each finger (or null if it's not there) <br/>
		 * 0 = thumb, 4 = pinky
		 * @return - array of the averaged fingers; will return null if finger not present
		 */
		public PVector[] getFingers(){
			return fingers;
		}
		
		public float getScalingLength(){
			if (!isPinching && isFlipped && fingers[3] != null && fingers[4] != null){
				scaling = true;
				return fingers[3].dist(fingers[4]);
			}
			else{
				scaling = false;
				return 0;
			}
		}
		
		/**
		 * Checks if any fingers are detected.
		 * @return whether or not the hand is closed
		 */
		public boolean isClosed(){
			boolean isClosed = false;
			
			if (fingers[0] == null && fingers[1] == null && fingers[2] == null && 
				fingers[3] == null && fingers[4] == null)
				isClosed = true;
			
			return isClosed;
		}

	}

}
