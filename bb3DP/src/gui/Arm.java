package gui;

import java.text.DecimalFormat;
import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PVector;
import toxi.geom.Plane;
import toxi.geom.Vec3D;
import toxi.geom.mesh.TriangleMesh;
import toxi.physics.VerletConstrainedSpring;
import toxi.physics.VerletParticle;
import toxi.physics.VerletPhysics;
import toxi.physics.VerletSpring;


public class Arm {

	private World3D p5;
	private VerletPhysics physics;
	
	private static final int WRIST = 0;
	private static final int ELBOW = 1;
	
	// 3D SCAN pts from DepthCAM
	private Vec3D[] wScan = new Vec3D[3];							// wrist control points from the scan
	private Vec3D[] eScan = new Vec3D[3];							// elbow control points from the scan
	private Vec3D[] wristPlane = new Vec3D[3];						// holds the X-, Y-, and Z-Axis of the plane
	private Vec3D[] elbowPlane = new Vec3D[3];
	
	private ArrayList<Vec3D> wristCurve = new ArrayList<Vec3D>(); 	// original 2D curve of wrist
	private ArrayList<Vec3D> elbowCurve = new ArrayList<Vec3D>();	// original 2D curve of elbow
	private ArrayList<Vec3D> wrist = new ArrayList<Vec3D>(); 		// wrist cross-section
	private ArrayList<Vec3D> elbow = new ArrayList<Vec3D>();		// elbow cross-section
	private Vec3D centroid = new Vec3D();
	private Vec3D wristRef = new Vec3D();							// persistent reference to wrist to base scaling
	private Vec3D elbowRef = new Vec3D();							// persistent reference to elbow to base scaling
	
	private TriangleMesh mesh = new TriangleMesh();
	private int crvRes = 60;


	private ArrayList<Vec3D> wSection = new ArrayList<Vec3D>(); 	// wrist edge for 3D model
	private ArrayList<Vec3D> eSection = new ArrayList<Vec3D>();		// elbow edge for 3D model
	Float[] lerp = new Float[4];
	
	private float wristRot  = 1;	
	private float targetRot = 1;
	private static final int MAX_ROTATION = 45;
	private Vec3D[] wristRotPts = new Vec3D[2];
	
	private boolean initialized = false;
	private boolean freeze = false;
	
	private boolean display = false;
	
	// GESTURE VARAIBLES
	private boolean isMoving = false;
	private boolean isTouching = false;
	private boolean isScaling = false;
	private boolean isFlipped = false;
	
	public Arm(World3D p5){
		this.p5 = p5;	
		this.physics = p5.getPhysics();		
		
		// store original curves
		loadPts(wristCurve, p5.loadStrings("wristPts.txt"), -10);
		loadPts(elbowCurve, p5.loadStrings("elbowPts.txt"), 200);
		
		// load section curves
		loadPts(wrist, p5.loadStrings("wristPts.txt"), -10);
		loadPts(elbow, p5.loadStrings("elbowPts.txt"), 200);	
	}
	
	
	public void draw(){
		
		p5.pushStyle();

		p5.strokeWeight(5);			
		
		// draw springs
		p5.stroke(244,0,244,100);
		for (VerletSpring s : physics.springs)
			p5.line(s.a.x, s.a.y, s.a.z, s.b.x, s.b.y, s.b.z);
		
		
		
		p5.popStyle();
	}
	
	
	/**
	 * Updates the location of the wScan and eScan points:						<br/>
	 * First set of points are the wrist, and the second set are the elbow.		<br/><br/>
	 * Updates the 3D model based on the new scan data.
	 * 
	 * @param scanPts - 3D wrist and elbow  points from <i>depthCAM</i> 
	 */
	public void update(ArrayList<ArrayList<PVector>> scanPts){	
		
		wScan[0] =  new Vec3D(scanPts.get(0).get(0).x, scanPts.get(0).get(0).y, scanPts.get(0).get(0).z);
		wScan[1] =  new Vec3D(scanPts.get(0).get(1).x, scanPts.get(0).get(1).y, scanPts.get(0).get(1).z);
		wScan[2] =  new Vec3D(scanPts.get(0).get(2).x, scanPts.get(0).get(2).y, scanPts.get(0).get(2).z);
		
		eScan[0] =  new Vec3D(scanPts.get(1).get(0).x, scanPts.get(1).get(0).y, scanPts.get(1).get(0).z);
		eScan[1] =  new Vec3D(scanPts.get(1).get(1).x, scanPts.get(1).get(1).y, scanPts.get(1).get(1).z);
		eScan[2] =  new Vec3D(scanPts.get(1).get(2).x, scanPts.get(1).get(2).y, scanPts.get(1).get(2).z);	
			
	
		if (!isScaling){
			wristRef = new Vec3D(wScan[1]);
			elbowRef = new Vec3D(eScan[1]);
		}

		regenerate();
		
		if (!initialized)
			rig();
		
		// update our rig
		physics.particles.get(0).set(wristRef);
		physics.particles.get(2).set(elbowRef);
		wrist.get(0).set(physics.particles.get(1));
		elbow.get(0).set(physics.particles.get(3));
		
		if (wristRotPts[0] != null && wristRotPts[1] != null){
			physics.particles.get(4).set(wristRotPts[0]);
			physics.particles.get(6).set(wristRotPts[1]);
			
			float diff = physics.particles.get(5).z - physics.particles.get(7).z;
//			System.out.println(diff);
			diff /= 10;
			diff = PApplet.max(diff, -1);
			diff = PApplet.min(diff,  1);
			
			targetRot = diff * MAX_ROTATION;
		}

		
		// check if the arm is moving ... NOT USING YET, BUT COULD ... for something!
		float speed = physics.particles.get(1).distanceTo(physics.particles.get(1).getPreviousPosition());
		if (speed > 1)					// physics.getDrag() and physics.getSpeed() effect this threshold
			isMoving = true;
		else if (isMoving)		// turn off
			isMoving = false;
		
	}
	
	
	/**
	 * Connects the 3D model of the arm to the 3D scan data using springs.
	 * Should only be called/recalled once, when <i>depthCAM</i> detects a forearm.
	 * 
	 * Build / Connect wrist
	 * Build / Connect elbow
	 * Connect wrist to elbow to each other
	 * Connect wrist to elbow to scan points
	 */
	private void rig(){

		
		float stiff = 1f;
		
		// connect the edges of the wrist to the  center (at a lower resolution)
		VerletParticle p0  = new VerletParticle(wristRef);
		VerletParticle p1  = new VerletParticle(wrist.get(0));
		VerletParticle p2  = new VerletParticle(elbowRef);
		VerletParticle p3  = new VerletParticle(elbow.get(0));
		
		// the 0th through 4th particles in the physics world are the FOREARM
		physics.addParticle(p0);		
		physics.addParticle(p1);
		physics.addParticle(p2);
		physics.addParticle(p3);
		
		// add weight
		p1.setWeight(10);		
		p3.setWeight(10);
		
		physics.addSpring(new VerletConstrainedSpring(p0,p1,20,stiff/2));	// make connection to wrist tighter.
		physics.addSpring(new VerletSpring(p1,p3,215,.01f)); 			 	// 215 is length of my forearm
		physics.addSpring(new VerletSpring(p2,p3,10,stiff));
		
		VerletParticle p4  = new VerletParticle(5,-5,0);		// wrist left (scan)
		VerletParticle p5  = new VerletParticle(5,-5,-5);		// wrist left
		VerletParticle p6  = new VerletParticle(5,5,0);			// wrist right (scan)
		VerletParticle p7  = new VerletParticle(5,5,-5);		// wrist right
		
		// the 5th through 8th particles in the physics world are the WRIST
		physics.addParticle(p4);		
		physics.addParticle(p5);
		physics.addParticle(p6);
		physics.addParticle(p7);
		
		// add weight to wrist points
		p5.setWeight(15);		
		p7.setWeight(15);
					
		// WRIST springs
		physics.addSpring(new VerletSpring(p4,p5,1,stiff*2));
		physics.addSpring(new VerletSpring(p6,p7,1,stiff*2));
		System.out.println("AFTER FOREARM RIGGED: ");
		System.out.println("particles.size(): "+physics.particles.size());
		System.out.println("springs.size()  : "+physics.springs.size());
		
		initialized = true;
	}
	
	
	/**
	 * Centers the scan data around the origin.  <br/>
	 * Finds the location and orientation of updated arm axis and aligns wrist and elbow sections. <br/>
	 * Finds the wrist rotation.				 <br/>
	 * Rebuilds the mesh.						 <br/>
	 * Finds the new canvas area for Model		 <br/>
	 */
	private void regenerate(){
		
		if (!isScaling && physics.particles.size() > 7){
			// update centroid of the scan points
			centroid.x = (wristRef.x - elbowRef.x) * .85f + elbowRef.x;
			centroid.y = (wristRef.y - elbowRef.y) * .85f + elbowRef.y;
			centroid.z = (wristRef.z - elbowRef.z) * .85f + elbowRef.z;
			
			
			// move scan points and reference points to center on the origin
			wristRef.subSelf(centroid);
			elbowRef.subSelf(centroid);
		}
		

		wScan[1].subSelf(centroid);
		eScan[1].subSelf(centroid);

		
		/*
		 * Update the position of the wrist rotation points
		 */
		if (wristRotPts[0] != null && wristRotPts[1] != null){	

			wristRotPts[0].subSelf(centroid);
			wristRotPts[1].subSelf(centroid);

		}

		// rotate the edge curves so they align with arm axis
		findAxes();
		alignSection(wrist, wristCurve, wristPlane, WRIST);
		alignSection(elbow, elbowCurve, elbowPlane, ELBOW);	
		
		// rebuild the mesh
		mesh = meshArm();
		
		// locate the canvas area
		findCanvasArea();
		
	}
	
	/**
	 * Finds the local planes aligned with the elbow and wrist.
	 */
	private void findAxes(){
		int length = 25;
		// Find wrist and elbow planes		
		Vec3D topAxis = wrist.get(0).sub(elbow.get(0));
		Plane wPlane = new Plane(wrist.get(0),topAxis);
		Plane ePlane = new Plane(elbow.get(0),topAxis);
		
		// hacky way to get stable axes
		Vec3D yAxis = wrist.get(0).sub(topAxis.normalizeTo(length));
		Vec3D zAxis = wPlane.getProjectedPoint(new Vec3D(yAxis.x, yAxis.y, yAxis.z+length));
		Vec3D xAxis = wPlane.getProjectedPoint(new Vec3D(yAxis.x-length, yAxis.y, yAxis.z));
	
		Vec3D n = xAxis.sub(wrist.get(0)).normalizeTo(15);
		xAxis = n.add(wrist.get(0));
		Vec3D m = yAxis.sub(wrist.get(0)).normalizeTo(15);
		yAxis = m.add(wrist.get(0));
		zAxis = m.cross(n).normalizeTo(15);
		zAxis.addSelf(wrist.get(0));
		
		wristPlane[0] = xAxis;
		wristPlane[1] = yAxis;
		wristPlane[2] = zAxis;
		
		p5.strokeWeight(2);
		// visualize
		if (display){
			p5.stroke(255, 196, 0, 200);	 // x = orange
			p5.line(wrist.get(0).x,wrist.get(0).y,wrist.get(0).z,xAxis.x,xAxis.y,xAxis.z);
			p5.stroke(152, 255, 0, 200);	 // y = green
			p5.line(wrist.get(0).x,wrist.get(0).y,wrist.get(0).z,yAxis.x,yAxis.y,yAxis.z);
			p5.stroke(0, 189, 255, 200); 	 // z = blue
			p5.line(wrist.get(0).x,wrist.get(0).y,wrist.get(0).z,zAxis.x,zAxis.y,zAxis.z);
		}
		
		yAxis = elbow.get(0).sub(topAxis.normalizeTo(length));
		zAxis = ePlane.getProjectedPoint(new Vec3D(yAxis.x, yAxis.y, yAxis.z+length));
		xAxis = ePlane.getProjectedPoint(new Vec3D(yAxis.x-length, yAxis.y, yAxis.z));
		
		elbowPlane[0] = xAxis;
		elbowPlane[1] = yAxis;
		elbowPlane[2] = zAxis;
		
		if (display){			
			p5.stroke(255, 196, 0, 200);	 // x = orange
			p5.line(elbow.get(0).x,elbow.get(0).y,elbow.get(0).z,xAxis.x,xAxis.y,xAxis.z);
			p5.stroke(152, 255, 0, 200);	 // y = green
			p5.line(elbow.get(0).x,elbow.get(0).y,elbow.get(0).z,yAxis.x,yAxis.y,yAxis.z);
			p5.stroke(0, 189, 255, 200); 	 // z = blue
			p5.line(elbow.get(0).x,elbow.get(0).y,elbow.get(0).z,zAxis.x,zAxis.y,zAxis.z);
		}
	}


	/**
	 * Find the necessary angles for the targetPlane to align with the world axes.
	 * 
	 * @param pts			-	points to rotate
	 * @param originalPts	-	original, unmodified curve points
	 * @param targetPlane	-	local axis of edge
	 */
	private void alignSection(ArrayList<Vec3D> pts, ArrayList<Vec3D> originalPts,  Vec3D[] targetPlane, int mode) {
		
		// move local and target to the origin to perform rotations
		Vec3D toOrigin = Vec3D.ZERO.sub(pts.get(0));
		float theta0, theta1, theta2;
		
		for (int i=0; i<3; i++){
			targetPlane[i].addSelf(toOrigin);
			targetPlane[i].normalizeTo(15);
		}
		
		// find the XY slope of the arm
		float slope = 0;
		if (!p5.isFrozen())			
			slope = getXYSlope();
			
	
		targetPlane[0].normalizeTo(10);
		targetPlane[1].normalizeTo(10);
		targetPlane[2].normalizeTo(10);
		
		// find rotation angles to align the target plane with the world plane
		theta0 = targetPlane[0].angleBetween(new Vec3D(-1,0,0),true);
		if (slope != 0 && slope < 0)	// account for flipping over Y axis
			theta0 *= -1;
		for (int i=0; i<3; i++)
			targetPlane[i] = targetPlane[i].rotateAroundAxis(Vec3D.Z_AXIS, theta0);
		
		theta1 = targetPlane[0].angleBetween(new Vec3D(-1,0,0),true);
		if (slope != 0 && slope > 0)	// account for flipping over Y axis
			theta1 *= -1;
		for (int i=0; i<3; i++)
			targetPlane[i] = targetPlane[i].rotateAroundAxis(Vec3D.Y_AXIS, theta1);
	
		// X-Axis should be aligned by now ....
		
		theta2 = -1*targetPlane[2].angleBetween(Vec3D.Z_AXIS,true);
		for (int i=0; i<3; i++)
			targetPlane[i] = targetPlane[i].rotateAroundAxis(Vec3D.X_AXIS, theta2);
			
		
		// move our plane back
		for (int i=0; i<3; i++)
			targetPlane[i].subSelf(toOrigin);
	
		
		/*
		 *  Modify original points by these thetas
		 */
		
		// find the translation from centroid to origin			
		Vec3D offset = Vec3D.ZERO.sub(originalPts.get(0));	// get the centroid from the original curve
		
		for (int i=1; i<pts.size(); i++){
			
			Vec3D p = new Vec3D(originalPts.get(i)); 		// get the point from the original curve
			
			// modify wrist by rotation
			if (mode == WRIST && !isFlipped){
				p.rotateAroundAxis(Vec3D.Y_AXIS, PApplet.radians(targetRot));
			}
			// flip it good!
			else if (mode == WRIST && isFlipped){
				p.rotateAroundAxis(Vec3D.Y_AXIS, PApplet.radians(135));
				p.rotateAroundAxis(Vec3D.Y_AXIS, PApplet.radians(-1*targetRot));
			}
			else if (mode == ELBOW && isFlipped){
				p.rotateAroundAxis(Vec3D.Y_AXIS, PApplet.radians(165));
			}
			
			
			// translate by offset
			p.addSelf(offset);
			
			// do rotations
			p = p.rotateAroundAxis(Vec3D.Z_AXIS, -1*theta0);
			p = p.rotateAroundAxis(Vec3D.Y_AXIS, -1*theta1);
			p = p.rotateAroundAxis(Vec3D.X_AXIS, -1*theta2);
			
			// move to the updated centroid
			p.addSelf(pts.get(0));
	
			// set our edge curve to this modified point
			pts.set(i,p);
		}
		
	
	}


	/**
	 * Build simple quads from the two arm sections
	 * @return
	 */
	private TriangleMesh meshArm() {
		TriangleMesh temp = new TriangleMesh();
		Vec3D v0, v1, v2, v3;
		
		// skip the first point, it's the medial axis 
		int res = (wrist.size() - 1) / crvRes;
		for (int i=1; i<crvRes; i++){

			v0 = elbow.get(i*res);
			v2 = elbow.get((i+1)*res);
			v1 = wrist.get(i*res);
			v3 = wrist.get((i+1)*res);
			
			temp.addFace(v0,v1,v2);
			temp.addFace(v2,v1,v3);

		}
		
		// close the mesh
		v0 = elbow.get(elbow.size()-1);
		v2 = elbow.get(res);
		v1 = wrist.get(wrist.size()-1);
		v3 = wrist.get(res);
		
		temp.addFace(v0,v1,v2);
		temp.addFace(v2,v1,v3);
			
		return temp;
	}
	
	
	/**
	 * Interpolate sections of the arm as our start/end edges of the 3D model.
	 * 
	 * Give weight for the wEdge to stay near the wrist
	 */
	private void findCanvasArea(){			

		float wSegment = .95f;
		float eSegment = .05f;
		
		if (lerp[0] != null && lerp[1] != null && lerp[2] != null && lerp[3] != null){
			wSegment = lerp[0];
			eSegment = lerp[3];
		}			
		
		eSegment = PApplet.max(.05f, eSegment);
		wSegment = PApplet.min(.95f, wSegment);
			
		// create interpolated sections along the arm	
		wSection.clear();
		eSection.clear(); 
		
		int res = (wrist.size()) / crvRes ;
		for (int i=1; i<=crvRes; i++){
			
			float x0 = (wrist.get(i*res).x - elbow.get(i*res).x) * wSegment + elbow.get(i*res).x;
			float y0 = (wrist.get(i*res).y - elbow.get(i*res).y) * wSegment + elbow.get(i*res).y;
			float z0 = (wrist.get(i*res).z - elbow.get(i*res).z) * wSegment + elbow.get(i*res).z;
			wSection.add(new Vec3D(x0,y0,z0));
			
			x0 = (wrist.get(i*res).x - elbow.get(i*res).x) * eSegment + elbow.get(i*res).x;
			y0 = (wrist.get(i*res).y - elbow.get(i*res).y) * eSegment + elbow.get(i*res).y;
			z0 = (wrist.get(i*res).z - elbow.get(i*res).z) * eSegment + elbow.get(i*res).z;
			eSection.add(new Vec3D(x0,y0,z0));
			
		}
				
	}
	
	
	
	/**
	 * Create a list 3D points from .txt file
	 *  
	 * @param ptList	- resulting points
	 * @param pts		- Strings to convert
	 */
	private void loadPts(ArrayList<Vec3D> ptList, String[] pts, int zValue) {
			
		DecimalFormat df = new DecimalFormat("##0.000");
		
		// add center point at the origin
		ptList.add(new Vec3D());
		
		for (int i=0; i<pts.length; i++){
			
			// split pts into x,y,z array
			String[] xyz = PApplet.split(pts[i].substring(1, pts[i].length()-1),", ");
			
			float x = Float.valueOf(df.format(Float.parseFloat(xyz[0]))) * 10;		
			float y = Float.valueOf(df.format(Float.parseFloat(xyz[1]))) * 10;
			float z = zValue;
			
			Vec3D v = new Vec3D(x,y,z);
			v.rotateZ(PApplet.radians(180));
			v.rotateX(PApplet.radians(90));
			// add to ptList
			ptList.add(v);
		}
		
		// fix the y location of the first point
		ptList.get(0).y = ptList.get(1).y;
		
	}
	
	public float getXYSlope(){
		return (elbow.get(0).y - wrist.get(0).y) / (elbow.get(0).x - wrist.get(0).x);
	}
	
	public float getYZSlope(){
		return (elbow.get(0).z - wrist.get(0).z) /  (elbow.get(0).y - wrist.get(0).y);
	}
	
	
	public TriangleMesh getMesh(){
		return mesh;
	}

	public ArrayList<Vec3D> getWristEdge(){
		return wSection;
	}
	
	public ArrayList<Vec3D> getElbowEdge(){
		return eSection;
	}
	
	/**
	 * Returns the centroid of the arm's scan data.
	 * @return
	 */
	public Vec3D getCentroid(){
		return centroid;
	}

	public boolean isInitialized() {
		return initialized;
	}
	
	public void isTouching(boolean flag) {
		isTouching = flag;
		
	}
	
	public boolean isTouching(){
		return isTouching;
	}

	
	/**
	 * Whether or not the modeling hand is scaling the arm's canvas area.
	 * Sent by DepthCAM
	 * @param flag
	 */
	public void isScaling(boolean flag){
		isScaling = false;
	}
	
	public void setFlip(boolean flag){
		isFlipped = flag;
	}
	
	/**
	 * Takes the scans from the DepthCAM and updates them in {@link #regenerate()}.
	 * @param pts
	 */
	public void setWristRotationPts(Vec3D[] pts){
		wristRotPts = pts;
	}
	
	

}
