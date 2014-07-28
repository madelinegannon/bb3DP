package gui;

import gab.opencv.Contour;
import gab.opencv.OpenCV;

import java.awt.Rectangle;
import java.util.ArrayList;

import SimpleOpenNI.SimpleOpenNI;
import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.core.PVector;
import processing.opengl.PGraphicsOpenGL;

public class DepthCAM extends PApplet{

	private Main parent;
	private World3D world;	// keep a pointer to the 3D world
	private int width;
	private int height;
	
	// KINECT Variables
	private SimpleOpenNI context;
	private int[] depth;
	
	// CV Variables
	private OpenCV opencv;
	
	
	private Hand canvasHand   = new Hand();
	private Hand modelingHand = new Hand();
	private Forearm canvasArm = new Forearm();
	private PImage fMask;	// forearm mask
	private PImage mhMask;	// modeling hand mask
	private PImage mHand; // opencv snapshot of processed modeling hand

	
	// GUI Variables
	private PeasyCam cam;
	private PFont font;
	private boolean freeze = false;
	private boolean near   = false;		// whether the modeling hand is within the bounding box of the full canvas arm
	private boolean display = true;
	
	
	private boolean initialized  = false;
	
	
	
	public DepthCAM(Main parent) {
		this.parent = parent;		
		this.width  = 640;		// hard coded for now
		this.height = 480;
	}
	
	public void setup(){
		size(this.width, this.height, PGraphicsOpenGL.OPENGL);
		font = loadFont("Menlo-Bold.vlw");

		this.world = parent.get3D();

		background(0);
		
		context = new SimpleOpenNI(this,SimpleOpenNI.RUN_MODE_MULTI_THREADED);
		context.setMirror(false);
		
		// enable depthMap generation 
		context.enableDepth();
		
		// initialize OpenCV
		opencv = new OpenCV(this, width, height);
		
		// flip the modeling hand
		modelingHand.isFlipped = true;
		
		initCamera();
		
	}
	
	
	public void draw(){	
		background(0);
		pushMatrix();
		translate(-width/2, -height/2);
		
		if (!freeze){
			context.update();
			depth = context.depthMap();
		}	
		
		// change the background based on the distance from modeling hand and canvas arm
		if (modelingHand.isDetected && canvasArm.isDetected){
			PVector mCP = new PVector();
			mCP.x = modelingHand.bb.x + modelingHand.bb.width/2;
			mCP.y = modelingHand.bb.y + modelingHand.bb.height/2;

			PVector cCP = new PVector();
			cCP.x = canvasArm.bb.x + canvasArm.bb.width/2;
			cCP.y = canvasArm.bb.y + canvasArm.bb.height/2;

			line(mCP.x,mCP.y,cCP.x,cCP.y);

			float dist = mCP.dist(cCP);

			background(map(dist,20,width/2,60,0));
		}		
		
		
//		image(context.depthImage(),0,0);
				
		int mid = 700;
		int far = 770;
		
		// dont' update the mask if the hand is close
		if (!near){
			fMask = getForearmMask(mid);
//			image(fMask,0,0);	
		}		
		findForearm();		
		mhMask = getModelingHandMask();
//		image(mhMask,0,0);
		findModelingHand();

		
		
		// visualize the corners of the forearm
		PVector[] corn	= canvasArm.getCorners();
		for (int i=0; i<corn.length; i++){
			int radius = ((i+1)*i)+15;
			if(corn[i] != null)
				ellipse(corn[i].x,corn[i].y,radius,radius);
		}
		
		
		// check which variables are turned on for the forearm
//		println("CANVAS ARM");
//		println("isDetected: "+canvasArm.isDetected);
//		println("isScaling : "+canvasArm.isScaling);
//		println();
//		println("MODELING HAND");
//		println("isDetected: "+modelingHand.isDetected);
//		println("isScaling : "+modelingHand.isScaling);
//		println("isTouching: "+modelingHand.isTouching);
//		println("isPinching: "+modelingHand.isPinching);
//		println();
//		println();
		
		popMatrix();
		
		
		// 2D rendering
		cam.beginHUD();
		pushStyle();
		fill(255);
		noStroke();
		textFont(font, 12);
		text((int)frameRate, 10, 22, 2);
		textFont(font, 14);
		ArrayList<String> g = canvasHand.getGestures(Hand.CANVAS);
		for (int i=0; i<g.size(); i++)
			text(g.get(i), width - 90, height - 22*(i+1) - 10, 2);
		
		g = modelingHand.getGestures(Hand.MODELING);
		for (int i=0; i<g.size(); i++)
			text(g.get(i), 10, height - 22*(i+1) - 10, 2);
		
		popStyle();
		cam.endHUD();
	}
	
	/**
	 * Finds the forearm by looking for pixels within the range of the
	 * top threshold and a given depth offset.
	 * 
	 * Also, ignoring parts of the scene that aren't likely to have the forearm. 
	 * (may have to change later)
	 *  
	 * @param top - near threshold
	 * @return segmented PImage
	 */
	private PImage getForearmMask(int top) {
		PImage temp = createImage(width,height,ARGB);
		
		int dOffset = top + 70;
		float xThresh = 2*width/3;
		float yThresh = 5*height/8;
		
		for (int i=0; i<depth.length; i++){
			
			int d = depth[i];
			
			// change 1D array into 2D array
			int y =  i / context.depthWidth();
			int x =  i % context.depthWidth();
		
			if (d > top && d < dOffset && 
				y < yThresh && x < xThresh){				
				temp.set(x, y, color(0,255,0));
				temp.updatePixels();
			}
		}
		
		return temp;
	}
	
	
	/**
	 * Finds the modeling hand by using the YZ slope of the forearm to 
	 * check weather the hand is above or below the forearm.
	 * 
	 * Also, ignoring parts of the scene that aren't likely to have the forearm. 
	 * (may have to change later)
	 *  
	 * @return segmented PImage
	 */
	private PImage getModelingHandMask() {
		PImage temp = createImage(width,height,ARGB);
	
		float dOffset = canvasArm.axisB.z -10;
		float xThresh = 2*width/3;
		float yThresh = 4*height/8;
		
		for (int i=0; i<depth.length; i++){
			
			int d = depth[i];
			
			// change 1D array into 2D array
			int y =  i / context.depthWidth();
			int x =  i % context.depthWidth();
		
			// if it's significantly above our highest point ... put to top
			if (d < dOffset && d > 400 && y < yThresh && x < xThresh){
				temp.set(x, y, color(255,0,0));
				temp.updatePixels();
			}
			else if (d >= dOffset && d < 770){
				// find the estimated depth of arm at (x,y)
				float run = canvasArm.axisB.y - y;
				float m = canvasArm.getYZSlope();		
				float d2 = canvasArm.axisB.z - 10 - (m*run); 
				
				// if d is smaller, and the point isn't apart of the canvas Arm, we're on top
				if (d < d2 && !canvasHand.bb.contains(x,y) && y < canvasHand.bb.y - 25){
//					println("axisA.z :"+canvasArm.axisA.z+ ", axisB.z: "+canvasArm.axisB.z+", slopeYZ: "+m);
//					println("estimated depth of arm at x,y: "+d2);
//					println("depth of modeling hand at x,y: "+d);
					
					temp.set(x, y, color(255,0,0));
					temp.updatePixels();
				}
			}
		}
		
		return temp;
	}
	
	
	/**
	 * Detects the prominent contour in the forearm mask:								<br/><br/>
	 * 		- Finds the hand, wrist, and forearm regions of the contour.				<br/>
	 * 		- Extracts the four corners of the <i>forearm region</i> as the forearm:	<br/><br/>
	 *			  [0] = wrist left, 
	 *			  [1] = wrist right, 
	 *			  [2] = elbow left, 
	 *			  [3] = elbow right	
	 *																					<br/><br/>
	 * 		- Sends the 3D points of the forearm to <i>World3D</i>.						<br/>
	 * 		- Finds the fingers of the canvasHand in the detected <i>hand region</i>.
	 */
	private void findForearm(){
			
			Contour arm = null;
			
			/*
			 *  Find the prominent contour in the forearm mask
			 */
			
			opencv.loadImage(fMask);
			opencv.blur(10);
			opencv.threshold(128);	
			opencv.erode();
//			opencv.erode();
			opencv.dilate();
			opencv.dilate();
//			image(opencv.getSnapshot(),0,0);
			
			ArrayList<Contour> canvas = opencv.findContours();
	
			for (Contour c : canvas){					
				if (c.area() > 10000){
	
					// we should only have one of this area; if not, tidy up your work station! 
					arm = c.getPolygonApproximation();
					if (display){
						pushStyle();
						noFill();
						strokeWeight(2);
						stroke(61,153,113);
						arm.draw();
						popStyle();
					}
				}
			}		
		
			
			/*
			 * Update our persistent corners if not scaling the forearm
			 */
			if (!near){//!canvasArm.isScaling && canvasArm.getCorners()[3].y < 5){			
				for (int i=0; i<4; i++)
					canvasArm.fixedCorners[i] = canvasArm.getCorners()[i].get();
			}
			
			
			if (canvasArm.isScaling() || canvasArm.stickMode)
				canvasArm.getScaledForearm();
			
			
			/*
			 *  If a contour is detected, ID the different regions of the arm
			 */				

			if (arm != null){
				
				canvasArm.isDetected = true;
				canvasHand.isDetected = true;

				Rectangle bb = arm.getConvexHull().getBoundingBox();
						
				Rectangle handRegion = new Rectangle();
				handRegion.x = bb.x - 5;		
				handRegion.width = bb.width + 10;
				handRegion.height = 90;
				handRegion.y = bb.height - handRegion.height + 5;			
				
				// set the canvasHand bounding box
				canvasHand.bb = handRegion;	
							
				Rectangle armRegion = new Rectangle();
				armRegion.x = bb.x - 5;
				armRegion.y = bb.y;
				armRegion.width = bb.width + 10;
				armRegion.height = bb.height - handRegion.height;
				
				/*
				 *  Check if we are near.
				 */
				if ( armRegion.contains(modelingHand.bb.x+modelingHand.bb.width, modelingHand.bb.y+modelingHand.bb.height/2) || 
					 armRegion.contains(modelingHand.bb.x+modelingHand.bb.width, modelingHand.bb.y+modelingHand.bb.height) 		)
					near = true;				
				else if (near)
					near = false;			
				
				Rectangle wristRegion = new Rectangle();
				wristRegion.x = armRegion.x;		
				wristRegion.width = armRegion.width;
				wristRegion.height = 40;
				wristRegion.y = (int) (.9f*armRegion.height - wristRegion.height);
				
				// visualize all three regions
				if (display){
					strokeWeight(3);
					stroke(61,153,113,100);
					fill(20,204,124,50);
					rect(handRegion.x,handRegion.y,handRegion.width,handRegion.height);
					rect(armRegion.x,armRegion.y,armRegion.width,armRegion.height);
					rect(wristRegion.x,wristRegion.y,wristRegion.width,wristRegion.height);
				}
				
				
				
				/*
				 *  Find and set wrist and elbow points
				 */
				
				ArrayList<PVector> wPts = new ArrayList<PVector>();
				ArrayList<PVector> ePts = new ArrayList<PVector>();
				for (int i=0; i<arm.numPoints(); i++){
					PVector pt = arm.getPoints().get(i);
					
					if (wristRegion.contains(pt.x,pt.y))
						wPts.add(pt);
					
					if (armRegion.contains(pt.x,pt.y) && !wristRegion.contains(pt.x,pt.y) && pt.y < 10)
						ePts.add(pt);

				}
				
				PVector w0 = new PVector();
				PVector w1 = new PVector();
				PVector e0 = new PVector();
				PVector e1 = new PVector();

				// ignore any extra points
				if (wPts.size() > 1){
					w0 = wPts.get(0);
					w1 = wPts.get(wPts.size()-1);
				}							
				if (ePts.size() > 1){
					
					// check the slope of the forearm to decide which point is first	
					if (canvasArm.getXYSlope() > -1){
						e0 = ePts.get(0);
						e1 = ePts.get(1);
					}
					else{
						e0 = ePts.get(1);
						e1 = ePts.get(0);
					}
					
				}
			
					
				if (!near){//!canvasArm.isScaling){
					ArrayList<PVector> forearmPts = new ArrayList<PVector>();
					forearmPts.add(w0);
					forearmPts.add(w1);
					forearmPts.add(e0);
					forearmPts.add(e1);


					canvasArm.addCorners(forearmPts);

					PVector[] corners	= canvasArm.getCorners();

					for (int i=0; i<corners.length; i++)
						canvasArm.fixedCorners[i] = corners[i];
			
				}
				
										
				/*
				 * 	Send 3D points to World3D
				 */
				canvasArm.generate3D(false);
				world.setArmPts(canvasArm.generate3D(true));

				
				/*
				 * 	Find the canvasHand
				 */
				canvasHand.detectTips(arm, Hand.CANVAS);			
				
			}
			
			else{
				canvasArm.isDetected  = false;
				canvasHand.isDetected = false;
			}
			
			
	}

	
	private void findModelingHand(){
			
		opencv.loadImage(mhMask);
		opencv.threshold(0);
		opencv.blur(15);
		opencv.threshold(128);		
		opencv.erode();
		opencv.erode();
		opencv.dilate();
		opencv.dilate();
		opencv.dilate();
		mHand = opencv.getSnapshot();
//		image(mHand,0,0);

		ArrayList<Contour> contours = opencv.findContours();

		
		/*
		 *  Find the prominent contour in the modelingHand mask
		 */
		
		Contour hand = null;		
		for (Contour c : contours){
			if (c.area() > 10000){
	
				hand = c.getPolygonApproximation();
				
				if (display){
					pushStyle();
					noFill();
					strokeWeight(2);
					stroke(143,83,166);
					
					hand.draw();

					stroke(109,242,104);
					hand.getConvexHull().draw();
					
					popStyle();
				}	
			}
		}
			

		/*
		 *  If a contour is detected, ID the different regions of the arm
		 */	
		
		if (hand != null){

			modelingHand.isDetected = true;
			
			
			// define a smaller bb to avoid processing the forearm ................................ should do a better job here.
			Rectangle handRegion = hand.getConvexHull().getBoundingBox();			

			handRegion.width =  150;
			handRegion.x += hand.getConvexHull().getBoundingBox().width - handRegion.width + 15;
			handRegion.height = 180;
			handRegion.y += hand.getConvexHull().getBoundingBox().height-handRegion.height + 15;

			modelingHand.bb = handRegion;

			fill(154,20,204,50);
			rect(handRegion.x,handRegion.y,handRegion.width,handRegion.height);


			/*
			 * Check if the hand is pinching
			 */
			opencv.loadImage(mHand);
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
			else if (modelingHand.isPinching)
				modelingHand.isPinching = false;			
			
			
			/*
			 * Detect / ID the finger tips , then send to World3D
			 */
			modelingHand.detectTips(hand, Hand.MODELING);
			world.setHandPts(modelingHand.rwps);
		}
		else if (modelingHand.isDetected){
			modelingHand.isDetected = false;
			modelingHand.isTouching = false;
			modelingHand.isPinching = false;
			modelingHand.isScaling  = false;
			canvasArm.isScaling 	= false;
			world.setHandPts(new PVector[5]);	// clear the hand points
		}		
	}
	

	private void initCamera(){
		// Initialize camera
        cam = new PeasyCam(this, height);
        cam.setCenterDragHandler(cam.getPanDragHandler());
        cam.setWheelHandler(cam.getZoomWheelHandler());
        cam.setWheelScale(.33f);

        cam.setRightDragHandler(null); // just allow 2D zooming

        cam.setLeftDragHandler(null);
        cam.setMinimumDistance(0);
        cam.setMaximumDistance(width*100);
//        cam.rotateX(radians(180));
	}
	
	public void keyPressed(){

		if (key == 'f')
			freeze = !freeze;

	}
	
	/**
	 * Returns the distance squared between two vectors.
	 * 
	 * @param a
	 * @param b
	 * @return distance squared
	 */
	private float distanceSq(PVector a, PVector b){				
		return (b.x - a.x)*(b.x - a.x) + (b.y - a.y)*(b.y - a.y) + (b.z - a.z)*(b.z - a.z);
	}
	
	
private class Forearm{
		
		private boolean isDetected = false;
		
		private ArrayList<ArrayList<PVector>> armPts = new ArrayList<ArrayList<PVector>>();
		private ArrayList<ArrayList<PVector>> rwpArmPts = new ArrayList<ArrayList<PVector>>();
		private ArrayList<ArrayList<PVector>> prevScan = new ArrayList<ArrayList<PVector>>();
		
		private PVector[] corners = new PVector [4];
		private PVector[] rwps	  = new PVector [4];
		
		
		private int smoothing = 2;
		private int counter = 0;
		
		// TOUCH DETECTION
		private ArrayList<PVector> lowResArmPts = new ArrayList<PVector>();
		
		// SCALING DETECTION
		private boolean isScaling = false;
		private PVector[] fixedCorners = new PVector[4];	// persistent reference to original corners of the forearm
		private Float[] scaledCorners  = new Float[4];	 	// holds the evaluation floats (t) for the scaled forearm
		private boolean stickMode = false;					// makes the scaledCorners stay after scaling is done
			
		private PVector axisA = new PVector();
		private PVector axisB = new PVector();
		

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
								
				// don't update point if we've lost our wrist/elbow points			
				if (list.size() != 0 && (list.size()-offset) != 0){			
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
			

			isScaling();
			
			findTouch();
					
			ArrayList<ArrayList<PVector>> temp = new ArrayList<ArrayList<PVector>>();
			temp.add(new ArrayList<PVector>()); // wrist points
			temp.add(new ArrayList<PVector>()); // elbow points
			
			int res = 5;			

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

					float t = i*(1.0f/(res-1));
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
					if (threeD){
						z0 *= -1;
						z1 *= -1;
					}
					
					// add the first, middle, and last points
					if (i == 0 || i == res/2 || i == res-1){
						temp.get(0).add(new PVector(x0,y0,z0));
						temp.get(1).add(new PVector(x1,y1,z1));
					}
	
				}
										
			}
			
			if (!threeD){
				
				// Set axes
				axisA = temp.get(0).get(1);
				int x = (int) axisA.x;
				int y = (int) axisA.y;
				axisA.z = context.depthMapRealWorld()[x + y * context.depthWidth()].z;
				axisB = temp.get(1).get(1);
				x = (int) axisB.x;
				y = (int) axisB.y;
				axisB.z = context.depthMapRealWorld()[x + y * context.depthWidth()].z;
				
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
		 * See if the modeling hand index finger is near a downsampled set of 3D points on the forearm.
		 * 
		 * Should return list of points.  
		 * Just visualizing for now.
		 */
		public void findTouch(){

			/*
			 * Find the 3D depth values of the forearm
			 */
			if (!near && canvasArm.isDetected && canvasArm.bb.x > 0){

				// down sample the depth map
				int step = 5;
				ArrayList<PVector> depth = new ArrayList<PVector>();

				// create a new point with the depthMap as the Z-value
				for (int x=canvasArm.bb.x; x<canvasArm.bb.x+canvasArm.bb.width; x+=step){
					for (int y=0; y<canvasArm.bb.y+canvasArm.bb.height; y+=step){					
						int index = x + y * context.depthWidth();
						int z = context.depthMap()[index];

						// if we're above the table
						if (z < 760)
							depth.add(new PVector(x,y,z));		
						
					}
				}

				lowResArmPts = depth;
			}
			
			
		
			pushStyle();
		
			stroke(61,153,113);
			strokeWeight(2);
			beginShape();
			for (PVector p : lowResArmPts)
				point(p.x,p.y);
			endShape(POINT);
			
			// check with we have an index finger and check how close it is
			if (modelingHand.isDetected && near && modelingHand.fingers[1] != null){
				PVector p0 = modelingHand.fingers[1];
				
				int touchCount = 0;
				float dist = 25f; 
				for (int i=0; i<lowResArmPts.size(); i++){
					PVector p1 = lowResArmPts.get(i);	
					if (distanceSq(p0,p1) < dist*dist){
//						println("forearm pt: "+p1);
//						println("hand pt   : "+p0);
//						println("distance  : "+distanceSq(p0,p1));
//						println();
						pushStyle();
						stroke(255,0,0);
						line(p1.x,p1.y,p0.x,p0.y);
						popStyle();
						touchCount++;
					}
				}

				if (touchCount > 0){
					modelingHand.isTouching = true;
					world.isTouching(true);
//					if (isScaling)
//						stickMode = true;
//					else
//						stickMode = false;
				}
				else if (modelingHand.isTouching){
					modelingHand.isTouching = false;
					world.isTouching(false);
//					stickMode = true; // leave it on
				}
//				println("sticking: "+stickMode);
			}
			popStyle();
			
			
		}

		
		
		/**
		 * Scales the canvas area of the forearm based on the location of the two fingers of the modeling hand.
		 * <br/><br/>
		 * (1) Find the horizontal intersection point of the two fingers and the left and right side.	<br/>
		 * (2) Find and store that point's (t) along the left and right side.							<br/>
		 * (3) Evaluate the left and right side at (t) and add the new corner to the averaging array.	
		 */
		public void getScaledForearm(){

				int minThickness = 15;

				PVector a,b,c,d;
				float t0 = 0,t1 = 0,t2 = 0,t3 = 0;
						
				a = fixedCorners[0].get();		// wrist left
				b = fixedCorners[2].get();		// elbow left
				c = fixedCorners[1].get();		// wrist right
				d = fixedCorners[3].get();		// elbow right
				
				// don't update the t-values, if we want the canvas area to remain scaled				
				if (isScaling){
					
					// LEFT SIDE
					float m = (b.y - a.y) / (b.x - a.x);

					line(a.x,a.y,b.x,b.y); // to check we're at the proper points in the forearm

					// (y-y1) = m(x-x1)	
					float x0 = (modelingHand.fingers[1].y - b.y) / m + b.x;			
					float y0 = min(modelingHand.fingers[1].y,a.y);

					float x1 = (modelingHand.fingers[0].y - b.y) / m + b.x;
					float y1 =  max(modelingHand.fingers[0].y,b.y);
					y1 = min(y1, y0-minThickness);

					PVector w0 = new PVector(x0,y0);
					PVector e0 = new PVector(x1,y1);;
					// find the evaluation points of (x0,y0) and (x1,y1)
					t0 = w0.dist(b) / a.dist(b);			
					t1 = e0.dist(b) / a.dist(b);

					pushStyle();
					noStroke();
					fill(255,0,0);
					ellipse(w0.x,w0.y,5,5);
					ellipse(e0.x,e0.y,7,7);
					popStyle();


					// RIGHT SIDE
					m = (d.y - c.y) / (d.x - c.x);
					float x2 = (modelingHand.fingers[1].y - c.y) / m + c.x;			
					float y2 = min(modelingHand.fingers[1].y,c.y);

					float x3 = (modelingHand.fingers[0].y - d.y) / m + d.x;
					float y3 =  max(modelingHand.fingers[0].y,d.y);
					y3 = min(y3, y2-minThickness);

					PVector w1 = new PVector(x2,y2);
					PVector e1 = new PVector(x3,y3);;
					// find the evaluation points of (x0,y0) and (x1,y1)
					t2 = w1.dist(d) / c.dist(d);			
					t3 = e1.dist(d) / c.dist(d);

					pushStyle();
					noStroke();
					fill(255,0,0);
					ellipse(w1.x,w1.y,5,5);
					ellipse(e1.x,e1.y,7,7);
					popStyle();

					line(c.x,c.y,d.x,d.y);				


					// update t-values

					if (canvasArm.getXYSlope() < -1){
						scaledCorners[0] = t0;
						scaledCorners[1] = t2;
						scaledCorners[2] = t1;
						scaledCorners[3] = t3;
					}
					else{
						scaledCorners[0] = t0;
						scaledCorners[1] = t2;
						scaledCorners[2] = t3;
						scaledCorners[3] = t1;					
					}
					
				}
		
				ArrayList<PVector> corners = new ArrayList<PVector>();
				corners.add(PVector.lerp(b, a, scaledCorners[0]));
				corners.add(PVector.lerp(d, c, scaledCorners[1]));
				corners.add(PVector.lerp(b, a, scaledCorners[2]));
				corners.add(PVector.lerp(d, c, scaledCorners[3]));

				addCorners(corners);
				
				println("isScaling: "+isScaling);
				println("t0: "+scaledCorners[0]);
				println("t1: "+scaledCorners[1]);
				println("t2: "+scaledCorners[2]);
				println("t3: "+scaledCorners[3]);
				println();
				
				// show original corners
				for (int i=0; i<fixedCorners.length; i++){
					int radius = ((i+1)*i)+15;
					if(fixedCorners[i] != null)
						ellipse(fixedCorners[i].x,fixedCorners[i].y,radius,radius);
				}

		}
	
		/**
		 * Checks if the modelingHand is trying to scale the canvas area of the forearm
		 * 
		 * @return isScaling
		 */
		private boolean isScaling(){
			
			if (modelingHand.isDetected && near && !modelingHand.isPinching && modelingHand.fingers[0] != null && modelingHand.fingers[1] != null){
				isScaling = true;
				modelingHand.isScaling = true;
				world.isScaling(true);
				stickMode = true;
			}
			else if (isScaling){
				isScaling = false;
				modelingHand.isScaling = false;
				world.isScaling(false);
			}
			
			
			return isScaling;
			
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
	
	private static final int CANVAS   = 0;
	private static final int MODELING = 1;

	// GESTURES
	private boolean isDetected = false;
	private boolean isTouching = false;
	private boolean isFlipped  = false;		// isFlipped is true when thumb is on the right side
	private boolean isPinching = false;
	private boolean isScaling  = false;
	private boolean isClosed   = false;
	
	private PVector touchPt = new PVector(); 
	private ArrayList<PVector> pinchPts = new ArrayList<PVector>();
	
	// for averaging points
	private ArrayList<ArrayList<PVector>> handPts = new ArrayList<ArrayList<PVector>>();	
	private ArrayList<ArrayList<PVector>> rwHandPts = new ArrayList<ArrayList<PVector>>();
	private int smoothing = 2;
	private int counter = 0;
	
	// averaged points
	private PVector[] fingers = new PVector [5];
	private PVector[] rwps	  = new PVector [5];
	
	private Rectangle bb = new Rectangle();
	
	private boolean display = true;
	
	

	/**
	 * Hand object that:			<br/>
	 * 	- Tracks Gestures			<br/>
	 * 	- Detects / IDs fingers		<br/>
	 * 	- Stores Real-World Points to share with World3D
	 */
	public Hand(){
		
		for (int i=0; i<5; i++){
			handPts.add(new ArrayList<PVector>());
			rwHandPts.add(new ArrayList<PVector>());

		}

	}
	
	/**
	 * Detects fingertips within the hand bounding box.
	 * 
	 * @param c - polygon approximation of contour detected in mask.
	 * @param mode	- 0 = canvasHand, 1 = modelingHand
	 */
	private void detectTips(Contour c, int mode) {
		
		PVector p,s,e;
		ArrayList<PVector> tips = new ArrayList<PVector>();
		
		c.getConvexHull().draw();
		
		for (int i=1; i<c.numPoints()-1; i++){		

			p = new PVector(c.getPoints().get(i).x, c.getPoints().get(i).y, c.getPoints().get(i).z);
			
			// search if our point is inside our hand's bounding box
			if (bb.contains(p.x,p.y)){
				 
				// ignore points that are too close to a convex hull point (was useful for modelingHand)
				boolean contains = false;
				float minDist = Float.MAX_VALUE;
				int distThresh = 5;
				
				for (int j=0; j<c.getConvexHull().numPoints(); j++){
					PVector v0 = c.getConvexHull().getPoints().get(j);				
					float dist = p.dist(v0);					
					if (dist < 2){
						// we're at a convex hull point
						contains = true;		
					}
					else if (dist < minDist)
						minDist = dist;	
				}
				
				
				// ignore any hull point in the far half of the hand region				
				if (mode == MODELING ){
					
					if ((isFlipped && p.x < bb.x+bb.width/2) || 
					   (!isFlipped && p.x > bb.x+bb.width/2)	){
						contains = false;	
						minDist = distThresh;
					}
					
				}
				
				
				
				float theta = 0;
				
				// if p is a candidate point
				if (contains || minDist > distThresh){
					
					s = new PVector(c.getPoints().get(i-1).x,c.getPoints().get(i-1).y, c.getPoints().get(i-1).z);
					e = new PVector(c.getPoints().get(i+1).x,c.getPoints().get(i+1).y, c.getPoints().get(i+1).z);

					PVector start = PVector.sub(s,p);
					PVector end = PVector.sub(e,p);
					theta = PVector.angleBetween(start, end);
					
//					println("i: "+i+", p.dist(s): "+p.dist(s)+", p.dist(e): "+p.dist(e)+", threshold:"+threshold);					
					
					float threshold;
					if (mode == MODELING)
						threshold = radians(105);
					else
						threshold = radians(95);
					
					
					// acute angles are tips and valleys
					if (theta < threshold){ 
						
						// inset the detected tip along the finger to stablize readings 
						start.normalize();							
						start.mult(10);
						end.normalize();
						end.mult(10);
						
						start.add(p);
						end.add(p);
						
						tips.add(PVector.lerp(start, end, .5f));						
//						tips.add(p);	
					}
					
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
				if (v.dist(v0) < 15)
					contains = true;
			}
			
			if (!contains){
				valleys.add(v);
				tips.remove(i);
				i--;
			}
			
		}
		
		// for debugging
//		if (valleys.size() > 0){
//			fill(154,20,204);
//			noStroke();
//			for (PVector pv : valleys)
//				ellipse(pv.x,pv.y, 5,5);
//		}
//		
//		if (tips.size() > 0){
//			fill(255,0,127,150);
//			for (PVector pv : tips)
//				ellipse(pv.x,pv.y, 15,15);
//		}
		
//		println("Number of fingers detected: "+tips.size());
		
		
		
		
		/*
		 * Check if the hand is open or closed, then classify detected tips
		 */
		
		if (tips.size() == 0 && !isDetected){
			isClosed = true;
			// reset averaging arrays
			for (int i=0; i<handPts.size(); i++){
				handPts.get(i).clear();
				rwHandPts.get(i).clear();
				counter = 0;
			}
		}
		else if (isClosed)
			isClosed = false;
		

		classifyFingers(tips, mode);
		
	
	}
	
	/**
	 * IDs fingers:
	 * 
	 * 	The goal is to have stable detection for primary fingers (index, thumb, pinky);
	 *  not trying to get every possible combination of detected fingers.
	 * 
	 * <b> MAYBE ADD MODE FOR MODELING vs CANVAS HAND ? </b>
	 * @param tips	- detected fingers
	 * @param mode	- 0 = canvasHand, 1 = modelingHand
	 */
	private void classifyFingers(ArrayList<PVector> tips, int mode) {
		
		ArrayList<PVector> hand = new ArrayList<PVector>();
		for (int i=0; i<5; i++)
			hand.add(new PVector());	
		
		// assume one finger is the index
		if (tips.size() == 1) {
			hand.set(1, tips.get(0));
		}
		
		// check for thumb / index, index / middle, thumb / pinky
		if (tips.size() == 2) {
			
			// assume thumb / pinky
			if (mode == CANVAS){
				hand.set(0, tips.get(0));
				hand.set(4, tips.get(1));
			}
			
			// should look at differentiating thumb / index or index / middle,
			// but for now assume thumb / index 
			else{
				hand.set(0, tips.get(1));
				hand.set(1, tips.get(0));
			}
			
		}		
		
		if (tips.size() == 3){
			// just assign thumb / index 
			if (mode == MODELING) {
				hand.set(0, tips.get(1));
				hand.set(1, tips.get(0));
			}
			// just assign thumb / pinky 
			else{
				hand.set(0, tips.get(0));
				hand.set(4, tips.get(2));
			}
			
		}		
		
		if (tips.size() == 4){
			// just assign thumb / index 
			if (mode == MODELING) {
				hand.set(0, tips.get(1));
				hand.set(1, tips.get(0));
			}
			// just assign thumb / pinky 
			else{
				hand.set(0, tips.get(0));
				hand.set(4, tips.get(2));
			}
			
		}
		
		if (tips.size() == 5) {
			hand.set(0, tips.get(0));
			hand.set(1, tips.get(1));
			hand.set(2, tips.get(2));
			hand.set(3, tips.get(3));
			hand.set(4, tips.get(4));			
		}
		
		addFingers(hand);			
	}

	/**
	 * Add detected fingers to our smoothing array, and find the averaged points. <br/>
	 * 
	 * @param pts - raw 2D points to add
	 */
	private void addFingers(ArrayList<PVector> pts){
		
		for (int i=0; i<5; i++){
			PVector v = pts.get(i);
			int x = (int) v.x; 
			int y = (int) v.y;
			// set Z-value for 2D points too
			int z = (int) context.depthMapRealWorld()[x+y*context.depthWidth()].z;
			
			if (counter < smoothing){
				handPts.get(i).add(new PVector(v.x,v.y,z));
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
	 * Average detected fingers for more stable readings.
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
			
			
			if (list.size() == 0 && (list.size() - offset) == 0){
				fingers[i] = null;
				rwps[i] = null;
			}
			else{
				for (int j=0; j<list.size(); j++){
					p.add(list.get(j));
					rwp.add(rwpList.get(j));
				}				
				
				rwp.div(list.size() - offset); // ignore 0 readings
				rwp.z = rwp.z;
				rwps[i] = rwp;
				
				p.div(list.size());
				p.y = Math.max(0, p.y);	// make sure y is positive
				p.z = rwp.z;
				fingers[i] = p;			
				
			}
		}
		
		
				
		
		/*
		 * Visualizing the fingers 
		 */
		
		
		
		pushStyle();
		rectMode(CENTER);
		noFill();
		strokeWeight(2);
		stroke(0,176,204,200);
		for (int i=0; i<fingers.length; i++){
			
			if (fingers[i] != null){	
				
				strokeWeight(2);
				stroke(0,176,204,200);
				rect(fingers[i].x, fingers[i].y,15,15);
				fill(255,0,0);
				noStroke();
				ellipse(fingers[i].x, fingers[i].y,3,3);
				
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
				textFont(font, 16);
				text(finger, fingers[i].x - xOffest,fingers[i].y+20, 2);
				noFill();
			}
			
		}
		popStyle();

		
	}
	

	
	public ArrayList<String> getGestures(int mode){
		ArrayList<String> g = new ArrayList<String>();
		
		if (isTouching)
			g.add("TOUCHING");
		if (isPinching)
			g.add("PINCHING");
		if (isScaling)
			g.add("SCALING");
		if (isClosed)
			g.add("CLOSED");
		if (!isDetected && mode == MODELING)
			g.add("HAND NOT \nDETECTED");
		if (!isDetected && mode == CANVAS)
			g.add("ARM NOT \nDETECTED");
		
		return g;
	}
	
	
	
	/**
	 * Flag for when the hand is closed.
	 * @return whether or not the hand is closed
	 */
	public boolean isClosed(){		
		return isClosed ;
	}

	
	

}
	
	

}
