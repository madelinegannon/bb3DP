package gui;

import java.util.ArrayList;
import java.util.List;

import processing.core.PApplet;
import processing.core.PVector;
import toxi.geom.Vec3D;
import toxi.geom.mesh.Face;
import toxi.geom.mesh.TriangleMesh;
import toxi.physics.VerletParticle;
import toxi.physics.VerletSpring;
import toxi.physics.behaviors.AttractionBehavior;

/**
 * Parametric model to generate around the Arm.
 * 
 * @author mad
 */
public class Model {

	private GUI_3D p5;
	private Arm arm;
	
	private PVector pointer = new PVector();
	
	private Vec3D medialAxis = new Vec3D(-1,-1,-1);
	
	private ArrayList<ArrayList<Vec3D>> ptList = new ArrayList<ArrayList<Vec3D>>();
	private TriangleMesh mesh = new TriangleMesh();	
	private int res = 10;
	
	ArrayList<SrfModule> modules = new ArrayList<SrfModule>();
	
	private boolean isInitialized = false;

	
	public Model(GUI_3D p5, Arm arm) {
		this.p5 = p5;
		this.arm = arm;
	}
	
	public void update(ArrayList<Vec3D> wrist, ArrayList<Vec3D> elbow){
			
		ptList = generateSrf(wrist,elbow);
		this.mesh = meshSrf(ptList);
		
		mesh.computeFaceNormals();
		List<Face> faces = mesh.getFaces();
		
		buildOuterShell(faces, wrist, elbow);
		this.mesh.addMesh(buildInnerShell());
		
		// update interior arm particles
		p5.physics.particles.get(1).set(wrist.get(0));
		p5.physics.particles.get(2).set(elbow.get(0));
		
		
		p5.stroke(0,255,255);
		if (pointer.x != 0 && pointer.y != 0 && pointer.z != 0){
			// update the position of the finger
			p5.physics.particles.get(0).set(new Vec3D(pointer.x,pointer.y,pointer.z));
		}
	}

	/**
	 * Uses modules to create a reactive outer layer ... will need to be customized to how modules are generated.
	 * 
	 * @param faces - faces of the arm mesh
	 * @param wrist	- list of wrist points
	 * @param elbow - list of elbow points
	 */
	private void buildOuterShell(List<Face> faces, ArrayList<Vec3D> wrist, ArrayList<Vec3D> elbow) {
		
		if (!isInitialized ){
			
			// add one attraction particle at the beginning for the pointer finger
			VerletParticle p = new VerletParticle(0, 0, 0);
			p5.physics.addParticle(p);
			p.setWeight(10);
			p5.physics.addBehavior(new AttractionBehavior(p,50,-5));
			
			// add two particles to repel points away from inside of arm
			p = new VerletParticle(wrist.get(0));
			p5.physics.addParticle(p);
			p.setWeight(2);
			p5.physics.addBehavior(new AttractionBehavior(p,wrist.get(0).distanceTo(elbow.get(0))/2,-1));
			
			p = new VerletParticle(elbow.get(0));
			p5.physics.addParticle(p);
//			p.setWeight(2);
			p5.physics.addBehavior(new AttractionBehavior(p,wrist.get(0).distanceTo(elbow.get(0))/2,-1));
			

			for (int i=0; i<faces.size(); i+=2){ // i+=2 because 1 normal faces in and one faces out
				Vec3D start;
				
				Vec3D n = faces.get(i).normal;
				if (i >= faces.size() - 2*res)
					start = faces.get(i).c;
				else
					start = faces.get(i+res).a;
				modules.add(new SrfModule(p5.physics,start,n));	
			}
						
			isInitialized = true;
		}

		// update anchor points
		if (faces.size() <= 2*res*res){ // sometimes faces.size() jumps by another section (??)
			for (int i=0; i<faces.size(); i+=2){
				Vec3D anchor;
				if (i >= faces.size() - 2*res)
					anchor = faces.get(i).c;
				else
					anchor = faces.get(i+2*res).a;

					modules.get(i/2).update(anchor); // don't know why this is going OutOfBounds ?
			}
		}

		// build mesh
		TriangleMesh temp = new TriangleMesh();
		for (int i=1; i<ptList.size()-1; i++){
			Vec3D v0, v1, v2, v3, v4;		
			for (int j=0; j<ptList.get(1).size(); j++){

				int index = (i-1)*ptList.get(1).size() + j ;							

				if (j == ptList.get(1).size()-1){					
					v1 = ptList.get(i).get(j);
					v2 = ptList.get(i).get(0);
					v3 = ptList.get(i+1).get(0);	
					v4 = ptList.get(i+1).get(j);				
				}
				
				else{
					v1 = ptList.get(i).get(j);
					v2 = ptList.get(i).get(j+1);
					v3 = ptList.get(i+1).get(j+1);	
					v4 = ptList.get(i+1).get(j);
				}
				
				v0 = p5.physics.particles.get(modules.get(index).getHead());	

				temp.addFace(v2,v1,v0);				
				temp.addFace(v0,v4,v3);
				temp.addFace(v1,v4,v0);						
				temp.addFace(v2,v0,v3);			
				
			}		
		}
		
		this.mesh = temp;
		
//		p5.noStroke();
//		p5.fill(0,204,135);
//		p5.gfx().mesh(temp,true,1);
		
	}

	private TriangleMesh buildInnerShell() {
		
		TriangleMesh temp = new TriangleMesh();
		
		ArrayList<Vec3D> w = new ArrayList<Vec3D>();
		ArrayList<Vec3D> e = new ArrayList<Vec3D>();
		
		for (int i=0; i<ptList.get(0).size(); i++){
			Vec3D v = new Vec3D(ptList.get(0).get(i));
			w.add(v);
			v = new Vec3D(ptList.get(ptList.size()-1).get(i));
			e.add(v);
		}
		
		float scalar = 1/1.1f; // scalar determines thickness of edge
		
		Vec3D wTrans = new Vec3D(arm.wristCP[1]);
		wTrans.scaleSelf(scalar);
		wTrans.subSelf(arm.wristCP[1]);
		
		Vec3D eTrans = new Vec3D(arm.elbowCP[1]);
		eTrans.scaleSelf(scalar);
		eTrans.subSelf(arm.elbowCP[1]);

		for (int i=0; i<w.size(); i++){			
			w.get(i).scaleSelf(scalar).subSelf(wTrans);
			e.get(i).scaleSelf(scalar).subSelf(eTrans);	
		}
		
		// build inner surface
		for (int i=0; i<w.size(); i++){
			Vec3D v0, v1, v2, v3;

			if (i == w.size()-1){
				v0 = w.get(i);
				v1 = w.get(0);
				v2 = e.get(i);
				v3 = e.get(0);
			}
			else{
				v0 = w.get(i);
				v1 = w.get(i+1);
				v2 = e.get(i);
				v3 = e.get(i+1);
			}
			
			temp.addFace(v0,v1,v2);
			temp.addFace(v2,v1,v3);			
		}
		
		// connect edges
		for (int i=0; i<w.size(); i++){
			
			Vec3D v0, v1, v2, v3;
			
			if (i == w.size()-1){
				v0 = w.get(i);
				v1 = w.get(0);
				v2 = ptList.get(0).get(i);
				v3 = ptList.get(0).get(0);
			}
			else{
				v0 = w.get(i);
				v1 = w.get(i+1);
				v2 = ptList.get(0).get(i);
				v3 = ptList.get(0).get(i+1);
			}
			
			temp.addFace(v2,v1,v0);
			temp.addFace(v3,v1,v2);
				
			if (i == w.size()-1){
				v0 = e.get(i);
				v1 = e.get(0);
				v2 = ptList.get(ptList.size()-1).get(i);
				v3 = ptList.get(ptList.size()-1).get(0);
			}
			else{
				v0 = e.get(i);
				v1 = e.get(i+1);
				v2 = ptList.get(ptList.size()-1).get(i);
				v3 = ptList.get(ptList.size()-1).get(i+1);
			}
			
			temp.addFace(v0,v1,v2);
			temp.addFace(v2,v1,v3);
			
		}

		return temp;
	}

	private TriangleMesh meshSrf(ArrayList<ArrayList<Vec3D>> pts) {
		TriangleMesh temp = new TriangleMesh();
		
		Vec3D v0, v1, v2, v3;

		for (int i=0; i<pts.size()-1; i++){
			for (int j=0; j<pts.get(1).size(); j++){
				
				if (j == pts.get(1).size()-1){
					v0 = pts.get(i).get(j);
					v1 = pts.get(i).get(0);
					v2 = pts.get(i+1).get(0);
					v3 = pts.get(i+1).get(j);					
				}
				else{
					v0 = pts.get(i).get(j);
					v1 = pts.get(i).get(j+1);
					v2 = pts.get(i+1).get(j+1);
					v3 = pts.get(i+1).get(j);
				}
				
				// don't know if normals are the correct direction
				temp.addFace(v0,v1,v3);
				temp.addFace(v2,v1,v3);
			
			}
		}
				
		return temp;

	}

	/**
	 * Update the position of the locked particles
	 */
	private void updateSpringSrf() {

		int xSpring = ptList.get(0).size() - 3*ptList.get(0).size()/4;
		int ySpring = ptList.size();
		
		int particleCounter = 0;
		for (int i=0; i<ySpring; i++){
			ArrayList<Vec3D> list = ptList.get(i);
			for (int j=0; j<xSpring; j++){
				int pIndex;
				int index  = list.size() - xSpring + j;
				
								
//				System.out.println("i: "+i);
//				System.out.println("j: "+j);
//				System.out.println("pIndex: "+pIndex);
//				System.out.println("index: "+index);
//				System.out.println();

				// lock edges
				if (i == 0 || i == ySpring-1 || j == 0 || j == xSpring-1){	
					pIndex = i*xSpring + j;
					
				}
				// tie down middle particles
				else{
					pIndex = particleIndices.get(particleCounter);
					particleCounter++;
				}
				
				p5.physics.particles.get(pIndex).x = list.get(index).x;
				p5.physics.particles.get(pIndex).y = list.get(index).y;
				p5.physics.particles.get(pIndex).z = list.get(index).z;
			}
		}
		
		
		
	}

	/** 
	 * Create a spring mesh over part of the surface mesh
	 */
	
	ArrayList<Integer> particleIndices = new ArrayList<Integer>();
	private void createSpringSrf() {

		float soft = .15f;
		float stiff = .95f;
		
		int xSpring = ptList.get(0).size() - 3*ptList.get(0).size()/4;
		int ySpring = ptList.size();

		for (int i=0; i<ySpring; i++){
			ArrayList<Vec3D> list = ptList.get(i);
			for (int j=0; j<xSpring; j++){
				
				int index = list.size() - xSpring + j;
				VerletParticle p = new VerletParticle(list.get(index));
				p5.physics.addParticle(p);
				
				// create horizontal springs
				if (j > 0){
					int pIndex = i*xSpring + j;
					VerletParticle p0 = p5.physics.particles.get(pIndex);
					VerletParticle p1 = p5.physics.particles.get(pIndex - 1);
					if (j == 1 || j == xSpring-1)
						p5.physics.addSpring(new VerletSpring(p1,p0,p1.distanceTo(p0)/2,stiff));
					else
						p5.physics.addSpring(new VerletSpring(p1,p0,p1.distanceTo(p0),soft));
				}
				
				// create vertical springs
				if (i>0){
					index = (i-1)*xSpring + j;
					VerletParticle p0 = p5.physics.particles.get(index);
					index = i*xSpring + j;
					VerletParticle p1 = p5.physics.particles.get(index);
					if (i == 1 || i == ySpring-1)
						p5.physics.addSpring(new VerletSpring(p1,p0,p1.distanceTo(p0)/2,stiff));
					else
						p5.physics.addSpring(new VerletSpring(p1,p0,p1.distanceTo(p0),soft));
				}						
			}
		}
		
		// connect middle particles to model with springs
		for (int i=0; i<ySpring; i++){
			ArrayList<Vec3D> list = ptList.get(i);
			for (int j=0; j<xSpring; j++){
				
				if (j>0 && j<xSpring-1 && i>0 && i<ySpring-1){
					// add additional particle
					int index = list.size() - xSpring + j;
					VerletParticle p0 = new VerletParticle(list.get(index));
					p5.physics.addParticle(p0);
					particleIndices.add(p5.physics.particles.size()-1);
					int pIndex = i*xSpring + j;
					VerletParticle p = p5.physics.particles.get(pIndex);
					p5.physics.addSpring(new VerletSpring(p,p0,2,stiff));
				}
				
			}
		}
		
		
		// add one attraction particle at the end for the pointer finger
		VerletParticle p = new VerletParticle(0, 0, 0);
		p5.physics.addParticle(p);
		p.setWeight(100);
		p5.physics.addBehavior(new AttractionBehavior(p,50,-50));
		
		isInitialized = true;
	}

	

	/**
	 * Re-sample wrist and elbow with a given resolution.
	 * Scale-self to offset from the arm surface.
	 * Interpolate to generate cross-sections.
	 * 
	 * @param wrist
	 * @param elbow
	 * @return
	 */
	private ArrayList<ArrayList<Vec3D>> generateSrf(ArrayList<Vec3D> wrist, ArrayList<Vec3D> elbow) {
		
		 ArrayList<ArrayList<Vec3D>> pts = new  ArrayList<ArrayList<Vec3D>>();
		
		int step = (wrist.size()-1)/res;
		
		ArrayList<Vec3D> w = new ArrayList<Vec3D>();
		ArrayList<Vec3D> e = new ArrayList<Vec3D>();
		
		float scalar = 1.1f;
		
		// remember, the first point is the center
		Vec3D wTrans = new Vec3D(wrist.get(0));
		wTrans.scaleSelf(scalar);
		wTrans.subSelf(wrist.get(0));
		
		Vec3D eTrans = new Vec3D(elbow.get(0));
		eTrans.scaleSelf(scalar);
		eTrans.subSelf(elbow.get(0));
		
		// resample points to lower resolution
		for (int i=0; i<wrist.size()-1; i+=step){ 
			Vec3D v = new Vec3D(wrist.get(i+1));			
			v.scaleSelf(scalar);
			v.subSelf(wTrans);
			w.add(v);
			
			v = new Vec3D(elbow.get(i+1));
			v.scaleSelf(scalar);
			v.subSelf(eTrans);
			e.add(v);
		}
		
		// interpolate curves
		pts.add(w);
		float uRes = res;
		for (int i=0; i<uRes; i++){
			float t = i/uRes;
			ArrayList<Vec3D> section = new ArrayList<Vec3D>();
			for (int j=0; j<w.size(); j++){
				
				Vec3D w0 = w.get(j);
				Vec3D e0 = e.get(j);
				
				float x = (e0.x - w0.x) * t + w0.x;
				float y = (e0.y - w0.y) * t + w0.y;
				float z = (e0.z - w0.z) * t + w0.z;
				
				section.add(new Vec3D(x,y,z));
								
			}
			pts.add(section);
		}
		
		pts.add(e);
		
//		for (ArrayList<Vec3D> list : pts){
//			p5.beginShape();
//			for (Vec3D v : list)
//				p5.vertex(v.x,v.y,v.z);
//			p5.endShape();
//		}		
		
		return pts;
	}

	
	/**
	 * Build a mesh over the top 1/4 of
	 * Should mirror the mesh in <i>Arm.java</i>
	 * 
	 * @return
	 */
	private TriangleMesh meshSprings(){
		TriangleMesh temp = new TriangleMesh();
		
		Vec3D v0, v1, v2, v3;
		
		int xSpring = ptList.get(0).size() - 3*ptList.get(0).size()/4;
		int ySpring = ptList.size();

		for (int i=0; i<ySpring-1; i++){

			for (int j=0; j<xSpring-1; j++){
			
				v0 = p5.physics.particles.get(i*xSpring + j);
				v1 = p5.physics.particles.get(i*xSpring + j+1);
				v2 = p5.physics.particles.get((i+1)*xSpring + j+1);
				v3 = p5.physics.particles.get((i+1)*xSpring + j);

				
				// don't know if normals are the correct direction
				temp.addFace(v0,v1,v3);
				temp.addFace(v2,v1,v3);
			
			}
		}
		
		// figure out reflection
//		http://stackoverflow.com/questions/3306838/algorithm-for-reflecting-a-point-across-a-line
		
		return temp;
	}
	
	
	
	public TriangleMesh getMesh(){
		return mesh;
	}
	
	/**
	 * Update the pointer finger with a new real world point.
	 * @param p - current rwp of pointer finger
	 */
	public void setPointer(PVector p){
		pointer = p;
	}
	
	
	public void setMedialAxis(Vec3D v){
		medialAxis = v;
	}
	
	
	
	
	
	
	
	
}
