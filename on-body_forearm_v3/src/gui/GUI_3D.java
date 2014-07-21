package gui;


import java.util.ArrayList;











import org.apache.commons.math3.geometry.Vector;
import org.apache.commons.math3.geometry.euclidean.threed.Euclidean3D;
//import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PVector;
import processing.opengl.*;
import toxi.geom.Circle;
import toxi.geom.Plane;
import toxi.geom.Vec3D;
import toxi.geom.mesh.Face;
import toxi.geom.mesh.Mesh3D;
import toxi.geom.mesh.TriangleMesh;
import toxi.physics.VerletPhysics;
import toxi.physics.VerletSpring;
import toxi.physics.behaviors.GravityBehavior;
import toxi.processing.ToxiclibsSupport;

public class GUI_3D extends PApplet{
	
	private Main parent;
	private int width;
	private int height;
	
	private PeasyCam cam;
	private PFont font;
		
	VerletPhysics physics;
	private GravityBehavior g = new GravityBehavior(new Vec3D(0,0,-0));
	private int bounds = 1000;
	private boolean freezeOverride = true; // for internal control
	private boolean freeze = true; 		   // for external manipulation
	

	// Arm and Model
	private Arm arm;	
	private ArrayList<ArrayList<PVector>> forearm = new ArrayList<ArrayList<PVector>>(); // points from DepthCAM
	Model model;
	private PVector pointer = new PVector();
	private PVector pinchPos = new PVector();
	
	// MESH Variables
	private TriangleMesh mesh = new TriangleMesh();	
	private ToxiclibsSupport gfx;
	private boolean saveOut = false;
	private boolean showMesh = false;
	
	
	
	
	public GUI_3D(Main parent){
		this.parent = parent;
		this.width = parent.getWidth()/2;
		this.height = parent.getHeight();		
	}

	public void setup(){
		size(width,height, PGraphicsOpenGL.OPENGL);
		font = loadFont("CourierNewPSMT-18.vlw");
		
		// Initialize camera
        cam = new PeasyCam(this, bounds*2);
        cam.setCenterDragHandler(cam.getPanDragHandler());
        cam.setRightDragHandler(cam.getRotateDragHandler());
        cam.setWheelHandler(cam.getZoomWheelHandler());
        cam.setWheelScale(2);
        cam.setLeftDragHandler(null);
        cam.setMinimumDistance(0);
        cam.setMaximumDistance(bounds*100);
        
//        cam.rotateZ(radians(-60));        
        cam.rotateX(radians(180));
        cam.rotateZ(radians(45));
        
        // Initialize Physics
        initPhysics();

        gfx = new ToxiclibsSupport(this);  
        
        arm = new Arm(this);
        model = new Model(this, arm);
        
        forearm.add(new ArrayList<PVector>());
        forearm.add(new ArrayList<PVector>());
	}
	
	public void draw(){
		background(0);
		smooth(4);

		pushStyle();
		pushMatrix();
		translate(0,0,-800);
		
		model.setMedialAxis(arm.getMedialAxis());
		
		// if we have a pointer finger, pass it to the model
		if (pointer.x != 0 && pointer.y != 0 && pointer.z != 0){
			
			model.setPointer(pointer);

			pushMatrix();
			translate(pointer.x,pointer.y,pointer.z);
			fill(250,20,20);
			box(20);
			popMatrix();
		}
		else{
			// reset the pointer
			model.setPointer(new PVector());
		}
		
		
		
		if (pinchPos.x != 0 && pinchPos.y != 0 && pinchPos.z != 0){
			
			// send to model 
			
			
			// visualize in GUI
			pushMatrix();
			translate(pinchPos.x,pinchPos.y,pinchPos.z);
			stroke(31,204,182);
			fill(0,178,156);
			box(20);
			popMatrix();
			
		}

						
		if (!freezeOverride && !freeze){
			physics.update();
			arm.update(forearm);
		}

		stroke(0,200,200);
		noFill();
		stroke(255,50);
		fill(255,0,255,150);
		gfx.mesh(model.getMesh());

	
		// show spring mesh
//		pushStyle();
//		noFill();
//		stroke(131,85,255);
//		strokeWeight(2);	
//		for (VerletSpring s : physics.springs)
//			line(s.a.x,s.a.y,s.a.z,s.b.x,s.b.y,s.b.z);
//		popStyle();	
		
		
		popMatrix();
		popStyle();
		
		drawAxes();
		
		// why isn't this showing? because of resizing?
		cam.beginHUD();
		pushStyle();
		fill(255);
		noStroke();
		textFont(font, 12);
		text(frameRate, 10, 22);
		popStyle();
		cam.endHUD();
		
		if (saveOut){
			saveMesh(model.getMesh());
			saveOut = false;
		}
		
//		System.out.println("Time: "+millis()/1000.0);
	}
	


	private void initPhysics(){
		physics = new VerletPhysics();
		physics.addBehavior(g);	
		physics.setDrag(.3f);
	}
	
	
	/**
	 * Draw the bounds and axes of the physics world.
	 */
	private void drawAxes(){
		
		pushStyle();
		
		strokeWeight(5);	  
		stroke(0, 189, 255, 150);	 // +z = blue
		line(0, 0, 0, 0, 0, bounds);	
		strokeWeight(3);	  
		stroke(0, 189, 255, 50);	 // -z = blue
		line(0, 0,0, 0, 0, -bounds);
		
		strokeWeight(5);
		stroke(152, 255, 0,150);	 //+y = green
		line(0, 0, 0, 0, bounds, 0);	 
		strokeWeight(3);
		stroke(152, 255, 0, 50);	 // -y = green
		line(0, 0, 0, 0, -bounds, 0);
		
		strokeWeight(5);
		stroke(255, 196, 0,150);	 // +x = orange
		line(0, 0, 0, bounds, 0, 0);
		strokeWeight(3);
		stroke(255, 196, 0, 50);	 // -x = orange
		line(0, 0, 0, -bounds, 0, 0);

		// draw boundary of physics world
		stroke(255,255,255,50);
		noFill();
		box(2*bounds, 2*bounds, 2*bounds);
		popStyle();

	}
	
	

	/**
	 * Saves the creature meshes to src/prints
	 */
	private void saveMesh(TriangleMesh m){

		// Make path to save stl
		String path = sketchPath("")+ "src/data/prints/3dPrint_v." + month() + day()
				+ year() + "_" + hour() + "-" + minute() + "-" + second()+".stl";

		m.flipVertexOrder(); // faces outwards
		m.saveAsSTL(sketchPath(path));
		m.flipVertexOrder(); // put back to normal
		saveOut = false;

	}
	
	
	
	public void keyPressed(){
		
		// freeze and unfreeze the physics world
		if (key == 'f')
			freezeOverride = !freezeOverride;
		
		if (key == 's')
			saveOut = !saveOut;
		
		if (key == 'd')
			arm.display();

	}
	
	
	
	public Main getParent(){
		return parent;
	}
	
	public ToxiclibsSupport gfx(){
		return gfx;
	}
	
	public int getWorldBounds(){
		return bounds;
	}
	
	/**
	 * Update the pointer finger with a new rw point.
	 * @param p - current rwp of pointer finger
	 */
	public void setPointer(PVector p){
		pointer = p;
	}
		
	
	/**
	 * Load the 3D points of the wrist and elbow from the DepthCAM
	 * @param pts
	 */
	public void setArmPts(ArrayList<ArrayList<PVector>> pts){
		
		if (pts != null && arm != null){

				forearm.clear();
				forearm.addAll(pts);
		}
		
	}
	

	public void setWristRotation(float theta){
		if (arm != null)
			arm.setWristRotation(theta);
	}
	
	public void setFreeze(boolean flag){
		freeze = flag;
	}
	public boolean getFreeze(){
		return freeze;
	}

	public void setPinchPosition(PVector p) {
		pinchPos = p;
	}
}
