package gui;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;

import processing.core.PApplet;
import processing.core.PVector;
import toxi.geom.Circle;
import toxi.geom.Plane;
import toxi.geom.Vec3D;
import toxi.geom.mesh.TriangleMesh;

public class Arm {
	
	private GUI_3D p5;

	private ArrayList<PVector> avgPts = new ArrayList<PVector>();

	
	Vec3D[] wristCP = new Vec3D[3];						// wrist control points from the scan
	Vec3D[] elbowCP = new Vec3D[3];						// elbow control points from the scan
	
	private String[] wristPts;									// for loading in pre-drawn curves
	private String[] elbowPts;
	
	private Plane wPlane;										// plane of initial wrist points
	private Plane ePlane;										// plane of initial elbow points
	private float theta0 = 0;									// 		rotations for translating
	private float theta1 = 0;									//		between world XY and local
	private float theta2 = 0;									// 		coords of 3D planes
	
	private ArrayList<Vec3D> wrist = new ArrayList<Vec3D>(); 	// wrist cross-section
	private ArrayList<Vec3D> elbow = new ArrayList<Vec3D>();	// elbow cross-section
	
	private float wristRot = 0;
	
	private Vec3D medial_wrist;									// center point of wrist	
	private Vec3D medial_elbow;									// center point of elbow
	
	private Vec3D medialAxis; 									// medial_wrist.sub(medial_elbow)
	
	private TriangleMesh mesh = new TriangleMesh();				// for shell (eventually)

	private boolean display = true;
	
	
	public Arm(GUI_3D p5){
		this.p5 = p5;	
		
		wristPts = p5.loadStrings("wristPts.txt");		
		elbowPts = p5.loadStrings("elbowPts.txt");
		
		// load pts from section curves
		loadPts(wrist, wristPts);
		loadPts(elbow, elbowPts);

//		for (int i=0; i<3; i++){
//			wristCP[i] = new Vec3D(1,1,1);
//			elbowCP[i] = new Vec3D(1,1,1);
//		}
		
	}
	
	/**
	 * Create 3D points from .txt file
	 *  
	 * @param ptList	- resulting points
	 * @param pts		- Strings to convert
	 */
	private void loadPts(ArrayList<Vec3D> ptList, String[] pts) {
			
		DecimalFormat df = new DecimalFormat("##0.000");
		
		// add center point at the origin
		ptList.add(new Vec3D());
		
		for (int i=0; i<pts.length; i++){
			
			// split pts into x,y,z array
			String[] xyz = PApplet.split(pts[i].substring(1, pts[i].length()-1),", ");
			
			float x = Float.valueOf(df.format(Float.parseFloat(xyz[0]))) * 10;		
			float y = Float.valueOf(df.format(Float.parseFloat(xyz[1]))) * 10;
			float z = Float.valueOf(df.format(Float.parseFloat(xyz[2]))) * 10;
			
//			System.out.println("x: "+x);
//			System.out.println("y: "+y);
//			System.out.println("z: "+z);
//			System.out.println();
			
			// add to ptList
			ptList.add(new Vec3D(x,y,z));
		}
		
	}

	/**
	 * Still jittery in the Z values ... 
	 * Should detect whether moving or not, and only update Z if moving.
	 * Also, depth values should remain constant if the whole arm is moving to a different location.
	 * 
	 * Use particles instead of Vec3D, and if the acceleration is over a certain threshold, go (?)
	 * 
	 * @param scanPts - 3D wrist and points from the depthCAM 
	 */
	public void update(ArrayList<ArrayList<PVector>> scanPts){
		
		ArrayList<PVector> pts = new ArrayList<PVector>();
		// 0-th are the wrist points, and 1st are the elbow points
		for (int i=0; i<scanPts.size(); i++){
			pts.add(scanPts.get(i).get(1));
			pts.add(scanPts.get(i).get(scanPts.get(i).size()/2));
			pts.add(scanPts.get(i).get(scanPts.get(i).size() - 2));
		}
		
		if (display){
			p5.stroke(255,100);
			p5.fill(185,75,255);
			for (int i=0; i<pts.size(); i++){
				PVector p = pts.get(i);
				p5.pushMatrix();
				p5.translate(p.x, p.y, p.z);
				p5.box(5);
				p5.popMatrix();
			}

		}
		
//		float threshold = 1.0f;
		Vec3D v0 =  new Vec3D(pts.get(0).x, pts.get(0).y, pts.get(0).z);
		Vec3D v1 =  new Vec3D(pts.get(1).x, pts.get(1).y, pts.get(1).z);
		Vec3D v2 =  new Vec3D(pts.get(2).x, pts.get(2).y, pts.get(2).z);
		
//		if (wristCP[0] != null){
//			float delta = (v0.z - wristCP[0].z) / wristCP[0].z * 100;
//			if (delta < threshold)
//				v0.z = (float) Math.floor(wristCP[0].z);
//			delta = (v1.z - wristCP[1].z) / wristCP[1].z * 100;
//			if (delta < threshold)
//				v1.z = (float) Math.floor(wristCP[1].z);
//			delta = (v2.z - wristCP[2].z) / wristCP[2].z * 100;
//			if (delta < threshold)
//				v2.z = (float) Math.floor(wristCP[2].z);
//		}

		wristCP[0] = v0;
		wristCP[1] = v1;
		wristCP[2] = v2;
		
		v0 =  new Vec3D(pts.get(3).x, pts.get(3).y, pts.get(3).z);
		v1 =  new Vec3D(pts.get(4).x, pts.get(4).y, pts.get(4).z);
		v2 =  new Vec3D(pts.get(5).x, pts.get(5).y, pts.get(5).z);
		
//		if (elbowCP[0] != null){
//			float delta = (v0.z - elbowCP[0].z) / elbowCP[0].z * 100;
//			if (delta < threshold)
//				v0.z = elbowCP[0].z;
//			delta = (v1.z - elbowCP[1].z) / elbowCP[1].z * 100;
//			if (delta < threshold)
//				v1.z = elbowCP[1].z;
//			delta = (v2.z - elbowCP[2].z) / elbowCP[2].z * 100;
//			if (delta < threshold)
//				v2.z = elbowCP[2].z;
//		}
	
		elbowCP[0] = v0;
		elbowCP[1] = v1;
		elbowCP[2] = v2;
		
		
		
		generateCrossSection();
		
		p5.model.update(wrist, elbow); // should be called by GUI?
		
	}
	

	
	

	
	/**
	 * Find the wrist/elbow planes, and project CPs onto new planes
	 */
	private void generateCrossSection() {

		// Find wrist and elbow planes		
		Vec3D topAxis = wristCP[1].sub(elbowCP[1]);

		ePlane = new Plane(elbowCP[1],topAxis);
		wPlane = new Plane(wristCP[1],topAxis);

//		p5.pushStyle();
		p5.strokeWeight(1);
		p5.stroke(255);
		p5.fill(185,75,255,50);
		
//		p5.gfx().mesh(ePlane.toMesh(75));
//		p5.gfx().mesh(wPlane.toMesh(50));
		
		// only update the z-value of the points if there's significant change
//		float threshold = 1.0f;
//		Vec3D v0 =  wristCP[0].copy();
//		Vec3D v1 =  wristCP[1].copy();
//		Vec3D v2 =  wristCP[2].copy();
		

	
		// overwrite CPs with projected points on planes
		wristCP[0] = wPlane.getProjectedPoint(wristCP[0]);
		wristCP[1] = wPlane.getProjectedPoint(wristCP[1]);
		wristCP[2] = wPlane.getProjectedPoint(wristCP[2]);
		elbowCP[0] = ePlane.getProjectedPoint(elbowCP[0]);
		elbowCP[1] = ePlane.getProjectedPoint(elbowCP[1]);
		elbowCP[2] = ePlane.getProjectedPoint(elbowCP[2]);

		
//		float delta = (wristCP[0].z - v0.z) / v0.z * 100;
//		System.out.println("delta :"+delta);
//		if (delta < threshold){
//			wristCP[0].z = (float) Math.floor(v0.z);
//		}
//		delta = (wristCP[1].z - v1.z) / v1.z * 100;
//		if (delta < threshold) 
//			wristCP[1].z = v1.z;;
//		delta = (wristCP[0].z - v2.z) / v2.z * 100;
//		if (delta < threshold)
//			wristCP[2].z = v2.z;
//		
//		System.out.println("wristCP[0].z "+wristCP[0].z);
		
		// visualize to verify
//		for (int i=0; i<3; i++){
//			p5.pushMatrix();
//			p5.translate(wristCP[i].x, wristCP[i].y, wristCP[i].z);
//			p5.box(3);
//			p5.popMatrix();
//			
//			p5.pushMatrix();
//			p5.translate(elbowCP[i].x, elbowCP[i].y, elbowCP[i].z);
//			p5.box(3);
//			p5.popMatrix();
//		}

		
		p5.strokeWeight(3);
		// draw plane axes
		showAxes(elbowCP, ePlane, elbow, elbowPts, 50);
		showAxes(wristCP, wPlane, wrist, wristPts, 20);
		
		// set medial axis
		
			medial_elbow = elbow.get(0);
			medial_wrist = wrist.get(0);
		if (display)
			p5.line(elbow.get(0).x, elbow.get(0).y, elbow.get(0).z, wrist.get(0).x, wrist.get(0).y, wrist.get(0).z);
		
		
		// mesh the surface
//		p5.strokeWeight(1);
		mesh = meshArm();
//		p5.gfx().mesh(mesh);

//		p5.popStyle();
	}
	
	/**
	 * First finds and draws the axes of the local planes,
	 * then generates the section of the <i>elbow</i> or <i>wrist</i>,
	 * then meshes the resulting arm.
	 * 
	 * 
	 * @param cps
	 * @param p
	 * @param crvPts
	 * @param length
	 */
	private void showAxes(Vec3D[] cps, Plane p, ArrayList<Vec3D> crvPts, String[] pts, int length){

		// peek into our plane properties
//		System.out.println("plane normal: "+p.normal); 
//		System.out.println("plane origin: "+cps[1]); 
		
		Vec3D zAxis = cps[1].sub(p.normal.normalizeTo(length));		

		Vec3D n = cps[1].cross(p.normal).normalizeTo(length);
		Vec3D yAxis = cps[1].add(n); 

		Vec3D m = n.cross(p.normal).normalizeTo(length);
		Vec3D xAxis = cps[1].add(m);
		
		if (display){
			p5.stroke(255, 196, 0,200);	 // x = orange
			p5.line(cps[1].x,cps[1].y,cps[1].z,xAxis.x,xAxis.y,xAxis.z);
			p5.stroke(152, 255, 0,200);	 // y = green
			p5.line(cps[1].x,cps[1].y,cps[1].z,yAxis.x,yAxis.y,yAxis.z);
			p5.stroke(0, 189, 255, 200); // z = blue
			p5.line(cps[1].x,cps[1].y,cps[1].z,zAxis.x,zAxis.y,zAxis.z);
		}
		
//		System.out.println("wrist.size(): "+wrist.size());
//		System.out.println("elbow.size(): "+elbow.size());
		

		
		/////////////////////////////////////////////////////////////////
		
		findThetas(p, xAxis, yAxis, zAxis, false);
		// clear and regenerate points
		crvPts.clear();
		loadPts(crvPts,pts);	// <-- should store fully processed values, so this is only called once
//		findMedialAxis(cps, p, crvPts); // too unstable for now
		if (length < 50) // hacky way to do wrist
			alignWorldToLocal(crvPts, p, true);
		else
			alignWorldToLocal(crvPts, p, false);
		
		/////////////////////////////////////////////////////////////////
		
	}
	
	/**
	 * Build simple quads from the two arm sections
	 * @return
	 */
	private TriangleMesh meshArm() {
		TriangleMesh temp = new TriangleMesh();
		Vec3D v0, v1, v2, v3;
		
		// skip the first point, it's the medial axis 
		for (int i=1; i<elbow.size()-1; i++){
			
			v0 = elbow.get(i);
			v2 = elbow.get(i+1);
			v1 = wrist.get(i);
			v3 = wrist.get(i+1);
			
			temp.addFace(v0,v1,v2);
			temp.addFace(v2,v1,v3);
		}
		
		// close the mesh
		v0 = elbow.get(elbow.size()-1);
		v2 = elbow.get(1);
		v1 = wrist.get(wrist.size()-1);
		v3 = wrist.get(1);
		
		temp.addFace(v0,v1,v2);
		temp.addFace(v2,v1,v3);
			
		return temp;
	}

	/**
	 * Finds the rotation values for translating a 3D plane to world XY.
	 * 
	 * @param p		- plane
	 * @param xAxis	- local X
	 * @param yAxis	- local Y
	 * @param zAxis	- local Z
	 * @param display - whether or not to visualize rotated coordinate system
	 */
	private void findThetas(Plane p, Vec3D xAxis, Vec3D yAxis, Vec3D zAxis, boolean display){
		Vec3D planeCP = new Vec3D(p.x,p.y,p.z);
		
		Vec3D origin = new Vec3D();
		Vec3D toOrigin = Vec3D.ZERO.sub(planeCP);

		xAxis.addSelf(toOrigin);
		yAxis.addSelf(toOrigin);
		zAxis.addSelf(toOrigin);
		
		theta0 = xAxis.angleBetween(new Vec3D(-1,0,0),true);
		xAxis = xAxis.rotateAroundAxis(Vec3D.Y_AXIS, theta0);
		yAxis = yAxis.rotateAroundAxis(Vec3D.Y_AXIS, theta0);
		zAxis = zAxis.rotateAroundAxis(Vec3D.Y_AXIS, theta0);
		
		theta1 = -1*xAxis.angleBetween(new Vec3D(-1,0,0),true);
		xAxis = xAxis.rotateAroundAxis(Vec3D.Z_AXIS, theta1);
		yAxis = yAxis.rotateAroundAxis(Vec3D.Z_AXIS, theta1);
		zAxis = zAxis.rotateAroundAxis(Vec3D.Z_AXIS, theta1);
		
		theta2  = -1*zAxis.angleBetween(Vec3D.Y_AXIS,true);
		
		if (display){
			xAxis = xAxis.rotateAroundAxis(Vec3D.X_AXIS, theta2);
			yAxis = yAxis.rotateAroundAxis(Vec3D.X_AXIS, theta2);
			zAxis = zAxis.rotateAroundAxis(Vec3D.X_AXIS, theta2);
			
			// correct axis orientation
			xAxis = xAxis.rotateAroundAxis(Vec3D.X_AXIS, PApplet.radians(90));
			yAxis = yAxis.rotateAroundAxis(Vec3D.X_AXIS, PApplet.radians(90));
			zAxis = zAxis.rotateAroundAxis(Vec3D.X_AXIS, PApplet.radians(90));
			
			xAxis = xAxis.rotateAroundAxis(Vec3D.Z_AXIS, PApplet.radians(-90));
			yAxis = yAxis.rotateAroundAxis(Vec3D.Z_AXIS, PApplet.radians(-90));
			zAxis = zAxis.rotateAroundAxis(Vec3D.Z_AXIS, PApplet.radians(-90));
			

			p5.pushMatrix();
			p5.translate(0,0,800);
			p5.stroke(255, 196, 0,200);	 // x = orange
			p5.line(origin.x,origin.y,origin.z,xAxis.x,xAxis.y,xAxis.z);
			p5.stroke(152, 255, 0,200);	 // y = green
			p5.line(origin.x,origin.y,origin.z,yAxis.x,yAxis.y,yAxis.z);
			p5.stroke(0, 189, 255, 200); // z = blue
			p5.line(origin.x,origin.y,origin.z,zAxis.x,zAxis.y,zAxis.z);
			p5.popMatrix();		
		}
	}
	
	/**
	 * Transforms points from local coordinate system onto the world XY plane<br/>
	 * (don't look too closely at the messy math ... it just works somehow.)
	 * <br/><br/>
	 * Call <i>findThetas()</i> before using.
	 * 
	 * @param pts	- 2D plane
	 * @param p 	- 3D plane - source
	 * @return	transformed points
	 */
	private ArrayList<Vec3D> alignLocalToWorld(ArrayList<Vec3D> pts, Plane p){
		
		Vec3D planeCP = new Vec3D(p.x,p.y,p.z);

		// move to origin
		Vec3D toOrigin = Vec3D.ZERO.sub(planeCP);
		
		// TO GO FROM LOCAL TO WORLD AXIS
		for (Vec3D v : pts){
			v.addSelf(toOrigin);

			v = v.rotateAroundAxis(Vec3D.Y_AXIS, theta0);

			v = v.rotateAroundAxis(Vec3D.Z_AXIS, theta1);

			v = v.rotateAroundAxis(Vec3D.X_AXIS, theta2);

			// correct axis orientation
			v = v.rotateAroundAxis(Vec3D.X_AXIS, PApplet.radians(90));
			v = v.rotateAroundAxis(Vec3D.Z_AXIS, PApplet.radians(-90));

			// check
//			p5.pushMatrix();
//			p5.translate(v.x,v.y,v.z+800);
//			p5.box(2);
//			p5.popMatrix();

		}
		
		return pts;		
	}
	
	/**
	 * Transforms points on the world XY plane onto a given 3D plane
	 * 
	 * 
	 * @param pts	- 2D points
	 * @param p 	- 3D plane - destination
	 * @param rotWrist	-	whether or not to rotate/shake the wrist
	 * @return	transformed points
	 */
	private void alignWorldToLocal(ArrayList<Vec3D> pts, Plane p, boolean rotWrist ){
		
		
		Vec3D planeCP = new Vec3D(p.x,p.y,p.z);

		// move to origin
		Vec3D origin = new Vec3D();
		Vec3D toPlane = origin.sub(planeCP);
		
		// TO GO FROM WORLD TO LOCAL AXIS
		for (Vec3D v : pts){
			

			if (rotWrist){
				float angle = 1 * Math.round(Math.toDegrees(wristRot));
				// remove some jitter
				if (angle > 2 || angle < -2){				
					v.rotateAroundAxis(Vec3D.Z_AXIS, (float) Math.toRadians(angle));
//					System.out.println(angle);
				}
			}
			
			// un-correct axis orientation			
			v.rotateAroundAxis(Vec3D.Z_AXIS, PApplet.radians(90));
			v.rotateAroundAxis(Vec3D.X_AXIS, PApplet.radians(-90));
			
			v.rotateAroundAxis(Vec3D.X_AXIS, -1*theta2);
			
			v.rotateAroundAxis(Vec3D.Z_AXIS, -1*theta1);
			
			v.rotateAroundAxis(Vec3D.Y_AXIS, -1*theta0);
			
			v.subSelf(toPlane);			

			// check
//			p5.pushMatrix();
//			p5.translate(v.x,v.y,v.z);
//			p5.box(2);
//			p5.popMatrix();
		}
	}

	
	/**
	 * Use the 3 control points to generate a 3PT Circle
	 * 	- translate cps from local plane to world XY
	 * 	- create circle
	 * 	- from this circle's radius, regenerate with 3D points
	 * 	
	 * Scale1D the circle based on the distance from the origin 
	 * 
	 * 
	 * @param cps
	 * @param p
	 * @param crvPts
	 */
	private void findMedialAxis(Vec3D[] cps, Plane p, ArrayList<Vec3D> crvPts){
		
		ArrayList<Vec3D> arcSeg = new ArrayList<Vec3D>();
		
		int distToTable = (int) (790 - cps[1].z);
		
		arcSeg.add(new Vec3D(cps[0]));
		arcSeg.add(new Vec3D(cps[1]));
		arcSeg.add(new Vec3D(cps[2]));
		
		// translate cps from local plane to world XY
		alignLocalToWorld(arcSeg, p);
		
		Circle c = Circle.from3Points(arcSeg.get(0).to2DXY(), arcSeg.get(1).to2DXY(), arcSeg.get(2).to2DXY());
		
		// move section curve to new center point
		if (c!= null){			
			
			for (Vec3D v : crvPts)
				v.addSelf(c.to3DXY());
			
			
			// not working perfectly ... wait for smoothing to adjust
//			int centerPt = (int) PApplet.min(2*c.getRadius()/3,distToTable/2);

		}
	
	}
	
	
	public void setWristRotation(float theta){
		wristRot = theta;		
	}
	
	
	public ArrayList<Vec3D> getWristPts(){
		return wrist;
	}
	
	public ArrayList<Vec3D> getElbowPts(){
		return elbow;
	}
	
	public Vec3D getMedialAxis(){
		return elbow.get(0).sub(wrist.get(0));
	}
	
	public void display(){
		display = !display;
	}

	public TriangleMesh getMesh(){
		return mesh;
	}
	
	
}
