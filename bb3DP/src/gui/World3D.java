package gui;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Iterator;

import controlP5.ControlEvent;
import controlP5.ControlP5;
import controlP5.Group;
import peasy.PeasyCam;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PVector;
import processing.opengl.PGraphicsOpenGL;
import toxi.color.ColorGradient;
import toxi.color.ColorList;
import toxi.color.TColor;
import toxi.geom.AABB;
import toxi.geom.Vec3D;
import toxi.geom.mesh.TriangleMesh;
import toxi.math.CosineInterpolation;
import toxi.physics.VerletConstrainedSpring;
import toxi.physics.VerletParticle;
import toxi.physics.VerletPhysics;
import toxi.physics.VerletSpring;
import toxi.physics.behaviors.GravityBehavior;
import toxi.processing.ToxiclibsSupport;


@SuppressWarnings("serial")
public class World3D extends PApplet{

	private Main parent;
	private int width;
	private int height;
	
	// GUI Variables
	private PeasyCam cam;
	private PFont font;
	private ControlFrame cf;
	private ColorList backgroundGradient;
	private boolean saveMesh = false;
	private boolean freeze = true;
	
	// World Parameters
	private int bounds = 250;
	
	// Physics Variables
	private VerletPhysics physics;
	private GravityBehavior g = new GravityBehavior(new Vec3D(0,0,-.2f));
	private float drag = .3f;
	private float speed = .25f;
	
	
	// MODELING PARAMS
	private Arm arm;
	private Vec3D[] hand = new Vec3D[5];
	private boolean isHandDetected = false;
	private ArrayList<ArrayList<PVector>> armScan = new ArrayList<ArrayList<PVector>>(); 	// points from DepthCAM
	private Model model;
	private ToxiclibsSupport gfx;
	
	
	// GESTURE VARIABLES
	private boolean isTouching = false;
	private boolean isScaling = false;
	
	// DEBUGGING
	private boolean debugMode = true;
	
	
	public World3D(Main parent) {
		this.parent = parent;
		this.width  = 1600;		// hard coded for now
		this.height = 900;
		
		// sets up GUI in other window in the parent frame
		cf = new ControlFrame(parent, this, 640, 900-20-480);
	}
	
	public void setup(){
		size(this.width,this.height, PGraphicsOpenGL.OPENGL);
		background(127,127,133);
		font = loadFont("Menlo-Bold.vlw");	
		
		gfx = new ToxiclibsSupport(this);  
	
		// set up camera and background colors
		initCamera();
	
		// set up physics
		initPhysics();	
		
		// create arm and model
		arm = new Arm(this);
		model = new Model(this, arm);
		
		// add hand particles (4)
		rigHand();
		
	}
	
//	int c = 0;
	public void draw(){
//		background(127,127,133);
		smooth(4);
		lights();
		fill(setBackgroundColor());
		// create function to shift the color based on distance from origin
//		fill(backgroundGradient.get((int) (c%bounds)).toARGB()); c++;
		
		noStroke();
		sphere(10*bounds);

		
		drawAxes();
		

		if (armScan.size() > 0){
			arm.update(armScan);
		}
		
		arm.draw();
		model.update();
		
		if (isHandDetected){
			updateHand();
			showHandPoints();
		}
	
		strokeWeight(1);
		fill(171,165,144);
		stroke(158,153,134,100);			
		gfx.mesh(arm.getMesh());
		fill(15,205,255,80);
		stroke(255,40);
		gfx.mesh(model.getMesh());

		
		fill(15,205,255,200);
		for (VerletParticle p : physics.particles){
			pushMatrix();
			translate(p.x,p.y,p.z);
			box (2);
			popMatrix();
		}
			
		
		// 2D rendering
		cam.beginHUD();
		pushStyle();
		fill(255);
		noStroke();
		textFont(font, 14);
		text((int)frameRate, 10, 22);
		popStyle();
	
		cam.endHUD();
		
//		if (saveOut){
//			saveMesh(model.getMesh());
//			saveOut = false;
//		}
		
		if (!freeze)
			physics.update();
		
//		System.out.println("Time: "+millis()/1000.0);
	}

	
	/** 
	 * Sets up camera positions and system colors for mode changes
	 */
	private void initCamera(){
		cam = new PeasyCam(this, bounds*2);
        cam.setCenterDragHandler(cam.getPanDragHandler());
        cam.setRightDragHandler(cam.getRotateDragHandler());
        cam.setWheelHandler(cam.getZoomWheelHandler());
        cam.setWheelScale(.25f);
        cam.setLeftDragHandler(null);
        cam.setMinimumDistance(-100);
        cam.setMaximumDistance(bounds*10);
        
        cam.rotateX(radians(-90));
        cam.rotateY(radians(30));
        cam.rotateX(radians(15));
        
        // Set up background colors
        ColorGradient grad = new ColorGradient();
        grad.setInterpolator(new CosineInterpolation());
        grad.addColorAt(0,TColor.newHex("8AEFC1"));					// Light Green
        grad.addColorAt(bounds/4,TColor.newHex("87D8CA"));			// Light Blue
        grad.addColorAt(3*bounds/4,TColor.newHex("969696"));		// Gray
        grad.addColorAt(bounds,TColor.newHex("969696"));			// Gray
        backgroundGradient = grad.calcGradient(0,bounds);        
	}
	
	
	private void initPhysics(){
		physics = new VerletPhysics();
		physics.addBehavior(g);	
		physics.setDrag(drag);
		
		physics.setWorldBounds(new AABB(2*bounds));

	}
	
	/**
	 * If variables from GUI have been changed, update the physics world
	 */
	private void updatePhysicsVars(){
		
		physics.setDrag(drag);
		physics.setTimeStep(speed);

	}
	
	/**
	 * Sets the background color based on the hand position.
	 * GRAY			 =  neutral; inactive
	 * GRAY to BLUE	 =  moving towards arm
	 * BLUE TO GREEN =  near to touching the arm 
	 * @return
	 */
	private int setBackgroundColor() {
		
		if (!isHandDetected)
			return backgroundGradient.get(bounds-1).toARGB();
		
		else if (isHandDetected && !isTouching){

			// get distance from hand to origin
			if (hand[1] != null){
				int index = (int) hand[1].distanceTo(new Vec3D());

				index = max(index, 60);
				index = min(index, 300);
				index = (int) map(index,60,300,0,249);

				return backgroundGradient.get(index).toARGB();
			}

		}
		
		else{
			int index = (int) hand[1].distanceTo(new Vec3D());
			index = max(index, 30);
			index = min(index, 200);
			index = (int) map(index,30,200,0,50);
			return backgroundGradient.get(index).toARGB();
		}

		return 0;
	}

	/**
	 * Creates place holder particles for visualizing the modeling hand.
	 */
	private void rigHand() {
				
		float stiff = .1f;
		
		// connect the edges of the wrist to the  center (at a lower resolution)
		VerletParticle p0  = new VerletParticle(0,0,0);			// scan
		VerletParticle p1  = new VerletParticle(0,0,-2);		// modeling
		VerletParticle p2  = new VerletParticle(5,0,0);			// scan
		VerletParticle p3  = new VerletParticle(5,0,-2);		// modeling
		
		// the 0th through 4th particles in the physics world 
		physics.addParticle(p0);		
		physics.addParticle(p1);
		physics.addParticle(p2);
		physics.addParticle(p3);
		
		// add weight to the modeling points
		p1.setWeight(10);		
		p3.setWeight(10);
		
		// connect with springs
		physics.addSpring(new VerletSpring(p0,p1,10,stiff));
		physics.addSpring(new VerletSpring(p2,p3,10,stiff));
		
	}

	/**
	 * Updates the particles of the modeling hand (if detected)
	 */
	private void updateHand(){

		if (hand[0] != null){
			physics.particles.get(0).set(hand[0]);
			
			// not the best solution, but working
			if (Float.isNaN(physics.particles.get(1).x)){
//				println("isNaN hand[0]");
				VerletParticle p = new VerletParticle(hand[0].x, hand[0].y, hand[0].z - 10);
				physics.particles.get(1).set(p);
				physics.springs.get(0).b = physics.particles.get(1);
			}
			
		}

		
		if (hand[1] != null){
			physics.particles.get(2).set(hand[1]);

			// not the best solution, but working
			if (Float.isNaN(physics.particles.get(3).x)){
//				println("isNaN hand[1]");
				VerletParticle p = new VerletParticle(hand[1].x, hand[1].y, hand[1].z - 10);
				physics.particles.get(3).set(p);
				physics.springs.get(1).b = physics.particles.get(3);
			}
		
		}
	
	}

	private void showHandPoints() {
		
		if (isTouching)
			fill(188,156,90);
		else
			fill(204,32,193);
		
		if (hand[0] != null){			
			pushMatrix();
			translate(physics.particles.get(1).x,physics.particles.get(1).y,physics.particles.get(1).z);
			box(15);
			popMatrix();
		}
		
		if (hand[1] != null){
			pushMatrix();
			translate(physics.particles.get(3).x,physics.particles.get(3).y,physics.particles.get(3).z);
			box(15);
			popMatrix();		
		}
	}

	/**
	 * Draw the bounds and axes of the physics world.
	 */
	private void drawAxes(){
		
		pushStyle();
		
		strokeWeight(1);	  
		stroke(0, 189, 255, 150);	 // +z = blue
		line(0, 0, 0, 0, 0, bounds);	
//		strokeWeight(3);	  
		stroke(0, 189, 255, 50);	 // -z = blue
		line(0, 0,0, 0, 0, -bounds);
		
//		strokeWeight(5);
		stroke(152, 255, 0,150);	 //+y = green
		line(0, 0, 0, 0, bounds, 0);	 
//		strokeWeight(3);
		stroke(152, 255, 0, 50);	 // -y = green
		line(0, 0, 0, 0, -bounds, 0);
		
//		strokeWeight(5);
		stroke(255, 196, 0,150);	 // +x = orange
		line(0, 0, 0, bounds, 0, 0);
//		strokeWeight(3);
		stroke(255, 196, 0, 50);	 // -x = orange
		line(0, 0, 0, -bounds, 0, 0);

		// draw boundary of physics world
		stroke(255,255,255,50);
		noFill();
		box(2*bounds, 2*bounds, 2*bounds);
		popStyle();

	}
	
	/**
	 * Saves an .stl file to theh <i>src/data/prints</i> folder
	 */
	private void saveMesh(TriangleMesh m){

		// Make path to save stl
		String path = sketchPath("")+ "src/data/prints/3dPrint_v." + month() + day()
				+ year() + "_" + hour() + "-" + minute() + "-" + second()+".stl";

		m.flipVertexOrder(); // faces outwards
		m.saveAsSTL(sketchPath(path));
		m.flipVertexOrder(); // put back to normal
		saveMesh = false;

	}
	
	
	
	
	/**
	 * Load the 3D points of the wrist and elbow from the DepthCAM
	 * @param pts - wrist and elbow scan points
	 */
	public void setArmPts(ArrayList<ArrayList<PVector>> pts){
		
		if (pts != null && arm != null){
			armScan.clear();
			armScan.addAll(pts);
		}
		
	}
	
	/**
	 * Load the 3D points of the wrist and elbow from the DepthCAM.
	 * 
	 * Center the incoming points on the centroid of the arm scan data.
	 * 
	 * @param pts - wrist and elbow scan points
	 */
	public void setHandPts(PVector[] pts){
		
		for (int i=0; i<pts.length; i++){
			
			if (pts[i] == null)	
				hand[i] = null;
			else{
				pts[i].z *= -1; // flip the Z
				hand[i] = new Vec3D(pts[i].x, pts[i].y, pts[i].z);
				Vec3D offset = Vec3D.ZERO.sub(arm.getCentroid());
				hand[i].addSelf(offset);	
				
				isHandDetected = true;
				
			}
		}
		
		
		if (hand[0] == null && hand[1] == null && hand[2] == null && 
			hand[3] == null && hand[4] == null)
			isHandDetected = false;			
		
	}
	
	
	public void keyPressed(){

		if (key == 's')
			saveMesh = !saveMesh;
		
		if (key == 'd')
			debugMode = !debugMode;

		if (key == 'f')
			freeze = !freeze;
	}
	
	public boolean getDebugMode(){
		return debugMode;
	}
	
	public VerletPhysics getPhysics(){
		return physics;
	}
	
	public boolean isFrozen(){
		return freeze;
	}
	
	/**
	 * Whether or not the modeling hand is touching the arm.
	 * Set by DepthCAM
	 * @param flag
	 */
	public void isTouching(boolean flag){
		isTouching = flag;
		arm.isTouching(flag);
	}
	
	/**
	 * Whether or not the modeling hand is scaling the arm's canvas area.
	 * Set by DepthCAM
	 * @param flag
	 */
	public void isScaling(boolean flag){
		isScaling = flag;
		arm.isScaling(flag);
	}
	
	/**
	 * A separate window for our GUI controls
	 */
	private class ControlFrame extends PApplet {
		ControlP5 cp5;
		World3D p5;
		
		int width;
		int height;

		int abc = 100;
		
		private ControlFrame(Main parent, World3D p5, int w, int h) {
			this.p5 = p5;
			
			this.width = w;
			this.height = h;

			// 3D WORLD
			parent.c.fill = GridBagConstraints.HORIZONTAL;
			parent.c.insets  = new Insets(10,10,10,10);
			parent.c.gridx = 1;
			parent.c.gridwidth = 1;
			parent.c.gridheight = 1;
			parent.c.gridy = 0;
			parent.c.anchor = GridBagConstraints.FIRST_LINE_END;
			// add the PApplet to the frame
			parent.add(this,parent.c);

			parent.pack();
			this.init();

		}

		public void setup() {
			size(this.width,this.height, PGraphicsOpenGL.OPENGL);
			background(127,127,133);		
			cp5 = new ControlP5(this);
			initGUI();			
		}
		
		private void initGUI(){
			PFont label = createFont("Menlo-Bold.vlw", 14);

			Group g1 = cp5.addGroup("")
						.setPosition(10,20)
						.setBackgroundHeight(360)
						.setWidth(cf.width/2-20)
						.setBackgroundColor(color(255,100))
						.setColorForeground(color(128,148,166))
						.setColorBackground(color(76,97,115))
						.setOpen(true)	           
						;


			cp5.setColorActive(color(128,148,166))
				.setColorBackground(color(76,97,115))
				.setColorForeground(color(128,148,166))
				;


			cp5.addTextlabel("world")
				.setPosition(g1.getWidth()/2-38,5)
				.setText("WORLD")
				.setFont(label)
				.setGroup(g1)
				;

			cp5.addToggle("freeze",true)
				.setPosition(10, 30)
				.setWidth(20)
				.setHeight(20)
				.setGroup(g1)
				.plugTo(p5,"freeze")
				.setId(0);
				;
			
			Group g2 = cp5.addGroup("physics")
					.setTitle("")
					.setPosition(cf.width/2+10,20)
					.setBackgroundHeight(360)
					.setWidth(cf.width/2-20)
					.setBackgroundColor(color(255,100))
					.setColorForeground(color(128,148,166))
					.setColorBackground(color(76,97,115))
					.setOpen(true)	 
					;
			
			cp5.addSlider("drag")
				.plugTo(p5, "drag")
				.setValue(.33f)
				.setRange(0, 1)
				.setHeight(20)
				.setWidth(g2.getWidth()/4-5)
				.setPosition(10, 30)
				.setGroup(g2)
				.setId(1)
				;
			
			cp5.addSlider("speed")
				.plugTo(p5, "speed")
				.setValue(.25f)
				.setRange(0, 1)
				.setHeight(20)
				.setWidth(g2.getWidth()/4-5)
				.setPosition(10, 60)
				.setGroup(g2)
				.setId(2)
				;
			
		}

		
		public void controlEvent(ControlEvent theEvent) {
//			println("got a control event from controller with id "+theEvent.getController().getId());

			switch(theEvent.getController().getId()) {
				case(1):
					p5.updatePhysicsVars();
				break;
				case(2):
					p5.updatePhysicsVars();
				break;
			}
		}
		

		public void draw() {
			background(127,127,133);
			smooth();
		}

		

		public ControlP5 getCP5() {
			return cp5;
		}

	  
	}

}
