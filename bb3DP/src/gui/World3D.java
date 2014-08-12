package gui;

import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
import toxi.geom.Line3D;
import toxi.geom.Vec3D;
import toxi.geom.mesh.Face;
import toxi.geom.mesh.LaplacianSmooth;
import toxi.geom.mesh.Mesh3D;
import toxi.geom.mesh.TriangleMesh;
import toxi.geom.mesh.Vertex;
import toxi.geom.mesh.WETriangleMesh;
import toxi.math.CosineInterpolation;
import toxi.physics.VerletParticle;
import toxi.physics.VerletPhysics;
import toxi.physics.VerletSpring;
import toxi.physics.behaviors.GravityBehavior;
import toxi.processing.ToxiclibsSupport;
import toxi.util.datatypes.FloatRange;
import toxi.volume.BoxBrush;
import toxi.volume.HashIsoSurface;
import toxi.volume.MeshLatticeBuilder;
import toxi.volume.VolumetricBrush;


@SuppressWarnings("serial")
public class World3D extends PApplet{

	private Main parent;
	private int width;
	private int height;
	
	// GUI Variables
	private PeasyCam cam;
	private PFont font;
	
	/**
	 * GUI / Sliders for manipulating physics world.
	 */
	private ControlFrame cf;
	private ColorList backgroundGradient;
	private boolean saveMesh = false;
	private boolean freeze = true;
	
	// World Parameters
	private int bounds = 250;
	
	// Physics Variables
	private VerletPhysics physics;
	private GravityBehavior g = new GravityBehavior(new Vec3D(0,0,-.5f));
	private float drag = .3f;
	private float speed = .25f;
	
	
	/**
	 * 3D model of user's arm.
	 */
	private Arm arm;
	
	/**
	 * Finger tips of the user's modeling hand.
	 */
	private Vec3D[] hand = new Vec3D[5];
	
	/**
	 * If the modeling hand is detected.
	 */
	private boolean isHandDetected = false;
	
	/**
	 * Raw scan data from DepthCAM.
	 */
	private ArrayList<ArrayList<PVector>> armScan = new ArrayList<ArrayList<PVector>>(); 	
	
	/**
	 * Dynamic 3D model to manipulate with modeling hand.
	 */
	private Model model;
	
	boolean showMesh = true;
	
	
	/**
	 * Mesh rendering
	 */
	private ToxiclibsSupport gfx;
	
	/**
	 * Voxelized Mesh
	 */
	private Mesh3D mesh = new WETriangleMesh();
	private boolean voxelize = false;
	
	/**
	 * If the modeling hand is touching the canvas area.
	 */
	private boolean isTouching = false;
	private boolean isFlipped = false;
	private boolean isInitialized = false;

	
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
	
	}
	

	public void draw(){

		smooth(4);
		lights();
		
		// create gradient background
		fill(setBackgroundColor());		
		noStroke();
		sphere(10*bounds);

		
		drawAxes();
		
		// if we have valid scan data from the kinect, update the arm
		if (armScan.size() > 0){
			arm.update(armScan);
		}
		
		arm.draw();
		model.update();
		
		if (!isInitialized && physics.particles.size() > 7){
			// add (4) hand particles 
			rigHand();
			isInitialized = true;
		}
		
		
		if (physics.particles.size() > 7){
			updateHand();
//			showHandPoints();
		}
	
		strokeWeight(1);
		fill(171,165,144);
		stroke(158,153,134,100);			
		gfx.mesh(arm.getMesh());
		fill(15,205,255,80);
		stroke(255,40);
		if (showMesh)
			gfx.mesh(model.getMesh(),true);

		
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
		
		if (saveMesh){
//			saveMesh(model.getMesh());
//			
//			if (mesh.getNumFaces() != 0)
//				saveMesh(mesh);

			// save out the 3D paths of the agents
			if (saveCount == 0 || saveCount == 3 || saveCount >= 6)
				save();
			saveCount++;
			
			// just in case we forgot!
			saveMesh = false;
		}
		
		if (!freeze)
			physics.update();
		
		if (voxelize)
			voxelizeMesh();
		
		if (mesh.getNumFaces() != 0)
			gfx.mesh(mesh);
		
//		System.out.println("Time: "+millis()/1000.0);
	}

	
	public void keyPressed(){
	
			if (key == 's')
				saveMesh = !saveMesh;
			
	//		if (key == 'd')
	//			debugMode = !debugMode;
	
			if (key == 'f' || key == 'F')
				freeze = !freeze;
			
	//		if (key == 'v')
	//			voxelize = !voxelize;
			
			if (key == 'r' || key == 'R')
				model.reset();
			
			if (key == 'l' || key == 'L'){
				isFlipped = !isFlipped;
				arm.setFlip(isFlipped);			
			}
			
			if (key == 'b' || key == 'B'){
				model.heatMap.blur();
			}
			
			if (key == 'g' || key == 'G'){
				model.generate();
			}
			
			if (key == 'w' || key == 'W'){
				showMesh = !showMesh;
			}
			
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
        cam.rotateY(radians(-45));
        cam.rotateX(radians(45));
        
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
	 * The modeling hand is the last 4 particles in the physics world.
	 */
	private void rigHand() {
				
		float stiff = 1f;
		
		// connect the edges of the wrist to the  center (at a lower resolution)
		VerletParticle p8  = new VerletParticle(-5,0,0);		// scan
		VerletParticle p9  = new VerletParticle(-5,0,-2);		// modeling
		VerletParticle p10 = new VerletParticle(5,0,0);			// scan
		VerletParticle p11 = new VerletParticle(5,0,-2);		// modeling
		
		physics.addParticle(p8);		
		physics.addParticle(p9);
		physics.addParticle(p10);
		physics.addParticle(p11);	
	
		// add weight to the modeling points
		p9.setWeight(10);		
		p11.setWeight(10);
	
		// connect with springs
		physics.addSpring(new VerletSpring(p8,p9,1,stiff));
		physics.addSpring(new VerletSpring(p10,p11,1,stiff));
	
	}
	
	

	/**
	 * Updates the particles of the modeling hand (if detected).		<br/>
	 * If not detected, it locks them in place.							<br/>
	 * [ .... you can use isLocked() to check and see if they're activated].
	 */
	private void updateHand(){
		
		
		if (hand[0] == null && !physics.particles.get(8).isLocked()){
			physics.particles.get(8).lock();
			physics.particles.get(9).lock();
		}
		else if (hand[0] != null){
			
			if (physics.particles.get(8).isLocked()){
				physics.particles.get(8).unlock();
				physics.particles.get(9).unlock();
			}
			
			// check if we have a NaN error ... not ideal
			if (Float.isNaN(physics.particles.get(9).x)){
				VerletParticle p = new VerletParticle(hand[0].x, hand[0].y, hand[0].z - 10);
				p.setWeight(10);
				physics.particles.set(9,p);
				physics.springs.get(physics.springs.size()-2).b = physics.particles.get(9);
			}
			
			// update scan position ... not ideal
			physics.particles.get(8).set(hand[0]);
		}
		
		if (hand[1] == null && !physics.particles.get(10).isLocked()){
			physics.particles.get(10).lock();
			physics.particles.get(11).lock();
		}
		else if (hand[1] != null){

			if (physics.particles.get(10).isLocked()){
				physics.particles.get(10).unlock();
				physics.particles.get(11).unlock();
			}

			// check if we have a NaN error
			if (Float.isNaN(physics.particles.get(11).x)){
				VerletParticle p = new VerletParticle(hand[1].x, hand[1].y, hand[1].z - 10);
				p.setWeight(10);
				physics.particles.set(11,p);
				physics.springs.get(physics.springs.size()-1).b = physics.particles.get(11);
			}

			// update position
			physics.particles.get(10).set(hand[1]);

			/*
			 * MODIFY THE HEATMAP BASED ON TOUCH
			 */

			Vec3D v0 = physics.particles.get(11);
			Vertex v1 =  model.getMesh().getClosestVertexToPoint(physics.particles.get(11));


			strokeWeight(5);
			stroke(255,0,0,50);
			int res = 60;			

			if (v0 != null){
				for (Vertex v : getClosestVertices(model.getMesh(), v0, 10)){

					// Visualize the connection between the hand and the mesh
					line(v0.x,v0.y,v0.z,v.x,v.y,v.z);

					int index = -1;
					int x, y;
					if (v1.id % 2 != 0){
						y = (v.id) / res;
						x = (v.id) % res;						
					}
					else{
						y = (v.id) / res - 1;
						x = (v.id) % res;	
					}	

					index = x + y*res;

					if (index > 0 && index < model.heatMap.getCells().size()){
						model.heatMap.incrementColor(index);
					}

				}
			}

		}
	}
		

	/**
	 * Helper function to get a number of vertices within a given radius of the 
	 * hand point.
	 * @param m 		- mesh to search through
	 * @param p			- scan point to search from
	 * @param radius	- radius of interest
	 * @return
	 */
	private ArrayList<Vertex> getClosestVertices(TriangleMesh m, Vec3D p, int radius){
		ArrayList<Vertex> temp = new ArrayList<Vertex>();
		
		for (Vertex v : m.getVertices()){
			
			if (v.distanceToSquared(p) < radius*radius)
				temp.add(v);
			
		}
		
		
		return temp;
	}

	private void showHandPoints() {
		noStroke();
		
		if (isTouching)
			fill(188,156,90);
		else
			fill(204,32,193);
		
		if (hand[0] != null){			
			pushMatrix();
			translate(physics.particles.get(7).x,physics.particles.get(1).y,physics.particles.get(1).z);
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
	
	private int saveCount = 0;
	private void save(){
		String name = month() + day() + year() + "_" + hour() + "-" + minute() + "-" + second()+"agents_blur"+saveCount;
		PrintWriter output = createWriter(sketchPath("src/data/lines/"+name+".txt")); 
		println(model.structure.size()+" agents to save");
		for (Mycelium agent : model.structure){
			String agentPath = "";
			for (Vec3D v : agent.path3D){
				String s = "{"+v.x+","+v.y+","+v.z+"} ";
				agentPath += s;
			}

			// each agent @ 300 points each	on a new line
			output.println(agentPath);
		}

		output.flush();
		output.close();
		
		saveFrame(sketchPath("src/data/lines/"+name+".png"));

		System.err.println("Sucessfully saved out to: " + name);
		
		
		saveMesh = false;
	}
	
	
	
	/**
	 * Saves an .stl file to theh <i>src/data/prints</i> folder
	 */
	
	private void saveMesh(Mesh3D m){

		
		
//		// Make path to save stl
//		String path = sketchPath("")+ "src/data/prints/3dPrint_v." + month() + day()
//				+ year() + "_" + hour() + "-" + minute() + "-" + second()+".stl";
//
//		m.flipVertexOrder(); // faces outwards
//		if (m instanceof TriangleMesh)
//			((TriangleMesh) m).saveAsSTL(sketchPath(path));
//		m.flipVertexOrder(); // put back to normal
		saveMesh = false;

		
	}
	
	private void voxelizeMesh(){
		
		mesh = new WETriangleMesh();

		Vec3D extent = arm.getMesh().getBoundingBox().getExtent();	
		
		int voxelRes = 100;
		
		float maxAxis = max(extent.x, extent.y, extent.z);
		
		int resX = (int) (extent.x / maxAxis * 2 * voxelRes);
		int resY = (int) (extent.y / maxAxis * voxelRes) / 2;
		int resZ = (int) (extent.z / maxAxis * 2 * voxelRes);
		
		MeshLatticeBuilder builder = new MeshLatticeBuilder(extent.scale(2),
				resX, resY, resZ, new FloatRange(.01f, .01f));
		
		
		builder.setInputBounds(new AABB(arm.getMesh().getBoundingBox(), extent.scale(1.1f)));
		
		VolumetricBrush brush = new BoxBrush(builder.getVolume(), .33f);
		brush.setSize(0);
		brush.setMode(VolumetricBrush.MODE_PEAK);
		
		println("voxel size: "+builder.getVolume().voxelSize);
		
		
		// MESH SHOULD BE REORIENTED TOWARDS THE ORIGIN BEFORE CREATING LATTICE
		
		
		List<Face> faces = arm.getMesh().getFaces();
		for (Face f : faces){
			
			// create a Line3D of the face edge
			Line3D segment = new Line3D(f.a, f.b);
//			builder.createLattice(brush, segment, 1);
//			segment = new Line3D(f.b, f.c);
//			builder.createLattice(brush, segment, 1);
			segment = new Line3D(f.c, f.a);
			builder.createLattice(brush, segment, 1);
		
		}
		
		builder.getVolume().closeSides();
		
		
		new HashIsoSurface(builder.getVolume()).computeSurfaceMesh(mesh, 0.66f);
		new LaplacianSmooth().filter((WETriangleMesh) mesh, 4);


		voxelize = false;
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
	 * Sends a (+/-) percentage turned of the wrist to the arm.
	 * 
	 * @param percentage
	 */
//	public void setWristRotation(float percentage){		
//		if (arm != null)			
//			arm.setWristRotation(percentage);			
//	}
	
	
	/**
	 * Sends two points
	 * @param pts
	 */
	public void setWristRotationPts(PVector[] pts){
		if (arm != null){	
			Vec3D[] points = new Vec3D[2];
			points[0] = new Vec3D(pts[0].x,pts[0].y,-1*pts[0].z);
			points[1] = new Vec3D(pts[1].x,pts[1].y,-1*pts[1].z);
	
			arm.setWristRotationPts(points);
		}
	}
	
	/**
	 * Load the 3D points of the wrist and elbow from the DepthCAM
	 * @param pts - wrist and elbow scan points
	 */
	public void setCanvasArea(Float[] lerp){
		
		if (lerp != null && arm != null)
			arm.lerp = lerp;
		
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
				
				// lower the Z a tad to better correlate with the actual hand pos
				hand[i] = new Vec3D(pts[i].x, pts[i].y, pts[i].z - 15);
				Vec3D offset = Vec3D.ZERO.sub(arm.getCentroid()); // I think this is the wrong centroid
				hand[i].addSelf(offset);	
				
				isHandDetected = true;
				
			}
		}
		
		
		if (hand[0] == null && hand[1] == null && hand[2] == null && 
			hand[3] == null && hand[4] == null)
			isHandDetected = false;			
		
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
		// override for now
		arm.isScaling(false);
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
